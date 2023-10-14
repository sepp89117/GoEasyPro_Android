package com.sepp89117.goeasypro_android.gopro;

import android.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class GoSetting {
    private JSONObject _allOptions = null;
    private final Map<Integer, String> _availableOptionsMap = new HashMap<>();
    private final int _settingID;
    private int _currentOptionId;
    private String _settingName = "unknown setting";
    private String _currentOptionName = "unknown option";
    private boolean _isValid = false;

    public GoSetting(int settingID, int settingValue, JSONObject settingsValues, ArrayList<Pair<Integer, Integer>> availableSettingsOptions) {
        _settingID = settingID;
        _currentOptionId = settingValue;

        try {
            JSONObject setting = settingsValues.getJSONObject(String.valueOf(_settingID));
            _settingName = setting.getString("display_name");
            _allOptions = setting.getJSONObject("options");

            Iterator<String> keys = _allOptions.keys();
            while (keys.hasNext()) {
                int _optionId = Integer.parseInt(keys.next());
                String _optionValue = _allOptions.getString(String.valueOf(_optionId));

                if(availableSettingsOptions != null && availableSettingsOptions.size() > 0) {
                    //Test if the option is supported by the current GoPro model
                    for (Pair<Integer, Integer> settingOption : availableSettingsOptions) {
                        int settingId = settingOption.first;
                        int optionId = settingOption.second;

                        if (settingId == _settingID && optionId == _optionId) {
                            _availableOptionsMap.put(_optionId, _optionValue);
                            break;
                        }
                    }
                } else {
                    _availableOptionsMap.put(_optionId, _optionValue);
                }
            }

            _currentOptionName = _allOptions.getString(String.valueOf(_currentOptionId));

            if(_availableOptionsMap.size() > 0) {
                _isValid = true;
            }
        } catch (JSONException ignored) {
        }
    }

    public String getSettingName() {
        return _settingName;
    }

    public int getSettingId() {
        return _settingID;
    }

    public String getCurrentOptionName() {
        return _currentOptionName;
    }

    public int getCurrentOptionId() {
        return _currentOptionId;
    }

    public void setCurrentOption(int optionId) {
        _currentOptionId = optionId;
        try {
            _currentOptionName = _allOptions.getString(String.valueOf(_currentOptionId));
        } catch (JSONException e) {
            _currentOptionName = "unknown option";
            e.printStackTrace();
        }
    }

    public Map<Integer, String> getAvailableOptions() {
        return _availableOptionsMap;
    }

    public boolean isValid() {
        return _isValid;
    }
}
