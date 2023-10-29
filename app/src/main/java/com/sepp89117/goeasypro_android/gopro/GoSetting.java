package com.sepp89117.goeasypro_android.gopro;

import android.app.Application;
import android.util.Pair;

import com.sepp89117.goeasypro_android.MyApplication;
import com.sepp89117.goeasypro_android.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class GoSetting {
    private final Map<String, int[]> _settingGroups = new HashMap<>();
    private JSONObject _allOptions = null;
    private final Map<Integer, String> _availableOptionsMap = new HashMap<>();
    private final int _settingID;
    private final int _currentOptionId;
    private String _settingName = "unknown setting";
    private String _currentOptionName = "unknown option";
    private String _groupName = "Others";
    private boolean _isValid = false;

    private final Application _application;
    private final JSONObject _settingsValues;

    public GoSetting(int settingID, int settingValue, Application application) {
        _application = application;
        _settingsValues = ((MyApplication) _application).getSettingsValues();

        putSettingsToGroups();

        _settingID = settingID;
        _currentOptionId = settingValue;

        setGroupName();

        try {
            JSONObject setting = _settingsValues.getJSONObject(String.valueOf(_settingID));
            _settingName = setting.getString("display_name");
            _allOptions = setting.getJSONObject("options");

            Iterator<String> keys = _allOptions.keys();
            while (keys.hasNext()) {
                int _optionId = Integer.parseInt(keys.next());
                String _optionValue = _allOptions.getString(String.valueOf(_optionId));
                if (_currentOptionId == _optionId) {
                    _availableOptionsMap.put(_optionId, _optionValue);
                    break;
                }
            }

            setCurrentOptionName();

            if (_availableOptionsMap.size() > 0) {
                _isValid = true;
            }
        } catch (JSONException ignored) {
        }
    }

    public GoSetting(Application application, int settingID, int settingValue, ArrayList<Pair<Integer, Integer>> availableSettingsOptions) {
        _application = application;
        _settingsValues = ((MyApplication) _application).getSettingsValues();

        putSettingsToGroups();

        _settingID = settingID;
        _currentOptionId = settingValue;

        setGroupName();

        try {
            JSONObject setting = _settingsValues.getJSONObject(String.valueOf(_settingID));
            _settingName = setting.getString("display_name");
            _allOptions = setting.getJSONObject("options");

            Iterator<String> keys = _allOptions.keys();
            while (keys.hasNext()) {
                int _optionId = Integer.parseInt(keys.next());
                String _optionValue = _allOptions.getString(String.valueOf(_optionId));

                if (availableSettingsOptions != null && availableSettingsOptions.size() > 0) {
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

            setCurrentOptionName();

            if (_availableOptionsMap.size() > 0) {
                _isValid = true;
            }
        } catch (JSONException ignored) {
        }
    }

    private void setGroupName() {
        for (String group : _settingGroups.keySet()) {
            int[] ids = _settingGroups.get(group);
            for (int id : ids) {
                if (id == _settingID) {
                    _groupName = group;
                    return;
                }
            }
        }
    }

    private void putSettingsToGroups() {
        _settingGroups.put(_application.getResources().getString(R.string.str_general_device), new int[]{
                84 /* Language */,
                175 /* Controls */,
                87 /* Beeps */,
                54 /* Quick Capture */,
                161 /* Default Preset */,
                89 /* Default Mode */,
                59 /* Auto Off */,
                91 /* LEDs */,
                178 /* Wi-fi Band */,
                103 /* Auto Lock */,
                83 /* GPS */
        });
        _settingGroups.put(_application.getResources().getString(R.string.str_general_video), new int[]{
                182 /* Bit Rate */,
                183 /* Bit Depth */,
                134 /* Anti-Flicker */,
                57 /* Video Format (PAL, NTSC) */
        });
        _settingGroups.put(_application.getResources().getString(R.string.str_Current_Preset), new int[]{
                2 /* Resolution */,
                3 /* Frames Per Second */,
                4 /* Field of View */,
                8 /* Low Light*/,
                28 /* Resolution & FOV */,
                121 /* Lens */,
                122 /* Lens */,
                123 /* Lens */,
                165 /* Horizon Lock */,
                166 /* Horizon Lock */,
                135 /* HyperSmooth */,
                148 /* Max HyperSmooth */,
                184 /* Profiles (HDR...) */,
                185 /* Aspect Ratio */,
                186 /* Video Mode */,
                187 /* Lapse Mode */,
                188 /* Aspect Ratio */,
                189 /* Max Lens Mod */,
                190 /* Max Lens Mod Enable */,
                191 /* Photo Mode */,
                192 /* Aspect Ratio */,
                193 /* Framing */
        });
        _settingGroups.put(_application.getResources().getString(R.string.str_voice_control), new int[]{86, 85});
        _settingGroups.put(_application.getResources().getString(R.string.str_Displays), new int[]{
                52 /* Orientation */,
                112 /* Orientation */,
                51 /* Screen Saver Rear */,
                159 /* Screen Saver Rear */,
                158 /* Screen Saver Front */,
                72 /* LCD Display on/off*/,
                88 /* LCD Brightness */,
                50 /* LCD Lock */,
                154 /* Front LCD Mode */
        });
        _settingGroups.put(_application.getResources().getString(R.string.str_Shortcuts), new int[]{129, 130, 131, 132});
        _settingGroups.put(_application.getResources().getString(R.string.str_Capture), new int[]{
                179 /* Trail Length */,
                125 /* Output */,
                126 /* Output */,
                171 /* Interval */,
                32 /* Interval */,
                30 /* Interval */,
                7 /* Interval */,
                6 /* Interval */,
                5 /* Interval */,
                19 /* Shutter */,
                31 /* Shutter */,
                168 /* Scheduled Capture */,
                172 /* Duration */,
                157 /* Duration */,
                156 /* Duration */,
                167 /* HindSight */,
                105 /* Timer */
        });
        _settingGroups.put("Protune", new int[]{
                10 /* Protune on off */,
                21 /* Protune on off */,
                34 /* Protune on off */,
                114 /* Protune on off */,
                145 /* Shutter */,
                146 /* Shutter */,
                118 /* EV Comp */,
                115 /* White Balance */,
                102 /* ISO Min */,
                76 /* ISO Min */,
                75 /* ISO Min */,
                37 /* ISO Max */,
                24 /* ISO Max */,
                13 /* ISO Max */,
                14 /* Sharpness */,
                25 /* Sharpness */,
                38 /* Sharpness */,
                117 /* Sharpness */,
                12 /* Color */,
                23 /* Color */,
                36 /* Color */,
                116 /* Color */,
                139 /* RAW Audio */,
                149 /* Wind */,
                164 /* Media Mod */,
        });
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

    public String getGroupName() {
        return _groupName;
    }

    private void setCurrentOptionName() {
        try {
            _currentOptionName = _allOptions.getString(String.valueOf(_currentOptionId));
        } catch (JSONException e) {
            _currentOptionName = "unknown option";
        }
    }

    public Map<Integer, String> getAvailableOptions() {
        return _availableOptionsMap;
    }

    public boolean isValid() {
        return _isValid;
    }
}
