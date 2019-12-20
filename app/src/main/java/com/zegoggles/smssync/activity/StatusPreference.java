package com.zegoggles.smssync.activity;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.events.MissingPermissionsEvent;
import com.zegoggles.smssync.activity.events.PerformAction;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.service.CancelEvent;
import com.zegoggles.smssync.service.SmsBackupService;
import com.zegoggles.smssync.service.SmsRestoreService;
import com.zegoggles.smssync.service.state.BackupState;
import com.zegoggles.smssync.service.state.RestoreState;
import com.zegoggles.smssync.service.state.SmsSyncState;
import com.zegoggles.smssync.service.state.State;
import com.zegoggles.smssync.utils.Drawables;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.activity.events.PerformAction.Actions.Backup;
import static com.zegoggles.smssync.activity.events.PerformAction.Actions.Restore;

public class StatusPreference extends Preference implements View.OnClickListener {
    private Button backupButton;
    private Button restoreButton;

    private ImageView statusIcon;

    private TextView statusLabel;
    private TextView syncDetailsLabel;

    private ProgressBar progressBar;
    private final Preferences preferences;

    private final int idleColor, doneColor, errorColor, syncingColor;
    private final Drawable idle, done, error, syncing;

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

        idle = Drawables.getTinted(context.getResources(), idleDrawable, idleColor);
        done = Drawables.getTinted(context.getResources(), doneDrawable, doneColor);
        error = Drawables.getTinted(context.getResources(), errorDrawable, errorColor);
        syncing = Drawables.getTinted(context.getResources(),syncingDrawable, syncingColor);
    }

    @Override
    public void onDetached() {
        super.onDetached();
        App.unregister(this);
    }

    @Override
    public void onClick(View which) {
        if (which == backupButton) {
            onBackup();
        } else if (which == restoreButton) {
            onRestore();
        }
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
        View syncDetails = holder.findViewById(R.id.details_sync);
        syncDetailsLabel = syncDetails.findViewById(R.id.details_sync_label);
        progressBar = syncDetails.findViewById(R.id.details_sync_progress);

        idle();

        App.register(this);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        // TODO implement
        return super.onSaveInstanceState();
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        // TODO implement
        super.onRestoreInstanceState(state);
    }

    @Subscribe public void restoreStateChanged(final RestoreState newState) {
        if (App.LOCAL_LOGV) Log.v(TAG, "restoreStateChanged:" + newState);

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
                syncDetailsLabel.setText(getString(R.string.status_restore_canceled_details,
                        newState.currentRestoredCount,
                        newState.itemsToRestore));
                break;
            case UPDATING_THREADS:
                progressBar.setIndeterminate(true);
                syncDetailsLabel.setText(R.string.status_updating_threads);
                break;

        }
    }

    @Subscribe public void backupStateChanged(final BackupState newState) {
        if (App.LOCAL_LOGV) Log.v(TAG, "backupStateChanged:"+newState);
        if (newState.backupType.isBackground()) return;

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
                syncDetailsLabel.setText(getString(R.string.status_canceled_details,
                        newState.currentSyncedItems,
                        newState.itemsToSync));
                break;
        }
    }

    @Subscribe public void onMissingPermissions(MissingPermissionsEvent event) {
        displayMissingPermissions(event.permissions);
    }

    private void onBackup() {
        if (!SmsBackupService.isServiceWorking()) {
            if (LOCAL_LOGV) Log.v(TAG, "user requested sync");
            App.post(new PerformAction(Backup, preferences.confirmAction()));
        } else {
            if (LOCAL_LOGV) Log.v(TAG, "user requested cancel");
            // Sync button will be restored on next status update.
            backupButton.setText(R.string.ui_sync_button_label_canceling);
            backupButton.setEnabled(false);
            App.post(new CancelEvent());
        }
    }

    private void onRestore() {
        if (LOCAL_LOGV) Log.v(TAG, "restore");
        if (SmsRestoreService.isServiceIdle()) {
            App.post(new PerformAction(Restore, preferences.confirmAction()));
        } else {
            restoreButton.setText(R.string.ui_sync_button_label_canceling);
            restoreButton.setEnabled(false);
            App.post(new CancelEvent());
        }
    }

    private void onAuthFailed() {
        statusLabel.setText(R.string.status_auth_failure);

        if (new AuthPreferences(getContext()).useXOAuth()) {
            syncDetailsLabel.setText(R.string.status_auth_failure_details_xoauth);
        } else {
            syncDetailsLabel.setText(R.string.status_auth_failure_details_plain);
        }
    }

    private void displayMissingPermissions(List<AppPermission> appPermissions) {
        statusLabel.setText(R.string.status_permission_problem);
        syncDetailsLabel.setText(AppPermission.formatMissingPermissionDetails(getContext().getResources(), appPermissions));
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
            text = getString(R.string.status_backup_done_details_max_per_sync, backedUpCount);
        } else if (backedUpCount > 0) {
            text = getQuantityString(R.plurals.status_backup_done_details, backedUpCount, backedUpCount);
        } else if (backedUpCount == 0) {
            text = getString(R.string.status_backup_done_details_noitems);
        }
        syncDetailsLabel.setText(text);
        statusLabel.setText(R.string.status_done);
        statusLabel.setTextColor(doneColor);
        statusIcon.setImageDrawable(done);
    }

    private void finishedRestore(RestoreState newState) {
        statusLabel.setTextColor(doneColor);
        statusLabel.setText(R.string.status_done);
        statusIcon.setImageDrawable(done);
        syncDetailsLabel.setText(getQuantityString(
                R.plurals.status_restore_done_details,
                newState.actualRestoredCount,
                newState.actualRestoredCount,
                newState.duplicateCount));
    }

    private void idle() {
        syncDetailsLabel.setText(getLastSyncText(preferences.getDataTypePreferences().getMostRecentSyncedDate()));
        statusLabel.setText(R.string.status_idle);
        statusLabel.setTextColor(idleColor);
        statusIcon.setImageDrawable(idle);
    }

    private String getLastSyncText(final long lastSync) {
        return getString(R.string.status_idle_details,
                lastSync < 0 ? getString(R.string.status_idle_details_never) :
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
                    onAuthFailed();
                } else if (state.isPermissionException()) {
                    displayMissingPermissions(AppPermission.from(state.getMissingPermissions()));
                } else {
                    final String errorMessage = state.getErrorMessage(getContext().getResources());
                    statusLabel.setText(R.string.status_unknown_error);
                    syncDetailsLabel.setText(getString(R.string.status_unknown_error_details,
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
                statusIcon.setImageDrawable(syncing);
                break;
            case ERROR:
                progressBar.setProgress(0);
                progressBar.setIndeterminate(false);
                statusLabel.setTextColor(errorColor);
                statusIcon.setImageDrawable(error);
                setButtonsToDefault();
                break;
            default:
                progressBar.setProgress(0);
                progressBar.setIndeterminate(false);
                statusLabel.setTextColor(idleColor);
                statusIcon.setImageDrawable(idle);
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

    private String getString(int resourceId, Object... formatArgs) {
        return getContext().getResources().getString(resourceId, formatArgs);
    }

    private String getQuantityString(int resourceId, int quantity, Object... formatArgs) {
        return getContext().getResources().getQuantityString(resourceId, quantity, formatArgs);
    }
}
