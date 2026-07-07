package com.zook.hrinterview.common.constant;

/**
 * 第三方服务 API 地址统一管理。
 */
public final class ThirdPartyApiConstant {

    /**
     * OpenAI 兼容接口默认基础地址。
     */
    public static final String OPENAI_CHAT_BASE_URL = "https://api.openai.com/v1";

    /**
     * 火山实时对话 WebSocket 接口。
     */
    public static final String VOLCENGINE_REALTIME_DIALOGUE_WS_URL = "wss://openspeech.bytedance.com/api/v3/realtime/dialogue";

    /**
     * 企业微信扫码登录地址。
     */
    public static final String WECOM_QR_LOGIN_URL = "https://open.work.weixin.qq.com/wwopen/sso/qrConnect";

    /**
     * 企业微信获取 access_token 接口。
     */
    public static final String WECOM_GET_TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";

    /**
     * 企业微信获取扫码登录用户接口。
     */
    public static final String WECOM_GET_USER_INFO_URL = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo";

    /**
     * 企业微信获取成员详情接口。
     */
    public static final String WECOM_GET_USER_DETAIL_URL = "https://qyapi.weixin.qq.com/cgi-bin/user/get";

    private ThirdPartyApiConstant() {
    }
}
