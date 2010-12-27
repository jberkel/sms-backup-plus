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

public interface MmsConsts {

   String ID = "_id";

   String MESSAGE_ID = "m_id";

   String READ = "read";

   String SUBJECT = "sub";

   String DATE = "date";

   String THREAD_ID = "thread_id";

   String INSERT_ADDRESS_TOKEN = "insert-address-token";

   String TYPE = "m_type";

   String DELIVERY_REPORT = "134"; // 0x86

   int MESSAGE_BOX_INBOX = 1;
   int MESSAGE_BOX_SENT  = 2;

   int TO                = 0x97;
   int FROM              = 0x89;

   String LEGACY_HEADER  = "mms";
}
