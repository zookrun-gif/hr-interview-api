package com.zook.hrinterview.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zook.hrinterview.common.TraceIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiAccessLogFilter extends OncePerRequestFilter {

    private static final Logger API_ACCESS_LOGGER = LoggerFactory.getLogger("API_ACCESS_LOG");

    private static final int MAX_LOG_TEXT_LENGTH = 4000;

    private final ObjectMapper objectMapper;

    @Value("${spring.application.name:hr-interview-api}")
    private String applicationName;

    public ApiAccessLogFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        Exception failure = null;
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } catch (ServletException | IOException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            writeAccessLog(requestWrapper, responseWrapper, startTime, failure);
            responseWrapper.copyBodyToResponse();
        }
    }

    private void writeAccessLog(
            ContentCachingRequestWrapper request,
            ContentCachingResponseWrapper response,
            long startTime,
            Exception failure
    ) {
        Map<String, Object> accessLog = new LinkedHashMap<>();
        accessLog.put("logType", "API_ACCESS");
        accessLog.put("projectName", applicationName);
        accessLog.put("traceId", TraceIdContext.get());
        accessLog.put("clientIp", resolveClientIp(request));
        accessLog.put("httpMethod", request.getMethod());
        accessLog.put("requestUri", request.getRequestURI());
        accessLog.put("requestParams", resolveRequestParams(request));
        accessLog.put("requestBody", resolveRequestBody(request));
        accessLog.put("responseStatus", response.getStatus());
        accessLog.put("responseBody", resolveResponseBody(request, response));
        accessLog.put("time", System.currentTimeMillis() - startTime);
        if (failure != null) {
            accessLog.put("exception", failure.getClass().getSimpleName());
            accessLog.put("exceptionMessage", limitText(failure.getMessage()));
        }
        API_ACCESS_LOGGER.info(toJson(accessLog));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headers) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value) && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private Map<String, Object> resolveRequestParams(HttpServletRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (isSensitiveKey(key)) {
                params.put(key, "******");
            } else if (values == null) {
                params.put(key, null);
            } else if (values.length == 1) {
                params.put(key, limitText(values[0]));
            } else {
                List<String> valueList = new ArrayList<>();
                for (String value : values) {
                    valueList.add(limitText(value));
                }
                params.put(key, valueList);
            }
        });
        return params;
    }

    private String resolveRequestBody(ContentCachingRequestWrapper request) {
        if (isMultipart(request.getContentType())) {
            return "[multipart/form-data]";
        }
        return resolveCachedBody(request.getContentAsByteArray(), request.getCharacterEncoding());
    }

    private String resolveResponseBody(HttpServletRequest request, ContentCachingResponseWrapper response) {
        return resolveCachedBody(response.getContentAsByteArray(), response.getCharacterEncoding(), request.getRequestURI());
    }

    private String resolveCachedBody(byte[] content, String encoding) {
        return resolveCachedBody(content, encoding, null);
    }

    private String resolveCachedBody(byte[] content, String encoding, String requestUri) {
        if (content == null || content.length == 0) {
            return "";
        }
        Charset charset = StringUtils.hasText(encoding) ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        String body = new String(content, charset);
        return sanitizeAndLimitBody(body, requestUri);
    }

    private boolean isMultipart(String contentType) {
        return StringUtils.hasText(contentType)
                && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    private String sanitizeAndLimitBody(String body, String requestUri) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        try {
            Object parsed = objectMapper.readValue(body, Object.class);
            Object masked = maskSensitiveValue(parsed);
            Object compressed = compressPermissionListResponse(requestUri, compressPageResponse(masked));
            return limitText(objectMapper.writeValueAsString(compressed));
        } catch (Exception ignored) {
            return limitText(maskSensitiveText(body));
        }
    }

    @SuppressWarnings("unchecked")
    private Object maskSensitiveValue(Object value) {
        if (value instanceof Map) {
            Map<String, Object> source = (Map<String, Object>) value;
            Map<String, Object> masked = new LinkedHashMap<>();
            source.forEach((key, item) -> {
                if (isSensitiveKey(key)) {
                    masked.put(key, "******");
                } else {
                    masked.put(key, maskSensitiveValue(item));
                }
            });
            return masked;
        }
        if (value instanceof List) {
            List<Object> source = (List<Object>) value;
            List<Object> masked = new ArrayList<>();
            for (Object item : source) {
                masked.add(maskSensitiveValue(item));
            }
            return masked;
        }
        if (value instanceof String) {
            return limitText((String) value);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Object compressPageResponse(Object value) {
        if (!(value instanceof Map)) {
            return value;
        }
        Map<String, Object> response = (Map<String, Object>) value;
        Object data = response.get("data");
        if (!(data instanceof Map)) {
            return value;
        }
        Map<String, Object> pageData = (Map<String, Object>) data;
        Object records = pageData.get("records");
        if (!(records instanceof List)
                || !pageData.containsKey("total")
                || !pageData.containsKey("pageNo")
                || !pageData.containsKey("pageSize")) {
            return value;
        }

        Map<String, Object> compressedResponse = new LinkedHashMap<>(response);
        Map<String, Object> compressedData = new LinkedHashMap<>();
        compressedData.put("pageNo", pageData.get("pageNo"));
        compressedData.put("pageSize", pageData.get("pageSize"));
        compressedData.put("total", pageData.get("total"));
        compressedData.put("recordCount", ((List<?>) records).size());
        compressedData.put("records", "[compressed]");
        compressedResponse.put("data", compressedData);
        return compressedResponse;
    }

    @SuppressWarnings("unchecked")
    private Object compressPermissionListResponse(String requestUri, Object value) {
        if (!"/api/rbac/permissions/list".equals(requestUri) || !(value instanceof Map)) {
            return value;
        }
        Map<String, Object> response = (Map<String, Object>) value;
        Object data = response.get("data");
        if (!(data instanceof List)) {
            return value;
        }
        Map<String, Object> compressedResponse = new LinkedHashMap<>(response);
        Map<String, Object> compressedData = new LinkedHashMap<>();
        compressedData.put("count", ((List<?>) data).size());
        compressedData.put("records", "[compressed]");
        compressedResponse.put("data", compressedData);
        return compressedResponse;
    }

    private boolean isSensitiveKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        return normalizedKey.contains("password")
                || normalizedKey.contains("token")
                || normalizedKey.contains("secret")
                || normalizedKey.contains("authorization")
                || normalizedKey.contains("accesscode")
                || normalizedKey.contains("access_code")
                || normalizedKey.contains("access-code");
    }

    private String maskSensitiveText(String text) {
        return text
                .replaceAll("(?i)(\"?(password|token|secret|authorization|accessCode|access_code|access-code)\"?\\s*[:=]\\s*\")([^\"]+)(\")", "$1******$4")
                .replaceAll("(?i)((password|token|secret|authorization|accessCode|access_code|access-code)\\s*=\\s*)[^&\\s]+", "$1******");
    }

    private String limitText(String text) {
        if (text == null) {
            return null;
        }
        if (text.length() <= MAX_LOG_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_LOG_TEXT_LENGTH) + "...[truncated]";
    }

    private String toJson(Map<String, Object> accessLog) {
        try {
            return objectMapper.writeValueAsString(accessLog);
        } catch (JsonProcessingException ex) {
            return String.valueOf(accessLog);
        }
    }
}
