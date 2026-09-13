package io.github.hankaviator.greatgadsbye;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

final class FeatureSettings {
    static final String MODULE_PACKAGE = "io.github.hankaviator.greatgadsbye";
    static final String PREFERENCES = "features";
    static final String FEATURE_GMAIL = "gmail";
    static final String FEATURE_MAPS = "maps";

    private FeatureSettings() {}

    @SuppressLint("WorldReadableFiles")
    @SuppressWarnings("deprecation")
    static SharedPreferences preferences(Context context) {
        try {
            return context.getSharedPreferences(PREFERENCES, Context.MODE_WORLD_READABLE);
        } catch (SecurityException ignored) {
            return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        }
    }

    static boolean isEnabled(Context context, String feature) {
        return preferences(context).getBoolean(feature, true);
    }

    static void ensureDefaults(Context context) {
        SharedPreferences preferences = preferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        boolean changed = false;
        if (!preferences.contains(FEATURE_GMAIL)) {
            editor.putBoolean(FEATURE_GMAIL, true);
            changed = true;
        }
        if (!preferences.contains(FEATURE_MAPS)) {
            editor.putBoolean(FEATURE_MAPS, true);
            changed = true;
        }
        if (changed) {
            editor.apply();
        }
    }
}
