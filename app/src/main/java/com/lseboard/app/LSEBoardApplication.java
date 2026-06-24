package com.lseboard.app;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class LSEBoardApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 啟用 Material 3 動態顏色
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}

