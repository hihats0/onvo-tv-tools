package com.bamtechmedia.dominguez.main;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

// Kumandadaki Disney+ tuşu bu paketi açar; biz TOD'a yönlendiriyoruz.
public class MainActivity extends Activity {
    private static final String TARGET = "com.beonetv.tod";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PackageManager pm = getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(TARGET);
        if (launch == null) {
            launch = pm.getLaunchIntentForPackage(TARGET);
        }
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
        }
        finish();
    }
}
