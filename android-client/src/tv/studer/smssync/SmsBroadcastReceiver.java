package tv.studer.smssync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class SmsBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!SmsSyncService.isFirstSync(ctx) && SmsSyncService.isLoginInformationSet(ctx)) {
            Alarms.scheduleIncomingSync(ctx);
        } else {
            Log.i(Consts.TAG, "Received SMS but not ready to sync.");
        }
    }

}
