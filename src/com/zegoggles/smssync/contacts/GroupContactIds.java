package com.zegoggles.smssync.contacts;

import java.util.HashSet;
import java.util.Set;

public class GroupContactIds {
    public final Set<Long> ids = new HashSet<Long>();
    public final Set<Long> rawIds = new HashSet<Long>();

    public String toString() {
        return getClass().getSimpleName() + "[ids: " + ids + " rawIds: " + rawIds + "]";
    }
}
