package com.zook.hrinterview.realtime;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class VolcengineTtsProtocol {

    public static final int MSG_TYPE_FULL_CLIENT_REQUEST = 0b0001;
    public static final int MSG_TYPE_AUDIO_ONLY_CLIENT = 0b0010;
    public static final int MSG_TYPE_FULL_SERVER_RESPONSE = 0b1001;
    public static final int MSG_TYPE_AUDIO_ONLY_SERVER = 0b1011;
    public static final int MSG_TYPE_ERROR = 0b1111;

    private static final int VERSION_1 = 0b0001;
    private static final int HEADER_SIZE_4 = 0b0001;
    private static final int FLAG_WITH_EVENT = 0b0100;
    private static final int SERIALIZATION_RAW = 0b0000;
    private static final int SERIALIZATION_JSON = 0b0001;
    private static final int COMPRESSION_NONE = 0b0000;

    private static final int EVENT_START_CONNECTION = 1;
    private static final int EVENT_FINISH_CONNECTION = 2;
    private static final int EVENT_START_SESSION = 100;
    private static final int EVENT_FINISH_SESSION = 102;
    private static final int EVENT_TASK_REQUEST = 200;
    private static final int EVENT_SAY_HELLO = 300;
    private static final int EVENT_CHAT_TEXT_QUERY = 501;

    private VolcengineTtsProtocol() {
    }

    public static byte[] startConnection() {
        return fullClientRequest(EVENT_START_CONNECTION, "", "{}");
    }

    public static byte[] finishConnection() {
        return fullClientRequest(EVENT_FINISH_CONNECTION, "", "{}");
    }

    public static byte[] startSession(String sessionId, String payloadJson) {
        return fullClientRequest(EVENT_START_SESSION, sessionId, payloadJson);
    }

    public static byte[] taskRequest(String sessionId, String payloadJson) {
        return fullClientRequest(EVENT_TASK_REQUEST, sessionId, payloadJson);
    }

    public static byte[] audioTaskRequest(String sessionId, byte[] payload) {
        return clientRequest(MSG_TYPE_AUDIO_ONLY_CLIENT, EVENT_TASK_REQUEST, sessionId, payload, SERIALIZATION_RAW);
    }

    public static byte[] sayHello(String sessionId, String payloadJson) {
        return fullClientRequest(EVENT_SAY_HELLO, sessionId, payloadJson);
    }

    public static byte[] chatTextQuery(String sessionId, String payloadJson) {
        return fullClientRequest(EVENT_CHAT_TEXT_QUERY, sessionId, payloadJson);
    }

    public static byte[] finishSession(String sessionId) {
        return fullClientRequest(EVENT_FINISH_SESSION, sessionId, "{}");
    }

    public static ParsedMessage parse(byte[] data) {
        if (data == null || data.length < 4) {
            throw new IllegalArgumentException("火山 Realtime 响应为空");
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int versionAndHeaderSize = Byte.toUnsignedInt(buffer.get());
        int headerSize = versionAndHeaderSize & 0b00001111;
        int typeAndFlag = Byte.toUnsignedInt(buffer.get());
        int msgType = typeAndFlag >> 4;
        int flag = typeAndFlag & 0b00001111;
        buffer.get();
        int headerBytes = headerSize * 4;
        if (headerBytes > 3) {
            buffer.position(headerBytes);
        }

        ParsedMessage message = new ParsedMessage();
        message.setMsgType(msgType);
        message.setFlag(flag);

        if (msgType == MSG_TYPE_ERROR) {
            message.setErrorCode(buffer.getInt());
        }
        if (flag == FLAG_WITH_EVENT) {
            message.setEvent(buffer.getInt());
            if (!skipSessionId(message.getEvent()) && buffer.remaining() >= 4) {
                message.setSessionId(readSizedString(buffer));
            }
            if (isConnectionEvent(message.getEvent()) && buffer.remaining() >= 4) {
                message.setConnectId(readSizedString(buffer));
            }
        }
        if (buffer.remaining() >= 4) {
            int payloadSize = buffer.getInt();
            if (payloadSize > 0 && buffer.remaining() >= payloadSize) {
                byte[] payload = new byte[payloadSize];
                buffer.get(payload);
                message.setPayload(payload);
            }
        }
        return message;
    }

    private static byte[] fullClientRequest(int event, String sessionId, String payloadJson) {
        byte[] payload = payloadJson == null ? new byte[0] : payloadJson.getBytes(StandardCharsets.UTF_8);
        return clientRequest(MSG_TYPE_FULL_CLIENT_REQUEST, event, sessionId, payload, SERIALIZATION_JSON);
    }

    private static byte[] clientRequest(int msgType, int event, String sessionId, byte[] payload, int serialization) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write((VERSION_1 << 4) | HEADER_SIZE_4);
            output.write((msgType << 4) | FLAG_WITH_EVENT);
            output.write((serialization << 4) | COMPRESSION_NONE);
            output.write(0);
            output.write(intBytes(event));
            if (!skipSessionId(event)) {
                writeSizedString(output, sessionId);
            }
            output.write(intBytes(payload.length));
            output.write(payload);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("火山 Realtime 协议封包失败", ex);
        }
    }

    private static boolean skipSessionId(int event) {
        return event == 1 || event == 2 || event == 50 || event == 51 || event == 52;
    }

    private static boolean isConnectionEvent(int event) {
        return event == 50 || event == 51 || event == 52;
    }

    private static void writeSizedString(ByteArrayOutputStream output, String value) throws java.io.IOException {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        output.write(intBytes(bytes.length));
        output.write(bytes);
    }

    private static String readSizedString(ByteBuffer buffer) {
        int size = buffer.getInt();
        if (size <= 0 || buffer.remaining() < size) {
            return "";
        }
        byte[] bytes = new byte[size];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] intBytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    public static class ParsedMessage {

        private int msgType;

        private int flag;

        private int event;

        private String sessionId;

        private String connectId;

        private int errorCode;

        private byte[] payload = new byte[0];

        public int getMsgType() {
            return msgType;
        }

        public void setMsgType(int msgType) {
            this.msgType = msgType;
        }

        public int getFlag() {
            return flag;
        }

        public void setFlag(int flag) {
            this.flag = flag;
        }

        public int getEvent() {
            return event;
        }

        public void setEvent(int event) {
            this.event = event;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getConnectId() {
            return connectId;
        }

        public void setConnectId(String connectId) {
            this.connectId = connectId;
        }

        public int getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(int errorCode) {
            this.errorCode = errorCode;
        }

        public byte[] getPayload() {
            return payload;
        }

        public void setPayload(byte[] payload) {
            this.payload = payload;
        }
    }
}
