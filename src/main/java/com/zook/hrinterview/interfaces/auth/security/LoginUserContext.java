package com.zook.hrinterview.interfaces.auth.security;

import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.ErrorCode;

public final class LoginUserContext {

    private static final ThreadLocal<LoginUser> CURRENT_USER = new ThreadLocal<>();

    private LoginUserContext() {
    }

    public static void set(LoginUser loginUser) {
        CURRENT_USER.set(loginUser);
    }

    public static LoginUser get() {
        return CURRENT_USER.get();
    }

    public static LoginUser mustGet() {
        LoginUser loginUser = get();
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    public static Long getUserId() {
        return mustGet().getId();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
