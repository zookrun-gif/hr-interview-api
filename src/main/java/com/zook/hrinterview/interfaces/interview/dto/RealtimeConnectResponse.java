package com.zook.hrinterview.interfaces.interview.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("公开面试 Realtime 连接响应")
public class RealtimeConnectResponse {

    @ApiModelProperty(value = "后端 Realtime WebSocket 地址", required = true)
    private String websocketUrl;

    @ApiModelProperty(value = "短期连接票据", required = true)
    private String ticket;

    @ApiModelProperty(value = "Realtime 模型资源", required = true, example = "volc.speech.dialog")
    private String resourceId;

    @ApiModelProperty(value = "前端上传音频格式", required = true, example = "pcm_s16le")
    private String audioFormat;
}
