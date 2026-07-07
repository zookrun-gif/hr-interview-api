package com.zook.hrinterview.config;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "interview.report.queue")
public class InterviewReportQueueProperties {

    @ApiModelProperty(value = "是否启用 Redis 报告生成队列；启用后面试结束只入队，由后台 worker 慢慢生成报告", example = "true")
    private Boolean enabled = true;

    @ApiModelProperty(value = "后台 worker 每隔多少毫秒扫描一次报告队列；值越小响应越快，但空转越频繁", example = "3000")
    private Long pollIntervalMillis = 3000L;

    @ApiModelProperty(value = "每次扫描最多消费多少个报告任务；4核4G建议为1，避免多个 AI 报告同时占满资源", example = "1")
    private Integer batchSize = 1;

    @ApiModelProperty(value = "单个报告任务失败后的最大重试次数；超过后面试状态标记为失败", example = "2")
    private Integer maxRetryTimes = 2;

    @ApiModelProperty(value = "服务启动时是否把生成中队列里的未完成任务恢复到待生成队列，防止重启丢任务", example = "true")
    private Boolean recoverProcessingOnStartup = true;

    @ApiModelProperty(value = "服务启动时最多恢复多少条数据库中卡在生成中的报告任务；避免一次恢复过多压垮小机器", example = "200")
    private Integer startupRecoverLimit = 200;
}
