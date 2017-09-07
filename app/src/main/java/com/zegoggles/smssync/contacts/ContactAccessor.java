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
import android.os.Build;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface ContactAccessor {
    int EVERYBODY_ID = -1;

    /**
     * @param context the context
     * @return the email address of the Android phone owner, or null if not known
     */
    @SuppressWarnings("UnusedDeclaration")
    String getOwnerEmail(Context context);

    /**
     *
     * @param resolver the resolver
     * @param group   the group
     * @return All contacts from a group
     */
    @Nullable ContactGroupIds getGroupContactIds(ContentResolver resolver, ContactGroup group);

    /**
     * All groups a user has
     *
     *
     *
     * @param resolver the resolver
     * @param resources the resources
     * @return the ids and groups
     */
    Map<Integer, Group> getGroups(ContentResolver resolver, Resources resources);

    public static class Get {
        private static ContactAccessor sContactAccessor;

        private Get() {}

        public static ContactAccessor instance() {
            final int sdkVersion = Build.VERSION.SDK_INT;
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
    }
}
