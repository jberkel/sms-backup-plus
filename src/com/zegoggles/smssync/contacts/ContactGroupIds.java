package com.zegoggles.smssync.contacts;

import com.zegoggles.smssync.mail.PersonRecord;

import java.util.HashSet;
import java.util.Set;

public class ContactGroupIds {
    private final Set<Long> ids = new HashSet<Long>();
    private final Set<Long> rawIds = new HashSet<Long>();

    public void add(long id, long rawId) {
        this.ids.add(id);
        this.rawIds.add(rawId);
    }

    public boolean contains(PersonRecord personRecord) {
        return ids.contains(personRecord.getContactId());
    }

    public boolean isEmpty() {
        return ids.isEmpty() && rawIds.isEmpty();
    }

    public Set<Long> getIds() {
        return new HashSet<Long>(ids);
    }

    public Set<Long> getRawIds() {
        return new HashSet<Long>(rawIds);
    }

    public String toString() {
        return getClass().getSimpleName() + "[ids: " + ids + " rawIds: " + rawIds + "]";
    }
}
