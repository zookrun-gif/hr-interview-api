package com.zook.hrinterview.interfaces.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.ErrorCode;
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
import com.zook.hrinterview.interfaces.auth.entity.HrUser;
import com.zook.hrinterview.interfaces.auth.entity.RbacPermission;
import com.zook.hrinterview.interfaces.auth.entity.RbacRole;
import com.zook.hrinterview.interfaces.auth.entity.RbacRolePermission;
import com.zook.hrinterview.interfaces.auth.entity.RbacUserRole;
import com.zook.hrinterview.interfaces.auth.mapper.HrUserMapper;
import com.zook.hrinterview.interfaces.auth.mapper.RbacPermissionMapper;
import com.zook.hrinterview.interfaces.auth.mapper.RbacRoleMapper;
import com.zook.hrinterview.interfaces.auth.mapper.RbacRolePermissionMapper;
import com.zook.hrinterview.interfaces.auth.mapper.RbacUserRoleMapper;
import com.zook.hrinterview.interfaces.auth.service.RbacManageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RbacManageServiceImpl implements RbacManageService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String ROLE_USER = "USER";
    private static final String TYPE_MENU = "MENU";
    private static final String TYPE_API = "API";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final Set<String> BASIC_AUTH_PERMISSION_CODES = Set.of(
            "menu:account",
            "auth:me",
            "auth:logout",
            "auth:change-password",
            "auth:wecom-config",
            "auth:wecom-login"
    );
    private static final List<BuiltinPermission> BUILTIN_PERMISSIONS = List.of(
            new BuiltinPermission(null, "menu:jobs", "jobs", "岗位管理", TYPE_MENU, null, "jobs", "岗位管理菜单", 1000),
            new BuiltinPermission("menu:jobs", "job:create", null, "创建岗位", TYPE_API, "/api/jobs/create", null, "创建岗位", 1010),
            new BuiltinPermission("menu:jobs", "job:update", null, "更新岗位", TYPE_API, "/api/jobs/update", null, "更新岗位", 1020),
            new BuiltinPermission("menu:jobs", "job:delete", null, "删除岗位", TYPE_API, "/api/jobs/delete", null, "删除岗位", 1030),
            new BuiltinPermission("menu:jobs", "job:detail", null, "查询岗位详情", TYPE_API, "/api/jobs/detail", null, "查询岗位详情", 1040),
            new BuiltinPermission("menu:jobs", "job:list", null, "查询岗位列表", TYPE_API, "/api/jobs/list", null, "查询岗位列表", 1050),

            new BuiltinPermission(null, "menu:candidates", "candidates", "候选人管理", TYPE_MENU, null, "candidates", "候选人管理菜单", 2000),
            new BuiltinPermission("menu:candidates", "candidate:create", null, "创建候选人", TYPE_API, "/api/candidates/create", null, "创建候选人", 2010),
            new BuiltinPermission("menu:candidates", "candidate:update", null, "更新候选人", TYPE_API, "/api/candidates/update", null, "更新候选人", 2020),
            new BuiltinPermission("menu:candidates", "candidate:delete", null, "删除候选人", TYPE_API, "/api/candidates/delete", null, "删除候选人", 2030),
            new BuiltinPermission("menu:candidates", "candidate:detail", null, "查询候选人详情", TYPE_API, "/api/candidates/detail", null, "查询候选人详情", 2040),
            new BuiltinPermission("menu:candidates", "candidate:list", null, "查询候选人列表", TYPE_API, "/api/candidates/list", null, "查询候选人列表", 2050),
            new BuiltinPermission("menu:candidates", "candidate:resume-parse", null, "解析PDF简历", TYPE_API, "/api/candidates/resume/parse-pdf", null, "解析PDF简历", 2060),

            new BuiltinPermission(null, "menu:interviews", "interviews", "面试管理", TYPE_MENU, null, "interviews", "面试管理菜单", 3000),
            new BuiltinPermission("menu:interviews", "interview:create", null, "创建面试会话", TYPE_API, "/api/interviews/create", null, "创建面试会话", 3010),
            new BuiltinPermission("menu:interviews", "interview:detail", null, "查询面试详情", TYPE_API, "/api/interviews/detail", null, "查询面试会话详情", 3020),
            new BuiltinPermission("menu:interviews", "interview:list", null, "查询面试列表", TYPE_API, "/api/interviews/list", null, "查询面试会话列表", 3030),
            new BuiltinPermission("menu:interviews", "interview:access-code-reset", null, "重置面试口令", TYPE_API, "/api/interviews/access-code/reset", null, "重置面试访问口令", 3040),
            new BuiltinPermission("menu:interviews", "interview:messages-list", null, "查询面试消息", TYPE_API, "/api/interviews/messages/list", null, "查询面试消息记录", 3050),
            new BuiltinPermission("menu:interviews", "interview:report-detail", null, "查询面试报告", TYPE_API, "/api/interviews/reports/detail", null, "查询面试评估报告", 3060),

            new BuiltinPermission(null, "menu:rbac", "rbac", "权限管理", TYPE_MENU, null, "rbac", "权限管理父菜单", 9000),
            new BuiltinPermission("menu:rbac", "menu:rbac:menus", "rbacMenus", "菜单管理", TYPE_MENU, null, "rbacMenus", "菜单、按钮和接口权限管理", 9010),
            new BuiltinPermission("menu:rbac:menus", "rbac:permission:list", null, "查询权限列表", TYPE_API, "/api/rbac/permissions/list", null, "查询RBAC权限列表", 9011),
            new BuiltinPermission("menu:rbac:menus", "rbac:permission:create", null, "创建菜单权限", TYPE_API, "/api/rbac/permissions/create", null, "创建菜单、按钮或接口权限", 9012),
            new BuiltinPermission("menu:rbac:menus", "rbac:permission:update", null, "更新菜单权限", TYPE_API, "/api/rbac/permissions/update", null, "更新菜单、按钮或接口权限", 9013),
            new BuiltinPermission("menu:rbac:menus", "rbac:permission:delete", null, "删除菜单权限", TYPE_API, "/api/rbac/permissions/delete", null, "删除菜单、按钮或接口权限", 9014),
            new BuiltinPermission("menu:rbac", "menu:rbac:roles", "rbacRoles", "角色管理", TYPE_MENU, null, "rbacRoles", "角色和角色权限管理", 9020),
            new BuiltinPermission("menu:rbac:roles", "rbac:role:list", null, "查询角色列表", TYPE_API, "/api/rbac/roles/list", null, "查询RBAC角色列表", 9021),
            new BuiltinPermission("menu:rbac:roles", "rbac:role:create", null, "创建角色", TYPE_API, "/api/rbac/roles/create", null, "创建RBAC角色", 9022),
            new BuiltinPermission("menu:rbac:roles", "rbac:role:update", null, "更新角色", TYPE_API, "/api/rbac/roles/update", null, "更新RBAC角色", 9023),
            new BuiltinPermission("menu:rbac:roles", "rbac:role:delete", null, "删除角色", TYPE_API, "/api/rbac/roles/delete", null, "删除RBAC角色", 9024),
            new BuiltinPermission("menu:rbac:roles", "rbac:role-permission:detail", null, "查询角色权限", TYPE_API, "/api/rbac/roles/permissions/detail", null, "查询角色已绑定权限", 9025),
            new BuiltinPermission("menu:rbac:roles", "rbac:role-permission:save", null, "保存角色权限", TYPE_API, "/api/rbac/roles/permissions/save", null, "保存角色权限绑定", 9026),
            new BuiltinPermission("menu:rbac", "menu:rbac:users", "rbacUsers", "用户管理", TYPE_MENU, null, "rbacUsers", "用户角色授权管理", 9030),
            new BuiltinPermission("menu:rbac:users", "rbac:user:list", null, "查询授权用户", TYPE_API, "/api/rbac/users/list", null, "查询用户角色授权列表", 9031),
            new BuiltinPermission("menu:rbac:users", "rbac:user:create", null, "创建用户", TYPE_API, "/api/rbac/users/create", null, "创建后台用户", 9032),
            new BuiltinPermission("menu:rbac:users", "rbac:user-password:reset", null, "重置用户密码", TYPE_API, "/api/rbac/users/password/reset", null, "重置后台用户登录密码", 9033),
            new BuiltinPermission("menu:rbac:users", "rbac:user-role:save", null, "保存用户角色", TYPE_API, "/api/rbac/users/roles/save", null, "保存用户角色绑定", 9034)
    );

    @Resource
    private HrUserMapper hrUserMapper;

    @Resource
    private RbacRoleMapper rbacRoleMapper;

    @Resource
    private RbacPermissionMapper rbacPermissionMapper;

    @Resource
    private RbacUserRoleMapper rbacUserRoleMapper;

    @Resource
    private RbacRolePermissionMapper rbacRolePermissionMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    public PageResponse<RbacRoleResponse> listRoles(RbacRoleListRequest request) {
        LambdaQueryWrapper<RbacRole> wrapper = Wrappers.lambdaQuery(RbacRole.class)
                .and(StringUtils.hasText(request.getKeyword()), item -> item
                        .like(RbacRole::getCode, request.getKeyword())
                        .or()
                        .like(RbacRole::getName, request.getKeyword()))
                .eq(StringUtils.hasText(request.getStatus()), RbacRole::getStatus, request.getStatus())
                .ne(RbacRole::getCode, ROLE_ADMIN)
                .orderByAsc(RbacRole::getId);
        Page<RbacRole> page = rbacRoleMapper.selectPage(Page.of(request.getPageNo(), request.getPageSize()), wrapper);
        List<RbacRoleResponse> records = page.getRecords().stream()
                .map(this::toRoleResponse)
                .collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public RbacRoleResponse createRole(RbacRoleCreateRequest request) {
        Long count = rbacRoleMapper.selectCount(
                Wrappers.lambdaQuery(RbacRole.class).eq(RbacRole::getCode, request.getCode()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "角色编码已存在");
        }
        RbacRole role = new RbacRole();
        role.setCode(request.getCode());
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setStatus(request.getStatus());
        rbacRoleMapper.insert(role);
        return toRoleResponse(role);
    }

    @Override
    public RbacRoleResponse updateRole(RbacRoleUpdateRequest request) {
        RbacRole role = mustGetRole(request.getId());
        rejectAdminRoleOperation(role);
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setStatus(request.getStatus());
        rbacRoleMapper.updateById(role);
        return toRoleResponse(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteRole(IdRequest request) {
        RbacRole role = mustGetRole(request.getId());
        rejectAdminRoleOperation(role);
        rbacUserRoleMapper.delete(Wrappers.lambdaQuery(RbacUserRole.class).eq(RbacUserRole::getRoleId, request.getId()));
        rbacRolePermissionMapper.delete(Wrappers.lambdaQuery(RbacRolePermission.class).eq(RbacRolePermission::getRoleId, request.getId()));
        rbacRoleMapper.deleteById(request.getId());
        return Boolean.TRUE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<RbacPermissionResponse> listPermissions() {
        ensureBuiltinPermissionTree();
        return rbacPermissionMapper.selectList(
                        Wrappers.lambdaQuery(RbacPermission.class)
                                .orderByAsc(RbacPermission::getParentId)
                                .orderByAsc(RbacPermission::getSortNo)
                                .orderByAsc(RbacPermission::getId))
                .stream()
                .filter(permission -> !BASIC_AUTH_PERMISSION_CODES.contains(permission.getCode()))
                .map(this::toPermissionResponse)
                .collect(Collectors.toList());
    }

    @Override
    public RbacPermissionResponse createPermission(RbacPermissionCreateRequest request) {
        Long count = rbacPermissionMapper.selectCount(
                Wrappers.lambdaQuery(RbacPermission.class).eq(RbacPermission::getCode, request.getCode()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "权限编码已存在");
        }
        validateParentPermission(request.getParentId(), null);
        RbacPermission permission = new RbacPermission();
        permission.setParentId(request.getParentId());
        permission.setCode(request.getCode());
        permission.setPermissionKey(request.getPermissionKey());
        permission.setName(request.getName());
        permission.setType(request.getType());
        permission.setResourcePath(request.getResourcePath());
        permission.setComponent(request.getComponent());
        permission.setDescription(request.getDescription());
        permission.setSortNo(request.getSortNo());
        permission.setStatus(request.getStatus());
        rbacPermissionMapper.insert(permission);
        return toPermissionResponse(permission);
    }

    @Override
    public RbacPermissionResponse updatePermission(RbacPermissionUpdateRequest request) {
        RbacPermission permission = mustGetPermission(request.getId());
        validateParentPermission(request.getParentId(), request.getId());
        permission.setParentId(request.getParentId());
        permission.setPermissionKey(request.getPermissionKey());
        permission.setName(request.getName());
        permission.setType(request.getType());
        permission.setResourcePath(request.getResourcePath());
        permission.setComponent(request.getComponent());
        permission.setDescription(request.getDescription());
        permission.setSortNo(request.getSortNo());
        permission.setStatus(request.getStatus());
        rbacPermissionMapper.updateById(permission);
        return toPermissionResponse(permission);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deletePermission(IdRequest request) {
        mustGetPermission(request.getId());
        Long childCount = rbacPermissionMapper.selectCount(
                Wrappers.lambdaQuery(RbacPermission.class).eq(RbacPermission::getParentId, request.getId()));
        if (childCount > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请先删除子菜单或子权限");
        }
        rbacRolePermissionMapper.delete(
                Wrappers.lambdaQuery(RbacRolePermission.class).eq(RbacRolePermission::getPermissionId, request.getId()));
        rbacPermissionMapper.deleteById(request.getId());
        return Boolean.TRUE;
    }

    @Override
    public RbacRolePermissionDetailResponse rolePermissions(RbacRolePermissionDetailRequest request) {
        mustGetRole(request.getRoleId());
        List<Long> permissionIds = rbacRolePermissionMapper.selectList(
                        Wrappers.lambdaQuery(RbacRolePermission.class).eq(RbacRolePermission::getRoleId, request.getRoleId()))
                .stream()
                .map(RbacRolePermission::getPermissionId)
                .collect(Collectors.toList());
        RbacRolePermissionDetailResponse response = new RbacRolePermissionDetailResponse();
        response.setRoleId(request.getRoleId());
        response.setPermissionIds(permissionIds);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveRolePermissions(RbacRolePermissionSaveRequest request) {
        RbacRole role = mustGetRole(request.getRoleId());
        rejectAdminRoleOperation(role);
        rbacRolePermissionMapper.delete(
                Wrappers.lambdaQuery(RbacRolePermission.class).eq(RbacRolePermission::getRoleId, request.getRoleId()));
        Set<Long> permissionIds = completePermissionIdsWithParents(normalizeIds(request.getPermissionIds()));
        if (!permissionIds.isEmpty()) {
            validatePermissions(permissionIds);
            permissionIds.forEach(permissionId -> {
                RbacRolePermission relation = new RbacRolePermission();
                relation.setRoleId(request.getRoleId());
                relation.setPermissionId(permissionId);
                rbacRolePermissionMapper.insert(relation);
            });
        }
        return Boolean.TRUE;
    }

    @Override
    public PageResponse<RbacUserResponse> listUsers(RbacUserListRequest request) {
        LambdaQueryWrapper<HrUser> wrapper = Wrappers.lambdaQuery(HrUser.class)
                .and(StringUtils.hasText(request.getKeyword()), item -> item
                        .like(HrUser::getName, request.getKeyword())
                        .or()
                        .like(HrUser::getEmail, request.getKeyword()))
                .eq(StringUtils.hasText(request.getStatus()), HrUser::getStatus, request.getStatus())
                .orderByDesc(HrUser::getCreatedAt);
        Page<HrUser> page = hrUserMapper.selectPage(Page.of(request.getPageNo(), request.getPageSize()), wrapper);
        List<HrUser> users = page.getRecords();
        Map<Long, List<RbacRole>> userRoleMap = batchQueryUserRoles(users);
        List<RbacUserResponse> records = users.stream()
                .map(user -> toUserResponse(user, userRoleMap.getOrDefault(user.getId(), new ArrayList<>())))
                .collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RbacUserResponse createUser(RbacUserCreateRequest request) {
        Long count = hrUserMapper.selectCount(
                Wrappers.lambdaQuery(HrUser.class).eq(HrUser::getEmail, request.getEmail()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "邮箱已存在");
        }
        Set<Long> roleIds = normalizeIds(request.getRoleIds());
        validateAssignableRoles(roleIds);

        HrUser user = new HrUser();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(ROLE_USER);
        user.setStatus(request.getStatus());
        hrUserMapper.insert(user);

        saveUserRoleRelations(user.getId(), roleIds);
        return toUserResponse(user, queryRoles(roleIds));
    }

    @Override
    public Boolean resetUserPassword(RbacUserResetPasswordRequest request) {
        HrUser user = hrUserMapper.selectById(request.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        hrUserMapper.updateById(user);
        return Boolean.TRUE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveUserRoles(RbacUserRoleSaveRequest request) {
        HrUser user = hrUserMapper.selectById(request.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        if (ROLE_ADMIN.equals(user.getRole())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "系统管理员不需要分配角色");
        }
        Set<Long> roleIds = normalizeIds(request.getRoleIds());
        validateAssignableRoles(roleIds);
        saveUserRoleRelations(request.getUserId(), roleIds);
        return Boolean.TRUE;
    }

    private RbacRole mustGetRole(Long id) {
        RbacRole role = rbacRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        return role;
    }

    private void rejectAdminRoleOperation(RbacRole role) {
        if (role != null && ROLE_ADMIN.equals(role.getCode())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "系统管理员角色不允许编辑或删除");
        }
    }

    private RbacPermission mustGetPermission(Long id) {
        RbacPermission permission = rbacPermissionMapper.selectById(id);
        if (permission == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "权限不存在");
        }
        return permission;
    }

    private void validateParentPermission(Long parentId, Long currentId) {
        if (parentId == null || parentId == 0) {
            return;
        }
        if (parentId.equals(currentId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "父级不能选择自己");
        }
        if (rbacPermissionMapper.selectById(parentId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "父级权限不存在");
        }
    }

    private void validateRoles(Set<Long> roleIds) {
        if (rbacRoleMapper.selectBatchIds(roleIds).size() != roleIds.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
    }

    private void validateAssignableRoles(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        List<RbacRole> roles = rbacRoleMapper.selectBatchIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        boolean hasAdminRole = roles.stream().anyMatch(role -> ROLE_ADMIN.equals(role.getCode()));
        if (hasAdminRole) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "普通用户不能分配系统管理员角色");
        }
    }

    private void validatePermissions(Set<Long> permissionIds) {
        if (rbacPermissionMapper.selectBatchIds(permissionIds).size() != permissionIds.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "权限不存在");
        }
    }

    private Set<Long> completePermissionIdsWithParents(Set<Long> permissionIds) {
        if (permissionIds.isEmpty()) {
            return permissionIds;
        }
        Map<Long, RbacPermission> permissionMap = rbacPermissionMapper.selectList(Wrappers.lambdaQuery(RbacPermission.class))
                .stream()
                .collect(Collectors.toMap(RbacPermission::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        Set<Long> completedIds = new LinkedHashSet<>(permissionIds);
        permissionIds.forEach(permissionId -> appendParentPermissionIds(permissionId, permissionMap, completedIds));
        return completedIds;
    }

    private void appendParentPermissionIds(Long permissionId, Map<Long, RbacPermission> permissionMap, Set<Long> completedIds) {
        RbacPermission permission = permissionMap.get(permissionId);
        while (permission != null && permission.getParentId() != null && permission.getParentId() > 0) {
            Long parentId = permission.getParentId();
            completedIds.add(parentId);
            permission = permissionMap.get(parentId);
        }
    }

    private Set<Long> normalizeIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return new LinkedHashSet<>();
        }
        return ids.stream()
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void ensureBuiltinPermissionTree() {
        Map<String, RbacPermission> permissionMap = rbacPermissionMapper.selectList(Wrappers.lambdaQuery(RbacPermission.class))
                .stream()
                .collect(Collectors.toMap(RbacPermission::getCode, item -> item, (left, right) -> left, LinkedHashMap::new));

        for (BuiltinPermission builtin : BUILTIN_PERMISSIONS) {
            Long parentId = resolveParentId(permissionMap, builtin.parentCode());
            RbacPermission permission = permissionMap.get(builtin.code());
            if (permission == null) {
                permission = new RbacPermission();
                permission.setParentId(parentId);
                permission.setCode(builtin.code());
                fillBuiltinPermission(permission, builtin);
                rbacPermissionMapper.insert(permission);
                permissionMap.put(permission.getCode(), permission);
                continue;
            }
            fillBuiltinPermission(permission, builtin);
            permission.setParentId(parentId);
            rbacPermissionMapper.updateById(permission);
        }

        grantBuiltinPermissionsToAdmin(permissionMap);
    }

    private Long resolveParentId(Map<String, RbacPermission> permissionMap, String parentCode) {
        if (!StringUtils.hasText(parentCode)) {
            return 0L;
        }
        RbacPermission parent = permissionMap.get(parentCode);
        return parent == null ? 0L : parent.getId();
    }

    private void fillBuiltinPermission(RbacPermission permission, BuiltinPermission builtin) {
        permission.setPermissionKey(builtin.permissionKey());
        permission.setName(builtin.name());
        permission.setType(builtin.type());
        permission.setResourcePath(builtin.resourcePath());
        permission.setComponent(builtin.component());
        permission.setDescription(builtin.description());
        permission.setSortNo(builtin.sortNo());
        if (!StringUtils.hasText(permission.getStatus())) {
            permission.setStatus(STATUS_ENABLED);
        }
    }

    private void grantBuiltinPermissionsToAdmin(Map<String, RbacPermission> permissionMap) {
        List<RbacRole> roles = rbacRoleMapper.selectList(
                Wrappers.lambdaQuery(RbacRole.class).eq(RbacRole::getCode, ROLE_ADMIN));
        if (roles.isEmpty() || permissionMap.isEmpty()) {
            return;
        }
        List<Long> permissionIds = permissionMap.values().stream()
                .map(RbacPermission::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        if (permissionIds.isEmpty()) {
            return;
        }
        for (RbacRole role : roles) {
            List<Long> existingPermissionIds = rbacRolePermissionMapper.selectList(
                            Wrappers.lambdaQuery(RbacRolePermission.class).eq(RbacRolePermission::getRoleId, role.getId()))
                    .stream()
                    .map(RbacRolePermission::getPermissionId)
                    .collect(Collectors.toList());
            Set<Long> existingIdSet = new LinkedHashSet<>(existingPermissionIds);
            permissionIds.forEach(permissionId -> {
                if (!existingIdSet.contains(permissionId)) {
                    RbacRolePermission relation = new RbacRolePermission();
                    relation.setRoleId(role.getId());
                    relation.setPermissionId(permissionId);
                    rbacRolePermissionMapper.insert(relation);
                }
            });
        }
    }

    private void saveUserRoleRelations(Long userId, Set<Long> roleIds) {
        rbacUserRoleMapper.delete(Wrappers.lambdaQuery(RbacUserRole.class).eq(RbacUserRole::getUserId, userId));
        roleIds.forEach(roleId -> {
            RbacUserRole relation = new RbacUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            rbacUserRoleMapper.insert(relation);
        });
    }

    private List<RbacRole> queryRoles(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        return rbacRoleMapper.selectBatchIds(roleIds);
    }

    private Map<Long, List<RbacRole>> batchQueryUserRoles(List<HrUser> users) {
        Map<Long, List<RbacRole>> userRoleMap = new LinkedHashMap<>();
        if (CollectionUtils.isEmpty(users)) {
            return userRoleMap;
        }
        Set<Long> userIds = users.stream().map(HrUser::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<RbacUserRole> userRoles = rbacUserRoleMapper.selectList(
                Wrappers.lambdaQuery(RbacUserRole.class).in(RbacUserRole::getUserId, userIds));
        Set<Long> roleIds = userRoles.stream()
                .map(RbacUserRole::getRoleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, RbacRole> roleMap = new LinkedHashMap<>();
        if (!roleIds.isEmpty()) {
            rbacRoleMapper.selectBatchIds(roleIds).forEach(role -> roleMap.put(role.getId(), role));
        }
        userRoles.forEach(userRole -> {
            RbacRole role = roleMap.get(userRole.getRoleId());
            if (role != null) {
                userRoleMap.computeIfAbsent(userRole.getUserId(), key -> new ArrayList<>()).add(role);
            }
        });
        return userRoleMap;
    }

    private RbacRoleResponse toRoleResponse(RbacRole role) {
        RbacRoleResponse response = new RbacRoleResponse();
        response.setId(role.getId());
        response.setCode(role.getCode());
        response.setName(role.getName());
        response.setDescription(role.getDescription());
        response.setStatus(role.getStatus());
        response.setCreatedAt(role.getCreatedAt());
        return response;
    }

    private RbacPermissionResponse toPermissionResponse(RbacPermission permission) {
        RbacPermissionResponse response = new RbacPermissionResponse();
        response.setId(permission.getId());
        response.setParentId(permission.getParentId());
        response.setCode(permission.getCode());
        response.setPermissionKey(permission.getPermissionKey());
        response.setName(permission.getName());
        response.setType(permission.getType());
        response.setResourcePath(permission.getResourcePath());
        response.setComponent(permission.getComponent());
        response.setDescription(permission.getDescription());
        response.setSortNo(permission.getSortNo());
        response.setStatus(permission.getStatus());
        return response;
    }

    private RbacUserResponse toUserResponse(HrUser user, List<RbacRole> roles) {
        RbacUserResponse response = new RbacUserResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setRoleIds(roles.stream().map(RbacRole::getId).collect(Collectors.toList()));
        response.setRoleNames(roles.stream().map(RbacRole::getName).collect(Collectors.toList()));
        return response;
    }

    private record BuiltinPermission(
            String parentCode,
            String code,
            String permissionKey,
            String name,
            String type,
            String resourcePath,
            String component,
            String description,
            Integer sortNo
    ) {
    }
}
