package com.zook.hrinterview.interfaces.auth.service;

import com.zook.hrinterview.interfaces.auth.dto.ChangePasswordRequest;
import com.zook.hrinterview.interfaces.auth.dto.CurrentUserResponse;
import com.zook.hrinterview.interfaces.auth.dto.LoginRequest;
import com.zook.hrinterview.interfaces.auth.dto.LoginResponse;
import com.zook.hrinterview.interfaces.auth.dto.LogoutRequest;
import com.zook.hrinterview.interfaces.auth.dto.WeComLoginConfigResponse;
import com.zook.hrinterview.interfaces.auth.dto.WeComLoginRequest;

import javax.servlet.http.HttpServletRequest;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    WeComLoginConfigResponse wecomConfig();

    LoginResponse wecomLogin(WeComLoginRequest request);

    CurrentUserResponse me();

    Boolean logout(LogoutRequest request, HttpServletRequest httpServletRequest);

    Boolean changePassword(ChangePasswordRequest request);
}
