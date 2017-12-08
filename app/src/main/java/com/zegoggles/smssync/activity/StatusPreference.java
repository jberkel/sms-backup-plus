package com.zegoggles.smssync.activity;

import android.preference.Preference;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.service.SmsBackupService;
import com.zegoggles.smssync.service.SmsRestoreService;
import com.zegoggles.smssync.service.CancelEvent;
import com.zegoggles.smssync.service.state.BackupState;
import com.zegoggles.smssync.service.state.RestoreState;
import com.zegoggles.smssync.service.state.SmsSyncState;
import com.zegoggles.smssync.service.state.State;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

class StatusPreference extends Preference implements View.OnClickListener {
    private View mView;

    private Button mBackupButton;
    private Button mRestoreButton;

    private ImageView mStatusIcon;

    private TextView mStatusLabel;
    private TextView mSyncDetailsLabel;

    private ProgressBar mProgressBar;
    private MainActivity mainActivity;
    private final Preferences preferences;

    public StatusPreference(Preferences preferences, MainActivity mainActivity) {
        super(mainActivity);
        this.mainActivity = mainActivity;
        this.preferences = preferences;
        setSelectable(false);
        setOrder(0);
    }

    @Override
    public void onClick(View v) {
        if (v == mBackupButton) {
            if (!SmsBackupService.isServiceWorking()) {
                if (LOCAL_LOGV) Log.v(TAG, "user requested sync");
                mainActivity.performAction(MainActivity.Actions.Backup);
            } else {
                if (LOCAL_LOGV) Log.v(TAG, "user requested cancel");
                // Sync button will be restored on next status update.
                mBackupButton.setText(R.string.ui_sync_button_label_canceling);
                mBackupButton.setEnabled(false);
                App.bus.post(new CancelEvent());
            }
        } else if (v == mRestoreButton) {
            if (LOCAL_LOGV) Log.v(TAG, "restore");
            if (!SmsRestoreService.isServiceWorking()) {
                mainActivity.performAction(MainActivity.Actions.Restore);
            } else {
                mRestoreButton.setText(R.string.ui_sync_button_label_canceling);
                mRestoreButton.setEnabled(false);
                App.bus.post(new CancelEvent());
            }
        }
    }

    @Override
    public View getView(View convertView, ViewGroup parent) {
        if (mView == null) {
            mView = mainActivity.getLayoutInflater().inflate(R.layout.status, parent, false);
            mBackupButton = (Button) mView.findViewById(R.id.sync_button);
            mBackupButton.setOnClickListener(this);

            mRestoreButton = (Button) mView.findViewById(R.id.restore_button);
            mRestoreButton.setOnClickListener(this);

            mStatusIcon = (ImageView) mView.findViewById(R.id.status_icon);
            mStatusLabel  = (TextView) mView.findViewById(R.id.status_label);
            View mSyncDetails = mView.findViewById(R.id.details_sync);
            mSyncDetailsLabel = (TextView) mSyncDetails.findViewById(R.id.details_sync_label);
            mProgressBar = (ProgressBar) mSyncDetails.findViewById(R.id.details_sync_progress);

            idle();
        }
        return mView;
    }

    @Subscribe public void restoreStateChanged(final RestoreState newState) {
        if (App.LOCAL_LOGV) Log.v(TAG, "restoreStateChanged:" + newState);
        if (mView == null) return;

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
        if (mView == null || newState.backupType.isBackground()) return;

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
        mStatusLabel.setTextColor(getContext().getResources().getColor(R.color.status_done));
    }

    private void finishedRestore(RestoreState newState) {
        mStatusLabel.setTextColor(getContext().getResources().getColor(R.color.status_done));
        mStatusLabel.setText(R.string.status_done);
        mSyncDetailsLabel.setText(getContext().getResources().getQuantityString(
                R.plurals.status_restore_done_details,
                newState.actualRestoredCount,
                newState.actualRestoredCount,
                newState.duplicateCount));
    }

    private void idle() {
        mSyncDetailsLabel.setText(mainActivity.getLastSyncText(preferences.getDataTypePreferences().getMostRecentSyncedDate()));
        mStatusLabel.setText(R.string.status_idle);
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
                mStatusLabel.setTextColor(getContext().getResources().getColor(R.color.status_sync));
                mStatusIcon.setImageResource(R.drawable.ic_syncing);
                break;
            case ERROR:
                mProgressBar.setProgress(0);
                mProgressBar.setIndeterminate(false);
                mStatusLabel.setTextColor(getContext().getResources().getColor(R.color.status_error));
                mStatusIcon.setImageResource(R.drawable.ic_error);
                setButtonsToDefault();
                break;
            default:
                mProgressBar.setProgress(0);
                mProgressBar.setIndeterminate(false);
                mStatusLabel.setTextColor(getContext().getResources().getColor(R.color.status_idle));
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
