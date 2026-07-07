package com.zook.hrinterview.common;

public enum ErrorCode {

    PARAM_INVALID(400001, "参数不合法"),
    UNAUTHORIZED(401001, "未登录或登录已过期"),
    FORBIDDEN(403001, "无权访问"),
    RESOURCE_NOT_FOUND(404001, "资源不存在"),
    INTERVIEW_STATUS_INVALID(409001, "面试状态不允许该操作"),
    INTERVIEW_ACCESS_CODE_INVALID(409002, "面试口令不正确"),
    TOO_MANY_REQUESTS(429001, "请求过于频繁"),
    MODEL_SERVICE_ERROR(502001, "AI 服务暂时不可用"),
    SYSTEM_ERROR(500001, "系统异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
