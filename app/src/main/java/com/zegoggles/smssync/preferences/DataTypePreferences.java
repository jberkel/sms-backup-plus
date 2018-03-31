package com.zegoggles.smssync.preferences;

import android.content.SharedPreferences;
import com.zegoggles.smssync.mail.DataType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.zegoggles.smssync.mail.DataType.Defaults.MAX_SYNCED_DATE;
import static com.zegoggles.smssync.mail.DataType.MMS;

public class DataTypePreferences implements SharedPreferences.OnSharedPreferenceChangeListener {
    public interface DataTypeListener {
        void onChanged(DataType dataType, DataTypePreferences preferences);
    }

    private DataTypeListener listener;
    private final SharedPreferences sharedPreferences;

    DataTypePreferences(SharedPreferences sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    public boolean isBackupEnabled(DataType dataType) {
        return sharedPreferences.getBoolean(dataType.backupEnabledPreference, dataType.backupEnabledByDefault);
    }

    public void setBackupEnabled(boolean enabled, DataType dataType) {
        sharedPreferences
            .edit()
            .putBoolean(dataType.backupEnabledPreference, enabled)
            .apply();
    }

    public boolean isRestoreEnabled(DataType dataType) {
        return dataType.restoreEnabledPreference != null &&
            sharedPreferences.getBoolean(dataType.restoreEnabledPreference, dataType.restoreEnabledByDefault);
    }

    public EnumSet<DataType> enabled() {
        List<DataType> enabledTypes = new ArrayList<DataType>();
        for (DataType t : DataType.values()) {
            if (isBackupEnabled(t)) {
                enabledTypes.add(t);
            }
        }
        return enabledTypes.isEmpty() ? EnumSet.noneOf(DataType.class) : EnumSet.copyOf(enabledTypes);
    }

    public String getFolder(DataType dataType) {
        return sharedPreferences.getString(dataType.folderPreference, dataType.defaultFolder);
    }

    /**
     * @return returns the last synced date in milliseconds (epoch)
     */
    public long getMaxSyncedDate(DataType dataType) {
        final long maxSynced = sharedPreferences.getLong(dataType.maxSyncedPreference, MAX_SYNCED_DATE);
        if (dataType == MMS && maxSynced > 0) {
            return maxSynced * 1000L;
        } else {
            return maxSynced;
        }
    }

    public boolean setMaxSyncedDate(DataType dataType, long max) {
        return sharedPreferences.edit().putLong(dataType.maxSyncedPreference, max).commit();
    }

    public long getMostRecentSyncedDate() {
        return Math.max(Math.max(
            getMaxSyncedDate(DataType.SMS),
            getMaxSyncedDate(DataType.CALLLOG)),
            getMaxSyncedDate(DataType.MMS));
    }

    public void clearLastSyncData() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        for (DataType type : DataType.values()) {
            editor.remove(type.maxSyncedPreference);
        }
        editor.commit();
    }

    public void registerDataTypeListener(DataTypeListener listener) {
        this.listener = listener;
        if (listener != null) {
            for (DataType type : DataType.values()) {
                listener.onChanged(type, this);
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (listener == null) return;
        for (DataType type : DataType.values()) {
            if (type.backupEnabledPreference.equals(key)) {
                listener.onChanged(type, this);
            }
        }
    }
}
