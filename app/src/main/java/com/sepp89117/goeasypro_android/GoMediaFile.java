package com.sepp89117.goeasypro_android;

import android.graphics.Bitmap;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GoMediaFile {
    public String lrvUrl;
    public String extension = "";
    public String url = "";
    public String fileName = "";
    public Date lastModified;
    public long fileByteSize;
    public String mimeType;
    public String thumbNail_path = "";
    public Bitmap thumbNail = null;
    public boolean hasLrv = false;
    private String _directory = "";

    // multishot
    public boolean isGroup = false;
    public String groupBegin = "0";
    public String groupLast = "0";
    public int groupLength = 0;
    private final OkHttpClient httpClient = new OkHttpClient();

    public GoMediaFile(JSONObject fileDataJson, String directory, GoProDevice goProDevice) throws JSONException {
        _directory = directory;
        if (fileDataJson.has("g")) {
            if (fileDataJson.has("b") && fileDataJson.has("l")) {
                this.groupBegin = fileDataJson.getString("b");
                this.groupLast = fileDataJson.getString("l");

                int start = Integer.parseInt(this.groupBegin);
                int end = Integer.parseInt(this.groupLast);
                this.groupLength = end - start + 1;
            }
            this.isGroup = this.groupLength > 1;
        }

        this.fileName = fileDataJson.getString("n");
        int extIndex = this.fileName.lastIndexOf('.');
        this.extension = this.fileName.substring(extIndex).toLowerCase();
        this.url = "http://10.5.5.9:8080/videos/DCIM/" + directory + "/" + this.fileName;
        this.thumbNail_path = goProDevice.getThumbNail_query + directory + "/" + this.fileName;
        long lastModifiedS = Long.parseLong(fileDataJson.getString("mod"));
        long lastModifiedMs = lastModifiedS * 1000;
        this.lastModified = new Date(lastModifiedMs);
        this.fileByteSize = Long.parseLong(fileDataJson.getString("s"));

        parseMimeType();

        if (Objects.equals(this.extension, ".mp4")) {
            String lrvFileName = this.fileName.charAt(0) + "L" + this.fileName.substring(2, extIndex) + ".LRV";
            this.lrvUrl = "http://10.5.5.9:8080/videos/DCIM/" + directory + "/" + lrvFileName;
            checkLrvUrl(true);
        }
    }

    private void checkLrvUrl(boolean isFirstTry) {
        new Thread(() -> {
            Log.d("HTTP HEAD", lrvUrl);
            final Request testLrvExists = new Request.Builder()
                    .url(HttpUrl.get(URI.create(lrvUrl)))
                    .build();

            httpClient.newCall(testLrvExists).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e("checkLrvUrl", "fail");
                    lrvUrl = url;
                    e.printStackTrace();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        if(isFirstTry) {
                            Log.e("checkLrvUrl", "First request head response = not success");
                            int extIndex = fileName.lastIndexOf('.');
                            String lrvFileName = fileName.substring(0, extIndex) + ".LRV";
                            lrvUrl = "http://10.5.5.9:8080/videos/DCIM/" + _directory + "/" + lrvFileName;
                            checkLrvUrl(false);
                        } else {
                            Log.e("checkLrvUrl", "Second request head response = not success");
                            lrvUrl = url;
                        }
                    } else {
                        hasLrv = true;
                    }
                    response.close();
                }
            });
        }).start();
    }

    private void parseMimeType() {
        switch (extension) {
            case ".jpg":
            case ".thm": // thumbnail
                mimeType = "image/jpeg";
                break;
            case ".lrv": // low resolution video
            case ".mp4":
                mimeType = "video/mp4";
                break;
            case ".oga":
                mimeType = "audio/ogg";
                break;
            case ".mp3":
                mimeType = "audio/mpeg";
                break;
            case ".flac":
                mimeType = "audio/flac";
                break;
            case ".midi":
                mimeType = "audio/midi";
                break;
            case ".ape":
                mimeType = "audio/ape";
                break;
            case ".mpc":
                mimeType = "audio/musepack";
                break;
            case ".amr":
                mimeType = "audio/amr";
                break;
            case ".wav":
                mimeType = "audio/wav";
                break;
            case ".aiff":
                mimeType = "audio/aiff";
                break;
            case ".au":
                mimeType = "audio/basic";
                break;
            case ".aac":
                mimeType = "audio/aac";
                break;
            case ".voc":
                mimeType = "audio/x-unknown";
                break;
            case ".m4a":
                mimeType = "audio/x-m4a";
                break;
            case ".qcp":
                mimeType = "audio/qcelp";
                break;
            case ".xpm":
                mimeType = "image/x-xpixmap";
                break;
            case ".psd":
                mimeType = "image/vnd.adobe.photoshop";
                break;
            case ".png":
                mimeType = "image/png";
                break;
            case ".jxl":
                mimeType = "image/jxl";
                break;
            case ".jp2":
                mimeType = "image/jp2";
                break;
            case ".jpf":
                mimeType = "image/jpx";
                break;
            case ".jpm":
                mimeType = "image/jpm";
                break;
            case ".jxs":
                mimeType = "image/jxs";
                break;
            case ".gif":
                mimeType = "image/gif";
                break;
            case ".webp":
                mimeType = "image/webp";
                break;
            case ".tiff":
                mimeType = "image/tiff";
                break;
            case ".bmp":
                mimeType = "image/bmp";
                break;
            case ".ico":
                mimeType = "image/x-icon";
                break;
            case ".djvu":
                mimeType = "image/vnd.djvu";
                break;
            case ".bpg":
                mimeType = "image/bpg";
                break;
            case ".dwg":
                mimeType = "image/vnd.dwg";
                break;
            case ".icns":
                mimeType = "image/x-icns";
                break;
            case ".heic":
                mimeType = "image/heic";
                break;
            case ".heif":
                mimeType = "image/heif";
                break;
            case ".hdr":
                mimeType = "image/vnd.radiance";
                break;
            case ".xcf":
                mimeType = "image/x-xcf";
                break;
            case ".pat":
                mimeType = "image/x-gimp-pat";
                break;
            case ".gbr":
                mimeType = "image/x-gimp-gbr";
                break;
            case ".avif":
                mimeType = "image/avif";
                break;
            case ".jxr":
                mimeType = "image/jxr";
                break;
            case ".svg":
                mimeType = "image/svg+xml";
                break;
            case ".ogv":
                mimeType = "video/ogg";
                break;
            case ".mpeg":
                mimeType = "video/mpeg";
                break;
            case ".mov":
                mimeType = "video/quicktime";
                break;
            case ".mqv":
                mimeType = "video/quicktime";
                break;
            case ".webm":
                mimeType = "video/webm";
                break;
            case ".3gp":
                mimeType = "video/3gpp";
                break;
            case ".3g2":
                mimeType = "video/3gpp2";
                break;
            case ".avi":
                mimeType = "video/x-msvideo";
                break;
            case ".flv":
                mimeType = "video/x-flv";
                break;
            case ".mkv":
                mimeType = "video/x-matroska";
                break;
            case ".asf":
                mimeType = "video/x-ms-asf";
                break;
            case ".m4v":
                mimeType = "video/x-m4v";
                break;
            default:
                mimeType = "video/mp4";
        }
    }
}
