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

final class Consts {
    static final String TAG = "SmsSync";

    static final String PROTOCOL = "http";

    static final String SERVER = "android-sms.appspot.com";

    // static final String SERVER = "10.0.1.178:8080";

    static final boolean IS_TESTING = SERVER.startsWith("10.");

    static final String LOGIN_URI = PROTOCOL + "://" + SERVER + "/_ah/login?continue=&auth=%auth%"
            + (IS_TESTING ? "&email=something@example.com&action=submit-login" : "");

    static final String REG_URI = PROTOCOL + "://" + SERVER + "/reg";

    static final String SYNC_URI = PROTOCOL + "://" + SERVER + "/sync";

    static final String APP_VERSION = "1";

    static final String APP_ID = "chstuder-androidsms-" + APP_VERSION;

    // TODO(chstuder): Retrieve login from shared prefs.
    static final String LOGIN_USER = "xxxxxxxx@gmail.com";

    static final String LOGIN_PASSWORD = "xxxxxxxx";
}
