package com.zook.hrinterview.interfaces.auth.service;

import com.zook.hrinterview.interfaces.auth.security.LoginUser;

import java.util.Set;

public interface RbacPermissionService {

    Set<String> listPermissionCodes(Long userId, String legacyRole);

    boolean hasPermission(LoginUser loginUser, String permissionCode);
}
