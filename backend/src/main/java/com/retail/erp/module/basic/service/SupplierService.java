package com.retail.erp.module.basic.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.erp.common.BizException;
import com.retail.erp.common.PageResult;
import com.retail.erp.module.basic.dto.OptionVO;
import com.retail.erp.module.basic.dto.SupplierQuery;
import com.retail.erp.module.basic.dto.SupplierSaveDTO;
import com.retail.erp.module.basic.entity.Supplier;
import com.retail.erp.module.basic.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 供应商服务
 */
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierMapper supplierMapper;

    public PageResult<Supplier> page(SupplierQuery query) {
        IPage<Supplier> page = supplierMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                Wrappers.<Supplier>lambdaQuery()
                        .and(StringUtils.hasText(query.getKeyword()), w -> w
                                .like(Supplier::getName, query.getKeyword())
                                .or().like(Supplier::getCode, query.getKeyword())
                                .or().like(Supplier::getContact, query.getKeyword()))
                        .eq(query.getStatus() != null, Supplier::getStatus, query.getStatus())
                        .orderByAsc(Supplier::getCode));
        return PageResult.of(page);
    }

    /** 下拉选项：只返回启用中的供应商 */
    public List<OptionVO> options() {
        return supplierMapper.selectList(Wrappers.<Supplier>lambdaQuery()
                        .eq(Supplier::getStatus, 1)
                        .orderByAsc(Supplier::getCode))
                .stream()
                .map(s -> new OptionVO(s.getId(), s.getName()))
                .toList();
    }

    public Supplier detail(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw BizException.notFound("供应商");
        }
        return supplier;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SupplierSaveDTO dto) {
        checkCodeDuplicate(dto.code(), null);

        Supplier entity = new Supplier();
        apply(entity, dto);
        supplierMapper.insert(entity);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SupplierSaveDTO dto) {
        Supplier exist = supplierMapper.selectById(id);
        if (exist == null) {
            throw BizException.notFound("供应商");
        }
        checkCodeDuplicate(dto.code(), id);

        apply(exist, dto);
        supplierMapper.updateById(exist);
    }

    /**
     * 删除供应商
     *
     * 这里只做逻辑删除（@TableLogic），历史采购单仍保留 supplier_name 快照，
     * 所以删掉供应商不会导致旧单据显示成空白。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (supplierMapper.selectById(id) == null) {
            throw BizException.notFound("供应商");
        }
        supplierMapper.deleteById(id);
    }

    // ------------------------------------------------------------------

    private void apply(Supplier entity, SupplierSaveDTO dto) {
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setContact(dto.contact());
        entity.setPhone(dto.phone());
        entity.setAddress(dto.address());
        entity.setRemark(dto.remark());
        entity.setStatus(dto.statusOrDefault());
    }

    private void checkCodeDuplicate(String code, Long excludeId) {
        Long count = supplierMapper.selectCount(Wrappers.<Supplier>lambdaQuery()
                .eq(Supplier::getCode, code)
                .ne(excludeId != null, Supplier::getId, excludeId));
        if (count != null && count > 0) {
            throw BizException.duplicate("供应商编号【" + code + "】已存在");
        }
    }
}
