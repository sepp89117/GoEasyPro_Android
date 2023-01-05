package com.sepp89117.goeasypro_android;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Locale;

public class StorageBrowserActivity extends AppCompatActivity {
    private GoProDevice focusedDevice;
    private ArrayList<GoMediaFile> goMediaFiles;
    private ListView listView;
    private ArrayList<String> fileStrings;
    private StyledPlayerView playerView;
    private ExoPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_storage_browser);

        focusedDevice = ((MyApplication) this.getApplication()).getFocusedDevice();
        goMediaFiles = ((MyApplication) this.getApplication()).getGoMediaFiles();

        playerView = findViewById(R.id.vid_player_view);

        listView = findViewById(R.id.file_listView);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                GoMediaFile clickedFile = goMediaFiles.get(position);

                MediaSource mediaSource = new ProgressiveMediaSource.Factory(new DefaultDataSource.Factory(getApplicationContext())).createMediaSource(MediaItem.fromUri(Uri.parse(clickedFile.path)));
                player.setMediaSource(mediaSource);
            }
        });

        fileStrings = new ArrayList<>();
        DateFormat f = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault());
        for (int i = 0; i < goMediaFiles.size(); i++) {
            fileStrings.add(
                    goMediaFiles.get(i).fileName + "\n" + f.format(goMediaFiles.get(i).lastModified)
            );
        }

        setListAdapter();

        createPlayer();
    }

    private void createPlayer() {
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBufferDurationsMs(25000, 35000, 20000, 20000)
                .build();

        TrackSelector trackSelector = new DefaultTrackSelector(this);


        player = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build();

        playerView.setPlayer(player);
        player.addListener(playerListener);
        player.setPlayWhenReady(true);
        player.prepare();

        playerView.requestFocus();

        Log.i("createPlayer", "Player created");
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            Player.Listener.super.onPlaybackStateChanged(playbackState);
            switch (playbackState) {
                case Player.STATE_IDLE:
                    Log.i("onPlaybackStateChanged", "Player.STATE_IDLE -> finish PreviewActivity");
                    break;
                case Player.STATE_BUFFERING:
                    Log.i("onPlaybackStateChanged", "Player.STATE_BUFFERING");
                    break;
                case Player.STATE_READY:
                    Log.i("onPlaybackStateChanged", "Player.STATE_READY");
                    break;
                case Player.STATE_ENDED:
                    Log.i("onPlaybackStateChanged", "Player.STATE_ENDED -> finish PreviewActivity");
                    break;
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            Player.Listener.super.onPlayerError(error);
            Log.e("onPlayerError", error.getMessage() + "\n" + error.getCause());
        }
    };

    private void setListAdapter() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                listView.setAdapter(new ArrayAdapter<String>(getApplicationContext(), R.layout.bt_listitem, fileStrings) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        TextView textView = (TextView) super.getView(position, convertView, parent);
                        //String text = textView.getText().toString();
                        return textView;
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (player != null) {
            player.stop();
            player.release();
        }
        player = null;
    }
}