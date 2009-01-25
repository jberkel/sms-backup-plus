/* Copyright (c) 2009 Christoph Studer <chstuder@gmail.com>
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

package tv.studer.smssync;

/**
 * Class containing application wide constants.
 */
final class Consts {
    
    /** TAG used for logging. */
    static final String TAG = "SmsSync";

    /** Gmail IMAP URI. */
    static final String IMAP_URI = "imap+ssl+://%s:%s@imap.gmail.com:993";
    
    /** Number of times a failed sync attempt should be retried when initiated by an alarm. */
    static final int NUM_AUTO_RETRIES = 2;
    
    /**
     * Key in the intent extras for indication whether all unsynced messages should
     * be skipped or not.
     */
    static final String KEY_SKIP_MESSAGES = "skip_messages";
    
    /**
     * Key in the intent extras for the number of retries when getting an exception
     * during sync.
     */
    static final String KEY_NUM_RETRIES = "num_retries";
    
    /** Website containing more information about this application. */
    static final String URL_INFO_LINK = "http://code.google.com/p/android-sms/wiki/Info";

    /** Market link to details of this application. */
    static final String URL_MARKET_SEARCH =
        "http://market.android.com/search?q=pname:tv.studer.smssync";
    
}
