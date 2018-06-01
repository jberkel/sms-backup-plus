package com.zegoggles.smssync.contacts;

import java.util.Locale;

public class Group {
    final String title;
    final int _id, count;

    Group(int id, String title, int count) {
        this._id = id;
        this.title = title;
        this.count = count;
    }

    public String toString() {
        return count > 0 ? String.format(Locale.ENGLISH, "%s (%d)", title, count) : title;
    }
}
