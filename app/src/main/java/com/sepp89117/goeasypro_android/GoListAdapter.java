package com.sepp89117.goeasypro_android;

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

    private final int ORANGE = Color.argb(255, 255, 128, 0);
    private final int LIGHTBLUE = Color.argb(255, 0, 0x9F, 0xe0);

    public GoListAdapter(ArrayList<GoProDevice> goProDevices, Context context) {
        super(context, R.layout.golist_item, goProDevices);

        this.context = context;
        this.goProDevices = goProDevices;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public void onClick(View v) {
        int position = (Integer) v.getTag();
        GoProDevice goProDevice = getItem(position);

        // TODO get wifi connection
//        switch (v.getId())
//        {
//            case R.id.sd_symbol:
//
//                break;
//        }
    }

    @Override
    public int getCount() {
        // TODO Auto-generated method stub
        return goProDevices.size();
    }

    @Override
    public GoProDevice getItem(int position) {
        // TODO Auto-generated method stub
        return goProDevices.get(position);
    }

    @Override
    public long getItemId(int position) {
        // TODO Auto-generated method stub
        return position;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        View rowView = inflater.inflate(R.layout.golist_item, null, true);

        TextView name = (TextView) rowView.findViewById(R.id.name);
        TextView model = (TextView) rowView.findViewById(R.id.model);
        TextView preset = (TextView) rowView.findViewById(R.id.preset);
        TextView memory = (TextView) rowView.findViewById(R.id.memory);
        TextView battery = (TextView) rowView.findViewById(R.id.battery);
        TextView rssi = (TextView) rowView.findViewById(R.id.rssi);

        ImageView sd_symbol = (ImageView) rowView.findViewById(R.id.sd_symbol);
        ImageView bt_symbol = (ImageView) rowView.findViewById(R.id.bt_symbol);
        ImageView batt_symbol = (ImageView) rowView.findViewById(R.id.batt_symbol);


        name.setText(goProDevices.get(position).Name);


        // TODO get wifi connection
        // sd_symbol.setColorFilter(Color.RED); // not available
        // sd_symbol.setColorFilter(Color.YELLOW); // available but not connected
        // sd_symbol.setColorFilter(Color.GREEN); // connected
        sd_symbol.setColorFilter(Color.WHITE); // normal

        if (goProDevices.get(position).connected) {
            name.setTextColor(LIGHTBLUE);
            rssi.setText(String.valueOf(goProDevices.get(position).Rssi));
            battery.setText(goProDevices.get(position).Battery);
            model.setText(goProDevices.get(position).Model);
            preset.setText(goProDevices.get(position).Preset);
            memory.setText(goProDevices.get(position).Memory);

            int battPercent = Integer.parseInt(goProDevices.get(position).Battery.substring(0, goProDevices.get(position).Battery.length() - 1));

            if (battPercent > 30) {
                batt_symbol.setColorFilter(Color.GREEN);
            } else if (battPercent > 5) {
                batt_symbol.setColorFilter(ORANGE);
            } else {
                batt_symbol.setColorFilter(Color.RED);
            }

            if (goProDevices.get(position).Rssi <= -80) {
                bt_symbol.setColorFilter(ORANGE); // pour connection (RSSI <= -80)
            } else if (goProDevices.get(position).Rssi <= -70) {
                bt_symbol.setColorFilter(Color.YELLOW); // normal connection (RSSI <= -70)
            } else {
                bt_symbol.setColorFilter(Color.GREEN); // good connection (RSSI > -70)
            }
        } else {
            rssi.setText("NC");
            battery.setText("NC");
            model.setText("NC");
            preset.setText("NC");
            memory.setText("NC");

            name.setTextColor(Color.RED);
            bt_symbol.setColorFilter(Color.RED); // not connected
        }

        return rowView;
    }

    ;
}
