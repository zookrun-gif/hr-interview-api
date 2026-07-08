package com.zook.hrinterview.interfaces.auth.security;

import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.enums.RedisKeyEnum;
import com.zook.hrinterview.common.ErrorCode;
import com.zook.hrinterview.utils.RedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    public static final String TOKEN_PREFIX = "Bearer ";

    public static final String ACCESS_LOG_USER_ID_ATTR = "accessLogUserId";

    private final JwtTokenProvider jwtTokenProvider;

    private final RedisUtils redisUtils;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, RedisUtils redisUtils) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisUtils = redisUtils;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI() != null && request.getRequestURI().startsWith("/ws/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (StringUtils.hasText(token)) {
                if (isLogoutToken(token)) {
                    throw new BusinessException(ErrorCode.UNAUTHORIZED);
                }
                LoginUser loginUser = resolveLoginUser(token);
                LoginUserContext.set(loginUser);
                request.setAttribute(ACCESS_LOG_USER_ID_ATTR, loginUser.getId());
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        loginUser,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + loginUser.getRole()))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            filterChain.doFilter(request, response);
        } finally {
            LoginUserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private LoginUser resolveLoginUser(String token) {
        Object cachedLoginUser = readCachedLoginUser(token);
        if (cachedLoginUser instanceof LoginUser) {
            return (LoginUser) cachedLoginUser;
        }
        LoginUser loginUser = jwtTokenProvider.parseToken(token);
        cacheLoginUser(token, loginUser);
        return loginUser;
    }

    private Object readCachedLoginUser(String token) {
        try {
            return redisUtils.get(RedisKeyEnum.AUTH_LOGIN_TOKEN, token);
        } catch (Exception ex) {
            log.warn("Read auth token cache failed, fallback to jwt parse");
            return null;
        }
    }

    private boolean isLogoutToken(String token) {
        try {
            return Boolean.TRUE.equals(redisUtils.hasKey(RedisKeyEnum.AUTH_LOGOUT_TOKEN, token));
        } catch (Exception ex) {
            log.warn("Read auth logout token failed, fallback to jwt parse");
            return false;
        }
    }

    private void cacheLoginUser(String token, LoginUser loginUser) {
        long remainingSeconds = jwtTokenProvider.getRemainingSeconds(token);
        if (remainingSeconds <= 0) {
            return;
        }
        try {
            redisUtils.set(RedisKeyEnum.AUTH_LOGIN_TOKEN, token, loginUser, remainingSeconds, TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.warn("Write auth token cache failed");
        }
    }

    public static String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith(TOKEN_PREFIX)) {
            return authorization.substring(TOKEN_PREFIX.length());
        }
        return null;
    }
}
