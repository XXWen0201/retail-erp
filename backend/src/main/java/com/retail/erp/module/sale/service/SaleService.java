package com.retail.erp.module.sale.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.erp.common.BizException;
import com.retail.erp.common.ErrorCode;
import com.retail.erp.common.PageResult;
import com.retail.erp.common.enums.BizTypeEnum;
import com.retail.erp.common.enums.OrderStatusEnum;
import com.retail.erp.common.util.OrderNoGenerator;
import com.retail.erp.module.basic.entity.Product;
import com.retail.erp.module.basic.mapper.ProductMapper;
import com.retail.erp.module.sale.dto.SaleItemDTO;
import com.retail.erp.module.sale.dto.SaleItemVO;
import com.retail.erp.module.sale.dto.SaleQuery;
import com.retail.erp.module.sale.dto.SaleSaveDTO;
import com.retail.erp.module.sale.dto.SaleVO;
import com.retail.erp.module.sale.entity.SaleOrder;
import com.retail.erp.module.sale.entity.SaleOrderItem;
import com.retail.erp.module.sale.mapper.SaleOrderItemMapper;
import com.retail.erp.module.sale.mapper.SaleOrderMapper;
import com.retail.erp.module.stock.dto.BatchDeductResult;
import com.retail.erp.module.stock.service.StockCoreService;
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
import java.util.List;

/**
 * 销售服务
 *
 * 销售单是「保存即出库」：点下确认的那一刻，批次库存、总库存、流水
 * 就必须全部更新完。这与采购单「先存草稿、后入库」的节奏正好相反，
 * 因为门店收银是现场交易，货当场就被客人拿走了，不存在「待出库」这个中间态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleOrderMapper orderMapper;
    private final SaleOrderItemMapper itemMapper;
    private final ProductMapper productMapper;
    private final StockCoreService stockCoreService;
    private final OrderNoGenerator orderNoGenerator;
    private final OrderNoMapper orderNoMapper;

    // ==================================================================
    //  查询
    // ==================================================================

    public PageResult<SaleVO> page(SaleQuery query) {
        IPage<SaleOrder> page = orderMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                Wrappers.<SaleOrder>lambdaQuery()
                        .and(StringUtils.hasText(query.getKeyword()), w -> w
                                .like(SaleOrder::getOrderNo, query.getKeyword())
                                .or().like(SaleOrder::getCustomerName, query.getKeyword()))
                        .eq(StringUtils.hasText(query.getStatus()), SaleOrder::getStatus, query.getStatus())
                        .ge(query.getStartDate() != null, SaleOrder::getOrderDate, query.getStartDate())
                        .le(query.getEndDate() != null, SaleOrder::getOrderDate, query.getEndDate())
                        .orderByDesc(SaleOrder::getId));
        return PageResult.of(page).map(this::toVO);
    }

    public SaleVO detail(Long id) {
        SaleOrder order = requireOrder(id);
        SaleVO vo = toVO(order);
        vo.setItems(itemsOf(id).stream().map(this::toItemVO).toList());
        return vo;
    }

    // ==================================================================
    //  开单出库（核心）
    // ==================================================================

    /**
     * 开单并出库
     *
     * 整个方法在一个事务里：任何一条明细库存不足，整单回滚，
     * 不会出现「A 商品扣了、B 商品没扣」的半截单据。
     * 小票已经打给客人了，单据却是半截的，这种数据比直接失败更麻烦。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Long create(SaleSaveDTO dto) {
        SaleOrder order = new SaleOrder();
        order.setOrderNo(orderNoGenerator.next("SO", dto.orderDate(),
                orderNoMapper::selectMaxSaleSuffix));
        order.setCustomerName(dto.customerNameOrDefault());
        order.setStatus(OrderStatusEnum.FINISHED.name());
        order.setOrderDate(dto.orderDate());
        order.setRemark(dto.remark());
        order.setDiscountAmount(dto.discountOrDefault());
        applyOperator(order);
        order.setTotalQuantity(0);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setPayAmount(BigDecimal.ZERO);
        order.setTotalCost(BigDecimal.ZERO);
        order.setGrossProfit(BigDecimal.ZERO);
        orderMapper.insert(order);

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        int totalQuantity = 0;

        for (SaleItemDTO itemDTO : dto.items()) {
            Product product = requireOnSaleProduct(itemDTO.productId());

            // 出库：FEFO 挑批次扣减，返回实际扣掉的批次明细
            List<BatchDeductResult> deducted = stockCoreService.outbound(
                    new StockCoreService.OutboundCmd(
                            product.getId(),
                            product.getName(),
                            itemDTO.quantity(),
                            BizTypeEnum.SALE_OUT,
                            order.getOrderNo(),
                            order.getId(),
                            "销售出库"));

            // 一个商品可能跨多个批次，于是拆成多条明细 ——
            // 每条明细挂一个批次，成本才精确，退货时也才能原路退回原批次
            for (BatchDeductResult r : deducted) {
                SaleOrderItem item = new SaleOrderItem();
                item.setOrderId(order.getId());
                item.setProductId(product.getId());
                item.setProductName(product.getName());
                item.setBatchId(r.getBatchId());
                item.setBatchNo(r.getBatchNo());
                item.setQuantity(r.getQuantity());
                item.setPrice(itemDTO.price());
                item.setAmount(itemDTO.price().multiply(BigDecimal.valueOf(r.getQuantity())));
                item.setCostPrice(r.getCostPrice());
                item.setCostAmount(r.getCostPrice().multiply(BigDecimal.valueOf(r.getQuantity())));
                itemMapper.insert(item);

                totalAmount = totalAmount.add(item.getAmount());
                totalCost = totalCost.add(item.getCostAmount());
                totalQuantity += r.getQuantity();
            }
        }

        BigDecimal discount = dto.discountOrDefault();
        if (discount.compareTo(totalAmount) > 0) {
            throw BizException.badRequest("优惠金额 " + discount + " 超过了应收金额 " + totalAmount);
        }
        BigDecimal payAmount = totalAmount.subtract(discount);

        order.setTotalQuantity(totalQuantity);
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(discount);
        order.setPayAmount(payAmount);
        order.setTotalCost(totalCost);
        // 毛利按实收减成本算：优惠是真实让利，必须从毛利里扣掉，
        // 否则报表上的毛利会比实际赚到的钱多出一笔折扣
        order.setGrossProfit(payAmount.subtract(totalCost));
        orderMapper.updateById(order);

        log.info("销售开单完成 orderNo={} 明细数={} 金额={} 毛利={}",
                order.getOrderNo(), dto.items().size(), payAmount, order.getGrossProfit());
        return order.getId();
    }

    // ==================================================================
    //  作废回滚
    // ==================================================================

    /**
     * 作废销售单并回滚库存
     *
     * 回滚必须精确回到原来的批次。如果像盘盈那样新建一个批次，
     * 退回来的临期商品会被当成新货重新上架，保质期管理就废了。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void cancel(Long id) {
        SaleOrder order = requireOrder(id);
        if (OrderStatusEnum.CANCELED.is(order.getStatus())) {
            throw BizException.statusIllegal("该销售单已经作废过了");
        }

        List<SaleOrderItem> items = itemsOf(id);
        for (SaleOrderItem item : items) {
            if (item.getBatchId() == null) {
                // 极老的数据可能没有批次信息（早期版本没记批次），
                // 这时只能按商品粒度回补，不能再挑批次
                throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL,
                        "明细【" + item.getProductName() + "】没有绑定出库批次，无法自动回滚，请使用盘点调整");
            }
            stockCoreService.restoreToBatch(new StockCoreService.RestoreCmd(
                    item.getProductId(),
                    item.getBatchId(),
                    item.getQuantity(),
                    BizTypeEnum.SALE_CANCEL_ROLLBACK,
                    order.getOrderNo(),
                    order.getId(),
                    "销售单作废回滚"));
        }

        order.setStatus(OrderStatusEnum.CANCELED.name());
        orderMapper.updateById(order);
        log.info("销售单作废并回滚库存 orderNo={} 明细数={}", order.getOrderNo(), items.size());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SaleOrder order = requireOrder(id);
        if (!OrderStatusEnum.CANCELED.is(order.getStatus())) {
            // 只有作废后（库存已回滚）才允许删除，否则会留下一笔「看不到的扣减」
            throw BizException.statusIllegal("请先作废销售单，库存回滚后再删除");
        }
        itemMapper.delete(Wrappers.<SaleOrderItem>lambdaQuery()
                .eq(SaleOrderItem::getOrderId, id));
        orderMapper.deleteById(id);
    }

    // ==================================================================
    //  内部方法
    // ==================================================================

    /** 可售商品校验：停售商品不允许开单，避免把已下架的东西卖出去 */
    private Product requireOnSaleProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw BizException.notFound("商品");
        }
        if (product.getStatus() != null && product.getStatus() != 1) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL,
                    "商品【" + product.getName() + "】已停售，不能销售");
        }
        return product;
    }

    private List<SaleOrderItem> itemsOf(Long orderId) {
        return itemMapper.selectList(Wrappers.<SaleOrderItem>lambdaQuery()
                .eq(SaleOrderItem::getOrderId, orderId)
                .orderByAsc(SaleOrderItem::getId));
    }

    private SaleOrder requireOrder(Long id) {
        SaleOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw BizException.notFound("销售单");
        }
        return order;
    }

    private void applyOperator(SaleOrder order) {
        LoginUser operator = UserContext.get();
        if (operator != null) {
            order.setOperatorId(operator.getUserId());
            order.setOperatorName(operator.getRealName());
        }
    }

    private SaleVO toVO(SaleOrder order) {
        SaleVO vo = new SaleVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setCustomerName(order.getCustomerName());
        vo.setTotalQuantity(order.getTotalQuantity());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setDiscountAmount(order.getDiscountAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setTotalCost(order.getTotalCost());
        vo.setGrossProfit(order.getGrossProfit());
        vo.setStatus(order.getStatus());
        vo.setStatusLabel(OrderStatusEnum.labelOf(order.getStatus()));
        vo.setOrderDate(order.getOrderDate());
        vo.setOperatorName(order.getOperatorName());
        vo.setRemark(order.getRemark());
        return vo;
    }

    private SaleItemVO toItemVO(SaleOrderItem item) {
        SaleItemVO vo = new SaleItemVO();
        vo.setId(item.getId());
        vo.setProductId(item.getProductId());
        vo.setProductName(item.getProductName());
        vo.setBatchId(item.getBatchId());
        vo.setBatchNo(item.getBatchNo());
        vo.setQuantity(item.getQuantity());
        vo.setPrice(item.getPrice());
        vo.setAmount(item.getAmount());
        vo.setCostPrice(item.getCostPrice());
        vo.setCostAmount(item.getCostAmount());
        return vo;
    }
}
