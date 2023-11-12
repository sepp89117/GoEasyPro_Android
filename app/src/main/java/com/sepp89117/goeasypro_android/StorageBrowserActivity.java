package com.sepp89117.goeasypro_android;

import static com.sepp89117.goeasypro_android.gopro.GoProDevice.BT_NOT_CONNECTED;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;

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
import com.sepp89117.goeasypro_android.adapters.FileListAdapter;
import com.sepp89117.goeasypro_android.gopro.GoMediaFile;
import com.sepp89117.goeasypro_android.gopro.GoProDevice;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Collectors;

import me.saket.cascade.CascadePopupMenu;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

public class StorageBrowserActivity extends AppCompatActivity {
    private static final int LIGHT_BLUE = Color.argb(255, 0, 0x9F, 0xe0);
    private static final int GREY = Color.argb(255, 0x50, 0x50, 0x50);

    private static final String MY_DIR = "/GoEasyPro";

    private final File baseDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() + MY_DIR);
    private GoProDevice focusedDevice = null;
    private ArrayList<GoMediaFile> goMediaFiles;
    private ListView fileListView;
    private StyledPlayerView playerView = null;
    private ImageView imagePlayer;
    private ExoPlayer player = null;
    private OkHttpClient client = new OkHttpClient();
    private FileListAdapter fileListAdapter;
    private GoMediaFile clickedFile = null;
    private ProgressBar progressBar = null;
    private ArrayList<okhttp3.Call> calls = null;
    private AlertDialog dlDialog = null;
    private int dlTotalCount;
    private int dlCompleteCount;

    private boolean isFullscreen = false;
    private Dialog fullScreenDialog;
    private StyledPlayerView fullscreenPlayerView = null;
    private ImageView fullscreenImagePlayer = null;
    private ImageView dlMenuBtn = null;
    private ImageView delBtn = null;
    private int currentOrientation;
    private FrameLayout mediaFrame;
    private Bitmap currentBitmap = null;
    private Date lastImgClick = new Date();

    private int minBufferMs = 12500;
    private int bufferForPlaybackMs = 10000;
    private int bufferForPlaybackAfterRebufferMs = 10000;

    //region Activity lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_storage_browser);

        focusedDevice = ((MyApplication) this.getApplication()).getFocusedDevice();
        goMediaFiles = ((MyApplication) this.getApplication()).getGoMediaFiles();

        focusedDevice.getDataChanges(() -> runOnUiThread(() -> {
            if (focusedDevice.btConnectionStage == BT_NOT_CONNECTED) finish();
        }));

        for (GoMediaFile file : goMediaFiles) {
            String fileName = focusedDevice.btDeviceName + "_" + file.fileName;
            File testFile = new File(baseDir, fileName);
            file.alreadyDownloaded = baseDir.exists() && testFile.exists();
        }

        client = new OkHttpClient.Builder().addNetworkInterceptor(chain -> {
            Response originalResponse = chain.proceed(chain.request());
            return originalResponse.newBuilder().body(new ProgressResponseBody(originalResponse.body(), progressListener)).build();
        }).build();

        getThumbNailsAsync();

        TextView selFile_textView = findViewById(R.id.textView3);
        selFile_textView.setText(String.format(getResources().getString(R.string.str_Content_from), focusedDevice.displayName));

        mediaFrame = findViewById(R.id.main_media_frame);
        playerView = findViewById(R.id.vid_player_view);
        imagePlayer = findViewById(R.id.imagePlayer);
        dlMenuBtn = findViewById(R.id.dl_menu_btn);
        delBtn = findViewById(R.id.del_btn);

        fileListAdapter = new FileListAdapter(goMediaFiles, this);
        fileListAdapter.setOnItemCheckListener(checkedItems -> {
            if (checkedItems.size() > 0) {
                // show download menu
                dlMenuBtn.setColorFilter(LIGHT_BLUE);
                delBtn.setColorFilter(LIGHT_BLUE);
            } else {
                // hide download menu
                dlMenuBtn.setColorFilter(GREY);
                delBtn.setColorFilter(GREY);
            }
        });

        fileListView = findViewById(R.id.file_listView);
        registerForContextMenu(fileListView);
        fileListView.setOnItemClickListener((parent, view, position, id) -> {
            if (clickedFile == goMediaFiles.get(position)) return;

            clickedFile = goMediaFiles.get(position);

            destroyPlayer();

            if (Objects.equals(clickedFile.extension, ".mp4")) {
                String videoUrl;
                if (clickedFile.hasLrv) {
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

                // Set thumbnail as image resource until full image is loaded
                if (clickedFile.thumbNail != null) {
                    currentBitmap = clickedFile.thumbNail;
                    runOnUiThread(() -> imagePlayer.setImageBitmap(currentBitmap));
                }

                Request request = new Request.Builder().url(clickedFile.url).build();
                Log.d("HTTP GET", clickedFile.url);

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e("getImage", "fail");
                        imagePlayer.setImageResource(R.drawable.ic_baseline_no_photography_24);
                        e.printStackTrace();
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        if (!response.isSuccessful()) {
                            Log.e("getImage", "Request response = not success");
                            imagePlayer.setImageResource(R.drawable.ic_baseline_no_photography_24);
                        } else {
                            try {
                                Bitmap bmp = BitmapFactory.decodeStream(response.body().byteStream());
                                if (bmp != null) {
                                    currentBitmap = bmp;
                                    runOnUiThread(() -> imagePlayer.setImageBitmap(currentBitmap));
                                }
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

        // fullscreen
        fullscreenPlayerView = new StyledPlayerView(this);
        fullscreenImagePlayer = new ImageView(this);
        fullscreenImagePlayer.setOnClickListener(this::onImgClick);
        fullScreenDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        fullscreenImagePlayer.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        currentOrientation = this.getResources().getConfiguration().orientation;
        handleOrientation();
    }

    @Override
    protected void onResume() {
        super.onResume();

        ((MyApplication) this.getApplication()).resetIsAppPaused();
    }

    public void onDlMenuClick(View v) {
        CascadePopupMenu popupMenu = new CascadePopupMenu(StorageBrowserActivity.this, v, Gravity.BOTTOM | Gravity.START);
        popupMenu.setOnMenuItemClickListener(dlMenuItemClickListener);
        popupMenu.inflate(R.menu.dl_menu);
        popupMenu.show();
    }

    public void onDelBtnClick(View v) {
        ArrayList<GoMediaFile> selectedFiles = fileListAdapter.getSelectedItems();
        ArrayList<Integer> posList = new ArrayList<>();
        for (GoMediaFile file : selectedFiles) {
            posList.add(goMediaFiles.indexOf(file));
        }
        posList.sort(Collections.reverseOrder());

        AlertDialog alert = new AlertDialog.Builder(StorageBrowserActivity.this).setTitle(getResources().getString(R.string.str_Delete_file)).setMessage(String.format(getResources().getString(R.string.str_sure_delete), selectedFiles.size() + getResources().getString(R.string._files))).setPositiveButton(getResources().getString(R.string.str_Yes), (dialog, which) -> {
            for (int pos : posList) {
                deleteFile(pos);
            }
        }).setNegativeButton(getResources().getString(R.string.str_Cancel), null).create();
        alert.show();
    }

    @SuppressLint("NonConstantResourceId")
    private final PopupMenu.OnMenuItemClickListener dlMenuItemClickListener = item -> {
        if (item.hasSubMenu()) return true;

        switch (item.getItemId()) {
            case R.id.dl_menu_dl:
                if (player != null && isPlaying()) player.stop();

                // download selected files
                isLrvDownload = false;
                ArrayList<GoMediaFile> selectedFiles = fileListAdapter.getSelectedItems();

                downloadFiles(false, selectedFiles);
                break;
            case R.id.dl_menu_dl_lrv:
                if (player != null && isPlaying()) player.stop();

                // download selected LRV files
                isLrvDownload = true;
                ArrayList<GoMediaFile> selectedLrvFiles = fileListAdapter.getSelectedItems();

                downloadFiles(true, selectedLrvFiles);
                break;
        }

        return true;
    };

    @Override
    protected void onPause() {
        super.onPause();

        destroyPlayer();

        if (fullScreenDialog != null) fullScreenDialog.dismiss();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
    //endregion

    //region Fullscreen implementation
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        currentOrientation = newConfig.orientation;
        handleOrientation();
    }

    private void handleOrientation() {
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            mediaFrame.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 2.0f));

            if (isPlaying() && !isFullscreen) {
                enableFullscreen();
            }
        } else if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            mediaFrame.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

            if (isPlaying() && isFullscreen) {
                disableFullscreen();
            }
        }
    }

    private void enableFullscreen() {
        isFullscreen = true;

        if (fullscreenImagePlayer.getParent() != null)
            ((ViewGroup) fullscreenImagePlayer.getParent()).removeAllViews();
        else if (fullscreenPlayerView.getParent() != null)
            ((ViewGroup) fullscreenPlayerView.getParent()).removeAllViews();

        if (imagePlayer.getVisibility() == View.VISIBLE) {
            fullscreenImagePlayer.setImageBitmap(currentBitmap);
            fullScreenDialog.addContentView(fullscreenImagePlayer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            StyledPlayerView.switchTargetView(player, playerView, fullscreenPlayerView);
            fullScreenDialog.addContentView(fullscreenPlayerView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        fullScreenDialog.show();
    }

    private void disableFullscreen() {
        isFullscreen = false;
        StyledPlayerView.switchTargetView(player, fullscreenPlayerView, playerView);
        fullScreenDialog.dismiss();
    }

    public void onImgClick(View v) {
        Date now = new Date();
        if ((now.getTime() - lastImgClick.getTime()) < 200) {
            if (!isFullscreen) enableFullscreen();
            else disableFullscreen();
        }
        lastImgClick = new Date();
    }

    private boolean isPlaying() {
        return (player != null && (player.isPlaying() || player.isLoading())) | imagePlayer.getVisibility() == View.VISIBLE;
    }
    //endregion

    //region ContextMenu
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        int pos = info.position;

        menu.setHeaderTitle(getResources().getString(R.string.str_File_options));
        // add menu items
        menu.add(pos, 1, 0, getResources().getString(R.string.str_Download));
        if (goMediaFiles.get(pos).hasLrv) {
            menu.add(pos, 2, 0, getResources().getString(R.string.str_Download_LRV));
        }
        if (fileListAdapter.getSelectedItems().size() > 0) {
            menu.add(pos, 3, 0, getResources().getString(R.string.str_dl_selection));
            menu.add(pos, 4, 0, getResources().getString(R.string.str_dl_selection_lrv));
        }
        menu.add(pos, 0, 0, getResources().getString(R.string.str_Delete));
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int pos = item.getGroupId();

        switch (item.getItemId()) {
            case 0:
                // delete file
                AlertDialog alert = new AlertDialog.Builder(StorageBrowserActivity.this).setTitle(getResources().getString(R.string.str_Delete_file)).setMessage(String.format(getResources().getString(R.string.str_sure_delete), goMediaFiles.get(pos).fileName)).setPositiveButton(getResources().getString(R.string.str_Yes), (dialog, which) -> deleteFile(pos)).create();
                alert.show();
                break;
            case 1:
                if (player != null && isPlaying()) player.stop();

                // download file
                isLrvDownload = false;
                ArrayList<GoMediaFile> files = new ArrayList<>();
                files.add(goMediaFiles.get(pos));
                downloadFiles(false, files);
                break;
            case 2:
                if (player != null && isPlaying()) player.stop();

                // download LRV
                isLrvDownload = true;
                ArrayList<GoMediaFile> lrvFiles = new ArrayList<>();
                lrvFiles.add(goMediaFiles.get(pos));
                downloadFiles(true, lrvFiles);
                break;
            case 3:
                if (player != null && isPlaying()) player.stop();

                // download selected files
                isLrvDownload = false;
                ArrayList<GoMediaFile> selectedFiles = fileListAdapter.getSelectedItems();

                downloadFiles(false, selectedFiles);
                break;
            case 4:
                if (player != null && isPlaying()) player.stop();

                // download selected LRV files
                isLrvDownload = true;
                ArrayList<GoMediaFile> selectedLrvFiles = fileListAdapter.getSelectedItems();

                downloadFiles(true, selectedLrvFiles);
                break;
        }

        return true;
    }
    //endregion

    //region Media functions
    private void getThumbNailsAsync() {
        new Thread(() -> {
            for (int i = 0; i < goMediaFiles.size(); i++) {
                GoMediaFile file = goMediaFiles.get(i);
                String tn_url = file.thumbNail_path;

                Request request = new Request.Builder().url(tn_url).build();
                Log.d("HTTP GET", tn_url);

                final int finalI = i;
                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e("getThumbNailsAsync", "GET '" + call.request().url() + "' failed!");
                        e.printStackTrace();

                        if (finalI == goMediaFiles.size() - 1)
                            runOnUiThread(() -> fileListView.setAdapter(fileListAdapter));
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        if (!response.isSuccessful()) {
                            Log.e("getThumbNailsAsync", "GET '" + call.request().url() + "' unsuccessful!");
                        } else {
                            file.thumbNail = BitmapFactory.decodeStream(response.body().byteStream());
                        }

                        response.close();

                        if (finalI == goMediaFiles.size() - 1)
                            runOnUiThread(() -> fileListView.setAdapter(fileListAdapter));
                    }
                });
            }
        }).start();
    }

    private AlertDialog buildDownloadDialog() {
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        progressBar.setLayoutParams(lp);
        int pxFromDp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 25, getResources().getDisplayMetrics());
        progressBar.setPadding(pxFromDp, 0, pxFromDp, 0);

        return new AlertDialog.Builder(StorageBrowserActivity.this).setTitle(getResources().getString(R.string.str_Downloading)).setMessage(getResources().getString(R.string.str_wait_dl_complete)).setView(progressBar).setNegativeButton(getResources().getString(R.string.str_Cancel), (dialog, which) -> cancelDownload()).setCancelable(false).create();
    }

    private static final class DlInfo {
        public String Url;
        public String FileName;
        public String MimeType;
        public long FileSize;

        public DlInfo(String url, String fileName, String mimeType, long fileSize) {
            Url = url;
            FileName = fileName;
            MimeType = mimeType;
            FileSize = fileSize;
        }
    }

    private long multiDlSize = 0;
    private long multiDlDoneSize = 0;
    private boolean isLrvDownload = false;

    private void downloadFiles(boolean lrv, ArrayList<GoMediaFile> files) {
        if (!hasExtStoragePermissions()) {
            Toast.makeText(StorageBrowserActivity.this, getResources().getString(R.string.str_need_permissions), Toast.LENGTH_SHORT).show();
            requestIOPermissions();
            return;
        }

        focusedDevice.requestSetTurboActive(true);

        ArrayList<DlInfo> dlInfos = files.stream().flatMap(file -> getFileDlInfo(lrv, file).stream()).collect(Collectors.toCollection(ArrayList::new));
        dlTotalCount = dlInfos.size();
        multiDlSize = lrv ? dlTotalCount : dlInfos.stream().mapToLong(dlInfo -> dlInfo.FileSize).sum();
        dlCompleteCount = 0;
        multiDlDoneSize = 0;

        dlDialog = buildDownloadDialog();
        dlDialog.show();

        new Thread(() -> {
            calls = new ArrayList<>();
            for (DlInfo dlInfo : dlInfos) {
                Request request = new Request.Builder().url(dlInfo.Url).build();
                Log.d("HTTP GET", dlInfo.Url);
                final Call currentCall = client.newCall(request);
                synchronized (calls) {
                    calls.add(currentCall);
                }
            }
            executeCalls(dlInfos);
        }).start();
    }

    private void executeCalls(ArrayList<DlInfo> dlInfos) {
        Call currentCall = null;
        int callsLen = calls.size();

        for (int i = 0; i < callsLen; i++) {
            try {
                synchronized (calls) {
                    currentCall = calls.get(i);
                }
            } catch (Exception ignored) {
            }

            DlInfo dlInfo = dlInfos.get(i);

            if (currentCall != null) {
                try {
                    Response response = currentCall.execute();

                    if (!response.isSuccessful()) {
                        Log.e("downloadFile", "GET '" + currentCall.request().url() + "' unsuccessful!");
                    } else {
                        InputStream inputStream = response.body().byteStream();
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            if (!baseDir.exists() && !baseDir.mkdir()) {
                                Log.e("dlFile", "Could not create directory '" + baseDir.getPath() + "'!");
                                continue;
                            }
                        }

                        int fileIndex = 1;
                        String localFilename = focusedDevice.btDeviceName + "_" + dlInfo.FileName;
                        File file = new File(baseDir, localFilename);
                        while (file.exists()) {
                            if (fileIndex >= 10000) {
                                Log.e("dlFile", "There are more then 10000 files withe the same name in the directory '" + baseDir.getPath() + "'!");
                                return;
                            }
                            file = new File(baseDir, focusedDevice.btDeviceName + "_" + dlInfo.FileName.substring(0, dlInfo.FileName.length() - 4) + "(" + fileIndex + ")" + dlInfo.FileName.substring(dlInfo.FileName.length() - 4));
                            fileIndex++;
                        }

                        OutputStream outputStream;
                        String fileName = file.getName();

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (dlInfo.MimeType == null) {
                                Log.e("dlFile", "Mime type for file '" + fileName + "' unknown!");
                                continue;
                            }

                            ContentResolver contentResolver = StorageBrowserActivity.this.getContentResolver();
                            ContentValues values = new ContentValues();
                            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                            values.put(MediaStore.MediaColumns.MIME_TYPE, dlInfo.MimeType);
                            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + MY_DIR);

                            Uri fileUri = null;

                            if (dlInfo.MimeType.contains("image")) {
                                fileUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                            } else if (dlInfo.MimeType.contains("video")) {
                                fileUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                            }

                            if (fileUri == null) {
                                Log.e("dlFile", "File uri for file '" + fileName + "' unknown!");
                                continue;
                            }

                            outputStream = contentResolver.openOutputStream(fileUri);
                        } else {
                            outputStream = new FileOutputStream(file);
                        }

                        try {
                            byte[] buffer = new byte[8 * 1024];
                            int read;

                            while ((read = inputStream.read(buffer)) != -1)
                                outputStream.write(buffer, 0, read);

                            outputStream.flush();
                            outputStream.close();

                            dlCompleteCount++;
                            multiDlDoneSize += dlInfo.FileSize;
                            if (isLrvDownload)
                                progressBar.setProgress((int) ((float) (dlCompleteCount / dlTotalCount) * 100.0f));

                            if (dlCompleteCount == dlTotalCount) {
                                progressBar.setProgress(0);
                                dlDialog.dismiss();
                                runOnUiThread(() -> Toast.makeText(StorageBrowserActivity.this, getResources().getString(R.string.str_dl_completed), Toast.LENGTH_SHORT).show());
                                updateList();
                                focusedDevice.requestSetTurboActive(false);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            if (file.delete())
                                runOnUiThread(() -> Toast.makeText(StorageBrowserActivity.this, String.format(getResources().getString(R.string.str_incompletely_file_deleted), fileName), Toast.LENGTH_SHORT).show());
                        } finally {
                            inputStream.close();
                        }
                    }
                    response.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("dlFile", "File '" + dlInfo.FileName + "' download error!");
                }
            }
        }
    }

    private ArrayList<DlInfo> getFileDlInfo(boolean lrv, GoMediaFile file) {
        ArrayList<DlInfo> dlInfos = new ArrayList<>();

        String dlUrl = lrv ? file.lrvUrl : file.url;

        if (file.isGroup) {
            String fileName = file.fileName;
            String dlUrlI = dlUrl;

            int start = Integer.parseInt(file.groupBegin);
            int end = Integer.parseInt(file.groupLast);

            String lastFileName;

            dlInfos.add(new DlInfo(dlUrlI, file.fileName, file.mimeType, lrv ? 1 : file.fileByteSize));

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
                dlUrlI = dlUrlI.replace(lastFileName, fileName);

                dlInfos.add(new DlInfo(dlUrlI, file.fileName, file.mimeType, lrv ? 1 : file.fileByteSize));
            }
        } else {
            dlInfos.add(new DlInfo(dlUrl, file.fileName, file.mimeType, lrv ? 1 : file.fileByteSize));
        }

        return dlInfos;
    }

    private void cancelDownload() {
        synchronized (calls) {
            for (Iterator<Call> iterator = calls.iterator(); iterator.hasNext(); ) {
                Call call = iterator.next();
                if (call != null) call.cancel();

                iterator.remove();
            }
        }

        if (dlDialog != null) dlDialog.dismiss();

        updateList();
        focusedDevice.requestSetTurboActive(false);
    }

    final ProgressListener progressListener = (bytesRead, contentLength, done) -> {
        if (progressBar != null && !isLrvDownload) {
            float percentDone = ((float) (multiDlDoneSize + bytesRead) / multiDlSize * 100);
            progressBar.setProgress((int) percentDone);
        }
    };

    private void deleteFile(int pos) {
        if (isPlaying()) player.stop();

        final GoMediaFile goMediaFile = goMediaFiles.get(pos);

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

            String lastFileName;

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

            Request request = new Request.Builder().url(fileDelCmd).build();

            final int finalI = i;
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e("deleteFile", "GET '" + call.request().url() + "' failed!");
                    runOnUiThread(() -> Toast.makeText(StorageBrowserActivity.this, getResources().getString(R.string.str_something_wrong), Toast.LENGTH_SHORT).show());
                    e.printStackTrace();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    if (!response.isSuccessful()) {
                        Log.e("deleteFile", "GET '" + call.request().url() + "' unsuccessful!");
                        runOnUiThread(() -> Toast.makeText(StorageBrowserActivity.this, getResources().getString(R.string.str_something_wrong), Toast.LENGTH_SHORT).show());
                    } else {
                        if (finalI == 0) {
                            goMediaFiles.remove(goMediaFile);
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

            for (GoMediaFile file : _goMediaFiles) {
                String fileName = focusedDevice.btDeviceName + "_" + file.fileName;
                File testFile = new File(baseDir, fileName);
                file.alreadyDownloaded = baseDir.exists() && testFile.exists();
            }

            fileListAdapter.setNotifyOnChange(false);
            fileListAdapter.clear();
            fileListAdapter.addAll(_goMediaFiles);
            fileListAdapter.notifyDataSetChanged();
            goMediaFiles = new ArrayList<>(_goMediaFiles);
        });
    }
    //endregion

    //region okHttp
    private static class ProgressResponseBody extends ResponseBody {

        private final ResponseBody responseBody;
        private final ProgressListener progressListener;
        private BufferedSource bufferedSource;

        ProgressResponseBody(ResponseBody responseBody, ProgressListener progressListener) {
            this.responseBody = responseBody;
            this.progressListener = progressListener;
        }

        @Override
        public MediaType contentType() {
            return responseBody.contentType();
        }

        @Override
        public long contentLength() {
            return responseBody.contentLength();
        }

        @NonNull
        @Override
        public BufferedSource source() {
            if (bufferedSource == null) {
                bufferedSource = Okio.buffer(source(responseBody.source()));
            }
            return bufferedSource;
        }

        private Source source(Source source) {
            return new ForwardingSource(source) {
                long totalBytesRead = 0L;

                @Override
                public long read(@NonNull Buffer sink, long byteCount) throws IOException {
                    long bytesRead = super.read(sink, byteCount);
                    totalBytesRead += bytesRead != -1 ? bytesRead : 0;
                    progressListener.update(totalBytesRead, responseBody.contentLength(), bytesRead == -1);
                    return bytesRead;
                }
            };
        }
    }

    interface ProgressListener {
        void update(long bytesRead, long contentLength, boolean done);
    }
    //endregion

    //region Player
    private void createPlayer() {
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder().setPrioritizeTimeOverSizeThresholds(true).setBufferDurationsMs(minBufferMs, 30000, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs).build();

        TrackSelector trackSelector = new DefaultTrackSelector(this);

        player = new ExoPlayer.Builder(this).setTrackSelector(trackSelector).setLoadControl(loadControl).build();

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
                case Player.STATE_READY:
                    break;
                case Player.STATE_BUFFERING:
                    if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE && !isFullscreen) {
                        enableFullscreen();
                    } else if (currentOrientation == Configuration.ORIENTATION_PORTRAIT && isFullscreen) {
                        disableFullscreen();
                    }
                    break;
                case Player.STATE_ENDED:
                    if (isFullscreen) disableFullscreen();
                    break;
            }
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            Player.Listener.super.onPlayerError(error);
            Log.e("onPlayerError", error.getMessage() + "\n" + error.getCause());
        }
    };

    private void destroyPlayer() {
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
    }
    //endregion

    //region Permissions
    private boolean hasExtStoragePermissions() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) return true;

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q)
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

        return (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
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
    //endregion
}