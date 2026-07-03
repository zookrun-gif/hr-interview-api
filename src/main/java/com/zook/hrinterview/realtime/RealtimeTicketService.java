package com.zook.hrinterview.realtime;

import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.ErrorCode;
import com.zook.hrinterview.utils.RedisUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.UUID;

@Service
public class RealtimeTicketService {

    private static final String KEY_PREFIX = "hr:interview:realtime:ticket:";

    private static final Duration TICKET_TTL = Duration.ofMinutes(5);

    @Resource
    private RedisUtils redisUtils;

    public String createTicket(Long sessionId) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        redisUtils.set(KEY_PREFIX + ticket, String.valueOf(sessionId), TICKET_TTL);
        return ticket;
    }

    public Long consumeTicket(String ticket) {
        Object value = redisUtils.get(KEY_PREFIX + ticket);
        if (value == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Realtime 连接票据已失效");
        }
        redisUtils.delete(KEY_PREFIX + ticket);
        return Long.valueOf(String.valueOf(value));
    }
}
