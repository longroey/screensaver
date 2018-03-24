package com.rc.screensaver;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;

public class ScreensaverSettingsActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {

    public static final String KEY_SCREENSAVER_MODE = "screensaver_mode";
    public static final String KEY_NIGHT_MODE = "screensaver_night_mode";
    public static final String KEY_DISPLAY_DATE_ALARM = "display_date_alarm";
    public static final String KEY_SCREENSAVER_ANIMATION_STYLE = "screensaver_animation_style";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.dream_settings);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public boolean onPreferenceChange(Preference pref, Object newValue) {
        if (KEY_SCREENSAVER_MODE.equals(pref.getKey())) {
            final ListPreference screensaverModeListPref = (ListPreference) pref;
            final int idx = screensaverModeListPref.findIndexOfValue((String) newValue);
            screensaverModeListPref.setSummary(screensaverModeListPref.getEntries()[idx]);
        } else if (KEY_NIGHT_MODE.equals(pref.getKey())) {
            boolean nightModeState = ((CheckBoxPreference) pref).isChecked();
        } else if (KEY_DISPLAY_DATE_ALARM.equals(pref.getKey())) {
            boolean displayDateAlarmState = ((CheckBoxPreference) pref).isChecked();
        } else if (KEY_SCREENSAVER_ANIMATION_STYLE.equals(pref.getKey())) {
            final ListPreference animationStyleListPref = (ListPreference) pref;
            final int idx = animationStyleListPref.findIndexOfValue((String) newValue);
            animationStyleListPref.setSummary(animationStyleListPref.getEntries()[idx]);
        }

        return true;
    }

    private void refresh() {
        ListPreference screensaverModeListPref = (ListPreference) findPreference(KEY_SCREENSAVER_MODE);
        screensaverModeListPref.setSummary(screensaverModeListPref.getEntry());
        screensaverModeListPref.setOnPreferenceChangeListener(this);

        Preference nightModePref = findPreference(KEY_NIGHT_MODE);
        boolean nightModeState = ((CheckBoxPreference) nightModePref).isChecked();
        nightModePref.setOnPreferenceChangeListener(this);

        Preference displayDateAlarmPref = findPreference(KEY_DISPLAY_DATE_ALARM);
        boolean displayDateAlarmState = ((CheckBoxPreference) displayDateAlarmPref).isChecked();
        displayDateAlarmPref.setOnPreferenceChangeListener(this);

        ListPreference animationStyleListPref = (ListPreference) findPreference(KEY_SCREENSAVER_ANIMATION_STYLE);
        animationStyleListPref.setSummary(animationStyleListPref.getEntry());
        animationStyleListPref.setOnPreferenceChangeListener(this);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        // This activity is not exported so we can just approve everything
        return true;
    }
}

