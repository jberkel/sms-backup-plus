package com.zegoggles.smssync.mail;

import android.content.ContentResolver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class PersonLookupTest {

    private PersonLookup lookup;
    @Mock private ContentResolver resolver;

    @Before
    public void before() {
        initMocks(this);
        lookup = new PersonLookup(resolver);
    }

    @Test
    @Config(reportSdk = Build.VERSION_CODES.ECLAIR)
    public void shouldLookupUnknownPerson() throws Exception {
        when(resolver.query(any(Uri.class), any(String[].class), (String) isNull(), (String[]) isNull(), (String) isNull())).thenReturn(null);
        PersonRecord record = lookup.lookupPerson("1234");
        assertThat(record).isNotNull();
        assertThat(record.isUnknown()).isTrue();
        assertThat(record.getEmail()).isEqualTo("1234@unknown.email");
        assertThat(record.getName()).isEqualTo("1234");
    }

    @Test
    @Config(reportSdk = Build.VERSION_CODES.ECLAIR)
    public void shouldLookupExistingPerson() throws Exception {
        when(resolver.query( eq(Uri.parse("content://com.android.contacts/phone_lookup/1234")), any(String[].class),
                (String) isNull(),
                (String[]) isNull(),
                (String) isNull()
        )).thenReturn(name("Testor Test"));

        PersonRecord record = lookup.lookupPerson("1234");

        assertThat(record).isNotNull();
        assertThat(record.isUnknown()).isFalse();
        assertThat(record.getEmail()).isEqualTo("1234@unknown.email");
        assertThat(record.getName()).isEqualTo("Testor Test");
    }

    @Test
    @Config(reportSdk = Build.VERSION_CODES.ECLAIR)
    public void shouldLookupExistingPersonWithEmail() throws Exception {
        when(resolver.query( eq(Uri.parse("content://com.android.contacts/phone_lookup/1234")), any(String[].class),
                (String) isNull(),
                (String[]) isNull(),
                (String) isNull()
        )).thenReturn(name("Testor Test"));

        when(resolver.query(
                MessageConverter.ECLAIR_CONTENT_URI,
                new String[] { ContactsContract.CommonDataKinds.Email.DATA },
                ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                new String[] { String.valueOf(1) },
                ContactsContract.CommonDataKinds.Email.IS_PRIMARY + " DESC"))
                .thenReturn(email("foo@test.com") );

        PersonRecord record = lookup.lookupPerson("1234");
        assertThat(record).isNotNull();
        assertThat(record.getEmail()).isEqualTo("foo@test.com");
    }


    @Test
    @Config(reportSdk = Build.VERSION_CODES.ECLAIR)
    public void shouldLookupExistingPersonUsingGmailAsPrimaryEmail() throws Exception {
        when(resolver.query( eq(Uri.parse("content://com.android.contacts/phone_lookup/1234")), any(String[].class),
                (String) isNull(),
                (String[]) isNull(),
                (String) isNull()
        )).thenReturn(name("Testor Test"));

        when(resolver.query(
                MessageConverter.ECLAIR_CONTENT_URI,
                new String[] { ContactsContract.CommonDataKinds.Email.DATA },
                ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                new String[] { String.valueOf(1) },
                ContactsContract.CommonDataKinds.Email.IS_PRIMARY + " DESC"))
                .thenReturn(email("foo@test.com", "foo@gmail.com") );

        PersonRecord record = lookup.lookupPerson("1234");
        assertThat(record).isNotNull();
        assertThat(record.getEmail()).isEqualTo("foo@gmail.com");
    }

    private Cursor name(String... names) {
        MatrixCursor cursor = new MatrixCursor(new String[] {
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME
        });
        for (int i=0; i<names.length; i++) {
            cursor.addRow(new Object[] { i+1, names[i] });
        }
        return cursor;
    }

    private Cursor email(String... emails) {

        MatrixCursor cursor = new MatrixCursor(new String[] {
                ContactsContract.CommonDataKinds.Email.DATA
        });
        for (String email : emails) {
            cursor.addRow(new Object[] { email });
        }
        return cursor;
    }
}
