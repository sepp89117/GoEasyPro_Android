package com.sepp89117.goeasypro_android;

import static com.sepp89117.goeasypro_android.gopro.GoProDevice.BT_NOT_CONNECTED;

import android.annotation.SuppressLint;
import android.content.Intent;
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

import com.sepp89117.goeasypro_android.gopro.GoProDevice;
import com.sepp89117.goeasypro_android.stream.GoProStreamManager;

import me.saket.cascade.CascadePopupMenu;

public class PreviewActivity extends AppCompatActivity {
    private static final String TAG = "PreviewActivity";
    private TextView textView_mode_preset;
    private ImageView rec_icon;
    private GoProDevice streamingDevice;
    private Animation fadeAnimation;

    private GoProStreamManager streamManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        init();
    }

    @Override
    protected void onResume() {
        super.onResume();

        ((MyApplication) this.getApplication()).resetIsAppPaused();
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        Log.d(TAG, "onRestart");
        //if (player != null) player.play();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy");

        if (streamManager != null) {
            streamManager.stopStream();
        }
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

        streamManager = new GoProStreamManager(this, findViewById(R.id.player_view), streamingDevice);
        streamManager.startStream();

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

}