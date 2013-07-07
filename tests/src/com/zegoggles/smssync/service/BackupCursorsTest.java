package com.zegoggles.smssync.service;

import android.database.Cursor;
import android.database.MatrixCursor;
import com.zegoggles.smssync.mail.DataType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.NoSuchElementException;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;

@RunWith(RobolectricTestRunner.class)
public class BackupCursorsTest {
    BackupCursors cursors;

    @Before public void before() {
        cursors = new BackupCursors();

        cursors.add(DataType.SMS, cursor(1));
        cursors.add(DataType.MMS, cursor(4));
        cursors.add(DataType.CALLLOG, cursor(0));
    }

    @Test public void testEmptyCursor() {
        BackupCursors empty = new BackupCursors();
        assertThat(empty.count()).isEqualTo(0);
        assertThat(empty.hasNext()).isFalse();
    }

    @Test(expected = NoSuchElementException.class)
    public void testEmptyCursorShouldThrowNoSuchElementException() {
        BackupCursors empty = new BackupCursors();
        empty.next();
    }

    @Test public void shouldReportTotalCountOfAllCursors() throws Exception {
        assertThat(cursors.count()).isEqualTo(5);
    }

    @Test public void shouldReportCountForDataType() throws Exception {
        assertThat(cursors.count(DataType.SMS)).isEqualTo(1);
        assertThat(cursors.count(DataType.MMS)).isEqualTo(4);
        assertThat(cursors.count(DataType.CALLLOG)).isEqualTo(0);
        assertThat(cursors.count(DataType.WHATSAPP)).isEqualTo(0);
        assertThat(cursors.count(null)).isEqualTo(0);
    }

    @Test
    public void shouldIterateOverAllContainedCursors() throws Exception {
        for (int i=0; i<cursors.count(); i++) {
            assertThat(cursors.hasNext()).isTrue();
            BackupCursors.CursorAndType cursorAndType = cursors.next();

            assertThat(cursorAndType).isNotNull();
            assertThat(cursorAndType.cursor).isNotNull();
            assertThat(cursorAndType.type).isNotNull();
        }
        assertThat(cursors.hasNext()).isFalse();

        try {
            cursors.next();
            fail("expected exception");
        } catch (NoSuchElementException e) {
        }
    }

    private Cursor cursor(int rows) {
        MatrixCursor c = new MatrixCursor(new String[] {});
        for (int i=0; i<rows; i++) {
            c.addRow(new Object[] {});
        }
        return c;
    }
}
