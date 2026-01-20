package com.sepp89117.goeasypro_android.stream;

import static com.sepp89117.goeasypro_android.gopro.GoProDevice.BT_NOT_CONNECTED;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSource;

import com.sepp89117.goeasypro_android.gopro.GoProDevice;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GoProStreamManager {

    private static final String TAG = "GoProStreamManager";

    private final Context context;
    private final StyledPlayerView playerView;
    private ProgressBar bufferingView;
    private final GoProDevice device;
    private final OkHttpClient httpClient;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private ExoPlayer player;
    private Timer keepAliveTimer;
    private DatagramSocket udpSocket;
    private InetAddress inetAddress;
    private byte[] keepAliveData;
    private boolean _streamActive = false;

    public GoProStreamManager(Context context, StyledPlayerView playerView, GoProDevice device) {
        this.context = context;
        this.playerView = playerView;
        this.device = device;
        this.httpClient = new OkHttpClient();

        device.isPreviewStreaming = false;
        createOverlays();
    }

    public void startStream() {
        try {
            inetAddress = InetAddress.getByName(GoProDevice.goProIp);
            keepAliveData = device.keepAlive_msg.getBytes();
            udpSocket = new DatagramSocket();

            startFFmpeg();
            startPlayer();
            requestStream();

        } catch (Exception e) {
            Log.e(TAG, "Stream start failed", e);

            stopStream();
            throw new RuntimeException("Stream start failed!");
        }
    }

    public boolean streamActive(GoProDevice goProDevice) {
        return _streamActive && goProDevice.btMacAddress.equals(device.btMacAddress);
    }

    public boolean anyStreamActive() {
        return _streamActive;
    }

    public void showBuffering() {
        if (bufferingView != null)
            uiHandler.post(() -> {
                bufferingView.setVisibility(View.VISIBLE);
            });
    }

    public void hideBuffering() {
        if (bufferingView != null)
            uiHandler.post(() -> {
                bufferingView.setVisibility(View.GONE);
            });
    }

    public void showPlayerView() {
        playerView.setVisibility(View.VISIBLE);
        setPlayerHeight();
    }

    public void hidePlayerView() {
        playerView.setVisibility(View.GONE);
    }

    private boolean isLandscape() {
        Activity activity = (Activity) context;
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation(); //== Surface.ROTATION_0;

        return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
    }

    private boolean isPortrait() {
        Activity activity = (Activity) context;
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation(); //== Surface.ROTATION_0;

        return rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180;
    }

    private void setPlayerHeight() {
        // Set playerView height to 16:9
        uiHandler.post(() -> {
            if ((playerView.getLayoutParams() instanceof LinearLayout.LayoutParams)) {
                // MainActivity
                int playerViewWidth = playerView.getWidth();
                int playerViewHeight = playerViewWidth * 9 / 16;
                LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) playerView.getLayoutParams();
                layoutParams.height = playerViewHeight;
                playerView.setLayoutParams(layoutParams);

                if (isPortrait())
                    playerView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void createOverlays() {
        // Buffering overlay
        bufferingView = new ProgressBar(context, null, android.R.attr.progressBarStyleLarge);
        bufferingView.setIndeterminate(true);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.CENTER;

        bufferingView.setLayoutParams(params);
        bufferingView.setVisibility(View.VISIBLE);

        playerView.addView(bufferingView);
        bufferingView.bringToFront();

        if ((playerView.getLayoutParams() instanceof LinearLayout.LayoutParams)) {
            // MainActivity
            int playerViewWidth = playerView.getWidth();
            int playerViewHeight = Math.max(playerViewWidth * 9 / 16, 200); // Min height of 200 px
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) playerView.getLayoutParams();
            layoutParams.height = playerViewHeight;
            playerView.setLayoutParams(layoutParams);

            // Camera name overlay
            TextView camNameTV = new TextView(context, null, android.R.attr.textViewStyle);
            camNameTV.setText(device.displayName);
            camNameTV.setTextSize(16);
            camNameTV.setTextColor(0xFF00FF00);

            FrameLayout.LayoutParams camNameParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );

            camNameParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            camNameTV.setLayoutParams(camNameParams);
            camNameTV.setVisibility(View.VISIBLE);
            playerView.addView(camNameTV);

            if (isPortrait())
                playerView.setVisibility(View.VISIBLE);
        }
    }

    private void startFFmpeg() {
        String cmd = "-fflags nobuffer -flags low_delay -f:v mpegts -an " +
                "-probesize 100000 -i udp://:8554" +
                " -f mpegts -vcodec copy udp://localhost:8555?pkt_size=1316";

        new Thread(() -> FFmpegKit.execute(cmd)).start();
    }

    private void requestStream() {
        device.setSetting(64, 4); // Set Secondary Stream Window Size to 480p

        final Request startPreview = new Request.Builder().url(Objects.requireNonNull(HttpUrl.get(URI.create(device.startStream_query)))).build();

        httpClient.newCall(startPreview).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("requestStream", "failed");

                stopStream();
                throw new RuntimeException("Request Stream failed!");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful()) {
                    Log.e("requestStream", "Request response = not success");

                    stopStream();
                    throw new RuntimeException("Request Stream not successful!");
                } else {
                    Log.i("requestStream", "Request response = success");
                    if (keepAliveTimer == null) {
                        startKeepAlive();
                    }
                    hideBuffering();

                    setPlayerHeight();

                    _streamActive = true;
                    device.isPreviewStreaming = true;
                }
                response.close();
            }
        });
    }

    private void startPlayer() {
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(1000, 5000, 900, 900)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        player = new ExoPlayer.Builder(context)
                .setTrackSelector(new DefaultTrackSelector(context))
                .setLoadControl(loadControl)
                .build();

        ProgressiveMediaSource mediaSource =
                new ProgressiveMediaSource.Factory(new DefaultDataSource.Factory(context))
                        .createMediaSource(MediaItem.fromUri(Uri.parse("udp://@localhost:8555")));

        playerView.setPlayer(player);
        player.setMediaSource(mediaSource);
        player.setPlayWhenReady(true);
        player.prepare();
    }

    private void startKeepAlive() {
        keepAliveTimer = new Timer();
        keepAliveTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    DatagramPacket packet =
                            new DatagramPacket(keepAliveData, keepAliveData.length, inetAddress, 8554);
                    udpSocket.send(packet);
                } catch (Exception e) {
                    Log.e(TAG, "UDP send keep alive packet failed", e);
                    if (!device.isWifiConnected() || device.btConnectionStage == BT_NOT_CONNECTED) {
                        stopStream();
                    }
                }
            }
        }, 0, 7000);
    }

    public void stopStream() {
        uiHandler.post(() -> {
            if (player != null) {
                player.stop();
                player.release();
                player = null;
            }

            if (keepAliveTimer != null) {
                keepAliveTimer.cancel();
                keepAliveTimer = null;
            }

            if (udpSocket != null) {
                udpSocket.close();
            }

            FFmpegKit.cancel();

            _streamActive = false;
            device.isPreviewStreaming = false;
            playerView.setVisibility(View.GONE);
        });
    }
}
