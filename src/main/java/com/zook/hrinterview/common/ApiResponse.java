package com.zook.hrinterview.common;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel("统一接口响应")
public class ApiResponse<T> {

    @ApiModelProperty(value = "响应码，0 表示成功", required = true, example = "0")
    private Integer code;

    @ApiModelProperty(value = "响应消息", required = true, example = "success")
    private String message;

    @ApiModelProperty(value = "响应数据")
    private T data;

    @ApiModelProperty(value = "链路追踪 ID")
    private String traceId;

    public ApiResponse() {
    }

    public ApiResponse(Integer code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, TraceIdContext.get());
    }

    public static <T> ApiResponse<T> failure(Integer code, String message) {
        return new ApiResponse<>(code, message, null, TraceIdContext.get());
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
