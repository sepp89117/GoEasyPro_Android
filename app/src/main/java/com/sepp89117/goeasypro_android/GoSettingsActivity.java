package com.sepp89117.goeasypro_android;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class GoSettingsActivity extends AppCompatActivity {
    private GoProDevice focusedDevice;
    private ListView listView;
    private GoSettingListAdapter listAdapter;
    ArrayList<GoSetting> goSettings = new ArrayList<>();
    private int clickedSettingIndex = -1;
    private AlertDialog newSetAlert;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_go_settings);

        newSetAlert = new AlertDialog.Builder(GoSettingsActivity.this)
                .setTitle("Set new setting")
                .setMessage("Please wait until the new setting has been made!")
                .setCancelable(true)
                .create();

        focusedDevice = ((MyApplication) this.getApplication()).getFocusedDevice();

        if(!focusedDevice.providesAvailableOptions)
            Toast.makeText(this, "This camera does not provide the available options for the settings.", Toast.LENGTH_LONG).show();

        TextView devName = findViewById(R.id.gopro_name);
        devName.setText(focusedDevice.displayName);

        goSettings = focusedDevice.goSettings;

        listAdapter = new GoSettingListAdapter(goSettings, this);

        listView = findViewById(R.id.settings_list);
        listView.setAdapter(listAdapter);
        registerForContextMenu(listView);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            view.showContextMenu();
        });

        focusedDevice.getSettingsChanges(() -> {
            goSettings = focusedDevice.goSettings;
            updateList();
            newSetAlert.dismiss();
        });
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

        GoSetting clickedSetting = goSettings.get(info.position);
        int settingId = clickedSetting.getSettingId();
        String settingName = clickedSetting.getSettingName();
        Map<Integer, String> availableOptions = new HashMap<>(clickedSetting.getAvailableOptions());
        clickedSettingIndex = info.position;

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

        if(goSettings.get(clickedSettingIndex).getCurrentOptionId() != selectedOptionId) {
            focusedDevice.setSetting(settingId, selectedOptionId);

            newSetAlert.show();
        }
        return true;
    }

    private void updateList() {
        runOnUiThread(()->{
            ArrayList<GoSetting> _goSettings = new ArrayList<>(goSettings);
            listAdapter.setNotifyOnChange(false);
            listAdapter.clear();
            listAdapter.addAll(_goSettings);
            listAdapter.notifyDataSetChanged();
            goSettings = new ArrayList<>(_goSettings);
        });
    }
}