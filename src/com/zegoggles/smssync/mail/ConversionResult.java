package com.zegoggles.smssync.mail;

import com.fsck.k9.mail.Message;
import com.zegoggles.smssync.preferences.PrefStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConversionResult {
    public final DataType type;
    public final List<Message> messageList = new ArrayList<Message>();
    public final List<Map<String, String>> mapList = new ArrayList<Map<String, String>>();
    public long maxDate = PrefStore.DEFAULT_MAX_SYNCED_DATE;

    public ConversionResult(DataType type) {
        this.type = type;
    }
}
