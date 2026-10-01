package com.retail.erp.module.purchase.service;

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
import com.retail.erp.module.basic.entity.Supplier;
import com.retail.erp.module.basic.mapper.ProductMapper;
import com.retail.erp.module.basic.mapper.SupplierMapper;
import com.retail.erp.module.purchase.dto.PurchaseItemDTO;
import com.retail.erp.module.purchase.dto.PurchaseItemVO;
import com.retail.erp.module.purchase.dto.PurchaseQuery;
import com.retail.erp.module.purchase.dto.PurchaseSaveDTO;
import com.retail.erp.module.purchase.dto.PurchaseVO;
import com.retail.erp.module.purchase.entity.PurchaseOrder;
import com.retail.erp.module.purchase.entity.PurchaseOrderItem;
import com.retail.erp.module.purchase.mapper.PurchaseOrderItemMapper;
import com.retail.erp.module.purchase.mapper.PurchaseOrderMapper;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购服务
 *
 * 单据状态机：DRAFT（草稿）→ FINISHED（已入库）
 *                      ↘ CANCELED（作废）
 *
 * 只有 FINISHED 才代表货真的进了仓库、库存和批次已经生成。
 * 草稿阶段改多少遍都不影响库存，这是把「录单」和「入库」分开的意义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper itemMapper;
    private final SupplierMapper supplierMapper;
    private final ProductMapper productMapper;
    private final StockCoreService stockCoreService;
    private final OrderNoGenerator orderNoGenerator;
    private final OrderNoMapper orderNoMapper;

    // ==================================================================
    //  查询
    // ==================================================================

    public PageResult<PurchaseVO> page(PurchaseQuery query) {
        IPage<PurchaseOrder> page = orderMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                Wrappers.<PurchaseOrder>lambdaQuery()
                        .like(StringUtils.hasText(query.getKeyword()),
                                PurchaseOrder::getOrderNo, query.getKeyword())
                        .eq(query.getSupplierId() != null, PurchaseOrder::getSupplierId, query.getSupplierId())
                        .eq(StringUtils.hasText(query.getStatus()), PurchaseOrder::getStatus, query.getStatus())
                        .ge(query.getStartDate() != null, PurchaseOrder::getOrderDate, query.getStartDate())
                        .le(query.getEndDate() != null, PurchaseOrder::getOrderDate, query.getEndDate())
                        .orderByDesc(PurchaseOrder::getId));

        return PageResult.of(page).map(this::toVO);
    }

    public PurchaseVO detail(Long id) {
        PurchaseOrder order = requireOrder(id);
        PurchaseVO vo = toVO(order);
        vo.setItems(itemsOf(id).stream().map(this::toItemVO).toList());
        return vo;
    }

    // ==================================================================
    //  录单
    // ==================================================================

    @Transactional(rollbackFor = Exception.class)
    public Long create(PurchaseSaveDTO dto) {
        Supplier supplier = requireSupplier(dto.supplierId());

        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo(orderNoGenerator.next("PO", dto.orderDate(),
                orderNoMapper::selectMaxPurchaseSuffix));
        order.setSupplierId(supplier.getId());
        order.setSupplierName(supplier.getName());
        order.setStatus(OrderStatusEnum.DRAFT.name());
        order.setOrderDate(dto.orderDate());
        order.setRemark(dto.remark());
        applyOperator(order);
        order.setTotalQuantity(0);
        order.setTotalAmount(BigDecimal.ZERO);
        orderMapper.insert(order);

        saveItems(order.getId(), dto.items(), false);

        // 重新汇总总量与总额：明细都落库后再统一算，
        // 免得前端传的合计和明细对不上（前端算错或被篡改）
        recalculate(order);
        orderMapper.updateById(order);
        return order.getId();
    }

    /** 修改草稿。已入库的单据不允许改明细 —— 库存和批次都已经按旧明细生成了 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PurchaseSaveDTO dto) {
        PurchaseOrder order = requireOrder(id);
        if (!OrderStatusEnum.DRAFT.is(order.getStatus())) {
            throw BizException.statusIllegal("单据已「"
                    + OrderStatusEnum.labelOf(order.getStatus()) + "」，不能再修改");
        }
        Supplier supplier = requireSupplier(dto.supplierId());

        order.setSupplierId(supplier.getId());
        order.setSupplierName(supplier.getName());
        order.setOrderDate(dto.orderDate());
        order.setRemark(dto.remark());

        // 明细是「整体替换」而不是逐条比对：采购明细没有独立业务含义，
        // 全删重建比写一套 diff 逻辑简单可靠得多，也不会漏掉删除的条目
        itemMapper.delete(Wrappers.<PurchaseOrderItem>lambdaQuery()
                .eq(PurchaseOrderItem::getOrderId, id));
        saveItems(id, dto.items(), false);

        recalculate(order);
        orderMapper.updateById(order);
    }

    // ==================================================================
    //  入库（核心）
    // ==================================================================

    /**
     * 采购入库
     *
     * 每一步都通过 StockCoreService 完成，保证「批次 / 总库存 / 流水」三者同时更新，
     * 不会出现批次加上了但总库存没动这类半成品状态。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void receive(Long id) {
        PurchaseOrder order = requireOrder(id);
        if (OrderStatusEnum.FINISHED.is(order.getStatus())) {
            throw BizException.statusIllegal("该采购单已经入库过了，请勿重复操作");
        }
        if (OrderStatusEnum.CANCELED.is(order.getStatus())) {
            throw BizException.statusIllegal("已作废的采购单不能入库");
        }

        List<PurchaseOrderItem> items = itemsOf(id);
        if (items.isEmpty()) {
            throw BizException.badRequest("采购单没有明细，无法入库");
        }

        for (PurchaseOrderItem item : items) {
            Product product = productMapper.selectById(item.getProductId());
            if (product == null) {
                throw BizException.notFound("商品 id=" + item.getProductId());
            }

            // 保质期推算：只填了生产日期时，按商品配置的保质期天数补出到期日。
            // 放到后端算而不是让前端算，是为了保证「手工录单」和「导入单据」
            // 两条入口得到的到期日规则完全一致。
            LocalDate productionDate = item.getProductionDate();
            LocalDate expireDate = item.getExpireDate();
            if (expireDate == null && productionDate != null
                    && product.getShelfLifeDays() != null && product.getShelfLifeDays() > 0) {
                expireDate = productionDate.plusDays(product.getShelfLifeDays());
            }

            String batchNo = item.getBatchNo();
            if (!StringUtils.hasText(batchNo)) {
                // 批次号规则 B + 日期 + 流水，日期优先取生产日期，没填就用今天
                LocalDate batchDate = productionDate != null ? productionDate : LocalDate.now();
                batchNo = orderNoGenerator.next("B", batchDate, orderNoMapper::selectMaxBatchSuffix);
            }

            stockCoreService.inbound(new StockCoreService.InboundCmd(
                    product.getId(),
                    product.getName(),
                    item.getQuantity(),
                    item.getPrice(),
                    batchNo,
                    productionDate,
                    expireDate,
                    BizTypeEnum.PURCHASE_IN,
                    order.getOrderNo(),
                    order.getId(),
                    "采购入库"));

            // 把最终落地的批次号与到期日回写到明细，前端点开单据就能看到这批货的批次
            item.setBatchNo(batchNo);
            item.setProductionDate(productionDate);
            item.setExpireDate(expireDate);
            itemMapper.updateById(item);
        }

        order.setStatus(OrderStatusEnum.FINISHED.name());
        order.setReceiveTime(LocalDateTime.now());
        orderMapper.updateById(order);

        log.info("采购入库完成 orderNo={} 明细数={}", order.getOrderNo(), items.size());
    }

    // ==================================================================
    //  作废 / 删除
    // ==================================================================

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        PurchaseOrder order = requireOrder(id);
        if (OrderStatusEnum.FINISHED.is(order.getStatus())) {
            // 已入库的货已经在货架上了，作废单据不会让货消失。
            // 正确做法是走「采购退货」把货退给供应商，这样库存才会真的减掉。
            throw BizException.statusIllegal("已入库的采购单不能作废，如需退货请使用采购退货功能");
        }
        order.setStatus(OrderStatusEnum.CANCELED.name());
        orderMapper.updateById(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PurchaseOrder order = requireOrder(id);
        if (!OrderStatusEnum.DRAFT.is(order.getStatus())) {
            throw BizException.statusIllegal("只有草稿状态的采购单可以删除");
        }
        itemMapper.delete(Wrappers.<PurchaseOrderItem>lambdaQuery()
                .eq(PurchaseOrderItem::getOrderId, id));
        orderMapper.deleteById(id);
    }

    // ==================================================================
    //  内部方法
    // ==================================================================

    /**
     * 保存明细
     *
     * @param fillBatchInfo 是否回填批次信息。入库时由调用方自行回填，这里不需要
     */
    private void saveItems(Long orderId, List<PurchaseItemDTO> items, boolean fillBatchInfo) {
        for (PurchaseItemDTO dto : items) {
            Product product = productMapper.selectById(dto.productId());
            if (product == null) {
                throw BizException.notFound("商品 id=" + dto.productId());
            }
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setOrderId(orderId);
            item.setProductId(product.getId());
            // 商品名存快照：商品日后改名，历史单据仍显示当时的名字，
            // 否则对账时会发现「单据上写的是 A，商品档案里叫 B」
            item.setProductName(product.getName());
            item.setQuantity(dto.quantity());
            item.setPrice(dto.price());
            item.setAmount(dto.price().multiply(BigDecimal.valueOf(dto.quantity())));
            item.setBatchNo(dto.batchNo());
            item.setProductionDate(dto.productionDate());
            item.setExpireDate(dto.expireDate());
            itemMapper.insert(item);
        }
    }

    /** 按明细重算合计，避免前端传的合计与明细不一致 */
    private void recalculate(PurchaseOrder order) {
        List<PurchaseOrderItem> items = itemsOf(order.getId());
        int totalQty = items.stream().mapToInt(PurchaseOrderItem::getQuantity).sum();
        BigDecimal totalAmount = items.stream()
                .map(PurchaseOrderItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalQuantity(totalQty);
        order.setTotalAmount(totalAmount);
    }

    private List<PurchaseOrderItem> itemsOf(Long orderId) {
        return itemMapper.selectList(Wrappers.<PurchaseOrderItem>lambdaQuery()
                .eq(PurchaseOrderItem::getOrderId, orderId)
                .orderByAsc(PurchaseOrderItem::getId));
    }

    private PurchaseOrder requireOrder(Long id) {
        PurchaseOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw BizException.notFound("采购单");
        }
        return order;
    }

    private Supplier requireSupplier(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw BizException.notFound("供应商");
        }
        if (supplier.getStatus() != null && supplier.getStatus() != 1) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL,
                    "供应商【" + supplier.getName() + "】已停用，不能用于采购");
        }
        return supplier;
    }

    private void applyOperator(PurchaseOrder order) {
        LoginUser operator = UserContext.get();
        if (operator != null) {
            order.setOperatorId(operator.getUserId());
            order.setOperatorName(operator.getRealName());
        }
    }

    private PurchaseVO toVO(PurchaseOrder order) {
        PurchaseVO vo = new PurchaseVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setSupplierId(order.getSupplierId());
        vo.setSupplierName(order.getSupplierName());
        vo.setTotalQuantity(order.getTotalQuantity());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusLabel(OrderStatusEnum.labelOf(order.getStatus()));
        vo.setOrderDate(order.getOrderDate());
        vo.setReceiveTime(order.getReceiveTime());
        vo.setOperatorName(order.getOperatorName());
        vo.setRemark(order.getRemark());
        return vo;
    }

    private PurchaseItemVO toItemVO(PurchaseOrderItem item) {
        PurchaseItemVO vo = new PurchaseItemVO();
        vo.setId(item.getId());
        vo.setProductId(item.getProductId());
        vo.setProductName(item.getProductName());
        vo.setQuantity(item.getQuantity());
        vo.setPrice(item.getPrice());
        vo.setAmount(item.getAmount());
        vo.setBatchNo(item.getBatchNo());
        vo.setProductionDate(item.getProductionDate());
        vo.setExpireDate(item.getExpireDate());
        return vo;
    }
}
