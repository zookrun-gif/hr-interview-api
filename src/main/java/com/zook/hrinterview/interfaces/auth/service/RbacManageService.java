package com.zook.hrinterview.interfaces.auth.service;

import com.zook.hrinterview.common.IdRequest;
import com.zook.hrinterview.common.PageResponse;
import com.zook.hrinterview.interfaces.auth.dto.RbacPermissionResponse;
import com.zook.hrinterview.interfaces.auth.dto.RbacPermissionCreateRequest;
import com.zook.hrinterview.interfaces.auth.dto.RbacPermissionUpdateRequest;
import com.zook.hrinterview.interfaces.auth.dto.RbacRoleCreateRequest;
import com.zook.hrinterview.interfaces.auth.dto.RbacRoleListRequest;
import com.zook.hrinterview.interfaces.auth.dto.RbacRolePermissionDetailRequest;
import com.zook.hrinterview.interfaces.auth.dto.RbacRolePermissionDetailResponse;
import com.zook.hrinterview.interfaces.auth.dto.RbacRolePermissionSaveRequest;
import com.zook.hrinterview.interfaces.auth.dto.RbacRoleResponse;
import com.zook.hrinterview.interfaces.auth.dto.RbacRoleUpdateRequest;
import com.zook.hrinterview.interfaces.auth.dto.RbacUserCreateRequest;
import com.zook.hrinterview.interfaces.auth.dto.RbacUserListRequest;
import com.zook.hrinterview.interfaces.auth.dto.RbacUserResetPasswordRequest;
import com.zook.hrinterview.interfaces.auth.dto.RbacUserResponse;
import com.zook.hrinterview.interfaces.auth.dto.RbacUserRoleSaveRequest;

import java.util.List;

public interface RbacManageService {

    PageResponse<RbacRoleResponse> listRoles(RbacRoleListRequest request);

    RbacRoleResponse createRole(RbacRoleCreateRequest request);

    RbacRoleResponse updateRole(RbacRoleUpdateRequest request);

    Boolean deleteRole(IdRequest request);

    List<RbacPermissionResponse> listPermissions();

    RbacPermissionResponse createPermission(RbacPermissionCreateRequest request);

    RbacPermissionResponse updatePermission(RbacPermissionUpdateRequest request);

    Boolean deletePermission(IdRequest request);

    RbacRolePermissionDetailResponse rolePermissions(RbacRolePermissionDetailRequest request);

    Boolean saveRolePermissions(RbacRolePermissionSaveRequest request);

    PageResponse<RbacUserResponse> listUsers(RbacUserListRequest request);

    RbacUserResponse createUser(RbacUserCreateRequest request);

    Boolean resetUserPassword(RbacUserResetPasswordRequest request);

    Boolean saveUserRoles(RbacUserRoleSaveRequest request);
}
