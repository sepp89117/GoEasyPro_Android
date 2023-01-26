package com.sepp89117.goeasypro_android;

import android.graphics.Bitmap;

import java.util.Date;

public class GoMediaFile {
    public String extension = "";
    public String url = "";
    public String fileName = "";
    public Date lastModified;
    public long fileByteSize;

    //burst photos
    public boolean isGroup = false;
    public String groupBegin = "0";
    public String groupLast = "0";


    public String thumbNail_path = "";
    public Bitmap thumbNail = null;
}
