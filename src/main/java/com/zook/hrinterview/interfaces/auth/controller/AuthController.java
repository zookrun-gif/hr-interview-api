package com.zook.hrinterview.interfaces.auth.controller;

import com.zook.hrinterview.interfaces.auth.dto.ChangePasswordRequest;
import com.zook.hrinterview.interfaces.auth.dto.CurrentUserResponse;
import com.zook.hrinterview.interfaces.auth.dto.LoginRequest;
import com.zook.hrinterview.interfaces.auth.dto.LoginResponse;
import com.zook.hrinterview.interfaces.auth.dto.LogoutRequest;
import com.zook.hrinterview.interfaces.auth.dto.WeComLoginConfigResponse;
import com.zook.hrinterview.interfaces.auth.dto.WeComLoginRequest;
import com.zook.hrinterview.interfaces.auth.service.AuthService;
import com.zook.hrinterview.common.ApiResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@Api(tags = "登录认证")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    @ApiOperation("登录")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @ApiOperation("查询企业微信扫码登录配置")
    @PostMapping("/wecom/config")
    public ApiResponse<WeComLoginConfigResponse> wecomConfig() {
        return ApiResponse.success(authService.wecomConfig());
    }

    @ApiOperation("企业微信扫码登录")
    @PostMapping("/wecom/login")
    public ApiResponse<LoginResponse> wecomLogin(@Valid @RequestBody WeComLoginRequest request) {
        return ApiResponse.success(authService.wecomLogin(request));
    }

    @ApiOperation("查询当前用户")
    @PostMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        return ApiResponse.success(authService.me());
    }

    @ApiOperation("退出登录")
    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(@RequestBody LogoutRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.success(authService.logout(request, httpServletRequest));
    }

    @ApiOperation("修改密码")
    @PostMapping("/change-password")
    public ApiResponse<Boolean> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.success(authService.changePassword(request));
    }
}
