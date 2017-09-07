package com.zegoggles.smssync.service.state;

import android.content.res.Resources;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.mail.DataType;

import static com.zegoggles.smssync.service.state.SmsSyncState.INITIAL;

public class RestoreState extends State {
    /** items currently restored */
    public final int currentRestoredCount;

    /** total number of items to be restored */
    public final int itemsToRestore;

    /** how many items did get actually restored */
    public final int actualRestoredCount;

    /** how many duplicates where detected after restore */
    public final int duplicateCount;

    public RestoreState() {
        this(INITIAL, 0, 0, 0, 0, null, null);
    }

    public RestoreState(SmsSyncState state,
                        int currentRestoredCount,
                        int itemsToRestore,
                        int actualRestoredCount,
                        int duplicateCount,
                        DataType dataType,
                        Exception exception) {
        super(state, dataType, exception);
        this.currentRestoredCount = currentRestoredCount;
        this.actualRestoredCount = actualRestoredCount;
        this.itemsToRestore = itemsToRestore;
        this.duplicateCount = duplicateCount;
    }

    @Override
    public String toString() {
        return "RestoreStateChanged{" +
                "state=" + state +
                ", currentRestoredCount=" + currentRestoredCount +
                ", itemsToRestore=" + itemsToRestore +
                ", actualRestoredCount=" + actualRestoredCount +
                ", duplicateCount=" + duplicateCount +
                '}';
    }

    @Override
    public RestoreState transition(SmsSyncState newState, Exception exception) {
        return new RestoreState(newState, currentRestoredCount, itemsToRestore, actualRestoredCount, duplicateCount, dataType, exception);
    }

    @Override
    public String getNotificationLabel(Resources resources) {
        String label = super.getNotificationLabel(resources);
        if (label != null) return label;
        switch (state) {
            case RESTORE:
                label = resources.getString(R.string.status_restore_details,
                        currentRestoredCount,
                        itemsToRestore);
                if (dataType != null) {
                    label += " ("+resources.getString(dataType.resId)+")";
                }
                return label;
            case UPDATING_THREADS:
                return resources.getString(R.string.status_updating_threads);
            default:
                return "";
        }
    }
}
