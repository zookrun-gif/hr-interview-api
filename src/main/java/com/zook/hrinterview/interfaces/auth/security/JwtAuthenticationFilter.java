package com.zook.hrinterview.interfaces.auth.security;

import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.enums.RedisKeyEnum;
import com.zook.hrinterview.common.ErrorCode;
import com.zook.hrinterview.utils.RedisUtils;
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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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
                Object cachedLoginUser = redisUtils.get(RedisKeyEnum.AUTH_LOGIN_TOKEN, token);
                if (cachedLoginUser == null) {
                    throw new BusinessException(ErrorCode.UNAUTHORIZED);
                }
                LoginUser loginUser = cachedLoginUser instanceof LoginUser
                        ? (LoginUser) cachedLoginUser
                        : jwtTokenProvider.parseToken(token);
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

    public static String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith(TOKEN_PREFIX)) {
            return authorization.substring(TOKEN_PREFIX.length());
        }
        return null;
    }
}
