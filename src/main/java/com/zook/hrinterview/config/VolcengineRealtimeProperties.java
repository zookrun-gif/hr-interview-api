package com.zook.hrinterview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "volcengine.realtime")
public class VolcengineRealtimeProperties {

    private String wsUrl = "wss://openspeech.bytedance.com/api/v3/realtime/dialogue";

    private String appId;

    private String accessKey;

    private String resourceId = "volc.speech.dialog";

    private String appKey;

    private String speaker;

    private String dialogModel = "2.2.0.0";

    private String inputAudioFormat = "pcm_s16le";

    private String outputAudioFormat = "pcm_s16le";

    private Integer sampleRate = 16000;

    private Integer outputSampleRate = 24000;

    private Integer channel = 1;

    private String instructions = "你是一名专业、友善、克制的 AI 面试官。请围绕岗位 JD、能力要求和候选人简历进行中文面试。每次只问一个问题，问题要清晰、具体、适合口语回答。";

    public String getWsUrl() {
        return wsUrl;
    }

    public void setWsUrl(String wsUrl) {
        this.wsUrl = wsUrl;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getSpeaker() {
        return speaker;
    }

    public void setSpeaker(String speaker) {
        this.speaker = speaker;
    }

    public String getDialogModel() {
        return dialogModel;
    }

    public void setDialogModel(String dialogModel) {
        this.dialogModel = dialogModel;
    }

    public String getInputAudioFormat() {
        return inputAudioFormat;
    }

    public void setInputAudioFormat(String inputAudioFormat) {
        this.inputAudioFormat = inputAudioFormat;
    }

    public String getOutputAudioFormat() {
        return outputAudioFormat;
    }

    public void setOutputAudioFormat(String outputAudioFormat) {
        this.outputAudioFormat = outputAudioFormat;
    }

    public Integer getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(Integer sampleRate) {
        this.sampleRate = sampleRate;
    }

    public Integer getOutputSampleRate() {
        return outputSampleRate;
    }

    public void setOutputSampleRate(Integer outputSampleRate) {
        this.outputSampleRate = outputSampleRate;
    }

    public Integer getChannel() {
        return channel;
    }

    public void setChannel(Integer channel) {
        this.channel = channel;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }
}
