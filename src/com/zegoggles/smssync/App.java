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

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;
import android.os.Build;

import com.fsck.k9.K9;

@ReportsCrashes(formUri = "https://bugsense.appspot.com/api/acra?api_key=a2603e16", formKey = "")
public class App extends Application {
    public static final boolean DEBUG = BuildConfig.DEBUG;
    public static final boolean LOCAL_LOGV = DEBUG;
    public static final String TAG = "SmsBackup+";

    private static ContactAccessor sContactAccessor = null;
    private static CalendarAccessor sCalendarAccessor = null;

    public static final String LOG = "sms_backup_plus.log";

    @Override
    public void onCreate() {
      ACRA.init(this);
      super.onCreate();
      K9.app = this;
      K9.DEBUG = DEBUG;
      K9.DEBUG_PROTOCOL_IMAP = DEBUG;
    }

    public static ContactAccessor contactAccessor() {
      int sdkVersion = Build.VERSION.SDK_INT;
      if (sContactAccessor == null) {
        try {
          if (sdkVersion < Build.VERSION_CODES.ECLAIR) {
            sContactAccessor = new ContactAccessorPre20();
          } else {
            sContactAccessor = new ContactAccessorPost20();
          }
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      }
      return sContactAccessor;
    }

    public static CalendarAccessor calendarAccessor() {
        int sdkVersion = Build.VERSION.SDK_INT;
      if (sCalendarAccessor == null) {
        try {
          if (sdkVersion < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            sCalendarAccessor = new CalendarAccessorPre40();
          } else {
            sCalendarAccessor = new CalendarAccessorPost40();
          }
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      }
      return sCalendarAccessor;
    }
}
