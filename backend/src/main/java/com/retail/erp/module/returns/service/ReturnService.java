package com.retail.erp.module.returns.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.erp.common.BizException;
import com.retail.erp.common.ErrorCode;
import com.retail.erp.common.PageResult;
import com.retail.erp.common.enums.BizTypeEnum;
import com.retail.erp.common.enums.OrderStatusEnum;
import com.retail.erp.common.enums.ReturnTypeEnum;
import com.retail.erp.common.util.OrderNoGenerator;
import com.retail.erp.module.basic.entity.Product;
import com.retail.erp.module.basic.mapper.ProductMapper;
import com.retail.erp.module.purchase.entity.PurchaseOrder;
import com.retail.erp.module.purchase.entity.PurchaseOrderItem;
import com.retail.erp.module.purchase.mapper.PurchaseOrderItemMapper;
import com.retail.erp.module.purchase.mapper.PurchaseOrderMapper;
import com.retail.erp.module.returns.dto.ReturnItemDTO;
import com.retail.erp.module.returns.dto.ReturnItemVO;
import com.retail.erp.module.returns.dto.ReturnQuery;
import com.retail.erp.module.returns.dto.ReturnSaveDTO;
import com.retail.erp.module.returns.dto.ReturnVO;
import com.retail.erp.module.returns.entity.ReturnOrder;
import com.retail.erp.module.returns.entity.ReturnOrderItem;
import com.retail.erp.module.returns.mapper.ReturnOrderItemMapper;
import com.retail.erp.module.returns.mapper.ReturnOrderMapper;
import com.retail.erp.module.returns.mapper.ReturnPageMapper;
import com.retail.erp.module.sale.entity.SaleOrder;
import com.retail.erp.module.sale.entity.SaleOrderItem;
import com.retail.erp.module.sale.mapper.SaleOrderItemMapper;
import com.retail.erp.module.sale.mapper.SaleOrderMapper;
import com.retail.erp.module.stock.dto.BatchDeductResult;
import com.retail.erp.module.stock.entity.Stock;
import com.retail.erp.module.stock.entity.StockBatch;
import com.retail.erp.module.stock.entity.StockRecord;
import com.retail.erp.module.stock.mapper.StockMapper;
import com.retail.erp.module.stock.mapper.StockRecordMapper;
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
import java.util.ArrayList;
import java.util.List;

/**
 * 退货服务
 *
 * 两类退货对库存的作用方向正好相反，这是最容易写错的地方：
 *   采购退货 PURCHASE_RETURN —— 把货退给供应商，库存「减少」
 *   销售退货 SALE_RETURN     —— 顾客把货退回来，库存「增加」
 *
 * 退货单也是「保存即生效」，与销售单一致，因为货当场就搬走了/搬回来了。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnService {

    private final ReturnOrderMapper orderMapper;
    private final ReturnOrderItemMapper itemMapper;
    private final ReturnPageMapper returnPageMapper;
    private final ProductMapper productMapper;
    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final StockMapper stockMapper;
    private final StockRecordMapper stockRecordMapper;
    private final StockCoreService stockCoreService;
    private final OrderNoGenerator orderNoGenerator;
    private final OrderNoMapper orderNoMapper;

    // ==================================================================
    //  查询
    // ==================================================================

    public PageResult<ReturnVO> page(ReturnQuery query) {
        IPage<ReturnOrder> page = orderMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                Wrappers.<ReturnOrder>lambdaQuery()
                        .eq(StringUtils.hasText(query.getReturnType()),
                                ReturnOrder::getReturnType, query.getReturnType())
                        .and(StringUtils.hasText(query.getKeyword()), w -> w
                                .like(ReturnOrder::getOrderNo, query.getKeyword())
                                .or().like(ReturnOrder::getPartnerName, query.getKeyword())
                                .or().like(ReturnOrder::getSourceOrderNo, query.getKeyword()))
                        .ge(query.getStartDate() != null, ReturnOrder::getReturnDate, query.getStartDate())
                        .le(query.getEndDate() != null, ReturnOrder::getReturnDate, query.getEndDate())
                        .orderByDesc(ReturnOrder::getId));
        return PageResult.of(page).map(this::toVO);
    }

    public ReturnVO detail(Long id) {
        ReturnOrder order = requireOrder(id);
        ReturnVO vo = toVO(order);
        vo.setItems(itemsOf(id).stream().map(this::toItemVO).toList());
        return vo;
    }

    // ==================================================================
    //  新建退货（立即生效）
    // ==================================================================

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Long create(ReturnSaveDTO dto) {
        ReturnTypeEnum type = resolveType(dto.returnType());

        ReturnOrder order = new ReturnOrder();
        order.setOrderNo(orderNoGenerator.next("RT", dto.returnDate(),
                orderNoMapper::selectMaxReturnSuffix));
        order.setReturnType(type.name());
        order.setSourceOrderId(dto.sourceOrderId());
        order.setReturnDate(dto.returnDate());
        order.setReason(dto.reason());
        order.setStatus(OrderStatusEnum.FINISHED.name());
        applyOperator(order);
        fillSourceInfo(order, type, dto.sourceOrderId());
        order.setTotalQuantity(0);
        order.setTotalAmount(BigDecimal.ZERO);
        orderMapper.insert(order);

        int totalQuantity = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (ReturnItemDTO itemDTO : dto.items()) {
            Product product = requireProduct(itemDTO.productId());

            // 可退量校验：只能退原单里买过、且还没退过的部分
            if (dto.sourceOrderId() != null) {
                checkRefundable(type, dto.sourceOrderId(), product, itemDTO.quantity());
            }

            List<ItemPiece> pieces = type == ReturnTypeEnum.PURCHASE_RETURN
                    ? doPurchaseReturn(order, itemDTO, product)
                    : doSaleReturn(order, dto.sourceOrderId(), itemDTO, product);

            for (ItemPiece piece : pieces) {
                ReturnOrderItem item = new ReturnOrderItem();
                item.setReturnId(order.getId());
                item.setProductId(product.getId());
                item.setProductName(product.getName());
                item.setBatchId(piece.batchId());
                item.setBatchNo(piece.batchNo());
                item.setQuantity(piece.quantity());
                item.setPrice(itemDTO.price());
                item.setAmount(itemDTO.price().multiply(BigDecimal.valueOf(piece.quantity())));
                itemMapper.insert(item);

                totalQuantity += piece.quantity();
                totalAmount = totalAmount.add(item.getAmount());
            }
        }

        order.setTotalQuantity(totalQuantity);
        order.setTotalAmount(totalAmount);
        orderMapper.updateById(order);

        log.info("退货单完成 orderNo={} type={} 数量={} 金额={}",
                order.getOrderNo(), type, totalQuantity, totalAmount);
        return order.getId();
    }

    // ==================================================================
    //  作废（反向回滚）
    // ==================================================================

    /**
     * 作废退货单
     *
     * 回滚依据不是「再猜一遍扣了哪些批次」，而是直接查这次退货产生的库存流水，
     * 按流水里的 batch_id 逐条冲正。这样即使当初跨了多个批次、或者中间做过分批操作，
     * 也能一条不差地还原回去 —— 流水就是账，账怎么走的就怎么退回来。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void cancel(Long id) {
        ReturnOrder order = requireOrder(id);
        if (OrderStatusEnum.CANCELED.is(order.getStatus())) {
            throw BizException.statusIllegal("该退货单已经作废过了");
        }
        ReturnTypeEnum type = resolveType(order.getReturnType());

        List<StockRecord> records = stockRecordMapper.selectList(Wrappers.<StockRecord>lambdaQuery()
                .eq(StockRecord::getBizType, type.getBizType().name())
                .eq(StockRecord::getBizId, id));

        if (records.isEmpty()) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL,
                    "找不到该退货单产生的库存流水，无法自动回滚，请使用盘点调整");
        }

        if (type == ReturnTypeEnum.PURCHASE_RETURN) {
            // 采购退货当初是出库（流水为负），冲正就是把这批货加回来
            for (StockRecord r : records) {
                stockCoreService.restoreToBatch(new StockCoreService.RestoreCmd(
                        r.getProductId(), r.getBatchId(), Math.abs(r.getChangeQuantity()),
                        BizTypeEnum.PURCHASE_RETURN_CANCEL,
                        order.getOrderNo(), order.getId(), "采购退货单作废回滚"));
            }
        } else {
            // 销售退货当初是入库（流水为正），冲正就是再扣回去。
            // 扣的时候用当初那个批次，货从哪个批次回来的就从哪个批次出去
            for (StockRecord r : records) {
                Product product = productMapper.selectById(r.getProductId());
                stockCoreService.deductFromBatch(new StockCoreService.DeductBatchCmd(
                        r.getProductId(),
                        product == null ? "商品" : product.getName(),
                        r.getBatchId(),
                        Math.abs(r.getChangeQuantity()),
                        BizTypeEnum.SALE_RETURN_CANCEL,
                        order.getOrderNo(), order.getId(), "销售退货单作废回滚"));
            }
        }

        order.setStatus(OrderStatusEnum.CANCELED.name());
        orderMapper.updateById(order);
        log.info("退货单作废并回滚库存 orderNo={} type={} 冲正流水数={}",
                order.getOrderNo(), type, records.size());
    }

    // ==================================================================
    //  两类退货的库存处理
    // ==================================================================

    /** 采购退货：库存减少。指定批次就扣指定批次，否则按 FEFO 自动挑 */
    private List<ItemPiece> doPurchaseReturn(ReturnOrder order, ReturnItemDTO itemDTO, Product product) {
        List<ItemPiece> pieces = new ArrayList<>();

        if (itemDTO.batchId() != null) {
            BatchDeductResult r = stockCoreService.deductFromBatch(new StockCoreService.DeductBatchCmd(
                    product.getId(), product.getName(), itemDTO.batchId(), itemDTO.quantity(),
                    BizTypeEnum.PURCHASE_RETURN_OUT, order.getOrderNo(), order.getId(), "采购退货"));
            pieces.add(new ItemPiece(r.getBatchId(), r.getBatchNo(), r.getQuantity()));
            return pieces;
        }

        List<BatchDeductResult> deducted = stockCoreService.outbound(new StockCoreService.OutboundCmd(
                product.getId(), product.getName(), itemDTO.quantity(),
                BizTypeEnum.PURCHASE_RETURN_OUT, order.getOrderNo(), order.getId(), "采购退货"));
        deducted.forEach(r -> pieces.add(new ItemPiece(r.getBatchId(), r.getBatchNo(), r.getQuantity())));
        return pieces;
    }

    /** 销售退货：库存增加。能对上原单批次就退回原批次，对不上才新建批次 */
    private List<ItemPiece> doSaleReturn(ReturnOrder order, Long sourceOrderId,
                                         ReturnItemDTO itemDTO, Product product) {
        List<ItemPiece> pieces = new ArrayList<>();

        List<BatchPortion> portions = sourceOrderId == null ? List.of()
                : resolveSaleReturnBatches(sourceOrderId, product.getId(), itemDTO.quantity());

        if (!portions.isEmpty()) {
            for (BatchPortion p : portions) {
                stockCoreService.restoreToBatch(new StockCoreService.RestoreCmd(
                        product.getId(), p.batchId(), p.quantity(),
                        BizTypeEnum.SALE_RETURN_IN, order.getOrderNo(), order.getId(), "销售退货"));
                pieces.add(new ItemPiece(p.batchId(), p.batchNo(), p.quantity()));
            }
            return pieces;
        }

        // 没有原单可依托（顾客没带小票、或原单销售早于系统上线），
        // 只能建一个新批次。成本取当前移动加权平均成本，到期日留空 ——
        // 因为确实不知道这批货是什么时候生产的，宁可留空也不能瞎填。
        Stock stock = stockMapper.selectOne(Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getProductId, product.getId()));
        BigDecimal cost = (stock == null || stock.getAvgCost() == null)
                ? product.getPurchasePrice() : stock.getAvgCost();

        String batchNo = orderNoGenerator.next("RTB", order.getReturnDate(),
                orderNoMapper::selectMaxBatchSuffix);
        StockBatch batch = stockCoreService.inbound(new StockCoreService.InboundCmd(
                product.getId(), product.getName(), itemDTO.quantity(), cost,
                batchNo, null, null,
                BizTypeEnum.SALE_RETURN_IN, order.getOrderNo(), order.getId(),
                "销售退货（未关联原单，已新建批次，请补录保质期）"));
        pieces.add(new ItemPiece(batch.getId(), batch.getBatchNo(), itemDTO.quantity()));
        return pieces;
    }

    /**
     * 找出原销售单里该商品的出库批次，按需凑够退货数量
     *
     * 一个商品当初可能跨了多个批次出库，退货时也要按同样的顺序原路返回，
     * 不能用 FEFO 重新挑 —— 顾客退的是他手里那件，不是货架上最早到期那件。
     */
    private List<BatchPortion> resolveSaleReturnBatches(Long saleOrderId, Long productId, int quantity) {
        List<SaleOrderItem> items = saleOrderItemMapper.selectList(Wrappers.<SaleOrderItem>lambdaQuery()
                .eq(SaleOrderItem::getOrderId, saleOrderId)
                .eq(SaleOrderItem::getProductId, productId)
                .orderByAsc(SaleOrderItem::getId));

        List<BatchPortion> portions = new ArrayList<>();
        int remaining = quantity;
        for (SaleOrderItem item : items) {
            if (remaining <= 0) {
                break;
            }
            if (item.getBatchId() == null) {
                continue;
            }
            int take = Math.min(remaining, item.getQuantity());
            portions.add(new BatchPortion(item.getBatchId(), item.getBatchNo(), take));
            remaining -= take;
        }
        // 凑不齐说明原单里可依托的批次不够，交给调用方走新建批次的分支
        return remaining > 0 ? List.of() : portions;
    }

    // ==================================================================
    //  校验与工具
    // ==================================================================

    private void checkRefundable(ReturnTypeEnum type, Long sourceOrderId, Product product, int quantity) {
        int ordered = originalQuantity(type, sourceOrderId, product.getId());
        if (ordered <= 0) {
            throw new BizException(ErrorCode.RETURN_QTY_EXCEED,
                    "原单里没有商品【" + product.getName() + "】，无法退货");
        }
        int returned = returnPageMapper.sumReturnedQuantity(
                type.name(), sourceOrderId, product.getId());
        int refundable = ordered - returned;
        if (quantity > refundable) {
            throw new BizException(ErrorCode.RETURN_QTY_EXCEED,
                    String.format("商品【%s】可退数量为 %d（原单 %d，已退 %d），本次要退 %d",
                            product.getName(), refundable, ordered, returned, quantity));
        }
    }

    /** 原单里该商品的采购/销售总量 */
    private int originalQuantity(ReturnTypeEnum type, Long sourceOrderId, Long productId) {
        if (type == ReturnTypeEnum.PURCHASE_RETURN) {
            return purchaseOrderItemMapper.selectList(Wrappers.<PurchaseOrderItem>lambdaQuery()
                            .eq(PurchaseOrderItem::getOrderId, sourceOrderId)
                            .eq(PurchaseOrderItem::getProductId, productId))
                    .stream().mapToInt(PurchaseOrderItem::getQuantity).sum();
        }
        return saleOrderItemMapper.selectList(Wrappers.<SaleOrderItem>lambdaQuery()
                        .eq(SaleOrderItem::getOrderId, sourceOrderId)
                        .eq(SaleOrderItem::getProductId, productId))
                .stream().mapToInt(SaleOrderItem::getQuantity).sum();
    }

    /** 带上原单号与往来单位（采购退货记供应商、销售退货记客户） */
    private void fillSourceInfo(ReturnOrder order, ReturnTypeEnum type, Long sourceOrderId) {
        if (sourceOrderId == null) {
            return;
        }
        if (type == ReturnTypeEnum.PURCHASE_RETURN) {
            PurchaseOrder purchase = purchaseOrderMapper.selectById(sourceOrderId);
            if (purchase == null) {
                throw BizException.notFound("原采购单");
            }
            order.setSourceOrderNo(purchase.getOrderNo());
            order.setPartnerId(purchase.getSupplierId());
            order.setPartnerName(purchase.getSupplierName());
        } else {
            SaleOrder sale = saleOrderMapper.selectById(sourceOrderId);
            if (sale == null) {
                throw BizException.notFound("原销售单");
            }
            order.setSourceOrderNo(sale.getOrderNo());
            order.setPartnerName(sale.getCustomerName());
        }
    }

    private ReturnTypeEnum resolveType(String value) {
        try {
            return ReturnTypeEnum.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            throw BizException.badRequest("不支持的退货类型：" + value
                    + "，只支持 PURCHASE_RETURN 或 SALE_RETURN");
        }
    }

    private Product requireProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw BizException.notFound("商品");
        }
        return product;
    }

    private List<ReturnOrderItem> itemsOf(Long returnId) {
        return itemMapper.selectList(Wrappers.<ReturnOrderItem>lambdaQuery()
                .eq(ReturnOrderItem::getReturnId, returnId)
                .orderByAsc(ReturnOrderItem::getId));
    }

    private ReturnOrder requireOrder(Long id) {
        ReturnOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw BizException.notFound("退货单");
        }
        return order;
    }

    private void applyOperator(ReturnOrder order) {
        LoginUser operator = UserContext.get();
        if (operator != null) {
            order.setOperatorId(operator.getUserId());
            order.setOperatorName(operator.getRealName());
        }
    }

    private ReturnVO toVO(ReturnOrder order) {
        ReturnVO vo = new ReturnVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setReturnType(order.getReturnType());
        vo.setSourceOrderId(order.getSourceOrderId());
        vo.setSourceOrderNo(order.getSourceOrderNo());
        vo.setPartnerId(order.getPartnerId());
        vo.setPartnerName(order.getPartnerName());
        vo.setTotalQuantity(order.getTotalQuantity());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusLabel(OrderStatusEnum.labelOf(order.getStatus()));
        vo.setReturnDate(order.getReturnDate());
        vo.setOperatorName(order.getOperatorName());
        vo.setReason(order.getReason());
        try {
            vo.setReturnTypeLabel(ReturnTypeEnum.valueOf(order.getReturnType()).getLabel());
        } catch (Exception e) {
            vo.setReturnTypeLabel(order.getReturnType());
        }
        return vo;
    }

    private ReturnItemVO toItemVO(ReturnOrderItem item) {
        ReturnItemVO vo = new ReturnItemVO();
        vo.setId(item.getId());
        vo.setProductId(item.getProductId());
        vo.setProductName(item.getProductName());
        vo.setBatchId(item.getBatchId());
        vo.setBatchNo(item.getBatchNo());
        vo.setQuantity(item.getQuantity());
        vo.setPrice(item.getPrice());
        vo.setAmount(item.getAmount());
        return vo;
    }

    /** 一条退货明细最终落到哪个批次、多少数量 */
    private record ItemPiece(Long batchId, String batchNo, int quantity) {
    }

    /** 原销售单里某个批次的出库量 */
    private record BatchPortion(Long batchId, String batchNo, int quantity) {
    }
}
