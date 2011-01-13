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

/**
 * Class containing application wide constants.
 */
public interface Consts {
    /** Gmail IMAP URI. */
     String IMAP_URI = "imap%s://%s:%s@%s";

    /** Number of times a failed sync attempt should be retried when initiated by an alarm. */
     int NUM_AUTO_RETRIES = 2;

    /**
     * Key in the intent extras for indication whether all unsynced messages should
     * be skipped or not.
     */
     String KEY_SKIP_MESSAGES = "skip_messages";

    /**
     * Key in the intent extras for the number of retries when getting an exception
     * during sync.
     */
     String KEY_NUM_RETRIES = "num_retries";

    /** OAuth callback */
     String CALLBACK_URL = "smssync://gmail";

    // Scopes as defined in http://code.google.com/apis/accounts/docs/OAuth.html#prepScope
     String GMAIL_SCOPE  = "https://mail.google.com/";
     String CONTACTS_SCOPE  = "https://www.google.com/m8/feeds/";
}
