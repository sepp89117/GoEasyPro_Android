package com.sepp89117.goeasypro_android.gopro;

import android.content.Context;

import com.sepp89117.goeasypro_android.R;

import java.util.HashMap;
import java.util.Map;

public class GoMode {
    private final Context context;
    
    private final int id;
    private final String title;
    private static final Map<Integer, String> modes = new HashMap<>();

    public GoMode(Context context){
        this.context = context;

        id = -1;
        title = getStr(R.string.str_NC);
    }

    public GoMode(Context context, int id) {
        this.context = context;
        
        this.id = id;
        putValuesToMap();
        this.title = modes.getOrDefault(this.id, getStr(R.string.str_UNK) + " " + this.id);
    }

    private void putValuesToMap(){
        modes.put(-1, getStr(R.string.str_NC));
        modes.put(0, getStr(R.string.str_Video));
        modes.put(1, getStr(R.string.str_Photo));
        modes.put(2, getStr(R.string.str_Multishot));
        modes.put(3, getStr(R.string.str_Broadcast));
        modes.put(4, getStr(R.string.str_Playback));
        modes.put(5, getStr(R.string.str_Setup));
        modes.put(6, getStr(R.string.str_FW_Update));
        modes.put(7, getStr(R.string.str_USB_MTP));
        modes.put(8, getStr(R.string.str_SOS));
        modes.put(9, getStr(R.string.str_MEdit));
        modes.put(10, getStr(R.string.str_Calibration));
        modes.put(11, getStr(R.string.str_Direct_Offload));
        modes.put(12, getStr(R.string.str_Video));
        modes.put(13, getStr(R.string.str_Time_Lapse_Video));
        modes.put(14, getStr(R.string.str_Video_Photo));
        modes.put(15, getStr(R.string.str_Looping));
        modes.put(16, getStr(R.string.str_Single_Photo));
        modes.put(17, getStr(R.string.str_Photo));
        modes.put(18, getStr(R.string.str_Night_Photo));
        modes.put(19, getStr(R.string.str_Burst_Photo));
        modes.put(20, getStr(R.string.str_Time_Lapse_Photo));
        modes.put(21, getStr(R.string.str_Night_Lapse_Photo));
        modes.put(22, getStr(R.string.str_Broadcast_Record));
        modes.put(23, getStr(R.string.str_Webcam));
        modes.put(24, getStr(R.string.str_Time_Warp_Video));
        modes.put(25, getStr(R.string.str_Live_Burst));
        modes.put(26, getStr(R.string.str_Night_Lapse_Video));
        modes.put(27, getStr(R.string.str_Slo_Mo));
        modes.put(28, getStr(R.string.str_Idle));
        modes.put(29, getStr(R.string.str_Star_Trail));
        modes.put(30, getStr(R.string.str_Light_Painting));
        modes.put(31, getStr(R.string.str_Light_Trail));
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
