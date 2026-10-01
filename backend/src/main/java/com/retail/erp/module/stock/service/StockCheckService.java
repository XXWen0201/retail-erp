package com.retail.erp.module.stock.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.erp.common.BizException;
import com.retail.erp.common.PageResult;
import com.retail.erp.common.enums.OrderStatusEnum;
import com.retail.erp.common.util.OrderNoGenerator;
import com.retail.erp.module.basic.entity.Product;
import com.retail.erp.module.basic.mapper.ProductMapper;
import com.retail.erp.module.stock.dto.StockCheckCreateDTO;
import com.retail.erp.module.stock.dto.StockCheckItemUpdateDTO;
import com.retail.erp.module.stock.dto.StockCheckQuery;
import com.retail.erp.module.stock.dto.StockCheckVO;
import com.retail.erp.module.stock.entity.Stock;
import com.retail.erp.module.stock.entity.StockCheck;
import com.retail.erp.module.stock.entity.StockCheckItem;
import com.retail.erp.module.stock.mapper.StockCheckItemMapper;
import com.retail.erp.module.stock.mapper.StockCheckMapper;
import com.retail.erp.module.stock.mapper.StockMapper;
import com.retail.erp.module.stock.mapper.StockPageMapper;
import com.retail.erp.module.system.mapper.OrderNoMapper;
import com.retail.erp.security.LoginUser;
import com.retail.erp.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 盘点服务
 *
 * 「盘点差异追溯」这个课题在这里落地，关键在于三点：
 *   1. 建账时把账面数拍成快照存进 book_quantity，事后再看单据能还原出
 *      「当时账上写的是多少」，哪怕库存表后来又被改过
 *   2. 每条差异都必须写 reason，且以 CHECK_GAIN / CHECK_LOSS 记入库存流水，
 *      这样差异不是凭空消失，而是变成一条可查的账
 *   3. 提交时以「实盘数」为准把账面调平，保证盘完账实必然一致
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockCheckService {

    private final StockCheckMapper checkMapper;
    private final StockCheckItemMapper itemMapper;
    private final StockPageMapper stockPageMapper;
    private final StockMapper stockMapper;
    private final ProductMapper productMapper;
    private final StockCoreService stockCoreService;
    private final OrderNoGenerator orderNoGenerator;
    private final OrderNoMapper orderNoMapper;

    // ==================================================================
    //  查询
    // ==================================================================

    public PageResult<StockCheckVO> page(StockCheckQuery query) {
        IPage<StockCheck> page = checkMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                Wrappers.<StockCheck>lambdaQuery()
                        .eq(StringUtils.hasText(query.getStatus()), StockCheck::getStatus, query.getStatus())
                        .ge(query.getStartDate() != null, StockCheck::getCheckDate, query.getStartDate())
                        .le(query.getEndDate() != null, StockCheck::getCheckDate, query.getEndDate())
                        .orderByDesc(StockCheck::getId));
        return PageResult.of(page).map(this::toVO);
    }

    public StockCheckVO detail(Long id) {
        StockCheck check = requireCheck(id);
        StockCheckVO vo = toVO(check);
        vo.setItems(stockPageMapper.selectCheckItems(id));
        return vo;
    }

    // ==================================================================
    //  建账
    // ==================================================================

    @Transactional(rollbackFor = Exception.class)
    public Long create(StockCheckCreateDTO dto) {
        StockCheck check = new StockCheck();
        check.setCheckNo(orderNoGenerator.next("PC", dto.checkDate(),
                orderNoMapper::selectMaxCheckSuffix));
        check.setStatus(OrderStatusEnum.DRAFT.name());
        check.setCheckDate(dto.checkDate());
        check.setRemark(dto.remark());
        check.setTotalDiffQuantity(0);
        check.setDiffItemCount(0);
        applyOperator(check);
        checkMapper.insert(check);

        List<Product> products = resolveProducts(dto.productIds());
        if (products.isEmpty()) {
            throw BizException.badRequest("没有可盘点的商品，请检查是否已录入商品档案");
        }

        for (Product product : products) {
            Stock stock = stockMapper.selectOne(Wrappers.<Stock>lambdaQuery()
                    .eq(Stock::getProductId, product.getId()));
            int bookQuantity = (stock == null || stock.getQuantity() == null) ? 0 : stock.getQuantity();

            StockCheckItem item = new StockCheckItem();
            item.setCheckId(check.getId());
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setBookQuantity(bookQuantity);
            // 实盘数默认等于账面数：操作员只需要改有差异的那几行，
            // 没数到的商品不会被误判成「盘亏为零」
            item.setActualQuantity(bookQuantity);
            item.setDiffQuantity(0);
            itemMapper.insert(item);
        }

        log.info("盘点建账完成 checkNo={} 商品数={}", check.getCheckNo(), products.size());
        return check.getId();
    }

    // ==================================================================
    //  录入实盘数
    // ==================================================================

    @Transactional(rollbackFor = Exception.class)
    public void updateItems(Long id, StockCheckItemUpdateDTO dto) {
        StockCheck check = requireDraftCheck(id);

        for (StockCheckItemUpdateDTO.Item input : dto.items()) {
            StockCheckItem item = itemMapper.selectById(input.id());
            if (item == null || !check.getId().equals(item.getCheckId())) {
                throw BizException.notFound("盘点明细 id=" + input.id());
            }
            item.setActualQuantity(input.actualQuantity());
            item.setDiffQuantity(input.actualQuantity() - item.getBookQuantity());
            item.setReason(input.reason());
            itemMapper.updateById(item);
        }

        recalculate(check);
        checkMapper.updateById(check);
    }

    // ==================================================================
    //  提交盘点（应用差异）
    // ==================================================================

    /**
     * 提交盘点：按差异调整库存
     *
     * 一个容易被忽略的正确性细节：调整量不是拿「实盘 - 建账时的账面快照」,
     * 而是拿「实盘 - 提交这一刻的真实库存」。
     *
     * 原因：从建账到提交往往隔了几个小时，期间门店可能还在卖货。
     * 如果按快照算，比如快照 100、期间卖出 5、实盘 95，
     * 按快照算差异是 -5，库存会从 95 再被扣 5 变成 90 —— 盘完之后账实还是不一致。
     * 按当前库存算差异是 0，正好把账面调成实盘 95，这才是盘点要达到的效果。
     *
     * 快照值 diff_quantity 依然原样保留在明细里，用于回答「这次盘出来差了多少」。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void finish(Long id) {
        StockCheck check = requireDraftCheck(id);

        List<StockCheckItem> items = itemMapper.selectList(Wrappers.<StockCheckItem>lambdaQuery()
                .eq(StockCheckItem::getCheckId, id)
                .orderByAsc(StockCheckItem::getId));

        int diffItemCount = 0;
        int totalDiffQuantity = 0;
        int adjustedCount = 0;

        for (StockCheckItem item : items) {
            int reportedDiff = item.getDiffQuantity() == null ? 0 : item.getDiffQuantity();
            if (reportedDiff != 0) {
                diffItemCount++;
                totalDiffQuantity += Math.abs(reportedDiff);
            }

            Product product = productMapper.selectById(item.getProductId());
            if (product == null) {
                // 商品被删了（逻辑删除后 selectById 返回 null），跳过而不是整单失败，
                // 否则一张历史单据会把盘点功能永久卡死
                log.warn("盘点明细对应的商品已不存在，跳过 productId={} checkNo={}",
                        item.getProductId(), check.getCheckNo());
                continue;
            }

            int current = currentQuantity(product.getId());
            int actual = item.getActualQuantity() == null ? 0 : item.getActualQuantity();
            int applyDelta = actual - current;

            if (current != item.getBookQuantity()) {
                log.info("盘点期间库存发生变化 checkNo={} product={} 账面快照={} 当前={} 实盘={}",
                        check.getCheckNo(), product.getName(),
                        item.getBookQuantity(), current, actual);
            }
            if (applyDelta == 0) {
                continue;
            }

            String reason = StringUtils.hasText(item.getReason())
                    ? item.getReason() : (applyDelta > 0 ? "盘盈" : "盘亏");

            stockCoreService.adjust(new StockCoreService.AdjustCmd(
                    product.getId(),
                    product.getName(),
                    applyDelta,
                    currentAvgCost(product.getId()),
                    check.getCheckNo(),
                    check.getId(),
                    "盘点调整：" + reason));
            adjustedCount++;
        }

        check.setStatus(OrderStatusEnum.FINISHED.name());
        check.setFinishTime(LocalDateTime.now());
        check.setDiffItemCount(diffItemCount);
        check.setTotalDiffQuantity(totalDiffQuantity);
        checkMapper.updateById(check);

        log.info("盘点提交完成 checkNo={} 差异商品数={} 调整商品数={}",
                check.getCheckNo(), diffItemCount, adjustedCount);
    }

    // ==================================================================
    //  作废
    // ==================================================================

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        StockCheck check = requireCheck(id);
        if (OrderStatusEnum.FINISHED.is(check.getStatus())) {
            // 已完成盘点的库存调整已经通过流水落账，撤销它等于手工改账。
            // 正确做法是再做一次盘点，把数量数回来 —— 这样两次调整都有据可查。
            throw BizException.statusIllegal("已完成的盘点单不能作废，如需修正请重新建一张盘点单");
        }
        check.setStatus(OrderStatusEnum.CANCELED.name());
        checkMapper.updateById(check);
    }

    // ==================================================================
    //  内部方法
    // ==================================================================

    private List<Product> resolveProducts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            // 不传商品 = 全盘
            return productMapper.selectList(Wrappers.<Product>lambdaQuery()
                    .eq(Product::getStatus, 1)
                    .orderByAsc(Product::getId));
        }
        return productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .in(Product::getId, productIds)
                .orderByAsc(Product::getId));
    }

    private void recalculate(StockCheck check) {
        List<StockCheckItem> items = itemMapper.selectList(Wrappers.<StockCheckItem>lambdaQuery()
                .eq(StockCheckItem::getCheckId, check.getId()));
        int count = 0;
        int total = 0;
        for (StockCheckItem item : items) {
            int diff = item.getDiffQuantity() == null ? 0 : item.getDiffQuantity();
            if (diff != 0) {
                count++;
                total += Math.abs(diff);
            }
        }
        check.setDiffItemCount(count);
        check.setTotalDiffQuantity(total);
    }

    private int currentQuantity(Long productId) {
        Integer qty = stockPageMapper.selectQuantityByProductId(productId);
        return qty == null ? 0 : qty;
    }

    private BigDecimal currentAvgCost(Long productId) {
        Stock stock = stockMapper.selectOne(Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getProductId, productId));
        if (stock == null || stock.getAvgCost() == null) {
            return BigDecimal.ZERO;
        }
        return stock.getAvgCost();
    }

    private StockCheck requireCheck(Long id) {
        StockCheck check = checkMapper.selectById(id);
        if (check == null) {
            throw BizException.notFound("盘点单");
        }
        return check;
    }

    /** 只有草稿状态的盘点单可以录入与提交 */
    private StockCheck requireDraftCheck(Long id) {
        StockCheck check = requireCheck(id);
        if (OrderStatusEnum.FINISHED.is(check.getStatus())) {
            throw BizException.statusIllegal("该盘点单已提交完成，不能重复提交");
        }
        if (OrderStatusEnum.CANCELED.is(check.getStatus())) {
            throw BizException.statusIllegal("该盘点单已作废");
        }
        return check;
    }

    private void applyOperator(StockCheck check) {
        LoginUser operator = UserContext.get();
        if (operator != null) {
            check.setOperatorId(operator.getUserId());
            check.setOperatorName(operator.getRealName());
        }
    }

    private StockCheckVO toVO(StockCheck check) {
        StockCheckVO vo = new StockCheckVO();
        vo.setId(check.getId());
        vo.setCheckNo(check.getCheckNo());
        vo.setStatus(check.getStatus());
        vo.setStatusLabel(OrderStatusEnum.labelOf(check.getStatus()));
        vo.setCheckDate(check.getCheckDate());
        vo.setTotalDiffQuantity(check.getTotalDiffQuantity());
        vo.setDiffItemCount(check.getDiffItemCount());
        vo.setOperatorName(check.getOperatorName());
        vo.setFinishTime(check.getFinishTime());
        vo.setRemark(check.getRemark());
        return vo;
    }
}
