/* Copyright (c) 2009 Christoph Studer <chstuder@gmail.com>
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import static com.zegoggles.smssync.App.*;

public class SmsBroadcastReceiver extends BroadcastReceiver {
    public static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (LOCAL_LOGV) Log.v(TAG, "onReceive("+ctx+","+intent+")");

        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
          bootup(ctx);
        } else if (intent.getAction().equals(SMS_RECEIVED)) {
          incomingSMS(ctx);
        }
    }

    private void bootup(Context ctx) {
		if (PrefStore.isEnableAutoSync(ctx) &&
		    PrefStore.isLoginInformationSet(ctx) &&
		    !PrefStore.isFirstSync(ctx)) {
		
		    Alarms.scheduleRegularSync(ctx);
	    } else {
		        Log.i(TAG, "Received bootup but not set up to sync.");
		}
    }

    private void incomingSMS(Context ctx) {
        if (PrefStore.isEnableAutoSync(ctx) &&
            PrefStore.isLoginInformationSet(ctx) &&
            !PrefStore.isFirstSync(ctx)) {

            Alarms.scheduleIncomingSync(ctx);
        } else {
            Log.i(TAG, "Received SMS but not set up to sync.");
        }
    }
}
