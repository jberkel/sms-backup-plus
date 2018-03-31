package com.zegoggles.smssync.service;

import android.database.Cursor;
import android.util.Log;
import com.zegoggles.smssync.mail.DataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.service.BackupItemsFetcher.emptyCursor;

public class BackupCursors implements Iterator<BackupCursors.CursorAndType> {
    private Map<DataType, Cursor> cursorMap = new HashMap<DataType, Cursor>();
    private List<CursorAndType> cursorAndTypes = new ArrayList<CursorAndType>();

    private int index;

    public static class CursorAndType {
        final DataType type;
        final Cursor cursor;

        public CursorAndType(DataType type, Cursor cursor) {
            this.type = type;
            this.cursor = cursor;
        }

        public boolean hasNext() {
            return cursor.getCount() > 0 && !cursor.isLast();
        }

        @Override public String toString() {
            return "CursorAndType{" +
                    "type=" + type +
                    ", cursor=" + cursor +
                    '}';
        }

        public static CursorAndType empty() {
            return new CursorAndType(DataType.SMS, emptyCursor());
        }
    }

    BackupCursors() {
    }

    void add(DataType type, Cursor cursor) {
        cursorAndTypes.add(new CursorAndType(type, cursor));
        cursorMap.put(type, cursor);
    }

    public int count() {
        int total = 0;
        for (CursorAndType ct : cursorAndTypes) {
            total += ct.cursor.getCount();
        }
        return total;
    }

    public int count(DataType type) {
        Cursor cursor = cursorMap.get(type);
        return cursor == null ? 0 : cursor.getCount();
    }

    @Override public boolean hasNext() {
        return !cursorAndTypes.isEmpty() && (getCurrent().hasNext() || getNextNonEmptyIndex() != -1);
    }

    @Override public CursorAndType next() {
        if (cursorAndTypes.isEmpty()) throw new NoSuchElementException();

        if (getCurrent().hasNext()) {
            getCurrentCursor().moveToNext();
        } else if (getNextNonEmptyIndex() != -1) {
            index = getNextNonEmptyIndex();
            getCurrentCursor().moveToFirst();
        } else {
            throw new NoSuchElementException();
        }

        return getCurrent();
    }

    @Override public void remove() {
        throw new UnsupportedOperationException();
    }

    public void close() {
        for (CursorAndType ct : cursorAndTypes) {
            try {
                ct.cursor.close();
            } catch (Exception e) {
                Log.w(TAG, e);
            }
        }
    }

    private int getNextNonEmptyIndex() {
        for (int i = index + 1; i < cursorAndTypes.size(); i++) {
            if (cursorAndTypes.get(i).hasNext()) {
                return i;
            }
        }
        return -1;
    }

    private CursorAndType getCurrent() {
        return index < cursorAndTypes.size() ? cursorAndTypes.get(index) : CursorAndType.empty();
    }


    private Cursor getCurrentCursor() {
        return getCurrent().cursor;
    }


}
