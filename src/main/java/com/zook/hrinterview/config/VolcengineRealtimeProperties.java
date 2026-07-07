package com.zook.hrinterview.config;

import com.zook.hrinterview.common.constant.ThirdPartyApiConstant;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "volcengine.realtime")
public class VolcengineRealtimeProperties {

    @ApiModelProperty(value = "火山实时对话 WebSocket 地址；一般不用改，除非火山接口地址调整", example = "wss://openspeech.bytedance.com/api/v3/realtime/dialogue")
    private String wsUrl = ThirdPartyApiConstant.VOLCENGINE_REALTIME_DIALOGUE_WS_URL;

    @ApiModelProperty(value = "火山应用 AppId；用于实时语音鉴权，和火山控制台保持一致")
    private String appId;

    @ApiModelProperty(value = "火山 AccessKey；用于实时语音鉴权，属于敏感配置")
    private String accessKey;

    @ApiModelProperty(value = "火山实时对话资源 ID；默认 volc.speech.dialog，一般不用改", example = "volc.speech.dialog")
    private String resourceId = "volc.speech.dialog";

    @ApiModelProperty(value = "火山 AppKey；用于实时语音鉴权，属于敏感配置")
    private String appKey;

    @ApiModelProperty(value = "后端连接火山 WebSocket 的客户端实现；java 为 JDK 自带客户端，okhttp 为 OkHttp 客户端", example = "okhttp")
    private String websocketClient = "java";

    @ApiModelProperty(value = "连接火山 WebSocket 超时时间，单位秒；网络不稳定可适当调大", example = "10")
    private Integer connectTimeoutSeconds = 10;

    @ApiModelProperty(value = "火山 TTS 发音人；控制 AI 面试官声音", example = "zh_female_vv_jupiter_bigtts")
    private String speaker;

    @ApiModelProperty(value = "火山实时对话模型版本；1.2.1.1 使用 system_role，2.2.0.0 使用 character_manifest", example = "1.2.1.1")
    private String dialogModel = "2.2.0.0";

    @ApiModelProperty(value = "浏览器上行音频格式；前端当前发送 PCM 16bit 小端", example = "pcm_s16le")
    private String inputAudioFormat = "pcm_s16le";

    @ApiModelProperty(value = "火山返回音频格式；前端按 PCM 16bit 小端播放", example = "pcm_s16le")
    private String outputAudioFormat = "pcm_s16le";

    @ApiModelProperty(value = "浏览器上行音频采样率；需和前端重采样保持一致", example = "16000")
    private Integer sampleRate = 16000;

    @ApiModelProperty(value = "火山返回音频采样率；需和前端播放解码保持一致", example = "24000")
    private Integer outputSampleRate = 24000;

    @ApiModelProperty(value = "音频声道数；当前实时面试按单声道处理", example = "1")
    private Integer channel = 1;

    @ApiModelProperty(value = "实时 AI 面试官基础提示词；控制提问风格、岗位 JD 和简历追问方式")
    private String instructions = "你是一名专业、友善、克制的 AI 面试官。请围绕岗位 JD、能力要求和候选人简历进行中文面试。每次只问一个问题，问题要清晰、具体、适合口语回答。";

    @ApiModelProperty(value = "目标提问数量；不是固定题库数量，AI 接近该数量后开始收尾", example = "8")
    private Integer targetQuestionCount = 8;

    @ApiModelProperty(value = "最大提问数量；达到后后端会硬性停止继续追问", example = "12")
    private Integer maxQuestionCount = 12;

    @ApiModelProperty(value = "达到最大提问数后，允许候选人补充说明或反问的轮次数；0 表示答完最后一个正式问题后直接自动结束", example = "1")
    private Integer closingFollowUpTurnLimit = 1;

    @ApiModelProperty(value = "同一能力点或同一项目连续追问上限；防止 AI 围绕一个点无限深挖", example = "2")
    private Integer maxFollowUpPerTopic = 2;

}
