package com.zegoggles.smssync.compat;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.provider.Telephony.Sms;
import android.support.annotation.NonNull;
import android.telephony.SmsMessage;
import android.util.Log;
import com.zegoggles.smssync.Consts;
import com.zegoggles.smssync.utils.ThreadHelper;

import static android.provider.Telephony.Sms.Intents.getMessagesFromIntent;
import static android.provider.Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX;
import static android.provider.Telephony.TextBasedSmsColumns.STATUS_NONE;
import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

public class SmsReceiver extends BroadcastReceiver {
    private final ThreadHelper threadHelper = new ThreadHelper();

    @Override public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive("+intent+")");
        if (isSmsBackupDefaultSmsApp(context)) {
            final SmsMessage[] messages = getMessagesFromIntent(intent);
            if (messages != null && messages.length > 0) {
                storeMessage(context, messages);
            }
        }
    }


    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static boolean isSmsBackupDefaultSmsApp(@NonNull Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
               context.getPackageName().equals(Sms.getDefaultSmsPackage(context));
    }

    // Adapted from
    // https://github.com/aosp-mirror/platform_packages_apps_mms/blob/master/src/com/android/mms/transaction/SmsReceiverService.java#L554
    private void storeMessage(Context context, SmsMessage[] messages) {
        final int pduCount = messages.length;
        SmsMessage sms = messages[0];
        Log.d(TAG, "storeMessage( "+sms+")");

        ContentValues values = new ContentValues();
        final String address = sms.getDisplayOriginatingAddress();
        values.put(Telephony.TextBasedSmsColumns.ADDRESS, address);
        values.put(Telephony.TextBasedSmsColumns.DATE_SENT, sms.getTimestampMillis());
        values.put(Telephony.TextBasedSmsColumns.DATE, System.currentTimeMillis());
        values.put(Telephony.TextBasedSmsColumns.TYPE, MESSAGE_TYPE_INBOX);
        values.put(Telephony.TextBasedSmsColumns.PROTOCOL, sms.getProtocolIdentifier());
        values.put(Telephony.TextBasedSmsColumns.SERVICE_CENTER, sms.getServiceCenterAddress());
        values.put(Telephony.TextBasedSmsColumns.STATUS, STATUS_NONE);
        values.put(Telephony.TextBasedSmsColumns.THREAD_ID, threadHelper.getThreadId(context, address));
        values.put(Telephony.TextBasedSmsColumns.READ, 0);
        values.put(Telephony.TextBasedSmsColumns.SEEN, 0);
        if (sms.getPseudoSubject() != null && sms.getPseudoSubject().length() > 0) {
            values.put(Telephony.TextBasedSmsColumns.SUBJECT, sms.getPseudoSubject());
        }
        values.put(Telephony.TextBasedSmsColumns.REPLY_PATH_PRESENT, sms.isReplyPathPresent() ? 1 : 0);

        if (pduCount == 1) {
            // There is only one part, so grab the body directly.
            values.put(Telephony.TextBasedSmsColumns.BODY, sms.getDisplayMessageBody());
        } else {
            // Build up the body from the parts.
            StringBuilder body = new StringBuilder();
            for (SmsMessage message : messages) {
                body.append(message.getDisplayMessageBody());
            }
            values.put(Telephony.TextBasedSmsColumns.BODY, body.toString());
        }

        final Uri uri = context.getContentResolver().insert(Consts.SMS_PROVIDER, values);
        Log.d(TAG, "inserted as "+uri);
    }
}
