package com.zook.hrinterview.interfaces.auth.controller;

import com.zook.hrinterview.common.ApiResponse;
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
import com.zook.hrinterview.interfaces.auth.service.RbacManageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

@Api(tags = "RBAC权限管理")
@RestController
@RequestMapping("/api/rbac")
public class RbacController {

    @Resource
    private RbacManageService rbacManageService;

    @ApiOperation("查询角色列表")
    @PostMapping("/roles/list")
    public ApiResponse<PageResponse<RbacRoleResponse>> listRoles(@Valid @RequestBody RbacRoleListRequest request) {
        return ApiResponse.success(rbacManageService.listRoles(request));
    }

    @ApiOperation("创建角色")
    @PostMapping("/roles/create")
    public ApiResponse<RbacRoleResponse> createRole(@Valid @RequestBody RbacRoleCreateRequest request) {
        return ApiResponse.success(rbacManageService.createRole(request));
    }

    @ApiOperation("更新角色")
    @PostMapping("/roles/update")
    public ApiResponse<RbacRoleResponse> updateRole(@Valid @RequestBody RbacRoleUpdateRequest request) {
        return ApiResponse.success(rbacManageService.updateRole(request));
    }

    @ApiOperation("删除角色")
    @PostMapping("/roles/delete")
    public ApiResponse<Boolean> deleteRole(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(rbacManageService.deleteRole(request));
    }

    @ApiOperation("查询权限列表")
    @PostMapping("/permissions/list")
    public ApiResponse<List<RbacPermissionResponse>> listPermissions() {
        return ApiResponse.success(rbacManageService.listPermissions());
    }

    @ApiOperation("创建菜单权限")
    @PostMapping("/permissions/create")
    public ApiResponse<RbacPermissionResponse> createPermission(@Valid @RequestBody RbacPermissionCreateRequest request) {
        return ApiResponse.success(rbacManageService.createPermission(request));
    }

    @ApiOperation("更新菜单权限")
    @PostMapping("/permissions/update")
    public ApiResponse<RbacPermissionResponse> updatePermission(@Valid @RequestBody RbacPermissionUpdateRequest request) {
        return ApiResponse.success(rbacManageService.updatePermission(request));
    }

    @ApiOperation("删除菜单权限")
    @PostMapping("/permissions/delete")
    public ApiResponse<Boolean> deletePermission(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(rbacManageService.deletePermission(request));
    }

    @ApiOperation("查询角色权限")
    @PostMapping("/roles/permissions/detail")
    public ApiResponse<RbacRolePermissionDetailResponse> rolePermissions(@Valid @RequestBody RbacRolePermissionDetailRequest request) {
        return ApiResponse.success(rbacManageService.rolePermissions(request));
    }

    @ApiOperation("保存角色权限")
    @PostMapping("/roles/permissions/save")
    public ApiResponse<Boolean> saveRolePermissions(@Valid @RequestBody RbacRolePermissionSaveRequest request) {
        return ApiResponse.success(rbacManageService.saveRolePermissions(request));
    }

    @ApiOperation("查询用户列表")
    @PostMapping("/users/list")
    public ApiResponse<PageResponse<RbacUserResponse>> listUsers(@Valid @RequestBody RbacUserListRequest request) {
        return ApiResponse.success(rbacManageService.listUsers(request));
    }

    @ApiOperation("创建用户")
    @PostMapping("/users/create")
    public ApiResponse<RbacUserResponse> createUser(@Valid @RequestBody RbacUserCreateRequest request) {
        return ApiResponse.success(rbacManageService.createUser(request));
    }

    @ApiOperation("重置用户密码")
    @PostMapping("/users/password/reset")
    public ApiResponse<Boolean> resetUserPassword(@Valid @RequestBody RbacUserResetPasswordRequest request) {
        return ApiResponse.success(rbacManageService.resetUserPassword(request));
    }

    @ApiOperation("保存用户角色")
    @PostMapping("/users/roles/save")
    public ApiResponse<Boolean> saveUserRoles(@Valid @RequestBody RbacUserRoleSaveRequest request) {
        return ApiResponse.success(rbacManageService.saveUserRoles(request));
    }
}
