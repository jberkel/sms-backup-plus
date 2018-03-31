package com.zegoggles.smssync.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.activity.events.AutoBackupSettingsChangedEvent;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

public class PackageReplacedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (LOCAL_LOGV) Log.v(TAG, "onReceive(" + context + "," + intent + ")");

        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            Log.d(TAG, "now installed version: " + App.getVersionCode(context));
            //  just post event and let application handle the rest
            App.post(new AutoBackupSettingsChangedEvent());
        } else {
            Log.w(TAG, "unhandled intent: "+intent);
        }
    }
}
