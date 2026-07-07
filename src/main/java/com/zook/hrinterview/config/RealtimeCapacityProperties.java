package com.zook.hrinterview.config;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "interview.realtime.capacity")
public class RealtimeCapacityProperties {

    @ApiModelProperty(value = "单机允许同时进行的实时语音面试数量；超过后候选人进入排队等待", example = "20")
    private Integer maxOnlineSessions = 20;
}
