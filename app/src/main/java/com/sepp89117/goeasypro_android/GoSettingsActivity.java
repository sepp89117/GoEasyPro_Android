package com.sepp89117.goeasypro_android;

import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.sepp89117.goeasypro_android.adapters.GoSettingsExpandableListAdapter;
import com.sepp89117.goeasypro_android.gopro.GoProDevice;
import com.sepp89117.goeasypro_android.gopro.GoSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class GoSettingsActivity extends AppCompatActivity {
    private GoProDevice focusedDevice;
    private GoSettingsExpandableListAdapter expListAdapter;
    private ArrayList<GoSetting> goSettings = new ArrayList<>();
    private GoSetting clickedSetting;
    private AlertDialog newSetAlert;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_go_settings);

        newSetAlert = new AlertDialog.Builder(GoSettingsActivity.this)
                .setTitle(getResources().getString(R.string.str_set_setting))
                .setMessage(getResources().getString(R.string.str_wait_setting_set))
                .setCancelable(true)
                .create();

        focusedDevice = ((MyApplication) this.getApplication()).getFocusedDevice();

        if (!focusedDevice.providesAvailableOptions)
            Toast.makeText(this, getResources().getString(R.string.str_no_available_options), Toast.LENGTH_LONG).show();

        TextView devName = findViewById(R.id.gopro_name);
        devName.setText(focusedDevice.displayName);

        goSettings = focusedDevice.goSettings;
        expListAdapter = new GoSettingsExpandableListAdapter(this, goSettings);

        ExpandableListView listView = findViewById(R.id.settings_list);
        listView.setAdapter(expListAdapter);
        registerForContextMenu(listView);
        listView.setOnChildClickListener((parent, view, groupPosition, childPosition, id) -> {
            clickedSetting = (GoSetting) expListAdapter.getChild(groupPosition, childPosition);
            view.showContextMenu();
            return true;
        });

        focusedDevice.getSettingsChanges(() -> {
            goSettings = focusedDevice.goSettings;
            updateList();
            newSetAlert.dismiss();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        ((MyApplication) this.getApplication()).resetIsAppPaused();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        if(clickedSetting == null)
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
            focusedDevice.setSetting(settingId, selectedOptionId);

            newSetAlert.show();
        }
        clickedSetting = null;
        return true;
    }

    private void updateList() {
        runOnUiThread(() -> {
            ArrayList<GoSetting> _goSettings = new ArrayList<>(goSettings);
            expListAdapter.setGoSettings(_goSettings);
            expListAdapter.notifyDataSetChanged();

            goSettings = new ArrayList<>(_goSettings);
        });
    }
}