package com.zook.hrinterview.interfaces.auth.service.impl;

import com.zook.hrinterview.config.WeComProperties;
import com.zook.hrinterview.interfaces.auth.service.AuthService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zook.hrinterview.common.constant.ThirdPartyApiConstant;
import com.zook.hrinterview.interfaces.auth.dto.ChangePasswordRequest;
import com.zook.hrinterview.interfaces.auth.dto.CurrentUserResponse;
import com.zook.hrinterview.interfaces.auth.dto.LoginRequest;
import com.zook.hrinterview.interfaces.auth.dto.LoginResponse;
import com.zook.hrinterview.interfaces.auth.dto.LogoutRequest;
import com.zook.hrinterview.interfaces.auth.dto.WeComLoginConfigResponse;
import com.zook.hrinterview.interfaces.auth.dto.WeComLoginRequest;
import com.zook.hrinterview.interfaces.auth.entity.HrUser;
import com.zook.hrinterview.interfaces.auth.entity.RbacRole;
import com.zook.hrinterview.interfaces.auth.entity.RbacUserRole;
import com.zook.hrinterview.interfaces.auth.mapper.HrUserMapper;
import com.zook.hrinterview.interfaces.auth.mapper.RbacRoleMapper;
import com.zook.hrinterview.interfaces.auth.mapper.RbacUserRoleMapper;
import com.zook.hrinterview.interfaces.auth.security.JwtAuthenticationFilter;
import com.zook.hrinterview.interfaces.auth.security.JwtTokenProvider;
import com.zook.hrinterview.interfaces.auth.security.LoginUser;
import com.zook.hrinterview.interfaces.auth.security.LoginUserContext;
import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.enums.RedisKeyEnum;
import com.zook.hrinterview.common.ErrorCode;
import com.zook.hrinterview.interfaces.auth.service.RbacPermissionService;
import com.zook.hrinterview.interfaces.auth.wecom.WeComTokenResponse;
import com.zook.hrinterview.interfaces.auth.wecom.WeComUserDetailResponse;
import com.zook.hrinterview.interfaces.auth.wecom.WeComUserInfoResponse;
import com.zook.hrinterview.utils.HttpRestClient;
import com.zook.hrinterview.utils.RedisUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthServiceImpl extends ServiceImpl<HrUserMapper, HrUser> implements AuthService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String ROLE_USER = "USER";

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private JwtTokenProvider jwtTokenProvider;

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private RbacPermissionService rbacPermissionService;

    @Resource
    private WeComProperties weComProperties;

    @Resource
    private RbacRoleMapper rbacRoleMapper;

    @Resource
    private RbacUserRoleMapper rbacUserRoleMapper;

    @Resource
    private HttpRestClient httpRestClient;

    @Override
    public LoginResponse login(LoginRequest request) {
        HrUser user = getOne(Wrappers.lambdaQuery(HrUser.class).eq(HrUser::getEmail, request.getEmail()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "邮箱或密码错误");
        }
        if (!STATUS_ENABLED.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }

        return createLoginResponse(user);
    }

    @Override
    public WeComLoginConfigResponse wecomConfig() {
        validateWeComConfig(false);
        String state = UUID.randomUUID().toString().replace("-", "");
        redisUtils.set(RedisKeyEnum.AUTH_WECOM_STATE, state, "1");

        WeComLoginConfigResponse response = new WeComLoginConfigResponse();
        response.setEnabled(Boolean.TRUE.equals(weComProperties.getEnabled()));
        response.setCorpId(weComProperties.getCorpId());
        response.setAgentId(weComProperties.getAgentId());
        response.setRedirectUri(weComProperties.getRedirectUri());
        response.setState(state);
        response.setLoginUrl(buildWeComLoginUrl(state));
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse wecomLogin(WeComLoginRequest request) {
        validateWeComConfig(true);
        if (!Boolean.TRUE.equals(redisUtils.hasKey(RedisKeyEnum.AUTH_WECOM_STATE, request.getState()))) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "企业微信登录已过期，请重新扫码");
        }
        redisUtils.delete(RedisKeyEnum.AUTH_WECOM_STATE, request.getState());

        String accessToken = getWeComAccessToken();
        WeComUserInfoResponse userInfo = requestWeComUserInfo(accessToken, request.getCode());
        if (!StringUtils.hasText(userInfo.getUserId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "当前企业微信账号不是企业成员");
        }

        WeComUserDetailResponse userDetail = requestWeComUserDetail(accessToken, userInfo.getUserId());
        HrUser user = findOrCreateWeComUser(userDetail);
        if (!STATUS_ENABLED.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        return createLoginResponse(user);
    }

    @Override
    public CurrentUserResponse me() {
        LoginUser loginUser = LoginUserContext.mustGet();
        HrUser user = getById(loginUser.getId());
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return toCurrentUserResponse(user, rbacPermissionService.listPermissionCodes(user.getId(), user.getRole()));
    }

    @Override
    public Boolean logout(LogoutRequest request, HttpServletRequest httpServletRequest) {
        String token = request.getToken();
        if (!StringUtils.hasText(token)) {
            token = JwtAuthenticationFilter.resolveToken(httpServletRequest);
        }
        if (StringUtils.hasText(token)) {
            redisUtils.delete(RedisKeyEnum.AUTH_LOGIN_TOKEN, token);
        }
        return Boolean.TRUE;
    }

    @Override
    public Boolean changePassword(ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "两次输入的新密码不一致");
        }

        LoginUser loginUser = LoginUserContext.mustGet();
        HrUser user = getById(loginUser.getId());
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "原密码错误");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        updateById(user);
        return Boolean.TRUE;
    }

    private LoginUser toLoginUser(HrUser user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(user.getId());
        loginUser.setName(user.getName());
        loginUser.setEmail(user.getEmail());
        loginUser.setRole(user.getRole());
        loginUser.setPermissionCodes(rbacPermissionService.listPermissionCodes(user.getId(), user.getRole()));
        return loginUser;
    }

    private CurrentUserResponse toCurrentUserResponse(HrUser user) {
        return toCurrentUserResponse(user, rbacPermissionService.listPermissionCodes(user.getId(), user.getRole()));
    }

    private CurrentUserResponse toCurrentUserResponse(HrUser user, java.util.Set<String> permissionCodes) {
        CurrentUserResponse response = new CurrentUserResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setMobile(user.getMobile());
        response.setWecomUserid(user.getWecomUserid());
        response.setAvatar(user.getAvatar());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setPermissionCodes(permissionCodes);
        return response;
    }

    private LoginResponse createLoginResponse(HrUser user) {
        LoginUser loginUser = toLoginUser(user);
        String token = jwtTokenProvider.createToken(loginUser);
        redisUtils.set(RedisKeyEnum.AUTH_LOGIN_TOKEN, token, loginUser, jwtTokenProvider.getExpiresInSeconds(), TimeUnit.SECONDS);

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtTokenProvider.getExpiresInSeconds());
        response.setUser(toCurrentUserResponse(user, loginUser.getPermissionCodes()));
        return response;
    }

    private void validateWeComConfig(boolean requireSecret) {
        if (!Boolean.TRUE.equals(weComProperties.getEnabled())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "企业微信登录未启用");
        }
        if (!StringUtils.hasText(weComProperties.getCorpId())
                || weComProperties.getCorpId().contains("请填写")
                || !StringUtils.hasText(weComProperties.getAgentId())
                || !StringUtils.hasText(weComProperties.getRedirectUri())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "企业微信登录配置不完整，请先配置corpId、agentId和redirectUri");
        }
        if (requireSecret && !StringUtils.hasText(weComProperties.getSecret())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "企业微信应用secret未配置");
        }
    }

    private String buildWeComLoginUrl(String state) {
        return ThirdPartyApiConstant.WECOM_QR_LOGIN_URL
                + "?appid=" + encode(weComProperties.getCorpId())
                + "&agentid=" + encode(weComProperties.getAgentId())
                + "&redirect_uri=" + encode(weComProperties.getRedirectUri())
                + "&state=" + encode(state);
    }

    private String getWeComAccessToken() {
        Object cachedToken = redisUtils.get(RedisKeyEnum.AUTH_WECOM_ACCESS_TOKEN);
        if (cachedToken instanceof String && StringUtils.hasText((String) cachedToken)) {
            return (String) cachedToken;
        }

        String url = httpRestClient.appendQuery(ThirdPartyApiConstant.WECOM_GET_TOKEN_URL, httpRestClient.mapOf(
                "corpid", weComProperties.getCorpId(),
                "corpsecret", weComProperties.getSecret()
        ));
        WeComTokenResponse response = httpRestClient.getForObject(url, WeComTokenResponse.class, 10);
        validateWeComResponse(response == null ? null : response.getErrCode(), response == null ? null : response.getErrMsg(), "获取企业微信access_token失败");
        if (!StringUtils.hasText(response.getAccessToken())) {
            throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, "企业微信access_token为空");
        }
        long expiresIn = response.getExpiresIn() == null ? 7200L : response.getExpiresIn();
        redisUtils.set(RedisKeyEnum.AUTH_WECOM_ACCESS_TOKEN, response.getAccessToken(), Math.max(60L, expiresIn - 300L), TimeUnit.SECONDS);
        return response.getAccessToken();
    }

    private WeComUserInfoResponse requestWeComUserInfo(String accessToken, String code) {
        String url = httpRestClient.appendQuery(ThirdPartyApiConstant.WECOM_GET_USER_INFO_URL, httpRestClient.mapOf(
                "access_token", accessToken,
                "code", code
        ));
        WeComUserInfoResponse response = httpRestClient.getForObject(url, WeComUserInfoResponse.class, 10);
        validateWeComResponse(response == null ? null : response.getErrCode(), response == null ? null : response.getErrMsg(), "获取企业微信登录用户失败");
        return response;
    }

    private WeComUserDetailResponse requestWeComUserDetail(String accessToken, String userId) {
        String url = httpRestClient.appendQuery(ThirdPartyApiConstant.WECOM_GET_USER_DETAIL_URL, httpRestClient.mapOf(
                "access_token", accessToken,
                "userid", userId
        ));
        WeComUserDetailResponse response = httpRestClient.getForObject(url, WeComUserDetailResponse.class, 10);
        validateWeComResponse(response == null ? null : response.getErrCode(), response == null ? null : response.getErrMsg(), "获取企业微信成员详情失败");
        return response;
    }

    private void validateWeComResponse(Integer errCode, String errMsg, String defaultMessage) {
        if (errCode == null || errCode != 0) {
            throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, defaultMessage + (StringUtils.hasText(errMsg) ? "：" + errMsg : ""));
        }
    }

    private HrUser findOrCreateWeComUser(WeComUserDetailResponse userDetail) {
        if (userDetail == null || !StringUtils.hasText(userDetail.getUserId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "企业微信成员信息为空");
        }

        HrUser user = getOne(Wrappers.lambdaQuery(HrUser.class).eq(HrUser::getWecomUserid, userDetail.getUserId()), false);
        if (user == null && StringUtils.hasText(userDetail.getEmail())) {
            user = getOne(Wrappers.lambdaQuery(HrUser.class).eq(HrUser::getEmail, userDetail.getEmail()), false);
        }
        if (user == null && StringUtils.hasText(userDetail.getMobile())) {
            user = getOne(Wrappers.lambdaQuery(HrUser.class).eq(HrUser::getMobile, userDetail.getMobile()), false);
        }

        if (user != null) {
            user.setWecomUserid(userDetail.getUserId());
            if (StringUtils.hasText(userDetail.getName())) {
                user.setName(userDetail.getName());
            }
            if (StringUtils.hasText(userDetail.getMobile())) {
                user.setMobile(userDetail.getMobile());
            }
            if (StringUtils.hasText(userDetail.getAvatar())) {
                user.setAvatar(userDetail.getAvatar());
            }
            updateById(user);
            return user;
        }

        if (!Boolean.TRUE.equals(weComProperties.getAutoCreateUser())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前企业微信账号未绑定系统账号，请联系管理员");
        }

        HrUser created = new HrUser();
        created.setName(StringUtils.hasText(userDetail.getName()) ? userDetail.getName() : userDetail.getUserId());
        created.setEmail(resolveEmail(userDetail));
        created.setMobile(userDetail.getMobile());
        created.setWecomUserid(userDetail.getUserId());
        created.setAvatar(userDetail.getAvatar());
        created.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        created.setRole(ROLE_USER);
        created.setStatus(STATUS_ENABLED);
        created.setCreatedAt(LocalDateTime.now());
        created.setUpdatedAt(LocalDateTime.now());
        save(created);
        bindDefaultRole(created.getId());
        return created;
    }

    private String resolveEmail(WeComUserDetailResponse userDetail) {
        if (StringUtils.hasText(userDetail.getEmail())) {
            return userDetail.getEmail();
        }
        String userId = userDetail.getUserId().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return userId + "@wecom.local";
    }

    private void bindDefaultRole(Long userId) {
        if (!StringUtils.hasText(weComProperties.getDefaultRoleCode())) {
            return;
        }
        RbacRole role = rbacRoleMapper.selectOne(
                Wrappers.lambdaQuery(RbacRole.class)
                        .eq(RbacRole::getCode, weComProperties.getDefaultRoleCode())
                        .eq(RbacRole::getStatus, STATUS_ENABLED));
        if (role == null) {
            return;
        }
        RbacUserRole relation = new RbacUserRole();
        relation.setUserId(userId);
        relation.setRoleId(role.getId());
        rbacUserRoleMapper.insert(relation);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
