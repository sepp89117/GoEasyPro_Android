package com.sepp89117.goeasypro_android;

public class GoHeader {
    private int headerLength = 1;
    private int msgLength = 0;

    public GoHeader(byte[] payload) {
        int byte0 = payload[0] & 0xff;
        int byte1 = payload[1] & 0xff;
        int byte2 = payload[2] & 0xff;

        if ((byte0 & 32) > 0) {
            headerLength = 2;
            msgLength = ((byte0 & 0x0f) << 8) | byte1; //0x24 & 0xf = 0x4; 0x4 << 8 = 0x400; 0x400 | 0xb5 = 0x4b5; (1205)
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
    }

    public int getHeaderLength() {
        return headerLength;
    }

    public int getMsgLength() {
        return msgLength;
    }
}
