package com.sepp89117.goeasypro_android;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Objects;

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
    private ImageView imagePlayer;
    private ExoPlayer player;
    private final OkHttpClient client = new OkHttpClient();
    private FileListAdapter fileListAdapter;
    private GoMediaFile clickedFile = null;

    private int minBufferMs = 12500;
    private int bufferForPlaybackMs = 10000;
    private int bufferForPlaybackAfterRebufferMs = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_storage_browser);

        focusedDevice = ((MyApplication) this.getApplication()).getFocusedDevice();
        goMediaFiles = ((MyApplication) this.getApplication()).getGoMediaFiles();

        getThumbNailsAsync();

        TextView selFile_textView = findViewById(R.id.textView3);
        selFile_textView.setText("Select a file from '" + focusedDevice.displayName + "'");

        playerView = findViewById(R.id.vid_player_view);
        imagePlayer = findViewById(R.id.imagePlayer);

        fileListAdapter = new FileListAdapter(goMediaFiles, this);

        fileListView = findViewById(R.id.file_listView);
        registerForContextMenu(fileListView);
        fileListView.setOnItemClickListener((parent, view, position, id) -> {
            if (clickedFile == goMediaFiles.get(position))
                return;

            clickedFile = goMediaFiles.get(position);

            destroyPlayer();

            if (Objects.equals(clickedFile.extension, ".mp4")) {
                String videoUrl;
                if(clickedFile.hasLrv) {
                    videoUrl = clickedFile.lrvUrl;
                    minBufferMs = 2500;
                    bufferForPlaybackMs = 1000;
                    bufferForPlaybackAfterRebufferMs = 1000;
                } else {
                    videoUrl = clickedFile.url;
                    minBufferMs = 12500;
                    bufferForPlaybackMs = 10000;
                    bufferForPlaybackAfterRebufferMs = 10000;
                }

                createPlayer();

                MediaSource mediaSource = new ProgressiveMediaSource.Factory(new DefaultDataSource.Factory(getApplicationContext())).createMediaSource(MediaItem.fromUri(Uri.parse(videoUrl)));
                player.setMediaSource(mediaSource);
            } else if (Objects.equals(clickedFile.extension, ".jpg")) {
                playerView.setVisibility(View.INVISIBLE);
                imagePlayer.setImageResource(R.drawable.ic_baseline_photo_camera_24);
                imagePlayer.setVisibility(View.VISIBLE);

                Request request = new Request.Builder()
                        .url(clickedFile.url)
                        .build();
                Log.d("HTTP GET", clickedFile.url);

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e("getImage", "fail");
                        imagePlayer.setImageResource(R.drawable.ic_baseline_no_photography_24);
                        e.printStackTrace();
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (!response.isSuccessful()) {
                            Log.e("getImage", "Request response = not success");
                            imagePlayer.setImageResource(R.drawable.ic_baseline_no_photography_24);
                        } else {
                            try {
                                Bitmap bmp = BitmapFactory.decodeStream(response.body().byteStream());
                                if (bmp != null)
                                    runOnUiThread(() -> imagePlayer.setImageBitmap(bmp));
                            } catch (Exception ex) {
                                Log.e("loadJPG", ex.getMessage());
                            }
                        }
                        response.close();
                    }
                });
            }
        });

        fileListView.setAdapter(fileListAdapter);
    }

    private void destroyPlayer() {
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
    }

    private void getThumbNailsAsync() {
        new Thread(() -> {
            for (int i = 0; i < goMediaFiles.size(); i++) {
                GoMediaFile file = goMediaFiles.get(i);
                String tn_url = file.thumbNail_path;
                final boolean[] gotResponse = {false};

                Request request = new Request.Builder()
                        .url(tn_url)
                        .build();
                Log.d("HTTP GET", tn_url);

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        gotResponse[0] = true;
                        Log.e("getThumbNailsAsync", "GET '" + call.request().url() + "' failed!");
                        e.printStackTrace();
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        gotResponse[0] = true;
                        if (!response.isSuccessful()) {
                            Log.e("getThumbNailsAsync", "GET '" + call.request().url() + "' unsuccessful!");
                        } else {
                            Bitmap bmp = BitmapFactory.decodeStream(response.body().byteStream());
                            file.thumbNail = bmp;
                        }
                        response.close();
                    }
                });

                while (!gotResponse[0]) ;
            }
            runOnUiThread(() -> fileListView.setAdapter(fileListAdapter));
        }).start();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        int pos = info.position;

        menu.setHeaderTitle("File options");
        // add menu items
        menu.add(pos, 1, 0, "Download");
        menu.add(pos, 0, 0, "Delete");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int pos = item.getGroupId();

        switch (item.getItemId()) {
            case 0:
                // delete file
                AlertDialog alert = new AlertDialog.Builder(StorageBrowserActivity.this)
                        .setTitle("Delete device")
                        .setMessage("Are you sure you want to delete " + goMediaFiles.get(pos).fileName + "?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            deleteFile(pos);
                        })
                        .create();
                alert.show();
                break;
            case 1:
                // download file
                downloadFile(pos);
                break;
        }

        return true;
    }

    private void downloadFile(int pos) {
        if (!hasExtStoragePermissions()) {
            Toast.makeText(StorageBrowserActivity.this, "Need permissions first!", Toast.LENGTH_SHORT).show();
            requestIOPermissions();
            return;
        }

        final AlertDialog dlAlert = new AlertDialog.Builder(StorageBrowserActivity.this)
                .setTitle("Downloading")
                .setMessage("Please wait until the download is complete!")
                .setCancelable(true)
                .create();

        dlAlert.show();

        GoMediaFile goMediaFile = goMediaFiles.get(pos);

        ArrayList<String> fileDlCmds = new ArrayList<>();

        if (goMediaFile.isGroup) {
            String fileName = goMediaFile.fileName;
            String dlCmdI = goMediaFile.url;

            int start = Integer.parseInt(goMediaFile.groupBegin);
            int end = Integer.parseInt(goMediaFile.groupLast);

            if (!fileName.contains(String.valueOf(start))) {
                Log.e("downloadFile", "File name '" + fileName + "' doesn't contains '" + start + "'");
                return;
            }

            String lastFileName = "";

            fileDlCmds.add(dlCmdI);

            for (int i = start + 1; i < end + 1; i++) {
                String lastInt = String.valueOf(i - 1);
                String nextInt = String.valueOf(i);
                lastFileName = fileName;

                fileName = fileName.replace(lastInt, nextInt);
                if (lastInt.length() != nextInt.length()) {
                    int delta = Math.abs(nextInt.length() - lastInt.length());
                    int intIndex = fileName.indexOf(nextInt);
                    String fileNameEnding = fileName.substring(intIndex);
                    fileName = fileName.substring(0, intIndex - delta) + fileNameEnding;
                }
                dlCmdI = dlCmdI.replace(lastFileName, fileName);

                fileDlCmds.add(dlCmdI);
            }
        } else {
            fileDlCmds.add(goMediaFile.url);
        }

        for (int i = 0; i < fileDlCmds.size(); i++) {
            String fileDelCmd = fileDlCmds.get(i);

            Request request = new Request.Builder()
                    .url(fileDelCmd)
                    .build();
            Log.d("HTTP GET", fileDelCmd);

            int finalI = i;
            //TODO build in a progress listener https://github.com/square/okhttp/blob/master/samples/guide/src/main/java/okhttp3/recipes/Progress.java
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e("downloadFile", "GET '" + call.request().url() + "' failed!");
                    dlAlert.dismiss();
                    runOnUiThread(() -> Toast.makeText(StorageBrowserActivity.this, "Something went wrong. Please try again!", Toast.LENGTH_SHORT).show());
                    e.printStackTrace();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        Log.e("downloadFile", "GET '" + call.request().url() + "' unsuccessful!");
                        dlAlert.dismiss();
                        runOnUiThread(() -> Toast.makeText(StorageBrowserActivity.this, "Download may not have been successful. Maybe try again!", Toast.LENGTH_SHORT).show());
                    } else {
                        try {
                            InputStream inputStream = response.body().byteStream();
                            File dir;

                            dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() + "/GoEasyPro");
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                if (!dir.exists() && !dir.mkdir()) {
                                    Log.e("dlFile", "Could not create directory '" + dir.getPath() + "'!");
                                    runOnUiThread(() -> Toast.makeText(StorageBrowserActivity.this, "Could not create directory '" + dir.getPath() + "'!", Toast.LENGTH_SHORT).show());
                                    dlAlert.dismiss();
                                    return;
                                }
                            }

                            int fileIndex = 1;
                            File file = new File(dir, goMediaFile.fileName);
                            while (file.exists()) {
                                if (fileIndex >= 10000) {
                                    Log.e("dlFile", "There are more then 10000 files withe the same name in the directory '" + dir.getPath() + "'!");
                                    runOnUiThread(() -> Toast.makeText(StorageBrowserActivity.this, "There are more then 10000 files withe the same name in the directory!", Toast.LENGTH_SHORT).show());
                                    dlAlert.dismiss();
                                    return;
                                }
                                file = new File(dir, goMediaFile.fileName.substring(0, goMediaFile.fileName.length() - 4) + "(" + fileIndex + ")" + goMediaFile.fileName.substring(goMediaFile.fileName.length() - 4));
                                fileIndex++;
                            }

                            OutputStream outputStream;
                            String fileName = file.getName();

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                String mimeType = goMediaFile.mimeType;

                                if (mimeType == null) {
                                    Log.e("dlFile", "Mime type for file '" + fileName + "' unknown!");
                                    runOnUiThread(() -> Toast.makeText(StorageBrowserActivity.this, "Mime type for file '" + fileName + "' unknown!", Toast.LENGTH_SHORT).show());
                                    dlAlert.dismiss();
                                    return;
                                }

                                ContentResolver contentResolver = StorageBrowserActivity.this.getContentResolver();
                                ContentValues values = new ContentValues();
                                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                                values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/GoEasyPro");

                                Uri fileUri = null;

                                if(mimeType.contains("image")) {
                                    fileUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                                } else if(mimeType.contains("video")) {
                                    fileUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                                }

                                if (fileUri == null) {
                                    Log.e("dlFile", "File uri for file '" + fileName + "' unknown!");
                                    runOnUiThread(() -> Toast.makeText(StorageBrowserActivity.this, "File uri for file '" + fileName + "' unknown!", Toast.LENGTH_SHORT).show());
                                    dlAlert.dismiss();
                                    return;
                                }

                                outputStream = contentResolver.openOutputStream(fileUri);
                            } else {
                                outputStream = new FileOutputStream(file);
                            }

                            try {
                                byte[] buffer = new byte[8 * 1024];
                                int read;

                                while ((read = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, read);
                                }

                                outputStream.flush();
                                outputStream.close();

                                if (finalI == fileDlCmds.size() - 1) {
                                    dlAlert.dismiss();
                                    runOnUiThread(() -> Toast.makeText(StorageBrowserActivity.this, "Download completed!", Toast.LENGTH_SHORT).show());
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                inputStream.close();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.e("dlFile", "File '" + goMediaFile.fileName + "' download error!");
                            runOnUiThread(() -> Toast.makeText(StorageBrowserActivity.this, "File '" + goMediaFile.fileName + "' download error!", Toast.LENGTH_SHORT).show());
                            dlAlert.dismiss();
                        }
                    }
                    response.close();
                }
            });
        }
    }

    private void deleteFile(int pos) {
        GoMediaFile goMediaFile = goMediaFiles.get(pos);

        ArrayList<String> fileDelCmds = new ArrayList<>();

        if (goMediaFile.isGroup) {
            String fileName = goMediaFile.fileName;
            String delCmdI = "http://10.5.5.9/gp/gpControl/command/storage/delete?p=" + goMediaFile.url.substring(33);

            int start = Integer.parseInt(goMediaFile.groupBegin);
            int end = Integer.parseInt(goMediaFile.groupLast);

            if (!fileName.contains(String.valueOf(start))) {
                Log.e("delFile", "File name '" + fileName + "' doesn't contains '" + start + "'");
                return;
            }

            String lastFileName = "";

            fileDelCmds.add(delCmdI);

            for (int i = start + 1; i < end + 1; i++) {
                String lastInt = String.valueOf(i - 1);
                String nextInt = String.valueOf(i);
                lastFileName = fileName;

                fileName = fileName.replace(lastInt, nextInt);
                if (lastInt.length() != nextInt.length()) {
                    int delta = Math.abs(nextInt.length() - lastInt.length());
                    int intIndex = fileName.indexOf(nextInt);
                    String fileNameEnding = fileName.substring(intIndex);
                    fileName = fileName.substring(0, intIndex - delta) + fileNameEnding;
                }
                delCmdI = delCmdI.replace(lastFileName, fileName);

                fileDelCmds.add(delCmdI);
            }
        } else {
            fileDelCmds.add("http://10.5.5.9/gp/gpControl/command/storage/delete?p=" + goMediaFile.url.substring(33));
        }

        for (int i = 0; i < fileDelCmds.size(); i++) {
            String fileDelCmd = fileDelCmds.get(i);

            Request request = new Request.Builder()
                    .url(fileDelCmd)
                    .build();
            Log.d("HTTP GET", fileDelCmd);

            int finalI = i;
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e("deleteFile", "GET '" + call.request().url() + "' failed!");
                    runOnUiThread(() -> Toast.makeText(StorageBrowserActivity.this, "Something went wrong. Please try again!", Toast.LENGTH_SHORT).show());
                    e.printStackTrace();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        Log.e("deleteFile", "GET '" + call.request().url() + "' unsuccessful!");
                        runOnUiThread(() -> Toast.makeText(StorageBrowserActivity.this, "Something went wrong. Please try again!", Toast.LENGTH_SHORT).show());
                    } else {
                        if (finalI == 0) {
                            goMediaFiles.remove(pos);
                            updateList();
                        }
                    }
                    response.close();
                }
            });
        }
    }

    private void updateList() {
        runOnUiThread(() -> {
            ArrayList<GoMediaFile> _goMediaFiles = new ArrayList<>(goMediaFiles);
            fileListAdapter.setNotifyOnChange(false);
            fileListAdapter.clear();
            fileListAdapter.addAll(_goMediaFiles);
            fileListAdapter.notifyDataSetChanged();
            goMediaFiles = new ArrayList<>(_goMediaFiles);
        });
    }

    private void createPlayer() {
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBufferDurationsMs(minBufferMs, 30000, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)
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

        imagePlayer.setVisibility(View.INVISIBLE);
        playerView.setVisibility(View.VISIBLE);

        playerView.requestFocus();
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

    private boolean hasExtStoragePermissions() {
        if ((Build.VERSION.SDK_INT > Build.VERSION_CODES.Q || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }

    private void requestIOPermissions() {
        String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
        ActivityCompat.requestPermissions(StorageBrowserActivity.this, permissions, 1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) {
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                int grantResult = grantResults[i];

                switch (permission) {
                    case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && grantResult != PackageManager.PERMISSION_GRANTED)
                            ActivityCompat.requestPermissions(StorageBrowserActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                        break;
                    case Manifest.permission.READ_EXTERNAL_STORAGE:
                        if (grantResult != PackageManager.PERMISSION_GRANTED)
                            ActivityCompat.requestPermissions(StorageBrowserActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
                        break;
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        destroyPlayer();
    }
}