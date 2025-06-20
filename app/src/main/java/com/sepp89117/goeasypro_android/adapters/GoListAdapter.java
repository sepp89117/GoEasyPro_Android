package com.sepp89117.goeasypro_android.adapters;

import static com.sepp89117.goeasypro_android.gopro.GoProDevice.BT_CONNECTED;
import static com.sepp89117.goeasypro_android.gopro.GoProDevice.BT_NOT_CONNECTED;
import static com.sepp89117.goeasypro_android.gopro.LiveStreaming.EnumLiveStreamStatus.LIVE_STREAM_STATE_COMPLETE_STAY_ON;
import static com.sepp89117.goeasypro_android.gopro.LiveStreaming.EnumLiveStreamStatus.LIVE_STREAM_STATE_CONFIG;
import static com.sepp89117.goeasypro_android.gopro.LiveStreaming.EnumLiveStreamStatus.LIVE_STREAM_STATE_FAILED_STAY_ON;
import static com.sepp89117.goeasypro_android.gopro.LiveStreaming.EnumLiveStreamStatus.LIVE_STREAM_STATE_IDLE;
import static com.sepp89117.goeasypro_android.gopro.LiveStreaming.EnumLiveStreamStatus.LIVE_STREAM_STATE_READY;
import static com.sepp89117.goeasypro_android.gopro.LiveStreaming.EnumLiveStreamStatus.LIVE_STREAM_STATE_RECONNECTING;
import static com.sepp89117.goeasypro_android.gopro.LiveStreaming.EnumLiveStreamStatus.LIVE_STREAM_STATE_STREAMING;
import static com.sepp89117.goeasypro_android.gopro.LiveStreaming.EnumLiveStreamStatus.LIVE_STREAM_STATE_UNAVAILABLE;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.sepp89117.goeasypro_android.R;
import com.sepp89117.goeasypro_android.gopro.GoProDevice;

import java.util.ArrayList;
import java.util.Locale;

public class GoListAdapter extends ArrayAdapter<GoProDevice> {
    Context context;
    private final ArrayList<GoProDevice> goProDevices;
    private static LayoutInflater inflater = null;

    private static final int ORANGE = Color.argb(255, 255, 128, 0);
    private static final int LIGHT_BLUE = Color.argb(255, 0, 0x9F, 0xe0);
    private final Animation fadeAnimation;

    public GoListAdapter(ArrayList<GoProDevice> goProDevices, Context context) {
        super(context, R.layout.golist_item, goProDevices);

        this.context = context;
        this.goProDevices = goProDevices;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        fadeAnimation = AnimationUtils.loadAnimation(context, R.anim.tween);
    }

    @Override
    public int getCount() {
        return goProDevices.size();
    }

    @Override
    public GoProDevice getItem(int position) {
        return goProDevices.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private static class ViewHolder {
        TextView name;
        TextView model;
        TextView mode;
        TextView preset;
        TextView memory;
        TextView battery;
        TextView rssi;

        ImageView sd_symbol;
        ImageView bt_symbol;
        ImageView batt_symbol;
        ImageView shutter_symbol;
        ImageView hot_view;
        ImageView cold_view;
        ImageView mode_symbol;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder mViewHolder;

        if (convertView == null) {
            mViewHolder = new ViewHolder();
            convertView = inflater.inflate(R.layout.golist_item, parent, false);

            mViewHolder.name = convertView.findViewById(R.id.name);
            mViewHolder.model = convertView.findViewById(R.id.model);
            mViewHolder.mode = convertView.findViewById(R.id.flat_mode);
            mViewHolder.preset = convertView.findViewById(R.id.preset);
            mViewHolder.memory = convertView.findViewById(R.id.memory);
            mViewHolder.battery = convertView.findViewById(R.id.battery);
            mViewHolder.rssi = convertView.findViewById(R.id.rssi);

            mViewHolder.sd_symbol = convertView.findViewById(R.id.sd_symbol);
            mViewHolder.bt_symbol = convertView.findViewById(R.id.bt_symbol);
            mViewHolder.batt_symbol = convertView.findViewById(R.id.batt_symbol);
            mViewHolder.shutter_symbol = convertView.findViewById(R.id.shutter_symbol);
            mViewHolder.hot_view = convertView.findViewById(R.id.hot_view);
            mViewHolder.cold_view = convertView.findViewById(R.id.cold_view);
            mViewHolder.mode_symbol = convertView.findViewById(R.id.mode_symbol);

            convertView.setTag(mViewHolder);
        } else {
            mViewHolder = (ViewHolder) convertView.getTag();
        }

        GoProDevice goProDevice = goProDevices.get(position);

        mViewHolder.name.setText(goProDevice.displayName);

        if (goProDevice.wifiApState > 0) {
            if (goProDevice.isWifiConnected())
                mViewHolder.sd_symbol.setColorFilter(Color.GREEN); // AP connected
            else
                mViewHolder.sd_symbol.setColorFilter(Color.YELLOW); // AP available but not connected
        } else {
            mViewHolder.sd_symbol.setColorFilter(Color.RED); // AP not available
        }

        if (goProDevice.isRecording) {
            mViewHolder.shutter_symbol.startAnimation(fadeAnimation);
            mViewHolder.shutter_symbol.setVisibility(View.VISIBLE);
        } else {
            mViewHolder.shutter_symbol.clearAnimation();
            mViewHolder.shutter_symbol.setVisibility(View.INVISIBLE);
        }

        // Visualize live stream status
        String modeTitle = "";
        // If Livestream status is not idle 'modeTitle' must contain "Live"
        if (goProDevice.liveStreamStatus.getLiveStreamStatus() == LIVE_STREAM_STATE_IDLE) {
            // Initial status. Livestream has not yet been configured
            // ignore
        } else if (goProDevice.liveStreamStatus.getLiveStreamStatus() == LIVE_STREAM_STATE_CONFIG) {
            // Livestream is being configured
            modeTitle = "Livestream configured";
        } else if (goProDevice.liveStreamStatus.getLiveStreamStatus() == LIVE_STREAM_STATE_READY) {
            // Livestream has finished configuration and is ready to start streaming
            modeTitle = "Livestream ready";
        } else if (goProDevice.liveStreamStatus.getLiveStreamStatus() == LIVE_STREAM_STATE_STREAMING) {
            // Livestream is actively streaming
            modeTitle = "Livestream active";
        } else if (goProDevice.liveStreamStatus.getLiveStreamStatus() == LIVE_STREAM_STATE_COMPLETE_STAY_ON) {
            // Live stream is exiting. No errors occurred.
            modeTitle = "Livestream exiting";
        } else if (goProDevice.liveStreamStatus.getLiveStreamStatus() == LIVE_STREAM_STATE_FAILED_STAY_ON) {
            // Live stream is exiting. An error occurred.
            modeTitle = "Livestream error exiting";
        } else if (goProDevice.liveStreamStatus.getLiveStreamStatus() == LIVE_STREAM_STATE_RECONNECTING) {
            // An error occurred during livestream and stream is attempting to reconnect.
            modeTitle = "Livestream reconnect";
        } else if (goProDevice.liveStreamStatus.getLiveStreamStatus() == LIVE_STREAM_STATE_UNAVAILABLE) {
            // Live stream setup is unavailable due to camera lens configuration
            modeTitle = "Livestream unavailable";
        }

        if (goProDevice.isCold) {
            mViewHolder.cold_view.setVisibility(View.VISIBLE);
        } else {
            mViewHolder.cold_view.setVisibility(View.INVISIBLE);
        }

        if (goProDevice.isHot) {
            mViewHolder.hot_view.setVisibility(View.VISIBLE);
        } else {
            mViewHolder.hot_view.setVisibility(View.INVISIBLE);
        }

        if (goProDevice.isCharging) {
            mViewHolder.batt_symbol.setImageResource(R.drawable.battery_charging_symbol);
        } else {
            mViewHolder.batt_symbol.setImageResource(R.drawable.battery_std_symbol);
        }

        mViewHolder.model.setText(goProDevice.modelName);

        if (goProDevice.btConnectionStage == BT_CONNECTED) {
            mViewHolder.name.setTextColor(LIGHT_BLUE);
            mViewHolder.rssi.setText(String.valueOf(goProDevice.btRssi));
            Locale locale = context.getResources().getConfiguration().getLocales().get(0);
            mViewHolder.battery.setText(String.format(locale, "%d%%", goProDevice.remainingBatteryPercent));
            try {
                mViewHolder.preset.setText(goProDevice.getCurrentSettingsString());
            } catch (Exception ignore) {

            }

            if (modeTitle.isEmpty()) {
                if (goProDevice.hasProtoPresets() && goProDevice.protoPreset != null) {
                    modeTitle = goProDevice.protoPreset.getModeTitle();
                    mViewHolder.mode.setText(goProDevice.protoPreset.getPresetTitle());
                } else {
                    modeTitle = goProDevice.mode.getTitle();
                    mViewHolder.mode.setText(modeTitle);
                }
            }

            mViewHolder.mode_symbol.setVisibility(View.VISIBLE);

            if (modeTitle.contains("Video")) {
                mViewHolder.mode_symbol.setImageResource(R.drawable.mode_video_symbol);
            } else if (modeTitle.contains("Photo")) {
                mViewHolder.mode_symbol.setImageResource(R.drawable.mode_photo_symbol);
            } else if (modeTitle.contains("Lapse")) {
                mViewHolder.mode_symbol.setImageResource(R.drawable.mode_timelapse_symbol);
            } else if (modeTitle.contains("Multi")) {
                mViewHolder.mode_symbol.setImageResource(R.drawable.mode_multishot_symbol);
            } else if (modeTitle.contains("Live")) {
                mViewHolder.mode.setText(modeTitle);
                mViewHolder.mode_symbol.setImageResource(R.drawable.live_symbol);
            } else {
                // Unknown Mode
                mViewHolder.mode_symbol.setVisibility(View.GONE);
            }

            mViewHolder.memory.setText(goProDevice.remainingMemory);

            if (goProDevice.remainingBatteryPercent > 30) {
                mViewHolder.batt_symbol.setColorFilter(Color.GREEN);
            } else if (goProDevice.remainingBatteryPercent > 5) {
                mViewHolder.batt_symbol.setColorFilter(ORANGE);
            } else {
                mViewHolder.batt_symbol.setColorFilter(Color.RED);
            }

            if (goProDevice.btRssi <= -80) {
                mViewHolder.bt_symbol.setColorFilter(ORANGE); // pour connection (RSSI <= -80)
            } else if (goProDevice.btRssi <= -70) {
                mViewHolder.bt_symbol.setColorFilter(Color.YELLOW); // normal connection (RSSI <= -70)
            } else {
                mViewHolder.bt_symbol.setColorFilter(Color.GREEN); // good connection (RSSI > -70)
            }
        } else {
            mViewHolder.preset.setText("");
            mViewHolder.memory.setText(R.string.str_NC);
            mViewHolder.battery.setText(R.string.str_NC);
            mViewHolder.rssi.setText(R.string.str_NC);

            if (goProDevice.btConnectionStage == BT_NOT_CONNECTED) {
                mViewHolder.mode.setText(R.string.str_not_connected);
                mViewHolder.name.setTextColor(Color.RED);
                mViewHolder.batt_symbol.setColorFilter(Color.RED);
                if (goProDevice.camBtAvailable) {
                    mViewHolder.bt_symbol.setColorFilter(Color.YELLOW);
                } else {
                    mViewHolder.bt_symbol.setColorFilter(Color.RED);
                }
            } else { // BT_CONNECTING || BT_FETCHING_DATA
                mViewHolder.mode.setText(R.string.str_connecting);
                mViewHolder.name.setTextColor(ORANGE);
                mViewHolder.bt_symbol.setColorFilter(ORANGE);
                mViewHolder.batt_symbol.setColorFilter(ORANGE);
            }
        }

        return convertView;
    }
}
