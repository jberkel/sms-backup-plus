package com.zegoggles.smssync.utils;

import org.apache.james.mime4j.codec.EncoderUtil;

public final class Sanitizer {

    private Sanitizer() {}

    public static String sanitize(String s) {
        return s != null ? s.replaceAll("\\p{Cntrl}", "") : null;
    }

    public static String encodeLocal(String s) {
        return (s != null ? EncoderUtil.encodeAddressLocalPart(sanitize(s)) : null);
    }
}
