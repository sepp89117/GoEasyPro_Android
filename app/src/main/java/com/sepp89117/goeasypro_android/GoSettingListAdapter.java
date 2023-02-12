package com.sepp89117.goeasypro_android;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class GoSettingListAdapter extends ArrayAdapter<GoSetting> {
    Context context;
    private final ArrayList<GoSetting> goSettings;
    private static LayoutInflater inflater = null;

    public GoSettingListAdapter(ArrayList<GoSetting> goSettings, Context context) {
        super(context, R.layout.gosettingslist_item, goSettings);

        this.context = context;
        this.goSettings = goSettings;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        //TODO use ViewHolder mViewHolder;

        View rowView = inflater.inflate(R.layout.gosettingslist_item, parent, false);

        GoSetting goSetting = goSettings.get(position);

        String settingName = goSetting.getSettingName();
        String currentOptionName = goSetting.getCurrentOptionName();

        TextView name = rowView.findViewById(R.id.setting_name_text);
        TextView option = rowView.findViewById(R.id.current_option_name_text);

        name.setText(settingName);
        option.setText(currentOptionName);

        return rowView;
    }
}
