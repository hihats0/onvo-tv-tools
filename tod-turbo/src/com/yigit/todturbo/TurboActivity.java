package com.yigit.todturbo;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

// Arka plandaki ağır uygulamaları kapatıp TOD'u temiz başlatır.
public class TurboActivity extends Activity {
    private static final String TAG = "TodTurbo";
    private static final String TOD = "com.beonetv.tod";

    // Sadece cached/arka plan süreçleri etkilenir; sistem ve persistent servisler korunur.
    private static final String[] KILL = {
        TOD,
        "com.netflix.ninja",
        "com.netflix.tokenmanager",
        "com.WhaleTV.whaleos.youtube.tv",
        "com.spotify.tv.android",
        "com.whaletv.web.app_runtime",
        "me.efesser.flauncher",
        "com.iqqi.imeservice",
        "com.zeasn.whaleos.settings",
        "com.android.tv.settings",
        "com.droidlogic.tv.settings",
        "com.droidlogic.FileBrower",
        "com.android.documentsui",
        "com.android.packageinstaller",
        "com.android.externalstorage",
        "com.android.keychain",
        "com.android.permissioncontroller",
        "com.android.webview",
        "com.google.android.webview.beta",
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        final long before = availMb(am);
        for (String pkg : KILL) {
            try {
                am.killBackgroundProcesses(pkg);
                Log.i(TAG, "kill ok " + pkg);
            } catch (RuntimeException e) {
                Log.w(TAG, "kill fail " + pkg + ": " + e);
            }
        }
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                long freed = Math.max(0, availMb(am) - before);
                Toast.makeText(getApplicationContext(),
                        "TOD Turbo: +" + freed + " MB bosaltildi", Toast.LENGTH_SHORT).show();
                launchTod();
                finish();
            }
        }, 700);
    }

    private void launchTod() {
        PackageManager pm = getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(TOD);
        if (launch == null) {
            launch = pm.getLaunchIntentForPackage(TOD);
        }
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(launch);
        }
    }

    private static long availMb(ActivityManager am) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem / (1024 * 1024);
    }
}
