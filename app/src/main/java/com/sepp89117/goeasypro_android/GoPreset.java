package com.sepp89117.goeasypro_android;

import java.util.HashMap;
import java.util.Map;

public class GoPreset {
    private int id = -1;
    private String title = "NC";

    private static final Map<Integer, String> presets = new HashMap<Integer, String>() {{
        put(-2, "NA");
        put(-1, "NC");
        put(0x00000000, "Standard");
        put(0x00000001, "Activity");
        put(0x00000002, "Cinematic");
        put(0x00000003, "Slo-Mo");
        put(0x00000004, "Ultra Slo-Mo");
        put(0x00000005, "Basic");
        put(0x00000006, "Water");
        put(0x00000007, "Indoor");
        put(0x00010000, "Photo");
        put(0x00010001, "Live Burst");
        put(0x00010002, "Burst Photo");
        put(0x00010003, "Night Photo");
        put(0x00020000, "Time Warp");
        put(0x00020001, "Time Lapse");
        put(0x00020002, "Night Lapse");
        put(0x00030000, "Max Video");
        put(0x00040000, "Max Photo");
        put(0x00050000, "Max Time Warp");
    }};

    //To determine which presets are available for immediate use, get Preset Status.
    public GoPreset(){};

    public GoPreset(int id) {
        this.id = id;
        this.title = presets.getOrDefault(this.id, "UNK " + this.id);
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }
}
