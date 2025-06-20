package com.sepp89117.goeasypro_android.gopro;

import java.util.ArrayList;
import java.util.List;

public class GoProProtocol {
    public static List<byte[]> packetizeMessage(byte[] msg) {
        List<byte[]> packets = new ArrayList<>();
        int len = msg.length;

        if (len <= 20) {
            // Package with 8-bit header
            byte[] packet = new byte[1 + len];
            packet[0] = (byte) len;
            System.arraycopy(msg, 0, packet, 1, len);
            packets.add(packet);
            return packets;
        }

        // The message is longer than 20 bytes
        // Split into N packets with packet 1 containing a start packet header and packets 2..N containing a continuation packet header
        // Starter package with 16-bit header (0x2000 | len)
        int header = 0x2000 | len;
        int chunkSize = 19; // 1 byte for the header

        // First packet with 2-byte header
        int firstPayloadLen = Math.min(len, chunkSize - 1);
        byte[] firstPacket = new byte[2 + firstPayloadLen];
        firstPacket[0] = (byte) ((header >> 8) & 0xFF); // High byte
        firstPacket[1] = (byte) (header & 0xFF);        // Low byte
        System.arraycopy(msg, 0, firstPacket, 2, firstPayloadLen);
        packets.add(firstPacket);

        // Follow-up packets with continuation packet header
        int offset = firstPayloadLen;
        int counter = 0;
        while (offset < len) {
            int remaining = len - offset;
            int payloadLen = Math.min(chunkSize, remaining);
            byte[] packet = new byte[1 + payloadLen];
            packet[0] = (byte) (0x80 | (counter & 0x0F)); // Continuation packet header
            System.arraycopy(msg, offset, packet, 1, payloadLen);
            packets.add(packet);

            offset += payloadLen;
            counter = (counter + 1) & 0x0F; // The counter starts at 0x0 and is reset after 0xF
        }

        return packets;
    }
}
