package com.zegoggles.smssync.activity;

import com.zegoggles.smssync.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static com.google.common.truth.Truth.assertThat;

@RunWith(RobolectricTestRunner.class)
public class PreferenceTitlesTest {
    private PreferenceTitles subject;

    @Before
    public void setUp() throws Exception {
        subject = new PreferenceTitles(RuntimeEnvironment.application.getResources(), R.xml.preferences);
    }

    @Test public void testParseValidKey() {
        final int res = subject.getTitleRes("com.zegoggles.smssync.activity.fragments.AutoBackupSettings");
        assertThat(res).isGreaterThan(0);
        String resolved = RuntimeEnvironment.application.getString(res);
        assertThat(resolved).isEqualTo("Auto backup settings");
    }

    @Test public void testInvalidKeyReturnsZero() {
        final int res = subject.getTitleRes("foo.bar.not.found");
        assertThat(res).isEqualTo(0);
    }
}
