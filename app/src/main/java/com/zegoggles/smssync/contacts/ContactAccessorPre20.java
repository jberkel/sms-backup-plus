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
package com.zegoggles.smssync.contacts;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;

import java.util.Collections;
import java.util.Map;

/**
 * @noinspection UnusedDeclaration
 */
public class ContactAccessorPre20 implements ContactAccessor {
    public String getOwnerEmail(Context context) {
        return null;
    }

    public ContactGroupIds getGroupContactIds(ContentResolver resolver, ContactGroup group) {
        return null;
    }

    public Map<Integer, Group> getGroups(ContentResolver resolver, Resources resources) {
        return Collections.emptyMap();
    }
}
