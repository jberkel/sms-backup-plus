package com.zegoggles.smssync;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class TestHelper {

    public static String getResource(String name) throws IOException {
        final InputStream resourceAsStream = TestHelper.class.getResourceAsStream(name);
        assert(resourceAsStream != null);
        int n;
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while ((n = resourceAsStream.read(buffer)) != -1) {
            bos.write(buffer, 0, n);
        }
        return new String(bos.toByteArray());
    }
}
