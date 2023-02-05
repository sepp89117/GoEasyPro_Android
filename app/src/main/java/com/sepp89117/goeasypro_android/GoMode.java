package com.sepp89117.goeasypro_android;

import java.util.HashMap;
import java.util.Map;

public class GoMode {
    private int id = -1;
    private String title = "NC";

    private static final Map<Integer, String> modes = new HashMap<Integer, String>() {{
        put(-1, "NC");
        put(0, "Video");
        put(1, "Photo");
        put(2, "Multishot");
        put(3, "Broadcast");
        put(4, "Playback");
        put(5, "Setup");
        put(6, "FW Update");
        put(7, "USB MTP");
        put(8, "SOS");
        put(9, "MEdit");
        put(10, "Calibration");
        put(11, "Direct Offload");
        put(12, "Video");
        put(13, "Time Lapse Video");
        put(14, "Video + Photo");
        put(15, "Looping");
        put(16, "Single Photo");
        put(17, "Photo");
        put(18, "Night Photo");
        put(19, "Burst Photo");
        put(20, "Time Lapse Photo");
        put(21, "Night Lapse Photo");
        put(22, "Broadcast Record");
        put(23, "Webcam");
        put(24, "Time Warp Video");
        put(25, "Live Burst");
        put(26, "Night Lapse Video");
        put(27, "Slo-Mo");
    }};

    public GoMode(){};

    public GoMode(int id) {
        this.id = id;
        this.title = modes.getOrDefault(this.id, "UNK " + this.id);
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }
}
