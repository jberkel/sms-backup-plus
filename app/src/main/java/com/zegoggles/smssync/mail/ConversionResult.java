package com.zegoggles.smssync.mail;

import com.fsck.k9.mail.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConversionResult {
    public final DataType type;
    private final List<Message> messages = new ArrayList<Message>();
    private final List<Map<String, String>> mapList = new ArrayList<Map<String, String>>();
    private long maxDate = DataType.Defaults.MAX_SYNCED_DATE;

    public ConversionResult(DataType type) {
        this.type = type;
    }

    public void add(Message message, Map<String, String> map) {
        messages.add(message);
        mapList.add(map);

        String dateHeader = Headers.get(message, Headers.DATE);
        if (dateHeader != null) {
            try {
                final long date = Long.parseLong(dateHeader);
                if (date > maxDate) {
                    maxDate = date;
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public List<Message> getMessages() {
        return messages;
    }

    public long getMaxDate() {
        return maxDate;
    }

    public List<Map<String, String>> getMapList() {
        return mapList;
    }

    public int size() {
        return messages.size();
    }
}
