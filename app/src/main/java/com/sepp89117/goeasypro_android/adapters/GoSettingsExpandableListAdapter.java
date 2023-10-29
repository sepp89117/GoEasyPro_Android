package com.sepp89117.goeasypro_android.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import com.sepp89117.goeasypro_android.R;
import com.sepp89117.goeasypro_android.gopro.GoSetting;

import java.util.ArrayList;

public class GoSettingsExpandableListAdapter extends BaseExpandableListAdapter {
    private final Context context;
    private ArrayList<String> goSettingsGroupNames;
    private ArrayList<ArrayList<GoSetting>> goSettingsByGroup;

    public GoSettingsExpandableListAdapter(Context context, ArrayList<GoSetting> goSettings) {
        this.context = context;
        setGroupNames(goSettings);
        setGoSettings(goSettings);
    }

    public void setGoSettings(ArrayList<GoSetting> goSettings) {
        setGroupNames(goSettings);
        this.goSettingsByGroup = new ArrayList<>();

        // Initialize goSettingsByGroup to hold GoSetting objects grouped by their group names
        for (String groupName : goSettingsGroupNames) {
            ArrayList<GoSetting> groupSettings = new ArrayList<>();
            for (GoSetting setting : goSettings) {
                if (setting.getGroupName().equals(groupName)) {
                    groupSettings.add(setting);
                }
            }
            goSettingsByGroup.add(groupSettings);
        }
    }

    private void setGroupNames(ArrayList<GoSetting> goSettings) {
        this.goSettingsGroupNames = new ArrayList<>();

        // List from GoSettings.putSettingsToGroups()
        ArrayList<String> desiredOrder = new ArrayList<String>();
        desiredOrder.add(context.getResources().getString(R.string.str_Current_Preset));
        desiredOrder.add(context.getResources().getString(R.string.str_Capture));
        desiredOrder.add("Protune");
        desiredOrder.add(context.getResources().getString(R.string.str_Shortcuts));
        desiredOrder.add(context.getResources().getString(R.string.str_general_device));
        desiredOrder.add(context.getResources().getString(R.string.str_general_video));
        desiredOrder.add(context.getResources().getString(R.string.str_voice_control));
        desiredOrder.add(context.getResources().getString(R.string.str_Displays));
        desiredOrder.add(context.getResources().getString(R.string.str_Others));

        for (String groupName : desiredOrder) {
            for (GoSetting goSetting : goSettings) {
                if (goSetting.getGroupName().equals(groupName) && !this.goSettingsGroupNames.contains(goSetting.getGroupName()))
                    this.goSettingsGroupNames.add(goSetting.getGroupName());
            }
        }
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return goSettingsByGroup.get(groupPosition).get(childPosition);
    }

    @Override
    public long getChildId(int listPosition, int expandedListPosition) {
        return expandedListPosition;
    }

    @Override
    public View getChildView(int groupPosition, final int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.gosettingslist_item, null);
        }

        GoSetting goSetting = (GoSetting) getChild(groupPosition, childPosition);

        String settingName = goSetting.getSettingName();
        String currentOptionName = goSetting.getCurrentOptionName();

        TextView name = convertView.findViewById(R.id.setting_name_text);
        TextView option = convertView.findViewById(R.id.current_option_name_text);

        name.setText(settingName);
        option.setText(currentOptionName);

        return convertView;
    }

    @Override
    public int getGroupCount() {
        return goSettingsGroupNames.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return goSettingsByGroup.get(groupPosition).size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return goSettingsGroupNames.get(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public View getGroupView(int listPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater layoutInflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = layoutInflater.inflate(R.layout.golist_group, null);
        }

        String goSettingsGroupName = (String) getGroup(listPosition);

        TextView listTitleTextView = convertView.findViewById(R.id.group_name);
        listTitleTextView.setText(goSettingsGroupName);

        return convertView;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public boolean isChildSelectable(int listPosition, int expandedListPosition) {
        return true;
    }
}
