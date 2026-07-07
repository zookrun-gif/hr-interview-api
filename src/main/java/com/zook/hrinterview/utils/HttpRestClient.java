package com.zook.hrinterview.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class HttpRestClient {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private static final int HTTP_LOG_BODY_SUMMARY_LENGTH = 2000;

    private static final long SLOW_REQUEST_MILLIS = 3000L;

    private static final String CONTENT_TYPE_JSON = "application/json";

    private static final int MAX_TRANSIENT_RETRY_TIMES = 2;

    private static final long RETRY_DELAY_MILLIS = 600L;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
            .build();

    @Resource
    private ObjectMapper objectMapper;

    public JsonNode postJsonForJson(String url, Object body, Integer timeoutSeconds) {
        return postJsonForJson(url, body, null, timeoutSeconds);
    }

    public JsonNode postJsonForJson(String url, Object body, String bearerToken, Integer timeoutSeconds) {
        String responseBody = postJsonForString(url, body, bearerToken, timeoutSeconds);
        try {
            return objectMapper.readTree(responseBody);
        } catch (JsonProcessingException ex) {
            log.warn("HTTP response JSON parse failed, url={}, body={}", safeUrl(url), summarize(responseBody), ex);
            throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, "HTTP 响应 JSON 解析失败");
        }
    }

    public String postJsonForString(String url, Object body, String bearerToken, Integer timeoutSeconds) {
        try {
            String requestBody = objectMapper.writeValueAsString(body);
            HttpRequest.Builder builder = baseBuilder(url, timeoutSeconds)
                    .header("Content-Type", CONTENT_TYPE_JSON)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));
            applyBearer(builder, bearerToken);
            return sendForString(builder.build(), requestBody);
        } catch (BusinessException ex) {
            throw ex;
        } catch (JsonProcessingException ex) {
            log.warn("HTTP request body JSON serialize failed, url={}", safeUrl(url), ex);
            throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, "HTTP 请求参数 JSON 序列化失败");
        } catch (Exception ex) {
            log.warn("HTTP POST request build failed, url={}", safeUrl(url), ex);
            throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, "HTTP 请求失败");
        }
    }

    public <T> T postJsonForObject(String url, Object body, String bearerToken, Class<T> responseType, Integer timeoutSeconds) {
        String responseBody = postJsonForString(url, body, bearerToken, timeoutSeconds);
        try {
            return objectMapper.readValue(responseBody, responseType);
        } catch (JsonProcessingException ex) {
            log.warn("HTTP response object convert failed, url={}, type={}, body={}",
                    safeUrl(url), responseType.getSimpleName(), summarize(responseBody), ex);
            throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, "HTTP 响应转换失败");
        }
    }

    public JsonNode getForJson(String url, Integer timeoutSeconds) {
        String responseBody = getForString(url, timeoutSeconds);
        try {
            return objectMapper.readTree(responseBody);
        } catch (JsonProcessingException ex) {
            log.warn("HTTP response JSON parse failed, url={}, body={}", safeUrl(url), summarize(responseBody), ex);
            throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, "HTTP 响应 JSON 解析失败");
        }
    }

    public <T> T getForObject(String url, Class<T> responseType, Integer timeoutSeconds) {
        String responseBody = getForString(url, timeoutSeconds);
        try {
            return objectMapper.readValue(responseBody, responseType);
        } catch (JsonProcessingException ex) {
            log.warn("HTTP response object convert failed, url={}, type={}, body={}",
                    safeUrl(url), responseType.getSimpleName(), summarize(responseBody), ex);
            throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, "HTTP 响应转换失败");
        }
    }

    public String getForString(String url, Integer timeoutSeconds) {
        try {
            HttpRequest request = baseBuilder(url, timeoutSeconds).GET().build();
            return sendForString(request, "");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("HTTP GET request build failed, url={}", safeUrl(url), ex);
            throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, "HTTP 请求失败");
        }
    }

    public String appendQuery(String url, Map<String, ?> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        StringBuilder builder = new StringBuilder(url);
        builder.append(url.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, ?> entry : params.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (!first) {
                builder.append('&');
            }
            builder.append(encode(entry.getKey())).append('=').append(encode(String.valueOf(entry.getValue())));
            first = false;
        }
        return builder.toString();
    }

    public Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (keyValues == null) {
            return map;
        }
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    private HttpRequest.Builder baseBuilder(String url, Integer timeoutSeconds) {
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, "HTTP 请求地址为空");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            log.warn("HTTP request url invalid, url={}", safeUrl(url), ex);
            throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, "HTTP 请求地址不合法");
        }
        return HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(resolveTimeoutSeconds(timeoutSeconds)));
    }

    private void applyBearer(HttpRequest.Builder builder, String bearerToken) {
        if (StringUtils.hasText(bearerToken)) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
    }

    private String sendForString(HttpRequest request, String requestBody) {
        int maxAttempts = MAX_TRANSIENT_RETRY_TIMES + 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long startTime = System.currentTimeMillis();
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                long costMillis = System.currentTimeMillis() - startTime;
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    if (shouldRetryStatus(response.statusCode()) && attempt < maxAttempts) {
                        retryLater(request, attempt, maxAttempts, response.statusCode(), response.body(), null);
                        continue;
                    }
                    logHttpAccess(request, response.statusCode(), costMillis, attempt, maxAttempts, requestBody, response.body(), "FAILED");
                    log.warn("HTTP {} {} failed, status={}, cost={}ms, body={}",
                            request.method(), safeUri(request.uri()), response.statusCode(), costMillis, summarize(response.body()));
                    throw new BusinessException(
                            ErrorCode.MODEL_SERVICE_ERROR,
                            "HTTP 请求失败，状态码：" + response.statusCode() + "，响应：" + summarize(response.body())
                    );
                }
                logHttpAccess(request, response.statusCode(), costMillis, attempt, maxAttempts, requestBody, response.body(),
                        costMillis >= SLOW_REQUEST_MILLIS ? "SLOW" : "SUCCESS");
                if (costMillis >= SLOW_REQUEST_MILLIS) {
                    log.warn("HTTP {} {} slow, status={}, cost={}ms",
                            request.method(), safeUri(request.uri()), response.statusCode(), costMillis);
                }
                return response.body();
            } catch (BusinessException ex) {
                throw ex;
            } catch (HttpTimeoutException ex) {
                if (attempt < maxAttempts) {
                    retryLater(request, attempt, maxAttempts, null, null, ex);
                    continue;
                }
                logHttpAccess(request, 0, System.currentTimeMillis() - startTime, attempt, maxAttempts, requestBody, ex.getMessage(), "TIMEOUT");
                log.warn("HTTP {} {} timeout", request.method(), safeUri(request.uri()), ex);
                throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, "HTTP 请求超时，请稍后重试");
            } catch (ConnectException ex) {
                if (attempt < maxAttempts) {
                    retryLater(request, attempt, maxAttempts, null, null, ex);
                    continue;
                }
                logHttpAccess(request, 0, System.currentTimeMillis() - startTime, attempt, maxAttempts, requestBody, ex.getMessage(), "CONNECT_FAILED");
                log.warn("HTTP {} {} connect failed", request.method(), safeUri(request.uri()), ex);
                throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, "HTTP 连接失败，请检查第三方服务地址或网络");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                logHttpAccess(request, 0, System.currentTimeMillis() - startTime, attempt, maxAttempts, requestBody, ex.getMessage(), "INTERRUPTED");
                log.warn("HTTP {} {} interrupted", request.method(), safeUri(request.uri()), ex);
                throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, "HTTP 请求被中断");
            } catch (IOException ex) {
                if (attempt < maxAttempts) {
                    retryLater(request, attempt, maxAttempts, null, null, ex);
                    continue;
                }
                logHttpAccess(request, 0, System.currentTimeMillis() - startTime, attempt, maxAttempts, requestBody, ex.getMessage(), "IO_FAILED");
                log.warn("HTTP {} {} IO failed", request.method(), safeUri(request.uri()), ex);
                throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, "HTTP 网络请求失败");
            } catch (Exception ex) {
                logHttpAccess(request, 0, System.currentTimeMillis() - startTime, attempt, maxAttempts, requestBody, ex.getMessage(), "FAILED");
                log.warn("HTTP {} {} failed, status={}, cost={}ms, body={}",
                        request.method(), safeUri(request.uri()), 0, System.currentTimeMillis() - startTime, ex.getMessage());
                throw new BusinessException(
                        ErrorCode.MODEL_SERVICE_ERROR,
                        "HTTP 请求失败"
                );
            }
        }
        throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, "HTTP 请求失败");
    }

    private void logHttpAccess(HttpRequest request,
                               int statusCode,
                               long costMillis,
                               int attempt,
                               int maxAttempts,
                               String requestBody,
                               String responseBody,
                               String result) {
        Map<String, Object> logBody = new LinkedHashMap<>();
        logBody.put("logType", "HTTP_ACCESS");
        logBody.put("result", result);
        logBody.put("method", request.method());
        logBody.put("url", safeUri(request.uri()));
        logBody.put("status", statusCode);
        logBody.put("cost", costMillis);
        logBody.put("attempt", attempt);
        logBody.put("maxAttempts", maxAttempts);
        logBody.put("requestBody", summarize(requestBody));
        logBody.put("responseBody", summarize(responseBody));
        try {
            log.info("HTTP_ACCESS_LOG - {}", objectMapper.writeValueAsString(logBody));
        } catch (JsonProcessingException ex) {
            log.info("HTTP_ACCESS_LOG - method={}, url={}, status={}, cost={}ms, attempt={}/{}, result={}, responseBody={}",
                    request.method(), safeUri(request.uri()), statusCode, costMillis, attempt, maxAttempts, result, summarize(responseBody));
        }
    }

    private boolean shouldRetryStatus(int statusCode) {
        return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private void retryLater(HttpRequest request, int attempt, int maxAttempts, Integer statusCode, String responseBody, Exception exception) {
        long delayMillis = RETRY_DELAY_MILLIS * attempt;
        if (statusCode != null) {
            log.warn("HTTP {} {} transient failed, status={}, attempt={}/{}, retryAfter={}ms, body={}",
                    request.method(), safeUri(request.uri()), statusCode, attempt, maxAttempts, delayMillis, summarize(responseBody));
        } else {
            log.warn("HTTP {} {} transient failed, attempt={}/{}, retryAfter={}ms, reason={}",
                    request.method(), safeUri(request.uri()), attempt, maxAttempts, delayMillis,
                    exception == null ? "" : exception.getClass().getSimpleName());
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR, "HTTP 请求被中断");
        }
    }

    private int resolveTimeoutSeconds(Integer timeoutSeconds) {
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        return timeoutSeconds;
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String safeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        try {
            return safeUri(URI.create(url));
        } catch (IllegalArgumentException ex) {
            return url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        }
    }

    private String safeUri(URI uri) {
        if (uri == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(uri.getScheme())) {
            builder.append(uri.getScheme()).append("://");
        }
        if (StringUtils.hasText(uri.getHost())) {
            builder.append(uri.getHost());
        }
        if (uri.getPort() > 0) {
            builder.append(':').append(uri.getPort());
        }
        if (StringUtils.hasText(uri.getPath())) {
            builder.append(uri.getPath());
        }
        return builder.length() == 0 ? uri.toString() : builder.toString();
    }

    private String summarize(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String text = maskSensitiveText(body).replaceAll("\\s+", " ").trim();
        if (text.length() <= HTTP_LOG_BODY_SUMMARY_LENGTH) {
            return text;
        }
        return text.substring(0, HTTP_LOG_BODY_SUMMARY_LENGTH) + "...";
    }

    private String maskSensitiveText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text
                .replaceAll("(?i)(\"(?:api[-_]?key|access[-_]?key|access[-_]?token|token|secret|password)\"\\s*:\\s*\")[^\"]*(\")", "$1******$2")
                .replaceAll("(?i)((?:api[-_]?key|access[-_]?key|access[-_]?token|token|secret|password)=)[^&\\s,}]+", "$1******")
                .replaceAll("(?i)(Bearer\\s+)[A-Za-z0-9._\\-]+", "$1******");
    }

}
