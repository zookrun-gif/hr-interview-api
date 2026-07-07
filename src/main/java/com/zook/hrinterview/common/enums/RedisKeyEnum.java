package com.zook.hrinterview.common.enums;

import lombok.Getter;

import java.time.Duration;

/**
 * Redis key 统一枚举。
 *
 * <p>项目约定：缓存、临时状态、计数、限流、票据、分布式锁等统一使用 Redis。
 * 新增 Redis key 必须在这里定义 key、msg、ttl，业务代码通过枚举取 key，避免散落硬编码。</p>
 */
@Getter
public enum RedisKeyEnum {

    /**
     * 登录 token 白名单。
     * value: 登录用户信息。
     */
    AUTH_LOGIN_TOKEN("hr:auth:token:", "登录 token 白名单", Duration.ofDays(7)),

    /**
     * 企业微信扫码登录 state。
     * value: 固定标记。
     */
    AUTH_WECOM_STATE("hr:auth:wecom:state:", "企业微信扫码登录 state", Duration.ofMinutes(5)),

    /**
     * 企业微信 access_token。
     * value: 企微 access_token，实际 TTL 使用企微返回时间提前量。
     */
    AUTH_WECOM_ACCESS_TOKEN("hr:auth:wecom:access-token", "企业微信 access_token", Duration.ofHours(1)),

    /**
     * RBAC 内置权限同步版本标记。
     * value: 当前代码内置权限签名，用于避免每次查询权限列表都同步内置权限。
     */
    RBAC_BUILTIN_PERMISSION_SYNC("hr:rbac:builtin-permission:sync", "RBAC 内置权限同步版本标记", Duration.ofDays(1)),

    /**
     * RBAC 内置权限同步锁。
     * value: 随机锁值，防止并发请求重复同步内置权限。
     */
    RBAC_BUILTIN_PERMISSION_SYNC_LOCK("hr:rbac:builtin-permission:sync-lock", "RBAC 内置权限同步锁", Duration.ofMinutes(1)),

    /**
     * RBAC 权限列表缓存。
     * value: List<RbacPermissionResponse>，权限变更后删除。
     */
    RBAC_PERMISSION_LIST("hr:rbac:permission:list", "RBAC 权限列表缓存", Duration.ofMinutes(10)),

    /**
     * AI 面试边界配置缓存。
     * value: AiInterviewSettingResponse，后台保存配置后覆盖，缓存失效后回源数据库。
     */
    AI_INTERVIEW_SETTING("hr:ai:interview:setting", "AI 面试边界配置缓存", Duration.ofDays(7)),

    /**
     * 公开面试 token 到面试会话 ID 的缓存。
     * value: interview_session.id。
     */
    INTERVIEW_PUBLIC_TOKEN("hr:interview:public:token:", "公开面试 token 会话缓存", Duration.ofMinutes(10)),

    /**
     * 公开面试口令校验通过缓存。
     * value: interview_session.id，用于减少 BCrypt 重复校验。
     */
    INTERVIEW_PUBLIC_ACCESS("hr:interview:public:access:", "公开面试口令校验缓存", Duration.ofMinutes(5)),

    /**
     * Realtime 连接一次性票据。
     * value: interview_session.id，消费后立即删除。
     */
    INTERVIEW_REALTIME_TICKET("hr:interview:realtime:ticket:", "实时语音一次性连接票据", Duration.ofMinutes(5)),

    /**
     * 当前在线实时面试会话集合。
     * value: interview_session.id set，TTL 作为异常退出兜底。
     */
    INTERVIEW_REALTIME_ONLINE_SESSIONS("hr:interview:realtime:online:sessions", "当前在线实时面试会话集合", Duration.ofHours(6)),

    /**
     * 实时面试会话到当前 WebSocket ID 的映射。
     * value: WebSocketSession.getId()，TTL 作为异常退出兜底。
     */
    INTERVIEW_REALTIME_SESSION_SOCKET("hr:interview:realtime:session-socket:", "实时面试会话 WebSocket 映射", Duration.ofHours(6)),

    /**
     * 面试消息序号计数器。
     * value: 当前最大 sequence_no，使用 Redis INCR 保证并发递增。
     */
    INTERVIEW_MESSAGE_SEQUENCE("hr:interview:message:seq:", "面试消息序号计数器", Duration.ofDays(7)),

    /**
     * 面试消息待异步入库队列。
     * value: RealtimeMessagePersistRetryItem list，实时链路只写 Redis，后台 worker 异步落库。
     */
    INTERVIEW_MESSAGE_PERSIST_PENDING_QUEUE("hr:interview:message:persist:pending-queue", "面试消息待异步入库队列", Duration.ofDays(7)),

    /**
     * 面试消息异步入库处理中队列。
     * value: RealtimeMessagePersistRetryItem list，用于服务重启后恢复未确认入库任务。
     */
    INTERVIEW_MESSAGE_PERSIST_PROCESSING_QUEUE("hr:interview:message:persist:processing-queue", "面试消息异步入库处理中队列", Duration.ofDays(7)),

    /**
     * 面试消息异步入库前的会话级读取缓冲。
     * value: hash，hashKey 为 sequence_no，hashValue 为 RealtimeMessagePersistRetryItem，用于断线重连和后台查询补齐未入库消息。
     */
    INTERVIEW_MESSAGE_PERSIST_SESSION_BUFFER("hr:interview:message:persist:session-buffer:", "面试消息会话级读取缓冲", Duration.ofDays(7)),

    /**
     * 面试消息数据库写入失败死信队列。
     * value: RealtimeMessagePersistRetryItem list，超过自动重试次数后保留人工排查。
     */
    INTERVIEW_MESSAGE_PERSIST_FAILED_QUEUE("hr:interview:message:persist:failed-queue", "面试消息写入失败死信队列", Duration.ofDays(30)),

    /**
     * 面试报告生成锁。
     * value: 随机锁值，防止同一个面试报告被并发重复生成。
     */
    INTERVIEW_REPORT_GENERATE_LOCK("hr:interview:report:generate-lock:", "面试报告生成锁", Duration.ofMinutes(5)),

    /**
     * 面试报告待生成队列。
     * value: interview_session.id list，后台 worker 按配置慢慢消费。
     */
    INTERVIEW_REPORT_PENDING_QUEUE("hr:interview:report:pending-queue", "面试报告待生成队列", Duration.ofDays(7)),

    /**
     * 面试报告生成中队列。
     * value: interview_session.id list，用于服务重启后恢复未确认任务。
     */
    INTERVIEW_REPORT_PROCESSING_QUEUE("hr:interview:report:processing-queue", "面试报告生成中队列", Duration.ofDays(7)),

    /**
     * 面试报告排队标记。
     * value: 固定标记，防止同一场面试重复进入报告队列。
     */
    INTERVIEW_REPORT_QUEUE_FLAG("hr:interview:report:queue-flag:", "面试报告排队标记", Duration.ofDays(7)),

    /**
     * 面试报告生成重试次数。
     * value: 当前失败重试次数，超过阈值后标记失败。
     */
    INTERVIEW_REPORT_RETRY_COUNT("hr:interview:report:retry-count:", "面试报告生成重试次数", Duration.ofDays(7));

    private final String key;

    private final String msg;

    private final Duration ttl;

    RedisKeyEnum(String key, String msg, Duration ttl) {
        this.key = key;
        this.msg = msg;
        this.ttl = ttl;
    }

    public String buildKey(Object suffix) {
        return key + String.valueOf(suffix);
    }
}
