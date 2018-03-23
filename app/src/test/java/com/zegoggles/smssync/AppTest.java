package com.zegoggles.smssync;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static com.google.common.truth.Truth.assertThat;

@RunWith(RobolectricTestRunner.class)
public class AppTest {
    @Ignore @Test public void shouldGetVersionName() throws Exception {
        assertThat(App.getVersionName(RuntimeEnvironment.application)).matches("\\d+\\.\\d+\\.\\d+(-\\w+)?");
    }

    @Test public void shouldGetVersionCode() throws Exception {
        assertThat(App.getVersionCode(RuntimeEnvironment.application)).isEqualTo(0);
    }

    @Test public void shouldTestOnSDCARD() throws Exception {
        assertThat(App.isInstalledOnSDCard(RuntimeEnvironment.application)).isFalse();
    }
}
