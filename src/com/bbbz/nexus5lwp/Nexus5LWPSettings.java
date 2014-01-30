
package com.bbbz.nexus5lwp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class Nexus5LWPSettings extends PreferenceActivity
    implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        PreferenceManager pm = getPreferenceManager();
        pm.setDefaultValues(getApplicationContext(), R.xml.nexus5lwp_settings, false);
        pm.setSharedPreferencesName(Nexus5LWP.SHARED_PREFS_NAME);
        addPreferencesFromResource(R.xml.nexus5lwp_settings);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
    }
}
