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

    /**
     * Key in the intent extras for indication whether all unsynced messages should
     * be skipped or not.
     */
    public static final String KEY_SKIP_MESSAGES = "com.zegoggles.smssync.SkipMessages";

    /**
     * Key in the intent extras for storing the previously set default sms provider,
     * allowing it to be restored after the restore finishes.
     */
    public static final String KEY_DEFAULT_SMS_PROVIDER = "com.zegoggles.smssync.DefaultSmsProvider";

    /**
     * OAuth callback
     */
    public static final String CALLBACK_URL = "smssync://gmail";


    public static final Uri MMS_PROVIDER     = Uri.parse("content://mms");
    public static final String MMS_PART      = "part";
    public static final Uri SMS_PROVIDER     = Uri.parse("content://sms");
    public static final Uri CALLLOG_PROVIDER = CallLog.Calls.CONTENT_URI;

    public static class Billing {
        public static final String PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCNglCUwSijU3RAODpyY" +
                "fZLYxwF/OveAxYgVKWYlJDf8KBhO3T81BrQHDVACmaLpBfS+uL7RrIb9PMk8RNewE8EAOLNXANnqbbV+8U54K7GX2N" +
                "wFMIBZ4tV52G6yEhWkKx/+JMLdhM5U8FutaFCdmdkDrz3IVGB5HD8c1mmopcqcQIDAQAB";

        public static final String DONATION_PREFIX = "donation.";

        public static final String SKU_DONATION_1 = "donation.1";
        public static final String SKU_DONATION_2 = "donation.2";
        public static final String SKU_DONATION_3 = "donation.3";

        public static final String[] ALL_SKUS = new String[]{
                SKU_DONATION_1,
                SKU_DONATION_2,
                SKU_DONATION_3,
        };
    }
}
