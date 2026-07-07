package com.zook.hrinterview.realtime.service;

import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.enums.RedisKeyEnum;
import com.zook.hrinterview.common.ErrorCode;
import com.zook.hrinterview.utils.RedisUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.UUID;

@Service
public class RealtimeTicketService {

    @Resource
    private RedisUtils redisUtils;

    public String createTicket(Long sessionId) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        redisUtils.set(RedisKeyEnum.INTERVIEW_REALTIME_TICKET, ticket, sessionId);
        return ticket;
    }

    public Long consumeTicket(String ticket) {
        Object value = redisUtils.getAndDelete(RedisKeyEnum.INTERVIEW_REALTIME_TICKET, ticket);
        if (value == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Realtime 连接票据已失效");
        }
        return Long.valueOf(String.valueOf(value));
    }
}
