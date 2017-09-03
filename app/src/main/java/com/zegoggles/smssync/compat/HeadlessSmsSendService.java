package com.zegoggles.smssync.compat;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class HeadlessSmsSendService extends Service {
    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
