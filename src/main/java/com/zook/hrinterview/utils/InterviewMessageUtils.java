package com.zook.hrinterview.utils;

import com.zook.hrinterview.interfaces.interview.entity.InterviewMessage;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class InterviewMessageUtils {

    private InterviewMessageUtils() {
    }

    public static List<InterviewMessage> mergeAdjacentSameRole(List<InterviewMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<InterviewMessage> mergedMessages = new ArrayList<>();
        for (InterviewMessage message : messages) {
            if (message == null || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            InterviewMessage last = mergedMessages.isEmpty() ? null : mergedMessages.get(mergedMessages.size() - 1);
            if (last != null && sameRole(last, message)) {
                last.setContent(mergeMessageContent(last.getContent(), message.getContent()));
                continue;
            }
            InterviewMessage copied = new InterviewMessage();
            copied.setId(message.getId());
            copied.setSessionId(message.getSessionId());
            copied.setRole(message.getRole());
            copied.setContent(normalizeMessageContent(message.getContent()));
            copied.setAudioUrl(message.getAudioUrl());
            copied.setSequenceNo(message.getSequenceNo());
            copied.setCreatedAt(message.getCreatedAt());
            mergedMessages.add(copied);
        }
        return mergedMessages;
    }

    public static String mergeMessageContent(String current, String incoming) {
        String base = normalizeMessageContent(current);
        String next = normalizeMessageContent(incoming);
        if (!StringUtils.hasText(base)) {
            return next;
        }
        if (!StringUtils.hasText(next) || base.equals(next) || base.endsWith(next)) {
            return base;
        }
        if (next.startsWith(base)) {
            return next;
        }
        return base + (needsSpaceBetween(base, next) ? " " : "") + next;
    }

    public static String normalizeMessageContent(String content) {
        return content == null ? "" : content.replaceAll("\\s+", " ").trim();
    }

    private static boolean sameRole(InterviewMessage left, InterviewMessage right) {
        return left != null
                && right != null
                && StringUtils.hasText(left.getRole())
                && left.getRole().equals(right.getRole());
    }

    private static boolean needsSpaceBetween(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        char last = left.charAt(left.length() - 1);
        char first = right.charAt(0);
        if (Character.isDigit(last) || Character.isDigit(first)) {
            return false;
        }
        return Character.isLetter(last) && Character.isLetter(first)
                && last < 128 && first < 128;
    }
}
