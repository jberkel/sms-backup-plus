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

import android.net.Uri;
import android.provider.CallLog;

/**
 * Class containing application wide constants.
 */
public final class Consts {
    private Consts() {}

    /** {@link android.provider.Telephony.Mms#CONTENT_URI} */
    public static final Uri MMS_PROVIDER     = Uri.parse("content://mms");
    public static final String MMS_PART      = "part";

    /** {@link android.provider.Telephony.Sms#CONTENT_URI} */
    public static final Uri SMS_PROVIDER     =  Uri.parse("content://sms");
    public static final Uri CALLLOG_PROVIDER = CallLog.Calls.CONTENT_URI;

    public static class Billing {
        private Billing() {}

        public static final String DONATION_PREFIX = "donation.";

        static final String SKU_DONATION_1 = "donation.1";
        static final String SKU_DONATION_2 = "donation.2";
        static final String SKU_DONATION_3 = "donation.3";

        public static final String[] ALL_SKUS = new String[]{
                SKU_DONATION_1,
                SKU_DONATION_2,
                SKU_DONATION_3,
        };
    }
}
