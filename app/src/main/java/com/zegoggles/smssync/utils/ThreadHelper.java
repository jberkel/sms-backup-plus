package com.zegoggles.smssync.utils;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

public class ThreadHelper {

    private Class<?> telephonyThreads;
    private Method getOrCreateThreadId;
    private boolean threadsAvailable = true;

    private static final int MAX_THREAD_CACHE_SIZE = 500;

    @SuppressWarnings("serial")
    private Map<String, Long> threadIdCache =
            new LinkedHashMap<String, Long>(MAX_THREAD_CACHE_SIZE + 1, .75F, true) {
                @Override
                public boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > MAX_THREAD_CACHE_SIZE;
                }
            };

    public Long getThreadId(final Context context, final String recipient) {
        if (recipient == null || !threadsAvailable) return null;

        if (threadIdCache.containsKey(recipient)) {
            return threadIdCache.get(recipient);
        } else if (getOrCreateThreadId == null) {
            try {
                telephonyThreads = Class.forName("android.provider.Telephony$Threads");
                getOrCreateThreadId = telephonyThreads.getMethod("getOrCreateThreadId",
                        Context.class, String.class);
            } catch (NoSuchMethodException e) {
                return noThreadsAvailable(e);
            } catch (ClassNotFoundException e) {
                return noThreadsAvailable(e);
            }
        }

        try {
            final Long id = (Long) getOrCreateThreadId.invoke(telephonyThreads,
                    context, recipient);
            if (LOCAL_LOGV) Log.v(TAG, "threadId for " + recipient + ": " + id);
            if (id != null) threadIdCache.put(recipient, id);

            return id;
        } catch (InvocationTargetException e) {
            return noThreadsAvailable(e);
        } catch (IllegalAccessException e) {
            return noThreadsAvailable(e);
        }
    }

    private Long noThreadsAvailable(Throwable e) {
        Log.e(TAG, "threadsNotAvailable", e);
        threadsAvailable = false;
        return null;
    }
}
