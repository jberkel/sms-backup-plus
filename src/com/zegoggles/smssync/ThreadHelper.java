package com.zegoggles.smssync;

import android.util.Log;
import android.content.Context;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.NoSuchMethodException;
import java.lang.ClassNotFoundException;
import java.util.Map;
import java.util.LinkedHashMap;

import static com.zegoggles.smssync.App.*;

public class ThreadHelper {

    private Class telephonyThreads;
    private Method getOrCreateThreadId;
    private boolean threadsAvailable = true;

    private static final int MAX_THREAD_CACHE_SIZE = 500;

    private Map<String, Long> mThreadIdCache =
          new LinkedHashMap<String, Long>(MAX_THREAD_CACHE_SIZE+1, .75F, true) {
          @Override public boolean removeEldestEntry(Map.Entry eldest) {
            return size() > MAX_THREAD_CACHE_SIZE;
          }
      };

    public Long getThreadId(final Context context, final String recipient) {
      if (recipient == null || !threadsAvailable) return null;

      if (mThreadIdCache.containsKey(recipient)) {
        return mThreadIdCache.get(recipient);
      } else if (getOrCreateThreadId == null) {
        try {
          telephonyThreads = Class.forName("android.provider.Telephony$Threads");
          getOrCreateThreadId = telephonyThreads.getMethod("getOrCreateThreadId",
                                                  new Class[] { Context.class, String.class });
        } catch (NoSuchMethodException e) {
          return noThreadsAvailable(e);
        } catch (ClassNotFoundException e) {
          return noThreadsAvailable(e);
        }
      }

      try {
        final Long id = (Long) getOrCreateThreadId.invoke(telephonyThreads,
                                                    new Object[] { context, recipient });
        if (LOCAL_LOGV) Log.v(TAG, "threadId for " + recipient + ": " + id);
        if (id != null) mThreadIdCache.put(recipient, id);

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
