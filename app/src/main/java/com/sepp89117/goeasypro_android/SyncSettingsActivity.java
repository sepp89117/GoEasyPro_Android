package com.sepp89117.goeasypro_android;

import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.sepp89117.goeasypro_android.adapters.GoSettingsExpandableListAdapter;
import com.sepp89117.goeasypro_android.gopro.GoProDevice;
import com.sepp89117.goeasypro_android.gopro.GoSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SyncSettingsActivity extends AppCompatActivity {
    private ArrayList<GoProDevice> goProDevices;
    private ArrayList<GoSetting> commonSettings;

    private GoSettingsExpandableListAdapter expListAdapter;
    private ExpandableListView listView;
    private GoSetting clickedSetting;
    private AlertDialog newSetAlert;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_settings);

        newSetAlert = new AlertDialog.Builder(this)
                .setTitle(getResources().getString(R.string.str_set_setting))
                .setMessage(getResources().getString(R.string.str_wait_setting_set))
                .setCancelable(true)
                .create();

        listView = findViewById(R.id.settings_list);

        goProDevices = ((MyApplication) this.getApplication()).getGoProDevices();
        if (goProDevices != null && !goProDevices.isEmpty()) {
            commonSettings = calculateCommonSettings(goProDevices);
            displaySettings(commonSettings);
        } else {
            finish();
        }
    }

    private ArrayList<GoSetting> calculateCommonSettings(ArrayList<GoProDevice> devices) {
        Map<Integer, Map<Integer, Integer>> settingOptionsMap = new HashMap<>();
        Map<Integer, Integer> currentOptionMap = new HashMap<>();
        ArrayList<GoSetting> commonSettingsList = new ArrayList<>();

        // Iterate over all cameras and their settings
        for (GoProDevice device : devices) {
            for (GoSetting setting : device.goSettings) {
                int settingId = setting.getSettingId();
                int currentOptionId = setting.getCurrentOptionId();

                // Process options of the current setting
                Map<Integer, String> availableOptions = setting.getAvailableOptions();

                if (!settingOptionsMap.containsKey(settingId)) {
                    // Add setting if it is not already in the map
                    Map<Integer, Integer> optionCount = new HashMap<>();
                    for (Integer optionId : availableOptions.keySet()) {
                        optionCount.put(optionId, 1);
                    }
                    settingOptionsMap.put(settingId, optionCount);
                    currentOptionMap.put(settingId, currentOptionId);
                } else {
                    // Filter only options that are also supported on this camera
                    Map<Integer, Integer> optionCount = settingOptionsMap.get(settingId);
                    if (optionCount != null)
                        optionCount.keySet().retainAll(availableOptions.keySet());

                    // If the current option is different, set it to Integer.MAX_VALUE
                    if (currentOptionMap.get(settingId) != currentOptionId) {
                        currentOptionMap.put(settingId, Integer.MAX_VALUE);
                    }
                }
            }
        }

        // Create common settings
        for (Map.Entry<Integer, Map<Integer, Integer>> entry : settingOptionsMap.entrySet()) {
            int settingId = entry.getKey();
            Map<Integer, Integer> commonOptions = entry.getValue();

            if (!commonOptions.isEmpty()) {
                // Set first available option ID as default value
                int settingValue = currentOptionMap.getOrDefault(settingId, Integer.MAX_VALUE);
                GoSetting commonSetting = new GoSetting(settingId, settingValue, getApplication());
                commonSetting.setUnknownOptionString("is asynchronous");

                // Add all common options
                for (Integer optionId : commonOptions.keySet()) {
                    commonSetting.getAvailableOptions().put(optionId, commonSetting.getOptionName(optionId));
                }

                commonSettingsList.add(commonSetting);
            }
        }

        return commonSettingsList;
    }

    private void displaySettings(ArrayList<GoSetting> settings) {
        expListAdapter = new GoSettingsExpandableListAdapter(this, settings);
        listView.setAdapter(expListAdapter);
        registerForContextMenu(listView);
        listView.setOnChildClickListener((parent, view, groupPosition, childPosition, id) -> {
            clickedSetting = (GoSetting) expListAdapter.getChild(groupPosition, childPosition);
            view.showContextMenu();
            return true;
        });
    }

    private void updateList(ArrayList<GoSetting> settings) {
        runOnUiThread(() -> {
            expListAdapter.setGoSettings(settings);
            expListAdapter.notifyDataSetChanged();
        });
    }

    private void applySettingToDevices(int settingId, int optionId) {
        for (GoProDevice device : goProDevices) {
            device.getSettingsChanges(() -> {
                commonSettings = calculateCommonSettings(goProDevices);
                updateList(commonSettings);
                newSetAlert.dismiss();
            });
            for (GoSetting setting : device.goSettings) {
                if (setting.getSettingId() == settingId && setting.getAvailableOptions().containsKey(optionId)) {
                    device.setSetting(settingId, optionId);
                }
            }
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        if (clickedSetting == null)
            return;

        int settingId = clickedSetting.getSettingId();
        String settingName = clickedSetting.getSettingName();
        Map<Integer, String> availableOptions = new HashMap<>(clickedSetting.getAvailableOptions());

        menu.setHeaderTitle(settingName);

        for (Map.Entry<Integer, String> entry : availableOptions.entrySet()) {
            int optionId = entry.getKey();
            String optionName = entry.getValue();

            menu.add(settingId, optionId, optionId, optionName);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int settingId = item.getGroupId();
        int selectedOptionId = item.getItemId();

        if (clickedSetting.getCurrentOptionId() != selectedOptionId) {
            applySettingToDevices(settingId, selectedOptionId);

            newSetAlert.show();
        }
        clickedSetting = null;
        return true;
    }
}
