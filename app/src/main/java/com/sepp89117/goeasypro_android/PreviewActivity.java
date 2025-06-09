package com.sepp89117.goeasypro_android;

import static com.sepp89117.goeasypro_android.gopro.GoProDevice.BT_NOT_CONNECTED;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;

import com.sepp89117.goeasypro_android.gopro.GoProDevice;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import me.saket.cascade.CascadePopupMenu;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PreviewActivity extends AppCompatActivity {
    private static final String TAG = "PreviewActivity";

    private OkHttpClient httpClient;
    private static String startStream_query = "";
    private Timer keepStreamAliveTimer = null;
    private TextView textView_mode_preset;
    private ImageView rec_icon;
    private byte[] keepStreamAliveData;
    private InetAddress inetAddress;
    private DatagramSocket udpSocket = null;
    private GoProDevice streamingDevice;
    private Animation fadeAnimation;

    private SurfaceView surfaceView;

    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        init();

        requestStream();
    }

    @Override
    protected void onResume() {
        super.onResume();

        ((MyApplication) this.getApplication()).resetIsAppPaused();
    }

    private void init() {
        surfaceView = findViewById(R.id.surfaceView);
        SurfaceHolder mSurfaceHolder = surfaceView.getHolder();
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {
                if (mediaPlayer != null)
                    mediaPlayer.getVLCVout().setWindowSize(surfaceHolder.getSurfaceFrame().width(), surfaceHolder.getSurfaceFrame().height());
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
            }
        });

        fadeAnimation = AnimationUtils.loadAnimation(this, R.anim.tween);
        streamingDevice = ((MyApplication) this.getApplication()).getFocusedDevice();
        textView_mode_preset = findViewById(R.id.textView_mode_preset);
        rec_icon = findViewById(R.id.rec_icon);
        setModePresetText();
        streamingDevice.getDataChanges(() -> runOnUiThread(() -> {
            if (streamingDevice.btConnectionStage == BT_NOT_CONNECTED) {
                finish();
                return;
            }
            setModePresetText();
        }));

        String keepAlive_str = streamingDevice.keepAlive_msg;
        startStream_query = ((MyApplication) this.getApplication()).getFocusedDevice().startStream_query;
        keepStreamAliveData = keepAlive_str.getBytes();
        try {
            inetAddress = InetAddress.getByName(GoProDevice.goProIp);
            udpSocket = new DatagramSocket();
        } catch (Exception e) {
            e.printStackTrace();
        }

        httpClient = new OkHttpClient();

        Log.i("PreviewActivity", "init done");
    }

    private void setModePresetText() {
        String _mode_preset;
        if (streamingDevice.hasProtoPresets() && streamingDevice.protoPreset != null) {
            _mode_preset = streamingDevice.protoPreset.getSettingsString();
        } else {
            _mode_preset = streamingDevice.mode.getTitle() + "\n" + streamingDevice.preset.getTitle();
        }

        textView_mode_preset.setText(_mode_preset);

        if (streamingDevice.isRecording && rec_icon.getVisibility() == View.INVISIBLE) {
            rec_icon.setVisibility(View.VISIBLE);
            rec_icon.startAnimation(fadeAnimation);
        } else if (!streamingDevice.isRecording && rec_icon.getVisibility() == View.VISIBLE) {
            rec_icon.clearAnimation();
            rec_icon.setVisibility(View.INVISIBLE);
        }
    }

    private void requestStream() {
        streamingDevice.setSetting(64, 4); // Set Secondary Stream Window Size to 480p

        final Request startPreview = new Request.Builder().url(Objects.requireNonNull(HttpUrl.get(URI.create(startStream_query)))).build();

        httpClient.newCall(startPreview).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, IOException e) {
                Log.e("requestStream", "fail");
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful()) {
                    Log.e("requestStream", "Request response = not success");
                } else {
                    Log.i("requestStream", "Request response = success");
                    runOnUiThread(() -> {
                        if (keepStreamAliveTimer == null) {
                            initRequestTimer();
                        }
                    });
                }
                response.close();
            }
        });
    }

    private void initRequestTimer() {
        keepStreamAliveTimer = new Timer();
        keepStreamAliveTimer.schedule(keepStreamAlive, 0, 7000);
        Log.i("initRequestTimer", "requestTimer init successfully");
    }

    private void killKeepStreamAliveTimer() {
        if (keepStreamAliveTimer == null) return;

        keepStreamAliveTimer.cancel();
        keepStreamAliveTimer.purge();
        keepStreamAliveTimer = null;
    }

    private final TimerTask keepStreamAlive = new TimerTask() {
        @Override
        public void run() {
            try {
                DatagramPacket keepStreamAlivePacket = new DatagramPacket(keepStreamAliveData, keepStreamAliveData.length, inetAddress, 8554);
                udpSocket.send(keepStreamAlivePacket);
                // Log.i("keepStreamAlive", "sent");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    public void onShutterClick(View v) {
        if (streamingDevice.isRecording) streamingDevice.shutterOff();
        else streamingDevice.shutterOn();
    }

    public void onHighlightClick(View v) {
        streamingDevice.highlight();
    }

    public void onSettingsClick(View v) {
        Intent goSettingsActivityIntent = new Intent(PreviewActivity.this, GoSettingsActivity.class);
        startActivity(goSettingsActivityIntent);
    }

    public void onModeClick(View v) {
        CascadePopupMenu popupMenu = new CascadePopupMenu(PreviewActivity.this, v);
        popupMenu.setOnMenuItemClickListener(modeMenuItemClickListener);
        popupMenu.inflate(R.menu.mode_menu);
        popupMenu.show();
    }

    private final PopupMenu.OnMenuItemClickListener modeMenuItemClickListener = new PopupMenu.OnMenuItemClickListener() {
        @SuppressLint("NonConstantResourceId")
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            if (item.hasSubMenu()) return true;

            switch (item.getItemId()) {
                case R.id.mode_video_single:
                    streamingDevice.setSubMode(0, 0);
                    break;
                case R.id.mode_video_timelapse:
                    streamingDevice.setSubMode(0, 1);
                    break;
                case R.id.mode_photo_single:
                    streamingDevice.setSubMode(1, 1);
                    break;
                case R.id.mode_photo_night:
                    streamingDevice.setSubMode(1, 2);
                    break;
                case R.id.mode_multishot_burst:
                    streamingDevice.setSubMode(2, 0);
                    break;
                case R.id.mode_multishot_timelapse:
                    streamingDevice.setSubMode(2, 1);
                    break;
                case R.id.mode_multishot_nightlapse:
                    streamingDevice.setSubMode(2, 2);
                    break;
            }

            return true;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();

        int cachingTimeMs = 500;
        ArrayList<String> options = new ArrayList<>();
        options.add("--network-caching=" + cachingTimeMs);
        options.add("--drop-late-frames");
        options.add("--audio-time-stretch");
        options.add("--h264-fps=30");
        options.add("--ts-cc-check");

        libVLC = new LibVLC(this, options);
        mediaPlayer = new MediaPlayer(libVLC);

        mediaPlayer.getVLCVout().setVideoView(surfaceView);
        mediaPlayer.getVLCVout().attachViews();

        String url = "udp://@:8554";
        Media media = new Media(libVLC, Uri.parse(url));
        media.setHWDecoderEnabled(true, false);
        media.addOption(":network-caching=" + cachingTimeMs);
        mediaPlayer.setMedia(media);
        media.release();

        mediaPlayer.play();
    }

    @Override
    protected void onStop() {
        super.onStop();

        Log.d(TAG, "onStop");
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.getVLCVout().detachViews();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (libVLC != null) {
            libVLC.release();
            libVLC = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        killKeepStreamAliveTimer();
        if (udpSocket != null) udpSocket.disconnect();
    }
}