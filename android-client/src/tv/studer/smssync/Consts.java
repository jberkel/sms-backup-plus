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

    /**
     * Protocol used to communicate with server. NOTE: Google login is always
     * via HTTPS.
     */
    static final String PROTOCOL = "http";

    /** Server address. */
//    static final String SERVER = "android-sms.appspot.com";

    // Uncomment next line to use for internal testing.
    static final String SERVER = "10.0.1.178:8080";
    
    /** Whether we're running against a local 'fake' AppEngine instance. */
    static final boolean IS_TESTING = SERVER.startsWith("10.");

    /** URI used to obtain the necessary token for the android-sms app. */
    static final String LOGIN_URI = PROTOCOL + "://" + SERVER + "/_ah/login?continue=&auth=%auth%"
            + (IS_TESTING ? "&email=something@example.com&action=submit-login" : "");

    /** Full URI for registering a new device. */
    static final String REG_URI = PROTOCOL + "://" + SERVER + "/reg";

    /** Full URI for performing a sync. */
    static final String SYNC_URI = PROTOCOL + "://" + SERVER + "/sync";

    /** Version of this application. This is sent when syncing. */
    static final String APP_VERSION = "1";

    /** Application ID used for Google ClientLogin. */
    static final String APP_ID = "chstuder-androidsms-" + APP_VERSION;

    /**
     * Key in the intent extras indicating whether all unsynced messages should
     * be skipped or not.
     */
    static final String KEY_SKIP_MESSAGES = "skip_messages";
}
