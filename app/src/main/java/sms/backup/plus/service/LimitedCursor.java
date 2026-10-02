package sms.backup.plus.service;

import android.database.Cursor;
import android.database.CursorWrapper;

/**
 * Exposes at most {@code limit} rows of the wrapped cursor.
 *
 * Used instead of appending "LIMIT n" to the sort order: recent Android versions reject
 * that with "IllegalArgumentException: Invalid token LIMIT".
 */
class LimitedCursor extends CursorWrapper {
    private final int limit;

    LimitedCursor(Cursor cursor, int limit) {
        super(cursor);
        this.limit = limit;
    }

    @Override public int getCount() {
        return Math.min(super.getCount(), limit);
    }

    @Override public boolean moveToPosition(int position) {
        final int count = getCount();
        if (position >= count) {
            super.moveToPosition(count);
            return false;
        }
        return super.moveToPosition(position);
    }

    @Override public boolean move(int offset) {
        return moveToPosition(getPosition() + offset);
    }

    @Override public boolean moveToNext() {
        return moveToPosition(getPosition() + 1);
    }

    @Override public boolean moveToLast() {
        return moveToPosition(getCount() - 1);
    }

    @Override public boolean isLast() {
        final int count = getCount();
        return count > 0 && getPosition() == count - 1;
    }

    @Override public boolean isAfterLast() {
        final int count = getCount();
        return count == 0 || getPosition() >= count;
    }
}
