package com.zegoggles.smssync.service.state;

import android.content.res.Resources;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import com.fsck.k9.mail.AuthenticationFailedException;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.store.imap.XOAuth2AuthenticationFailedException;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.service.exception.ConnectivityException;
import com.zegoggles.smssync.service.exception.LocalizableException;
import com.zegoggles.smssync.service.exception.MissingPermissionException;
import com.zegoggles.smssync.service.exception.RequiresLoginException;

import java.util.EnumSet;

public abstract class State {
    public final SmsSyncState state;
    public final Exception exception;
    public final @Nullable DataType dataType;

    State(SmsSyncState state, @Nullable DataType dataType, Exception exception) {
        this.state = state;
        this.exception = exception;
        this.dataType = dataType;
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

    public String getDetailedErrorMessage(Resources resources) {
        final String msg = getErrorMessage(resources);
        if (msg != null && exception != null) {
            final String underlying = exception.getCause() != null ? exception.getCause().toString() : null;
            final StringBuilder message = new StringBuilder().append(msg)
                    .append(" (exception: ")
                    .append(exception.toString());

            if (!TextUtils.isEmpty(underlying)) {
                message.append(", underlying=").append(underlying);
            }
            return message.append(")").toString();
        } else {
            return null;
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

    public boolean isFinished() {
        return !isInitialState() && !isRunning();
    }

    public abstract State transition(SmsSyncState newState, Exception exception);

    public boolean isAuthException() {
        return exception instanceof XOAuth2AuthenticationFailedException ||
               exception instanceof AuthenticationFailedException ||
               exception instanceof RequiresLoginException;
    }

    public boolean isPermissionException() {
        return exception instanceof MissingPermissionException;
    }

    public boolean isConnectivityError() {
        return exception instanceof ConnectivityException;
    }

    public boolean isError() {
        return state == SmsSyncState.ERROR;
    }

    public boolean isCanceled() {
        return state == SmsSyncState.CANCELED_BACKUP || state == SmsSyncState.CANCELED_RESTORE;
    }

    public String getNotificationLabel(Resources resources) {
        switch (state) {
            case LOGIN: return resources.getString(R.string.status_login_details);
            case CALC:  return resources.getString(R.string.status_calc_details);
            case ERROR: return getErrorMessage(resources);
            default: return null;
        }
    }

    public String[] getMissingPermissions() {
        if (isPermissionException()) {
            MissingPermissionException mpe = (MissingPermissionException)exception;
            return mpe.permissions.toArray(new String[mpe.permissions.size()]);
        } else {
            return new String[0];
        }
    }
}
