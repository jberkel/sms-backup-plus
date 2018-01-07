package com.zegoggles.smssync.activity;

import android.content.Context;
import android.content.res.TypedArray;
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
    private Button mBackupButton;
    private Button mRestoreButton;

    private ImageView mStatusIcon;

    private TextView mStatusLabel;
    private TextView mSyncDetailsLabel;

    private ProgressBar mProgressBar;
    private final Preferences preferences;

    private final int idle, done, error, syncing;

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
        this.preferences = new Preferences(context);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.StatusPreference,
                defStyleAttr,
                defStyleRes);

        idle = a.getColor(R.styleable.StatusPreference_statusIdle, 0);
        done = a.getColor(R.styleable.StatusPreference_statusDone, 0);
        error = a.getColor(R.styleable.StatusPreference_statusError, 0);
        syncing = a.getColor(R.styleable.StatusPreference_statusSyncing, 0);
        a.recycle();
    }

    @Override
    public void onClick(View v) {
        if (v == mBackupButton) {
            if (!SmsBackupService.isServiceWorking()) {
                if (LOCAL_LOGV) Log.v(TAG, "user requested sync");
                App.post(MainActivity.Actions.Backup);
            } else {
                if (LOCAL_LOGV) Log.v(TAG, "user requested cancel");
                // Sync button will be restored on next status update.
                mBackupButton.setText(R.string.ui_sync_button_label_canceling);
                mBackupButton.setEnabled(false);
                App.post(new CancelEvent());
            }
        } else if (v == mRestoreButton) {
            if (LOCAL_LOGV) Log.v(TAG, "restore");
            if (!SmsRestoreService.isServiceWorking()) {
                App.post(MainActivity.Actions.Restore);
            } else {
                mRestoreButton.setText(R.string.ui_sync_button_label_canceling);
                mRestoreButton.setEnabled(false);
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

        mBackupButton = (Button) holder.findViewById(R.id.sync_button);
        mBackupButton.setOnClickListener(this);

        mRestoreButton = (Button) holder.findViewById(R.id.restore_button);
        mRestoreButton.setOnClickListener(this);

        mStatusIcon = (ImageView) holder.findViewById(R.id.status_icon);
        mStatusLabel  = (TextView) holder.findViewById(R.id.status_label);
        View mSyncDetails = holder.findViewById(R.id.details_sync);
        mSyncDetailsLabel = (TextView) mSyncDetails.findViewById(R.id.details_sync_label);
        mProgressBar = (ProgressBar) mSyncDetails.findViewById(R.id.details_sync_progress);

        idle();
    }

    @Subscribe public void restoreStateChanged(final RestoreState newState) {
        if (App.LOCAL_LOGV) Log.v(TAG, "restoreStateChanged:" + newState);
        if (mBackupButton == null) return;

        stateChanged(newState);
        switch (newState.state) {
            case RESTORE:
                mBackupButton.setEnabled(false);
                mRestoreButton.setText(R.string.ui_restore_button_label_restoring);
                mStatusLabel.setText(R.string.status_restore);
                mSyncDetailsLabel.setText(newState.getNotificationLabel(getContext().getResources()));
                mProgressBar.setIndeterminate(false);
                mProgressBar.setProgress(newState.currentRestoredCount);
                mProgressBar.setMax(newState.itemsToRestore);
                break;
            case FINISHED_RESTORE:
                finishedRestore(newState);
                break;

            case CANCELED_RESTORE:
                mStatusLabel.setText(R.string.status_canceled);
                mSyncDetailsLabel.setText(getContext().getString(R.string.status_restore_canceled_details,
                        newState.currentRestoredCount,
                        newState.itemsToRestore));
                break;
            case UPDATING_THREADS:
                mProgressBar.setIndeterminate(true);
                mSyncDetailsLabel.setText(getContext().getString(R.string.status_updating_threads));
                break;

        }
    }

    @Subscribe public void backupStateChanged(final BackupState newState) {
        if (App.LOCAL_LOGV) Log.v(TAG, "backupStateChanged:"+newState);
        if (mBackupButton == null || newState.backupType.isBackground()) return;

        stateChanged(newState);

        switch (newState.state) {
            case FINISHED_BACKUP:
                finishedBackup(newState);
                break;
            case BACKUP:
                mRestoreButton.setEnabled(false);
                mBackupButton.setText(R.string.ui_sync_button_label_syncing);
                mStatusLabel.setText(R.string.status_backup);
                mSyncDetailsLabel.setText(newState.getNotificationLabel(getContext().getResources()));
                mProgressBar.setIndeterminate(false);
                mProgressBar.setProgress(newState.currentSyncedItems);
                mProgressBar.setMax(newState.itemsToSync);
                break;
            case CANCELED_BACKUP:
                mStatusLabel.setText(R.string.status_canceled);

                mSyncDetailsLabel.setText(getContext().getString(R.string.status_canceled_details,
                        newState.currentSyncedItems,
                        newState.itemsToSync));
                break;
        }
    }

    private void authFailed() {
        mStatusLabel.setText(R.string.status_auth_failure);

        if (new AuthPreferences(getContext()).useXOAuth()) {
            mSyncDetailsLabel.setText(R.string.status_auth_failure_details_xoauth);
        } else {
            mSyncDetailsLabel.setText(R.string.status_auth_failure_details_plain);
        }
    }

    private void calc() {
        mStatusLabel.setText(R.string.status_working);
        mSyncDetailsLabel.setText(R.string.status_calc_details);
        mProgressBar.setIndeterminate(true);
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
        mSyncDetailsLabel.setText(text);
        mStatusLabel.setText(R.string.status_done);
        mStatusLabel.setTextColor(done);
    }

    private void finishedRestore(RestoreState newState) {
        mStatusLabel.setTextColor(done);
        mStatusLabel.setText(R.string.status_done);
        mSyncDetailsLabel.setText(getContext().getResources().getQuantityString(
                R.plurals.status_restore_done_details,
                newState.actualRestoredCount,
                newState.actualRestoredCount,
                newState.duplicateCount));
    }

    private void idle() {
        mSyncDetailsLabel.setText(getLastSyncText(preferences.getDataTypePreferences().getMostRecentSyncedDate()));
        mStatusLabel.setText(R.string.status_idle);
        mStatusLabel.setTextColor(idle);
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
                mStatusLabel.setText(R.string.status_working);
                mSyncDetailsLabel.setText(R.string.status_login_details);
                mProgressBar.setIndeterminate(true);
                break;
            case CALC:
                calc();
                break;
            case ERROR:
                if (state.isAuthException()) {
                    authFailed();
                } else {
                    final String errorMessage = state.getErrorMessage(getContext().getResources());
                    mStatusLabel.setText(R.string.status_unknown_error);
                    mSyncDetailsLabel.setText(getContext().getString(R.string.status_unknown_error_details,
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
                mStatusLabel.setTextColor(syncing);
                mStatusIcon.setImageResource(R.drawable.ic_syncing);
                break;
            case ERROR:
                mProgressBar.setProgress(0);
                mProgressBar.setIndeterminate(false);
                mStatusLabel.setTextColor(error);
                mStatusIcon.setImageResource(R.drawable.ic_error);
                setButtonsToDefault();
                break;
            default:
                mProgressBar.setProgress(0);
                mProgressBar.setIndeterminate(false);
                mStatusLabel.setTextColor(idle);
                mStatusIcon.setImageResource(R.drawable.ic_idle);
                setButtonsToDefault();
                break;
        }
    }

    private void setButtonsToDefault() {
        mRestoreButton.setEnabled(true);
        mRestoreButton.setText(R.string.ui_restore_button_label_idle);
        mBackupButton.setEnabled(true);
        mBackupButton.setText(R.string.ui_sync_button_label_idle);
    }
}
