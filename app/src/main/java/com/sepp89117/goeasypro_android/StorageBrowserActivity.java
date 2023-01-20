package com.sepp89117.goeasypro_android;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

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

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class StorageBrowserActivity extends AppCompatActivity {
    private GoProDevice focusedDevice;
    private ArrayList<GoMediaFile> goMediaFiles;
    private ListView fileListView;
    private StyledPlayerView playerView;
    private ExoPlayer player;
    private OkHttpClient client = new OkHttpClient();
    private FileListAdapter fileListAdapter;
    private GoMediaFile clickedFile = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_storage_browser);

        focusedDevice = ((MyApplication) this.getApplication()).getFocusedDevice();
        goMediaFiles = ((MyApplication) this.getApplication()).getGoMediaFiles();

        getThumbNailsAsync();

        TextView selFile_textView = findViewById(R.id.textView3);
        selFile_textView.setText("Select a file from " + focusedDevice.name);

        playerView = findViewById(R.id.vid_player_view);

        fileListAdapter = new FileListAdapter(goMediaFiles, this);

        fileListView = findViewById(R.id.file_listView);
        registerForContextMenu(fileListView);
        fileListView.setOnItemClickListener((parent, view, position, id) -> {
            if (clickedFile == goMediaFiles.get(position))
                return;

            clickedFile = goMediaFiles.get(position);

            MediaSource mediaSource = new ProgressiveMediaSource.Factory(new DefaultDataSource.Factory(getApplicationContext())).createMediaSource(MediaItem.fromUri(Uri.parse(clickedFile.path)));
            player.setMediaSource(mediaSource);
        });

        fileListView.setAdapter(fileListAdapter);

        createPlayer();
    }

    private void getThumbNailsAsync() {
        new Thread(() -> {
            for (int i = 0; i < goMediaFiles.size(); i++) {
                GoMediaFile file = goMediaFiles.get(i);
                String tn_url = file.thumbNail_path;

                Request request = new Request.Builder()
                        .url(tn_url)
                        .build();
                Log.d("HTTP GET", tn_url);

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e("getThumbNailsAsync", "fail");
                        e.printStackTrace();
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (!response.isSuccessful()) {
                            Log.e("getThumbNailsAsync", "Request response = not success");
                        } else {
                            Bitmap bmp = BitmapFactory.decodeStream(response.body().byteStream());
                            file.thumbNail = bmp;

                            runOnUiThread(() -> fileListView.setAdapter(fileListAdapter));
                        }
                        response.close();
                    }
                });
            }
        }).start();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        int pos = info.position;

        menu.setHeaderTitle("Options");
        // add menu items
        menu.add(pos, 0, 0, "Delete file");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int pos = item.getGroupId();

        switch (item.getItemId()) {
            case 0:
                //delete file
                AlertDialog alert = new AlertDialog.Builder(StorageBrowserActivity.this)
                        .setTitle("Delete device")
                        .setMessage("Are you sure you want to delete " + goMediaFiles.get(pos).fileName + "?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            //delte File
                            String fileDelCmd = "http://10.5.5.9/gp/gpControl/command/storage/delete?p=" + goMediaFiles.get(pos).path.substring(32);

                            Request request = new Request.Builder()
                                    .url(fileDelCmd)
                                    .build();
                            Log.d("HTTP GET", fileDelCmd);

                            client.newCall(request).enqueue(new Callback() {
                                @Override
                                public void onFailure(Call call, IOException e) {
                                    Log.e("delFile", "fail");
                                    e.printStackTrace();
                                }

                                @Override
                                public void onResponse(Call call, Response response) throws IOException {
                                    if (!response.isSuccessful()) {
                                        Log.e("delFile", "Request response = not success");
                                        runOnUiThread(() -> Toast.makeText(StorageBrowserActivity.this, "Something went wrong. Please try again!", Toast.LENGTH_SHORT).show());
                                    } else {
                                        goMediaFiles.remove(pos);
                                        runOnUiThread(() -> fileListView.setAdapter(fileListAdapter));
                                    }
                                    response.close();
                                }
                            });
                        })
                        .create();
                alert.show();
                break;
        }

        return true;
    }

    private void createPlayer() {
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBufferDurationsMs(12500, 30000, 10000, 10000)
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