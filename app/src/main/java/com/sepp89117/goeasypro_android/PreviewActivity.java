package com.sepp89117.goeasypro_android;

import static com.sepp89117.goeasypro_android.gopro.GoProDevice.BT_NOT_CONNECTED;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.sepp89117.goeasypro_android.gopro.GoProDevice;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
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
    private ExoPlayer player;
    private OkHttpClient httpClient;
    private static String startStream_query = "";
    private String ffmpeg_output_uri;
    private String stream_input_uri;
    private Timer keepStreamAliveTimer = null;
    private StyledPlayerView playerView;
    private TextView textView_mode_preset;
    private ImageView rec_icon;
    private boolean streamStarted = false;
    private byte[] keepStreamAliveData;
    private InetAddress inetAddress;
    private DatagramSocket udpSocket = null;
    private GoProDevice streamingDevice;
    private Animation fadeAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        init();

        ffmpegThread.start();

        createPlayer();
    }

    @Override
    protected void onResume() {
        super.onResume();

        ((MyApplication) this.getApplication()).resetIsAppPaused();
    }

    private void init() {
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
        stream_input_uri = "udp://:8554"; // maybe different depending on gopro modelID?
        ffmpeg_output_uri = "udp://@localhost:8555";
        playerView = findViewById(R.id.player_view);
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
        if(streamingDevice.hasProtoPresets() && streamingDevice.protoPreset != null) {
            _mode_preset =  streamingDevice.protoPreset.getSettingsString();
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

    private final Thread ffmpegThread = new Thread(() -> {
        try {
            final String command = "-fflags nobuffer -flags low_delay -f:v mpegts -an -probesize 100000 -i " + stream_input_uri + " -f mpegts -vcodec copy udp://localhost:8555?pkt_size=1316"; // -probesize 100000 is minimum for Hero 10

            FFmpegKit.execute(command);

            this.finish();
        } catch (Exception e) {
            Log.e("FFmpeg", "Exception on command execution: " + e);
        }
    });

    private void requestStream() {
        final Request startPreview = new Request.Builder()
                .url(HttpUrl.get(URI.create(startStream_query)))
                .build();

        httpClient.newCall(startPreview).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("requestStream", "fail");
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) {
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

    private void createPlayer() {
        //Max. Puffer: Die maximale Dauer der Medien, die der Player zu puffern versucht, in Millisekunden. Sobald der Puffer Max Buffer erreicht, hört er auf, ihn aufzufüllen.
        //Min. Puffer: Die Mindestdauer von Medien, die der Player sicherstellen wird, dass sie jederzeit gepuffert werden, in Millisekunden.
        //Puffer für Wiedergabe: Die Standarddauer von Medien, die gepuffert werden müssen, damit die Wiedergabe nach einer Benutzeraktion wie einer Suche gestartet oder fortgesetzt wird, in Millisekunden.
        //Puffer für Wiedergabe nach Rebuffer: Die Dauer der Medien, die gepuffert werden müssen, damit die Wiedergabe nach einem Rebuffer fortgesetzt wird, in Millisekunden.

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBufferDurationsMs(1000, 5000, 900, 900)
                .build();

        TrackSelector trackSelector = new DefaultTrackSelector(this);
        MediaSource mediaSource = new ProgressiveMediaSource.Factory(new DefaultDataSource.Factory(getApplicationContext())).createMediaSource(MediaItem.fromUri(Uri.parse(ffmpeg_output_uri)));

        player = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build();

        playerView.setPlayer(player);
        player.addListener(playerListener);
        player.setMediaSource(mediaSource);
        player.setPlayWhenReady(true);
        player.prepare();

        playerView.requestFocus();

        Log.i("createPlayer", "Player created");
    }

    private void initRequestTimer() {
        keepStreamAliveTimer = new Timer();
        keepStreamAliveTimer.schedule(keepStreamAlive, 0, 7000);
        Log.i("initRequestTimer", "requestTimer init successfully");
    }

    private void killKeepStreamAliveTimer() {
        if (keepStreamAliveTimer == null)
            return;

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
                Log.i("keepStreamAlive", "sent");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            Player.Listener.super.onPlaybackStateChanged(playbackState);
            switch (playbackState) {
                case Player.STATE_IDLE:
                case Player.STATE_ENDED:
                    PreviewActivity.this.finish();
                    break;
                case Player.STATE_BUFFERING:
                    if (!streamStarted) {
                        streamStarted = true;
                        requestStream();
                    }
                    break;
                case Player.STATE_READY:
                    break;
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            Player.Listener.super.onPlayerError(error);
            Log.e("onPlayerError", error.getMessage() + "\n" + error.getCause());
        }
    };

    public void onShutterClick(View v) {
        if (streamingDevice.isRecording)
            streamingDevice.shutterOff();
        else
            streamingDevice.shutterOn();
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
            if (item.hasSubMenu())
                return true;

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
    protected void onDestroy() {
        super.onDestroy();
        FFmpegKit.cancel();
        killKeepStreamAliveTimer();

        if (udpSocket != null)
            udpSocket.disconnect();

        if (player != null) {
            player.stop();
            player.release();
        }
        player = null;
    }
}