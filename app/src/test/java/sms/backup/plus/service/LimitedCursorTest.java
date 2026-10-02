package sms.backup.plus.service;

import android.database.Cursor;
import android.database.MatrixCursor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.google.common.truth.Truth.assertThat;

@RunWith(RobolectricTestRunner.class)
public class LimitedCursorTest {
    private static Cursor cursorWithRows(int rows) {
        MatrixCursor cursor = new MatrixCursor(new String[] { "date" });
        for (int i = 0; i < rows; i++) {
            cursor.addRow(new Object[] { (long) i });
        }
        return cursor;
    }

    @Test public void shouldCapCount() {
        assertThat(new LimitedCursor(cursorWithRows(10), 3).getCount()).isEqualTo(3);
    }

    @Test public void shouldNotInflateCount() {
        assertThat(new LimitedCursor(cursorWithRows(2), 3).getCount()).isEqualTo(2);
    }

    @Test public void shouldIterateOnlyUpToLimit() {
        Cursor cursor = new LimitedCursor(cursorWithRows(10), 3);
        int seen = 0;
        long last = -1;
        for (boolean ok = cursor.moveToFirst(); ok; ok = cursor.moveToNext()) {
            last = cursor.getLong(0);
            seen++;
        }
        assertThat(seen).isEqualTo(3);
        assertThat(last).isEqualTo(2L);
        assertThat(cursor.isAfterLast()).isTrue();
    }

    @Test public void shouldReportLastRowAtLimit() {
        Cursor cursor = new LimitedCursor(cursorWithRows(10), 3);
        cursor.moveToPosition(1);
        assertThat(cursor.isLast()).isFalse();
        cursor.moveToNext();
        assertThat(cursor.isLast()).isTrue();
        assertThat(cursor.moveToNext()).isFalse();
    }

    @Test public void shouldMoveToLastWithinLimit() {
        Cursor cursor = new LimitedCursor(cursorWithRows(10), 3);
        assertThat(cursor.moveToLast()).isTrue();
        assertThat(cursor.getLong(0)).isEqualTo(2L);
    }

    @Test public void emptyCursorIsNeitherLastNorMovable() {
        Cursor cursor = new LimitedCursor(cursorWithRows(0), 3);
        assertThat(cursor.getCount()).isEqualTo(0);
        assertThat(cursor.isLast()).isFalse();
        assertThat(cursor.moveToFirst()).isFalse();
    }
}
