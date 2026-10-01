package com.retail.erp.module.system.dto;

import com.retail.erp.module.system.entity.SysUser;
import lombok.Data;

/**
 * 当前登录用户信息（返回给前端的最小信息集）
 *
 * 刻意不返回 password 字段 —— 即使是密文也不该离开服务端。
 */
@Data
public class UserVO {

    private Long userId;
    private String username;
    private String realName;
    private String role;
    private String phone;

    public static UserVO from(SysUser user) {
        UserVO vo = new UserVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRole(user.getRole());
        vo.setPhone(user.getPhone());
        return vo;
    }
}
