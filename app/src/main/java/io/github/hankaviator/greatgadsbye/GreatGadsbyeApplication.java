package io.github.hankaviator.greatgadsbye;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public final class GreatGadsbyeApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}

