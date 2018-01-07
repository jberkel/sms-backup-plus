package com.zegoggles.smssync.activity;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.service.CancelEvent;
import com.zegoggles.smssync.service.SmsBackupService;
import com.zegoggles.smssync.service.SmsRestoreService;
import com.zegoggles.smssync.service.state.BackupState;
import com.zegoggles.smssync.service.state.RestoreState;
import com.zegoggles.smssync.service.state.SmsSyncState;
import com.zegoggles.smssync.service.state.State;

import java.text.DateFormat;
import java.util.Date;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

public class StatusPreference extends Preference implements View.OnClickListener {
    private Button backupButton;
    private Button restoreButton;

    private ImageView statusIcon;

    private TextView statusLabel;
    private TextView syncDetailsLabel;

    private ProgressBar progressBar;
    private final Preferences preferences;

    private final int idleColor, doneColor, errorColor, syncingColor;

    private static final int doneDrawable = R.drawable.ic_done;
    private static final int idleDrawable = doneDrawable;
    private static final int errorDrawable = R.drawable.ic_syncing_problem;
    private static final int syncingDrawable = R.drawable.ic_syncing;

    @SuppressWarnings("unused")
    public StatusPreference(Context context) {
        this(context, null);
    }

    @SuppressWarnings("WeakerAccess")
    public StatusPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0);
    }

    @SuppressWarnings("WeakerAccess")
    public StatusPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        preferences = new Preferences(context);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.StatusPreference,
                defStyleAttr,
                defStyleRes);
        idleColor = a.getColor(R.styleable.StatusPreference_statusIdle, 0);
        doneColor = a.getColor(R.styleable.StatusPreference_statusDone, 0);
        errorColor = a.getColor(R.styleable.StatusPreference_statusError, 0);
        syncingColor = a.getColor(R.styleable.StatusPreference_statusSyncing, 0);
        a.recycle();
    }

    @Override
    public void onClick(View v) {
        if (v == backupButton) {
            if (!SmsBackupService.isServiceWorking()) {
                if (LOCAL_LOGV) Log.v(TAG, "user requested sync");
                App.post(MainActivity.Actions.Backup);
            } else {
                if (LOCAL_LOGV) Log.v(TAG, "user requested cancel");
                // Sync button will be restored on next status update.
                backupButton.setText(R.string.ui_sync_button_label_canceling);
                backupButton.setEnabled(false);
                App.post(new CancelEvent());
            }
        } else if (v == restoreButton) {
            if (LOCAL_LOGV) Log.v(TAG, "restore");
            if (!SmsRestoreService.isServiceWorking()) {
                App.post(MainActivity.Actions.Restore);
            } else {
                restoreButton.setText(R.string.ui_sync_button_label_canceling);
                restoreButton.setEnabled(false);
                App.post(new CancelEvent());
            }
        }
    }

    @Override
    public void onAttached() {
        super.onAttached();
        App.register(this);
    }

    @Override
    public void onDetached() {
        super.onDetached();
        App.unregister(this);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        backupButton = (Button) holder.findViewById(R.id.sync_button);
        backupButton.setOnClickListener(this);

        restoreButton = (Button) holder.findViewById(R.id.restore_button);
        restoreButton.setOnClickListener(this);

        statusIcon = (ImageView) holder.findViewById(R.id.status_icon);
        statusLabel = (TextView) holder.findViewById(R.id.status_label);
        View mSyncDetails = holder.findViewById(R.id.details_sync);
        syncDetailsLabel = (TextView) mSyncDetails.findViewById(R.id.details_sync_label);
        progressBar = (ProgressBar) mSyncDetails.findViewById(R.id.details_sync_progress);

        idle();
    }

    @Subscribe public void restoreStateChanged(final RestoreState newState) {
        if (App.LOCAL_LOGV) Log.v(TAG, "restoreStateChanged:" + newState);
        if (backupButton == null) return;

        stateChanged(newState);
        switch (newState.state) {
            case RESTORE:
                backupButton.setEnabled(false);
                restoreButton.setText(R.string.ui_restore_button_label_restoring);
                statusLabel.setText(R.string.status_restore);
                syncDetailsLabel.setText(newState.getNotificationLabel(getContext().getResources()));
                progressBar.setIndeterminate(false);
                progressBar.setProgress(newState.currentRestoredCount);
                progressBar.setMax(newState.itemsToRestore);
                break;
            case FINISHED_RESTORE:
                finishedRestore(newState);
                break;

            case CANCELED_RESTORE:
                statusLabel.setText(R.string.status_canceled);
                syncDetailsLabel.setText(getContext().getString(R.string.status_restore_canceled_details,
                        newState.currentRestoredCount,
                        newState.itemsToRestore));
                break;
            case UPDATING_THREADS:
                progressBar.setIndeterminate(true);
                syncDetailsLabel.setText(getContext().getString(R.string.status_updating_threads));
                break;

        }
    }

    @Subscribe public void backupStateChanged(final BackupState newState) {
        if (App.LOCAL_LOGV) Log.v(TAG, "backupStateChanged:"+newState);
        if (backupButton == null || newState.backupType.isBackground()) return;

        stateChanged(newState);

        switch (newState.state) {
            case FINISHED_BACKUP:
                finishedBackup(newState);
                break;
            case BACKUP:
                restoreButton.setEnabled(false);
                backupButton.setText(R.string.ui_sync_button_label_syncing);
                statusLabel.setText(R.string.status_backup);
                syncDetailsLabel.setText(newState.getNotificationLabel(getContext().getResources()));
                progressBar.setIndeterminate(false);
                progressBar.setProgress(newState.currentSyncedItems);
                progressBar.setMax(newState.itemsToSync);
                break;
            case CANCELED_BACKUP:
                statusLabel.setText(R.string.status_canceled);

                syncDetailsLabel.setText(getContext().getString(R.string.status_canceled_details,
                        newState.currentSyncedItems,
                        newState.itemsToSync));
                break;
        }
    }

    private void authFailed() {
        statusLabel.setText(R.string.status_auth_failure);

        if (new AuthPreferences(getContext()).useXOAuth()) {
            syncDetailsLabel.setText(R.string.status_auth_failure_details_xoauth);
        } else {
            syncDetailsLabel.setText(R.string.status_auth_failure_details_plain);
        }
    }

    private void calc() {
        statusLabel.setText(R.string.status_working);
        syncDetailsLabel.setText(R.string.status_calc_details);
        progressBar.setIndeterminate(true);
    }

    private void finishedBackup(BackupState state) {
        int backedUpCount = state.currentSyncedItems;
        String text = null;
        if (backedUpCount == preferences.getMaxItemsPerSync()) {
            text = getContext().getString(R.string.status_backup_done_details_max_per_sync, backedUpCount);
        } else if (backedUpCount > 0) {
            text = getContext().getResources().getQuantityString(R.plurals.status_backup_done_details, backedUpCount,
                    backedUpCount);
        } else if (backedUpCount == 0) {
            text = getContext().getString(R.string.status_backup_done_details_noitems);
        }
        syncDetailsLabel.setText(text);
        statusLabel.setText(R.string.status_done);
        statusLabel.setTextColor(doneColor);
    }

    private void finishedRestore(RestoreState newState) {
        statusLabel.setTextColor(doneColor);
        statusLabel.setText(R.string.status_done);
        syncDetailsLabel.setText(getContext().getResources().getQuantityString(
                R.plurals.status_restore_done_details,
                newState.actualRestoredCount,
                newState.actualRestoredCount,
                newState.duplicateCount));
    }

    private void idle() {
        syncDetailsLabel.setText(getLastSyncText(preferences.getDataTypePreferences().getMostRecentSyncedDate()));
        statusLabel.setText(R.string.status_idle);
        statusLabel.setTextColor(idleColor);
        statusIcon.setImageDrawable(getTintedDrawable(idleDrawable, idleColor));
    }

    private String getLastSyncText(final long lastSync) {
        return getContext().getString(R.string.status_idle_details,
                lastSync < 0 ? getContext().getString(R.string.status_idle_details_never) :
                        DateFormat.getDateTimeInstance().format(new Date(lastSync)));
    }

    private void stateChanged(State state) {
        setViewAttributes(state.state);
        switch (state.state) {
            case INITIAL:
                idle();
                break;
            case LOGIN:
                statusLabel.setText(R.string.status_working);
                syncDetailsLabel.setText(R.string.status_login_details);
                progressBar.setIndeterminate(true);
                break;
            case CALC:
                calc();
                break;
            case ERROR:
                if (state.isAuthException()) {
                    authFailed();
                } else {
                    final String errorMessage = state.getErrorMessage(getContext().getResources());
                    statusLabel.setText(R.string.status_unknown_error);
                    syncDetailsLabel.setText(getContext().getString(R.string.status_unknown_error_details,
                            errorMessage == null ? "N/A" : errorMessage));
                }
                break;
        }
    }

    private void setViewAttributes(final SmsSyncState state) {
        switch (state) {
            case LOGIN:
            case CALC:
            case BACKUP:
            case RESTORE:
                statusLabel.setTextColor(syncingColor);
                statusIcon.setImageDrawable(getTintedDrawable(syncingDrawable, syncingColor));
                break;
            case ERROR:
                progressBar.setProgress(0);
                progressBar.setIndeterminate(false);
                statusLabel.setTextColor(errorColor);
                statusIcon.setImageDrawable(getTintedDrawable(errorDrawable, errorColor));
                setButtonsToDefault();
                break;
            default:
                progressBar.setProgress(0);
                progressBar.setIndeterminate(false);
                statusLabel.setTextColor(idleColor);
                statusIcon.setImageDrawable(getTintedDrawable(idleDrawable, idleColor));
                setButtonsToDefault();
                break;
        }
    }

    private void setButtonsToDefault() {
        restoreButton.setEnabled(true);
        restoreButton.setText(R.string.ui_restore_button_label_idle);
        backupButton.setEnabled(true);
        backupButton.setText(R.string.ui_sync_button_label_idle);
    }

    @SuppressWarnings("deprecation")
    private @NonNull Drawable getTintedDrawable(int resource, int color) {
        Drawable drawable = getContext().getResources().getDrawable(resource);
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable.mutate(), color);
        return drawable;
    }
}
