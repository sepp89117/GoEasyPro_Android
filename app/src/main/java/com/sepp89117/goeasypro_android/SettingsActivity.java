package com.sepp89117.goeasypro_android;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        ((MyApplication) this.getApplication()).resetIsAppPaused();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        private static final String KEY_NOTIF_SWITCH = "pref_notifications_enabled";
        private SwitchPreferenceCompat notifSwitch;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            notifSwitch = findPreference(KEY_NOTIF_SWITCH);
            if (notifSwitch != null) {
                notifSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                    openAppNotificationSettings();
                    return false;
                });

                updateNotifSwitchState();
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            updateNotifSwitchState();

            try {
                ((MyApplication) requireActivity().getApplication()).resetIsAppPaused();
            } catch (Exception ignore) {
            }
        }

        private void updateNotifSwitchState() {
            boolean enabled =
                    NotificationManagerCompat.from(requireContext()).areNotificationsEnabled();
            notifSwitch.setChecked(enabled);
        }

        private void openAppNotificationSettings() {
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
            } else {
                intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package",
                                requireContext().getPackageName(), null));
            }
            startActivity(intent);
        }
    }
}