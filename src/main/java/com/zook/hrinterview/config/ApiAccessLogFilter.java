package com.zook.hrinterview.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zook.hrinterview.interfaces.auth.security.JwtAuthenticationFilter;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiAccessLogFilter extends OncePerRequestFilter {

    private static final Logger API_ACCESS_LOGGER = LoggerFactory.getLogger("API_ACCESS_LOG");

    private static final int MAX_LOG_TEXT_LENGTH = 4000;

    private static final int MAX_SUMMARY_TEXT_LENGTH = 200;

    private static final Set<String> SAFE_SUMMARY_KEYS = Set.of(
            "id",
            "sessionid",
            "interviewid",
            "candidateid",
            "jobid",
            "reportid",
            "pageno",
            "pagesize",
            "status"
    );

    private static final Set<String> SAFE_RESPONSE_KEYS = Set.of(
            "code",
            "message",
            "success"
    );

    private final ObjectMapper objectMapper;

    @Value("${spring.application.name:hr-interview-api}")
    private String applicationName;

    @Value("${app.api-access-log.enabled:true}")
    private Boolean accessLogEnabled;

    @Value("${app.api-access-log.include-body:false}")
    private Boolean includeBody;

    @Value("${app.api-access-log.slow-threshold-ms:1000}")
    private Long slowThresholdMs;

    public ApiAccessLogFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !Boolean.TRUE.equals(accessLogEnabled) || uri == null || !uri.startsWith("/api/");
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
            HttpServletRequest request,
            HttpServletResponse response,
            long startTime,
            Exception failure
    ) {
        Map<String, Object> accessLog = new LinkedHashMap<>();
        accessLog.put("logType", "API_ACCESS");
        accessLog.put("projectName", applicationName);
        accessLog.put("userId", request.getAttribute(JwtAuthenticationFilter.ACCESS_LOG_USER_ID_ATTR));
        accessLog.put("clientIp", resolveClientIp(request));
        accessLog.put("httpMethod", request.getMethod());
        accessLog.put("requestUri", request.getRequestURI());
        accessLog.put("responseStatus", response.getStatus());
        long costMillis = System.currentTimeMillis() - startTime;
        Map<String, Object> requestSummary = resolveRequestSummary(request);
        if (!requestSummary.isEmpty()) {
            accessLog.put("requestSummary", requestSummary);
        }
        if (shouldIncludeDetailedSummary(response, costMillis, failure)) {
            accessLog.put("detailReason", resolveDetailReason(response, costMillis, failure));
            Map<String, Object> responseSummary = resolveResponseSummary(response);
            if (!responseSummary.isEmpty()) {
                accessLog.put("responseSummary", responseSummary);
            }
        }
        if (Boolean.TRUE.equals(includeBody)) {
            Map<String, Object> requestParams = resolveRequestParams(request);
            if (!requestParams.isEmpty()) {
                accessLog.put("requestParams", requestParams);
            }
            accessLog.put("requestBody", resolveRequestBody((ContentCachingRequestWrapper) request));
            accessLog.put("responseBody", resolveResponseBody(request, (ContentCachingResponseWrapper) response));
        }
        accessLog.put("time", costMillis);
        if (failure != null) {
            accessLog.put("exception", failure.getClass().getSimpleName());
            accessLog.put("exceptionMessage", limitText(failure.getMessage()));
        }
        API_ACCESS_LOGGER.info(toJson(accessLog));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = resolveForwardedHeader(request.getHeader("Forwarded"));
        if (StringUtils.hasText(forwarded)) {
            return forwarded;
        }

        String[] headers = {
                "CF-Connecting-IP",
                "True-Client-IP",
                "X-Original-Forwarded-For",
                "X-Forwarded-For",
                "X-Real-IP",
                "X-Client-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP"
        };
        for (String header : headers) {
            String clientIp = resolveFirstPublicIp(request.getHeader(header));
            if (StringUtils.hasText(clientIp)) {
                return clientIp;
            }
        }
        return request.getRemoteAddr();
    }

    private String resolveForwardedHeader(String forwarded) {
        if (!StringUtils.hasText(forwarded)) {
            return "";
        }
        for (String group : forwarded.split(",")) {
            for (String item : group.split(";")) {
                String part = item.trim();
                if (!part.toLowerCase(Locale.ROOT).startsWith("for=")) {
                    continue;
                }
                String clientIp = normalizeIpCandidate(part.substring(4));
                if (StringUtils.hasText(clientIp)) {
                    return clientIp;
                }
            }
        }
        return "";
    }

    private String resolveFirstPublicIp(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return "";
        }
        String firstValidIp = "";
        for (String item : headerValue.split(",")) {
            String clientIp = normalizeIpCandidate(item);
            if (!StringUtils.hasText(clientIp)) {
                continue;
            }
            if (!StringUtils.hasText(firstValidIp)) {
                firstValidIp = clientIp;
            }
            if (!isInternalIpv4(clientIp)) {
                return clientIp;
            }
        }
        return firstValidIp;
    }

    private String normalizeIpCandidate(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String clientIp = value.trim();
        if (clientIp.startsWith("\"") && clientIp.endsWith("\"") && clientIp.length() > 1) {
            clientIp = clientIp.substring(1, clientIp.length() - 1);
        }
        if (!StringUtils.hasText(clientIp) || "unknown".equalsIgnoreCase(clientIp)) {
            return "";
        }
        if (clientIp.startsWith("[")) {
            int end = clientIp.indexOf(']');
            return end > 0 ? clientIp.substring(1, end) : "";
        }
        int portIndex = clientIp.indexOf(':');
        if (portIndex > 0 && clientIp.indexOf(':', portIndex + 1) < 0) {
            clientIp = clientIp.substring(0, portIndex);
        }
        return clientIp.trim();
    }

    private boolean isInternalIpv4(String ip) {
        if (!StringUtils.hasText(ip) || !ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return false;
        }
        String[] parts = ip.split("\\.");
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        return first == 10
                || first == 127
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
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

    private Map<String, Object> resolveRequestSummary(HttpServletRequest request) {
        Map<String, Object> summary = new LinkedHashMap<>();
        appendSafeFields(summary, request.getParameterMap());
        if (request instanceof ContentCachingRequestWrapper wrapper && !isMultipart(request.getContentType())) {
            String body = rawCachedBody(wrapper.getContentAsByteArray(), wrapper.getCharacterEncoding());
            appendSafeFieldsFromJson(summary, body);
        }
        return summary;
    }

    private Map<String, Object> resolveResponseSummary(HttpServletResponse response) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (!(response instanceof ContentCachingResponseWrapper wrapper)) {
            return summary;
        }
        String body = rawCachedBody(wrapper.getContentAsByteArray(), wrapper.getCharacterEncoding());
        if (!StringUtils.hasText(body)) {
            return summary;
        }
        try {
            Object parsed = objectMapper.readValue(body, Object.class);
            appendResponseSummary(summary, parsed);
        } catch (Exception ignored) {
            summary.put("body", limitSummaryText(maskSensitiveText(body)));
        }
        return summary;
    }

    private boolean shouldIncludeDetailedSummary(HttpServletResponse response, long costMillis, Exception failure) {
        return failure != null
                || response.getStatus() >= 400
                || costMillis >= (slowThresholdMs == null ? 1000L : slowThresholdMs);
    }

    private List<String> resolveDetailReason(HttpServletResponse response, long costMillis, Exception failure) {
        List<String> reasons = new ArrayList<>();
        if (failure != null) {
            reasons.add("exception");
        }
        if (response.getStatus() >= 400) {
            reasons.add("status");
        }
        if (costMillis >= (slowThresholdMs == null ? 1000L : slowThresholdMs)) {
            reasons.add("slow");
        }
        return reasons;
    }

    private String rawCachedBody(byte[] content, String encoding) {
        if (content == null || content.length == 0) {
            return "";
        }
        Charset charset = StringUtils.hasText(encoding) ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        return new String(content, charset);
    }

    private void appendSafeFields(Map<String, Object> summary, Map<String, String[]> params) {
        if (params == null || params.isEmpty()) {
            return;
        }
        params.forEach((key, values) -> {
            if (!isSafeSummaryKey(key)) {
                return;
            }
            if (isSensitiveKey(key)) {
                summary.put(key, "******");
            } else if (values == null) {
                summary.put(key, null);
            } else if (values.length == 1) {
                summary.put(key, limitSummaryText(values[0]));
            } else {
                List<String> valueList = new ArrayList<>();
                for (String value : values) {
                    valueList.add(limitSummaryText(value));
                }
                summary.put(key, valueList);
            }
        });
    }

    private void appendSafeFieldsFromJson(Map<String, Object> summary, String body) {
        if (!StringUtils.hasText(body)) {
            return;
        }
        try {
            appendSafeFieldsFromValue(summary, objectMapper.readValue(body, Object.class));
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private void appendSafeFieldsFromValue(Map<String, Object> summary, Object value) {
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            map.forEach((key, item) -> {
                if (isSafeSummaryKey(key)) {
                    summary.put(key, summarizeSafeValue(key, item));
                } else if (item instanceof Map || item instanceof Collection) {
                    appendSafeFieldsFromValue(summary, item);
                }
            });
            return;
        }
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                appendSafeFieldsFromValue(summary, item);
            }
        }
    }

    private Object summarizeSafeValue(String key, Object value) {
        if (isSensitiveKey(key)) {
            return "******";
        }
        if (value instanceof String) {
            return limitSummaryText((String) value);
        }
        if (value instanceof Number || value instanceof Boolean || value == null) {
            return value;
        }
        if (value instanceof Collection) {
            List<Object> values = new ArrayList<>();
            for (Object item : (Collection<?>) value) {
                values.add(summarizeSafeValue(key, item));
            }
            return values;
        }
        return limitSummaryText(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private void appendResponseSummary(Map<String, Object> summary, Object value) {
        if (!(value instanceof Map)) {
            summary.put("bodyType", value == null ? "null" : value.getClass().getSimpleName());
            return;
        }
        Map<String, Object> map = (Map<String, Object>) value;
        map.forEach((key, item) -> {
            if (SAFE_RESPONSE_KEYS.contains(normalizeKey(key))) {
                summary.put(key, summarizeSafeValue(key, item));
            }
        });
        Object data = map.get("data");
        if (data instanceof Map) {
            Map<String, Object> dataSummary = new LinkedHashMap<>();
            appendSafeFieldsFromValue(dataSummary, data);
            appendPageSummary(dataSummary, (Map<String, Object>) data);
            if (!dataSummary.isEmpty()) {
                summary.put("data", dataSummary);
            }
        } else if (data instanceof Collection) {
            summary.put("dataCount", ((Collection<?>) data).size());
        }
    }

    private void appendPageSummary(Map<String, Object> summary, Map<String, Object> data) {
        Object records = data.get("records");
        if (!(records instanceof Collection)
                || !data.containsKey("total")
                || !data.containsKey("pageNo")
                || !data.containsKey("pageSize")) {
            return;
        }
        summary.put("pageNo", data.get("pageNo"));
        summary.put("pageSize", data.get("pageSize"));
        summary.put("total", data.get("total"));
        summary.put("recordCount", ((Collection<?>) records).size());
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
            Object compressed = removeResponseTraceId(compressPermissionListResponse(requestUri, compressPageResponse(masked)));
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

    @SuppressWarnings("unchecked")
    private Object removeResponseTraceId(Object value) {
        if (!(value instanceof Map)) {
            return value;
        }
        Map<String, Object> response = new LinkedHashMap<>((Map<String, Object>) value);
        response.remove("traceId");
        return response;
    }

    private boolean isSensitiveKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String normalizedKey = normalizeKey(key);
        return normalizedKey.contains("password")
                || normalizedKey.contains("token")
                || normalizedKey.contains("secret")
                || normalizedKey.contains("authorization")
                || normalizedKey.contains("accesscode")
                || normalizedKey.contains("access_code")
                || normalizedKey.contains("access-code");
    }

    private boolean isSafeSummaryKey(String key) {
        return SAFE_SUMMARY_KEYS.contains(normalizeKey(key));
    }

    private String normalizeKey(String key) {
        return StringUtils.hasText(key)
                ? key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT)
                : "";
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

    private String limitSummaryText(String text) {
        if (text == null) {
            return null;
        }
        if (text.length() <= MAX_SUMMARY_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_SUMMARY_TEXT_LENGTH) + "...[truncated]";
    }

    private String toJson(Map<String, Object> accessLog) {
        try {
            return objectMapper.writeValueAsString(accessLog);
        } catch (JsonProcessingException ex) {
            return String.valueOf(accessLog);
        }
    }
}
