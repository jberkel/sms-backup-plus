package com.zegoggles.smssync.service;

import android.database.Cursor;
import android.database.MatrixCursor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.util.NoSuchElementException;

import static com.zegoggles.smssync.mail.DataType.*;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class BackupCursorsTest {
    BackupCursors cursors;

    @Before public void before() {
        cursors = new BackupCursors();

        cursors.add(SMS, cursor(1));
        cursors.add(CALLLOG, cursor(0));
        cursors.add(MMS, cursor(4));
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
        assertThat(cursors.count(SMS)).isEqualTo(1);
        assertThat(cursors.count(MMS)).isEqualTo(4);
        assertThat(cursors.count(CALLLOG)).isEqualTo(0);
        assertThat(cursors.count(WHATSAPP)).isEqualTo(0);
        assertThat(cursors.count(null)).isEqualTo(0);
    }

    @Test public void shouldIterateOverAllContainedCursors() throws Exception {
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

    @Test
    public void shouldCloseAllCursors() throws Exception {
        BackupCursors cursors = new BackupCursors();
        Cursor mockedCursor1 = Mockito.mock(Cursor.class);
        Cursor mockedCursor2 = Mockito.mock(Cursor.class);
        cursors.add(SMS, mockedCursor1);
        cursors.add(MMS, mockedCursor2);

        cursors.close();

        verify(mockedCursor1).close();
        verify(mockedCursor2).close();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldNotSupportRemove() throws Exception {
        cursors.remove();
    }

    private Cursor cursor(int rows) {
        MatrixCursor c = new MatrixCursor(new String[] {});
        for (int i=0; i<rows; i++) {
            c.addRow(new Object[] {});
        }
        return c;
    }
}
