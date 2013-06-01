package com.zegoggles.smssync.service.state;

import android.content.res.Resources;
import com.fsck.k9.mail.AuthenticationFailedException;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.XOAuth2AuthenticationFailedException;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.service.ConnectivityErrorException;
import com.zegoggles.smssync.service.LocalizableException;

import java.util.EnumSet;

public abstract class StateChanged {
    public final SmsSyncState state;
    public final Exception exception;

    public StateChanged(SmsSyncState state, Exception exception) {
        this.state = state;
        this.exception = exception;
    }

    public String getErrorMessage(Resources resources) {
        if (exception == null) return null;

        if (exception instanceof MessagingException &&
                "Unable to get IMAP prefix".equals(exception.getMessage())) {
            return resources.getString(R.string.status_gmail_temp_error);
        } else if (exception instanceof LocalizableException) {
            return resources.getString(((LocalizableException) exception).errorResourceId());
        } else {
            return exception.getLocalizedMessage();
        }
    }

    public boolean isInitialState() {
        return state == SmsSyncState.INITIAL;
    }

    public boolean isRunning() {
        return EnumSet.of(
                SmsSyncState.LOGIN,
                SmsSyncState.CALC,
                SmsSyncState.BACKUP,
                SmsSyncState.RESTORE,
                SmsSyncState.UPDATING_THREADS).contains(state);
    }

    public abstract StateChanged transition(SmsSyncState newState, Exception exception);

    public boolean isAuthException() {
        return exception instanceof XOAuth2AuthenticationFailedException ||
               exception instanceof AuthenticationFailedException;
    }

    public boolean isConnectivityError() {
        return exception instanceof ConnectivityErrorException;
    }

    public boolean isError() {
        return state == SmsSyncState.ERROR;
    }
}
