package com.sepp89117.goeasypro_android.gopro;

import android.content.Context;

import com.sepp89117.goeasypro_android.R;

import java.util.HashMap;
import java.util.Map;

public class GoPreset {
    private final Context context;

    private final int id;
    private final String title;
    private final Map<Integer, String> presets = new HashMap<>();

    public GoPreset(Context context) {
        this.context = context;

        this.id = -1;
        this.title = getStr(R.string.str_NC);
    }

    public GoPreset(Context context, int id) {
        this.context = context;

        this.id = id;
        putValuesToPresetsMap();
        this.title = presets.getOrDefault(this.id, getStr(R.string.str_Custom) + " " + this.id);
    }

    private void putValuesToPresetsMap() {
        presets.put(-2, "");
        presets.put(-1, getStr(R.string.str_NC));
        presets.put(0x00000000, getStr(R.string.str_Standard));
        presets.put(0x00000001, getStr(R.string.str_Activity));
        presets.put(0x00000002, getStr(R.string.str_Cinematic));
        presets.put(0x00000003, getStr(R.string.str_Slo_Mo));
        presets.put(0x00000004, getStr(R.string.str_Ultra_Slo_Mo));
        presets.put(0x00000005, getStr(R.string.str_Basic));
        presets.put(0x00010000, getStr(R.string.str_Photo));
        presets.put(0x00010001, getStr(R.string.str_Live_Burst));
        presets.put(0x00010002, getStr(R.string.str_Burst_Photo));
        presets.put(0x00010003, getStr(R.string.str_Night_Photo));
        presets.put(0x00020000, getStr(R.string.str_Time_Warp));
        presets.put(0x00020001, getStr(R.string.str_Time_Lapse));
        presets.put(0x00020002, getStr(R.string.str_Night_Lapse));
        presets.put(0x00030000, getStr(R.string.str_Max_Video));
        presets.put(0x00040000, getStr(R.string.str_Max_Photo));
        presets.put(0x00050000, getStr(R.string.str_Max_Time_Warp));
        presets.put(0x000A0000, getStr(R.string.str_HQ));
        presets.put(0x000A0001, getStr(R.string.str_SQ));
        presets.put(0x000A0002, getStr(R.string.str_BQ));
    }

    private String getStr(int resId) {
        return context.getResources().getString(resId);
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }
}
