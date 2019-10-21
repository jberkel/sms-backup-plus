package com.zegoggles.smssync.service;

import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.internet.BinaryTempFileBody;
import com.squareup.otto.Produce;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.auth.OAuth2Client;
import com.zegoggles.smssync.auth.TokenRefresher;
import com.zegoggles.smssync.contacts.ContactAccessor;
import com.zegoggles.smssync.mail.MessageConverter;
import com.zegoggles.smssync.mail.PersonLookup;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.service.exception.SmsProviderNotWritableException;
import com.zegoggles.smssync.service.state.RestoreState;

import java.io.File;
import java.io.FilenameFilter;

import static com.zegoggles.smssync.App.CHANNEL_ID;
import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.compat.SmsReceiver.isSmsBackupDefaultSmsApp;
import static com.zegoggles.smssync.mail.DataType.CALLLOG;
import static com.zegoggles.smssync.mail.DataType.SMS;
import static com.zegoggles.smssync.service.state.SmsSyncState.ERROR;

public class SmsRestoreService extends ServiceBase {
    private static final int RESTORE_ID = 2;


    @NonNull private RestoreState state = new RestoreState();
    @Nullable private static SmsRestoreService service;

    @Override @NonNull
    public RestoreState getState() {
        return state;
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
     * Android KitKat and above require SMS Backup+ to be the default SMS application in order to
     * write to the SMS Provider.
     */
    private boolean canWriteToSmsProvider() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT ||
               isSmsBackupDefaultSmsApp(this);
    }

    @Override
    protected void handleIntent(final Intent intent) {
        if (isWorking()) return;

        try {
            final boolean restoreCallLog = getPreferences().getDataTypePreferences().isRestoreEnabled(CALLLOG);
            final boolean restoreSms     = getPreferences().getDataTypePreferences().isRestoreEnabled(SMS);

            if (restoreSms && !canWriteToSmsProvider()) {
                postError(new SmsProviderNotWritableException());
                return;
            }

            MessageConverter converter = new MessageConverter(service,
                    getPreferences(),
                    getAuthPreferences().getUserEmail(),
                    new PersonLookup(getContentResolver()),
                    new ContactAccessor()
            );

            RestoreConfig config = new RestoreConfig(
                getBackupImapStore(),
                0,
                restoreSms,
                restoreCallLog,
                getPreferences().isRestoreStarredOnly(),
                getPreferences().getMaxItemsPerRestore(),
                0
            );

            final AuthPreferences authPreferences = new AuthPreferences(this);
            new RestoreTask(this, converter, getContentResolver(),
                    new TokenRefresher(service, new OAuth2Client(authPreferences.getOAuth2ClientId()), authPreferences)).execute(config);

        } catch (MessagingException e) {
            postError(e);
        }
    }

    private void postError(Exception exception) {
        App.post(state.transition(ERROR, exception));
    }

    private void asyncClearCache() {
        new Thread("clearCache") {
            @Override
            public void run() {
                clearCache();
            }
        }.start();
    }

    synchronized void clearCache() {
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
        this.state = state;
        if (this.state.isInitialState()) return;

        if (this.state.isRunning()) {
            notification = createNotification(R.string.status_restore)
                    .setContentTitle(getString(R.string.status_restore))
                    .setContentText(state.getNotificationLabel(getResources()))
                    .setContentIntent(getPendingIntent(null))
                    .build();

            startForeground(RESTORE_ID, notification);
        } else {
            Log.d(TAG, "stopping service, state"+ this.state);
            stopForeground(true);
            stopSelf();
        }
    }

    @Produce public RestoreState produceLastState() {
        return state;
    }

    @SuppressWarnings("deprecation")
    @Override protected int wakeLockType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // hold a full wake lock when restoring on newer version of Android, since
            // the user needs to switch  back the sms app afterwards
            return PowerManager.FULL_WAKE_LOCK;
        } else {
            return super.wakeLockType();
        }
    }

    public static boolean isServiceIdle() {
        return service == null || !service.isWorking();
    }
}
