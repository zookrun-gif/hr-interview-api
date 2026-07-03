package com.zook.hrinterview.interfaces.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zook.hrinterview.interfaces.auth.entity.RbacPermission;
import com.zook.hrinterview.interfaces.auth.entity.RbacRole;
import com.zook.hrinterview.interfaces.auth.entity.RbacRolePermission;
import com.zook.hrinterview.interfaces.auth.entity.RbacUserRole;
import com.zook.hrinterview.interfaces.auth.mapper.RbacPermissionMapper;
import com.zook.hrinterview.interfaces.auth.mapper.RbacRoleMapper;
import com.zook.hrinterview.interfaces.auth.mapper.RbacRolePermissionMapper;
import com.zook.hrinterview.interfaces.auth.mapper.RbacUserRoleMapper;
import com.zook.hrinterview.interfaces.auth.security.LoginUser;
import com.zook.hrinterview.interfaces.auth.service.RbacPermissionService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RbacPermissionServiceImpl implements RbacPermissionService {

    private static final String STATUS_ENABLED = "ENABLED";

    private static final String ROLE_ADMIN = "ADMIN";

    private static final String ALL_PERMISSION = "*";

    @Resource
    private RbacRoleMapper rbacRoleMapper;

    @Resource
    private RbacPermissionMapper rbacPermissionMapper;

    @Resource
    private RbacUserRoleMapper rbacUserRoleMapper;

    @Resource
    private RbacRolePermissionMapper rbacRolePermissionMapper;

    @Override
    public Set<String> listPermissionCodes(Long userId, String legacyRole) {
        if (ROLE_ADMIN.equals(legacyRole)) {
            return Collections.singleton(ALL_PERMISSION);
        }

        Set<Long> roleIds = new LinkedHashSet<>();
        if (userId != null) {
            List<RbacUserRole> userRoles = rbacUserRoleMapper.selectList(
                    Wrappers.lambdaQuery(RbacUserRole.class).eq(RbacUserRole::getUserId, userId));
            roleIds.addAll(userRoles.stream().map(RbacUserRole::getRoleId).collect(Collectors.toSet()));
        }

        if (StringUtils.hasText(legacyRole)) {
            RbacRole role = rbacRoleMapper.selectOne(
                    Wrappers.lambdaQuery(RbacRole.class)
                            .eq(RbacRole::getCode, legacyRole)
                            .eq(RbacRole::getStatus, STATUS_ENABLED));
            if (role != null) {
                roleIds.add(role.getId());
            }
        }

        if (CollectionUtils.isEmpty(roleIds)) {
            return Collections.emptySet();
        }

        List<RbacRole> roles = rbacRoleMapper.selectBatchIds(roleIds);
        boolean hasAdminRole = roles.stream()
                .anyMatch(role -> ROLE_ADMIN.equals(role.getCode()) && STATUS_ENABLED.equals(role.getStatus()));
        if (hasAdminRole) {
            return Collections.singleton(ALL_PERMISSION);
        }

        Set<Long> enabledRoleIds = roles.stream()
                .filter(role -> STATUS_ENABLED.equals(role.getStatus()))
                .map(RbacRole::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (enabledRoleIds.isEmpty()) {
            return Collections.emptySet();
        }

        List<RbacRolePermission> rolePermissions = rbacRolePermissionMapper.selectList(
                Wrappers.lambdaQuery(RbacRolePermission.class).in(RbacRolePermission::getRoleId, enabledRoleIds));
        Set<Long> permissionIds = rolePermissions.stream()
                .map(RbacRolePermission::getPermissionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (permissionIds.isEmpty()) {
            return Collections.emptySet();
        }

        List<RbacPermission> permissions = rbacPermissionMapper.selectBatchIds(permissionIds).stream()
                .filter(permission -> STATUS_ENABLED.equals(permission.getStatus()))
                .collect(Collectors.toList());
        Set<Long> completedPermissionIds = completeWithParentPermissionIds(permissions);
        if (completedPermissionIds.isEmpty()) {
            return Collections.emptySet();
        }
        return rbacPermissionMapper.selectBatchIds(completedPermissionIds).stream()
                .filter(permission -> STATUS_ENABLED.equals(permission.getStatus()))
                .map(RbacPermission::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public boolean hasPermission(LoginUser loginUser, String permissionCode) {
        if (loginUser == null || !StringUtils.hasText(permissionCode)) {
            return false;
        }
        if (ROLE_ADMIN.equals(loginUser.getRole())) {
            return true;
        }
        Set<String> permissionCodes = loginUser.getPermissionCodes();
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            permissionCodes = listPermissionCodes(loginUser.getId(), loginUser.getRole());
            loginUser.setPermissionCodes(permissionCodes);
        }
        return permissionCodes.contains(ALL_PERMISSION) || permissionCodes.contains(permissionCode);
    }

    private Set<Long> completeWithParentPermissionIds(List<RbacPermission> permissions) {
        if (permissions.isEmpty()) {
            return Collections.emptySet();
        }
        Map<Long, RbacPermission> permissionMap = rbacPermissionMapper.selectList(Wrappers.lambdaQuery(RbacPermission.class))
                .stream()
                .collect(Collectors.toMap(RbacPermission::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        Set<Long> completedIds = permissions.stream()
                .map(RbacPermission::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        permissions.forEach(permission -> {
            RbacPermission current = permission;
            while (current != null && current.getParentId() != null && current.getParentId() > 0) {
                Long parentId = current.getParentId();
                completedIds.add(parentId);
                current = permissionMap.get(parentId);
            }
        });
        return completedIds;
    }
}
