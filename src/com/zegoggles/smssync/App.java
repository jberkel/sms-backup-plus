package com.zegoggles.smssync;

import android.os.Build;
import android.app.Application;
import com.fsck.k9.K9;

import android.util.Config;

public class App extends Application {
    public static final boolean DEBUG = false;
    public static final boolean LOCAL_LOGV = App.DEBUG ? Config.LOGD : Config.LOGV;

    private static ContactAccessor sAccessor = null;

    @Override
    public void onCreate() {
        super.onCreate();
        K9.app = this;
        K9.DEBUG = DEBUG;
    }

    public static ContactAccessor getContactAccessor() {
       if (sAccessor == null) {
            String className;
            int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
            if (sdkVersion < Build.VERSION_CODES.ECLAIR) {
                className = "ContactAccessorPre20";
            } else {
                className = "ContactAccessorPost20";
            }
            try {
                Class<? extends ContactAccessor> clazz =
                   Class.forName(ContactAccessor.class.getPackage().getName() + "." + className)
                        .asSubclass(ContactAccessor.class);

                sAccessor = clazz.newInstance();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return sAccessor;
    }
}
