package com.retail.erp.module.basic.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.erp.common.BizException;
import com.retail.erp.common.ErrorCode;
import com.retail.erp.common.PageResult;
import com.retail.erp.module.basic.dto.OptionVO;
import com.retail.erp.module.basic.dto.ProductQuery;
import com.retail.erp.module.basic.dto.ProductSaveDTO;
import com.retail.erp.module.basic.dto.ProductVO;
import com.retail.erp.module.basic.entity.Category;
import com.retail.erp.module.basic.entity.Product;
import com.retail.erp.module.basic.mapper.CategoryMapper;
import com.retail.erp.module.basic.mapper.ProductMapper;
import com.retail.erp.module.basic.mapper.ProductPageMapper;
import com.retail.erp.module.stock.entity.Stock;
import com.retail.erp.module.stock.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品服务
 *
 * 一个关键设计：商品与库存是两张表。
 * 商品创建时必须同时插入一条 0 库存的 stock 记录，否则后续所有扣减逻辑
 * 都要额外处理「stock 记录不存在」这个分支 —— 把它消灭在创建时，
 * 后面的出库、盘点代码就只需处理一种情况。
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final ProductPageMapper productPageMapper;
    private final CategoryMapper categoryMapper;
    private final StockMapper stockMapper;

    public PageResult<ProductVO> page(ProductQuery query) {
        IPage<ProductVO> page = productPageMapper.selectProductPage(
                new Page<>(query.getPage(), query.getSize()), query);
        // 库存状态在数据库里算不了（依赖每条记录自己的上下限），放到 Java 里补
        page.getRecords().forEach(ProductVO::computeStockStatus);
        return PageResult.of(page);
    }

    public ProductVO detail(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw BizException.notFound("商品");
        }
        ProductVO vo = toVO(product);
        Stock stock = findStock(id);
        vo.setStockQuantity(stock == null ? 0 : stock.getQuantity());
        vo.setAvgCost(stock == null ? BigDecimal.ZERO : stock.getAvgCost());
        vo.computeStockStatus();
        return vo;
    }

    /** 下拉选项：只返回在售商品，供开单、盘点选择 */
    public List<OptionVO> options() {
        return productMapper.selectList(Wrappers.<Product>lambdaQuery()
                        .eq(Product::getStatus, 1)
                        .orderByAsc(Product::getCode))
                .stream()
                .map(p -> new OptionVO(p.getId(), p.getName() + " (" + p.getCode() + ")"))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ProductSaveDTO dto) {
        checkCodeDuplicate(dto.code(), null);
        checkCategoryExists(dto.categoryId());
        checkStockRange(dto);

        Product entity = new Product();
        apply(entity, dto);
        productMapper.insert(entity);

        // 同步初始化库存记录，让后续所有库存操作都能假定 stock 一定存在
        Stock stock = new Stock();
        stock.setProductId(entity.getId());
        stock.setQuantity(0);
        stock.setLockedQuantity(0);
        stock.setAvgCost(BigDecimal.ZERO);
        stock.setVersion(0);
        stockMapper.insert(stock);

        return entity.getId();
    }

    /**
     * 修改商品
     *
     * 刻意不允许修改库存数量。库存只能通过采购入库、销售出库、盘点调整这三条
     * 正规途径变动 —— 否则每一次都要留下流水，盘点差异才追得回来。
     * 如果这里允许直接改 quantity，就等于开了个绕过流水改账的口子。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ProductSaveDTO dto) {
        Product exist = productMapper.selectById(id);
        if (exist == null) {
            throw BizException.notFound("商品");
        }
        checkCodeDuplicate(dto.code(), id);
        checkCategoryExists(dto.categoryId());
        checkStockRange(dto);

        apply(exist, dto);
        productMapper.updateById(exist);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (productMapper.selectById(id) == null) {
            throw BizException.notFound("商品");
        }
        Stock stock = findStock(id);
        if (stock != null && stock.getQuantity() != null && stock.getQuantity() != 0) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL,
                    "该商品还有 " + stock.getQuantity() + " 件库存，请先出库或盘点清零后再删除");
        }
        // 逻辑删除，历史单据里的商品名快照不受影响
        productMapper.deleteById(id);
    }

    // ------------------------------------------------------------------

    private Stock findStock(Long productId) {
        return stockMapper.selectOne(Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getProductId, productId));
    }

    private ProductVO toVO(Product product) {
        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setCode(product.getCode());
        vo.setBarcode(product.getBarcode());
        vo.setName(product.getName());
        vo.setCategoryId(product.getCategoryId());
        vo.setSpec(product.getSpec());
        vo.setUnit(product.getUnit());
        vo.setPurchasePrice(product.getPurchasePrice());
        vo.setSalePrice(product.getSalePrice());
        vo.setStockUpper(product.getStockUpper());
        vo.setStockLower(product.getStockLower());
        vo.setShelfLifeDays(product.getShelfLifeDays());
        vo.setStatus(product.getStatus());
        if (product.getCategoryId() != null) {
            Category category = categoryMapper.selectById(product.getCategoryId());
            vo.setCategoryName(category == null ? null : category.getName());
        }
        return vo;
    }

    private void apply(Product entity, ProductSaveDTO dto) {
        entity.setCode(dto.code());
        entity.setBarcode(dto.barcode());
        entity.setName(dto.name());
        entity.setCategoryId(dto.categoryId());
        entity.setSpec(dto.spec());
        entity.setUnit(dto.unit());
        entity.setPurchasePrice(dto.purchasePrice());
        entity.setSalePrice(dto.salePrice());
        entity.setStockUpper(dto.stockUpper());
        entity.setStockLower(dto.stockLower());
        entity.setShelfLifeDays(dto.shelfLifeDays());
        entity.setStatus(dto.statusOrDefault());
    }

    private void checkStockRange(ProductSaveDTO dto) {
        int upper = dto.stockUpper() == null ? 0 : dto.stockUpper();
        int lower = dto.stockLower() == null ? 0 : dto.stockLower();
        if (upper > 0 && lower > 0 && upper < lower) {
            throw BizException.badRequest("库存上限不能小于下限（当前上限 " + upper + "，下限 " + lower + "）");
        }
    }

    private void checkCategoryExists(Long categoryId) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw BizException.notFound("商品分类");
        }
    }

    private void checkCodeDuplicate(String code, Long excludeId) {
        Long count = productMapper.selectCount(Wrappers.<Product>lambdaQuery()
                .eq(Product::getCode, code)
                .ne(excludeId != null, Product::getId, excludeId));
        if (count != null && count > 0) {
            throw BizException.duplicate("商品编码【" + code + "】已存在");
        }
    }
}
