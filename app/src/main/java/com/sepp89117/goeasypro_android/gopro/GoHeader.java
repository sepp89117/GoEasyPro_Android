package com.sepp89117.goeasypro_android.gopro;

import androidx.annotation.NonNull;

public class GoHeader {
    private final int headerLength;
    private final int msgLength;
    private final byte[] headerBytes;

    public GoHeader(byte[] payload) {
        int byte0 = payload[0] & 0xff;
        int byte1 = payload[1] & 0xff;
        int byte2 = payload[2] & 0xff;

        if ((byte0 & 32) > 0) {
            headerLength = 2;
            msgLength = ((byte0 & 0x0f) << 8) | byte1;
        } else if ((byte0 & 64) > 0) {
            headerLength = 3;
            msgLength = (byte1 << 8) | byte2;
        } else if ((byte0 & 128) > 0) {
            headerLength = 1;
            msgLength = -1;
        } else {
            headerLength = 1;
            msgLength = byte0;
        }

        headerBytes = new byte[headerLength];
        System.arraycopy(payload, 0, headerBytes, 0, headerLength);
    }

    public int getHeaderLength() {
        return headerLength;
    }

    public int getMsgLength() {
        return msgLength;
    }

    @NonNull
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("[GoHeader]\n");
        stringBuilder.append("\tHeader length: ").append(headerLength).append("\n");
        stringBuilder.append("\tHeader bytes: ");
        for (int i = 0; i < headerLength; i++) {
            stringBuilder.append(String.format("0x%02X", headerBytes[i]));
            if (i < headerLength -1) stringBuilder.append(", ");
            else stringBuilder.append("\n");
        }
        stringBuilder.append("\tPayload length: ").append(msgLength);

        return stringBuilder.toString();
    }
}
