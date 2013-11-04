package com.zegoggles.smssync.service;

import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import android.util.Log;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.internet.BinaryTempFileBody;
import com.squareup.otto.Produce;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.Consts;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.auth.TokenRefresher;
import com.zegoggles.smssync.mail.MessageConverter;
import com.zegoggles.smssync.mail.PersonLookup;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.service.state.RestoreState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FilenameFilter;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.mail.DataType.CALLLOG;
import static com.zegoggles.smssync.mail.DataType.SMS;
import static com.zegoggles.smssync.service.state.SmsSyncState.ERROR;

public class SmsRestoreService extends ServiceBase {
    private static final int RESTORE_ID = 2;

    @NotNull private RestoreState mState = new RestoreState();
    @Nullable private static SmsRestoreService service;

    @Override @NotNull
    public RestoreState getState() {
        return mState;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        asyncClearCache();
        BinaryTempFileBody.setTempDirectory(getCacheDir());
        service = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (LOCAL_LOGV) Log.v(TAG, "SmsRestoreService#onDestroy(state"+getState()+")");
        service = null;
    }

    /**
     * Android KitKat requires SMS Backup+ to be the default SMS application in order to
     * write to the SMS Provider.
     */
    private Boolean canWriteToSmsProvider() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return true;
        }

        String defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this);
        return defaultSmsPackage.equals(getPackageName());
    }

    @Override
    protected void handleIntent(final Intent intent) {
        if (isWorking()) return;
        if (!canWriteToSmsProvider()) {
            // TODO: The main app's status should be updated here to mention that SMS Backup+
            // is not the default SMS application, and that the restore cannot be completed.
            Log.e(TAG, "SMS Backup+ is not the default SMS provider, aborting.");
            return;
        }

        try {
            final boolean starredOnly   = getPreferences().isRestoreStarredOnly();
            final boolean restoreCallLog = CALLLOG.isRestoreEnabled(service);
            final boolean restoreSms     = SMS.isRestoreEnabled(service);

            MessageConverter converter = new MessageConverter(service,
                    getPreferences(),
                    getAuthPreferences().getUserEmail(),
                    new PersonLookup(getContentResolver())
            );

            RestoreConfig config = new RestoreConfig(
                getBackupImapStore(),
                0,
                restoreSms,
                restoreCallLog,
                starredOnly,
                getPreferences().getMaxItemsPerRestore(),
                0
            );

            new RestoreTask(this, converter, getContentResolver(),
                    new TokenRefresher(service, new AuthPreferences(this))).execute(config);

        } catch (MessagingException e) {
            App.bus.post(mState.transition(ERROR, e));
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
                intent.hasExtra(Consts.KEY_DEFAULT_SMS_PROVIDER)) {
                final String defaultSmsPackage = intent.getStringExtra(Consts.KEY_DEFAULT_SMS_PROVIDER);

                // NOTE: This will require user interaction.
                final Intent restoreSmsPackageIntent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                restoreSmsPackageIntent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, defaultSmsPackage);
                startActivity(restoreSmsPackageIntent);
            }
        }
    }

    private void asyncClearCache() {
        new Thread("clearCache") {
            @Override
            public void run() {
                clearCache();
            }
        }.start();
    }

    public synchronized void clearCache() {
        File tmp = getCacheDir();
        if (tmp == null) return; // not sure why this would return null

        Log.d(TAG, "clearing cache in " + tmp);
        for (File f : tmp.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("body");
            }
        })) {
            if (LOCAL_LOGV) Log.v(TAG, "deleting " + f);
            if (!f.delete()) Log.w(TAG, "error deleting " + f);
        }
    }

    @Subscribe public void restoreStateChanged(final RestoreState state) {
        mState = state;
        if (mState.isInitialState()) return;

        if (mState.isRunning()) {
            if (notification == null) {
                notification = createNotification(R.string.status_restore);
            }
            notification.setLatestEventInfo(this,
                    getString(R.string.status_restore),
                    state.getNotificationLabel(getResources()),
                    getPendingIntent());

            startForeground(RESTORE_ID, notification);
        } else {
            Log.d(TAG, "stopping service, state"+mState);
            stopForeground(true);
            stopSelf();
        }
    }

    @Produce public RestoreState produceLastState() {
        return mState;
    }

    public static boolean isServiceWorking() {
        return service != null && service.isWorking();
    }
}
