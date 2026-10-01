package com.retail.erp.security;

import com.retail.erp.common.BizException;
import com.retail.erp.common.ErrorCode;

/**
 * 当前请求的用户上下文
 *
 * 用 ThreadLocal 把 LoginUser 从鉴权拦截器传递到 Service 层，
 * 好处是 Service 不需要在方法签名里层层传 operatorId —— 这也正是业务代码
 * 不应该依赖 HttpServletRequest 的典型场景。
 *
 * ⚠️ 必须在请求结束时 remove，否则线程池复用会导致「上一个用户」泄漏到下一个请求。
 *    清理动作统一放在 AuthInterceptor.afterCompletion。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /** 可能为 null（未登录接口） */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 需要登录的场景使用，未登录直接抛 401 */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long currentUserId() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getUserId();
    }

    public static String currentUserName() {
        LoginUser u = HOLDER.get();
        return u == null ? "系统" : u.getRealName();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
