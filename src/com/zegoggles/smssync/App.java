/*
 * Copyright (c) 2010 Jan Berkel <jan.berkel@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zegoggles.smssync;

import android.os.Build;
import android.app.Application;
import com.fsck.k9.K9;

import android.util.Config;

public class App extends Application {
    public static final boolean DEBUG = true;
    public static final boolean LOCAL_LOGV = App.DEBUG ? Config.LOGD : Config.LOGV;
    public static final String TAG = "SmsBackup+";

    private static ContactAccessor sAccessor = null;

    @Override
    public void onCreate() {
        super.onCreate();
        K9.app = this;
        K9.DEBUG = DEBUG;
        K9.DEBUG_PROTOCOL_IMAP = DEBUG;
    }

    public static ContactAccessor contacts() {
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
