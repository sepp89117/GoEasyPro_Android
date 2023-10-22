package com.sepp89117.goeasypro_android.gopro;

import android.app.Application;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class GoProtoPreset {
    private final Context _context;
    private final Application _application;

    private final int _id;
    private final PresetStatus.EnumPresetIcon _icon;
    private final PresetStatus.EnumPresetTitle _title_id;
    private final PresetStatus.EnumFlatMode _mode;
    private final boolean _user_defined;
    private final boolean _is_modified;
    private ArrayList<GoSetting> _setting_array;
    private final boolean _isValid;

    private final String _preset_title;
    private final String _mode_title;

    public GoProtoPreset(Context context, PresetStatus.Preset preset, Application application) {
        this._context = context;
        this._application = application;

        this._id = preset.getId();
        this._mode = preset.getMode();
        this._title_id = preset.getTitleId();
        this._user_defined = preset.getUserDefined();
        this._icon = preset.getIcon();
        this._setting_array = new ArrayList<>();
        List<PresetStatus.PresetSetting> sList = preset.getSettingArrayList();
        for (PresetStatus.PresetSetting pSetting : sList) {
            _setting_array.add(new GoSetting(pSetting.getId(), pSetting.getValue(), _application));
        }
        this._is_modified = preset.getIsModified();

        this._preset_title = getReadableTitle(this._title_id.toString());
        this._mode_title = getReadableTitle(this._mode.toString());

        this._isValid = !this._mode_title.equals("Unknown");
    }

    private String getReadableTitle(String enumTitle) {
        StringBuilder title = new StringBuilder(enumTitle.replaceAll("FLAT_MODE_|PRESET_GROUP_ID_|PRESET_ICON_|PRESET_TITLE_", "").toLowerCase().replaceAll("_", " "));
        String[] titleSplit = title.toString().split(" ");
        title = new StringBuilder();

        for (int i = 0; i < titleSplit.length; i++) {
            String split = titleSplit[i];
            title.append(split.substring(0, 1).toUpperCase()).append(split.substring(1));
            if (i < titleSplit.length - 1) title.append(" ");
        }

        return title.toString();
    }

    public boolean isValid() {
        return _isValid;
    }

    public int getId() {
        return _id;
    }

    public String getPresetTitle() {
        return _preset_title;
    }

    public String getModeTitle() {
        return _mode_title;
    }

    public ArrayList<GoSetting> getSettingArray() {
        return _setting_array;
    }

    public void setSettingsFromNewPreset(GoProtoPreset newPreset) {
        this._setting_array = newPreset.getSettingArray();
    }

    public String getSettingsString() {
        StringBuilder settingsStr = new StringBuilder();
        for (int i = 0; i < _setting_array.size(); i++) {
            GoSetting goSetting = _setting_array.get(i);
            settingsStr.append(goSetting.getCurrentOptionName());
            if (i < _setting_array.size() - 1) settingsStr.append(" | ");
        }

        return settingsStr.toString();
    }
}
