package com.sepp89117.goeasypro_android;

import static com.sepp89117.goeasypro_android.GoProDevice.BT_CONNECTED;
import static com.sepp89117.goeasypro_android.GoProDevice.BT_NOT_CONNECTED;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

public class GoListAdapter extends ArrayAdapter<GoProDevice> implements View.OnClickListener {
    Context context;
    private final ArrayList<GoProDevice> goProDevices;
    private static LayoutInflater inflater = null;

    private static final int ORANGE = Color.argb(255, 255, 128, 0);
    private static final int LIGHTBLUE = Color.argb(255, 0, 0x9F, 0xe0);

    public GoListAdapter(ArrayList<GoProDevice> goProDevices, Context context) {
        super(context, R.layout.golist_item, goProDevices);

        this.context = context;
        this.goProDevices = goProDevices;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public void onClick(View v) {

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

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        View rowView = inflater.inflate(R.layout.golist_item, null, true);

        GoProDevice goProDevice = goProDevices.get(position);

        TextView name = (TextView) rowView.findViewById(R.id.name);
        TextView model = (TextView) rowView.findViewById(R.id.model);
        TextView preset = (TextView) rowView.findViewById(R.id.preset);
        TextView memory = (TextView) rowView.findViewById(R.id.memory);
        TextView battery = (TextView) rowView.findViewById(R.id.battery);
        TextView rssi = (TextView) rowView.findViewById(R.id.rssi);

        ImageView sd_symbol = (ImageView) rowView.findViewById(R.id.sd_symbol);
        ImageView bt_symbol = (ImageView) rowView.findViewById(R.id.bt_symbol);
        ImageView batt_symbol = (ImageView) rowView.findViewById(R.id.batt_symbol);
        //ImageView mode_imageView = rowView.findViewById(R.id.mode_imageView);

        name.setText(goProDevice.name);

        //Mode icon select
        /*switch (goProDevice.Mode) {
            case "Video": // Video
                mode_imageView.setImageResource(R.drawable.video_mode_ico);
                break;
            case "Photo": // Photo
                mode_imageView.setImageResource(R.drawable.photo_mode_ico);
                break;
            case "Burst": // Burst
                mode_imageView.setImageResource(R.drawable.burst_mode_ico);
                break;
            case "Time lapse": // Time lapse
                mode_imageView.setImageResource(R.drawable.video_timelapse_mode_ico);
                break;
            case "?": // ?
                mode_imageView.setImageResource(R.drawable.photo_timelapse_mode_ico);
                break;

        }*/

        if (goProDevice.wifiApState > 0) {
            if (goProDevice.isWifiConnected)
                sd_symbol.setColorFilter(Color.GREEN); // AP connected
            else
                sd_symbol.setColorFilter(Color.YELLOW); // AP available but not connected
        } else {
            sd_symbol.setColorFilter(Color.RED); // AP not available
        }

        if (goProDevice.btConnectionStage == BT_CONNECTED) {
            name.setTextColor(LIGHTBLUE);
            rssi.setText(String.valueOf(goProDevice.Rssi));
            battery.setText(goProDevice.Battery);
            model.setText(goProDevice.modelName);
            preset.setText(goProDevice.Preset);
            memory.setText(goProDevice.Memory);

            String battLvl = goProDevice.Battery.substring(0, goProDevice.Battery.length() - 1);
            int battPercent = 0;
            if (!battLvl.equals(""))
                battPercent = Integer.parseInt(battLvl);

            if (battPercent > 30) {
                batt_symbol.setColorFilter(Color.GREEN);
            } else if (battPercent > 5) {
                batt_symbol.setColorFilter(ORANGE);
            } else {
                batt_symbol.setColorFilter(Color.RED);
            }

            if (goProDevice.Rssi <= -80) {
                bt_symbol.setColorFilter(ORANGE); // pour connection (RSSI <= -80)
            } else if (goProDevice.Rssi <= -70) {
                bt_symbol.setColorFilter(Color.YELLOW); // normal connection (RSSI <= -70)
            } else {
                bt_symbol.setColorFilter(Color.GREEN); // good connection (RSSI > -70)
            }
        } else {
            rssi.setText("NC");
            battery.setText("NC");
            model.setText(goProDevice.modelName);
            preset.setText("NC");
            memory.setText("NC");

            if (goProDevice.btConnectionStage == BT_NOT_CONNECTED) {
                name.setTextColor(Color.RED);
                bt_symbol.setColorFilter(Color.RED);
            } else {
                name.setTextColor(ORANGE);
                bt_symbol.setColorFilter(ORANGE);
            }
        }

        return rowView;
    }
}
