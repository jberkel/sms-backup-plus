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
import android.content.res.Resources;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import com.zegoggles.smssync.R;

import java.util.LinkedHashMap;
import java.util.Map;


public class ContactAccessor {
    static final int EVERYBODY_ID = -1;

    /**
     * @param resolver the resolver
     * @param resources the resources
     * @return the ids and groups a user has
     * @throws SecurityException if READ_CONTACTS permission is missing
     */
    public Map<Integer, Group> getGroups(ContentResolver resolver, Resources resources) {
        final Map<Integer, Group> map = new LinkedHashMap<Integer, Group>();

        map.put(EVERYBODY_ID, new Group(EVERYBODY_ID, resources.getString(R.string.everybody), 0));

        final Cursor c = resolver.query(
                Groups.CONTENT_SUMMARY_URI,
                new String[]{Groups._ID, Groups.TITLE, Groups.SUMMARY_COUNT},
                null,
                null,
                Groups.TITLE + " ASC");

        while (c != null && c.moveToNext()) {
            map.put(c.getInt(0), new Group(c.getInt(0), c.getString(1), c.getInt(2)));
        }

        if (c != null) c.close();
        return map;
    }

    /**
     * @param resolver the resolver
     * @param group  the group
     * @return all contacts from a group
     * @throws SecurityException if READ_CONTACTS permission is missing
     */
    public ContactGroupIds getGroupContactIds(ContentResolver resolver, ContactGroup group) {
        if (group.isEveryBody()) return null;

        final ContactGroupIds contactIds = new ContactGroupIds();
        Cursor c = resolver.query(
                Data.CONTENT_URI,
                new String[]{
                        GroupMembership.CONTACT_ID,
                        GroupMembership.RAW_CONTACT_ID,
                        GroupMembership.GROUP_ROW_ID
                },
                GroupMembership.GROUP_ROW_ID + " = ? AND " + GroupMembership.MIMETYPE + " = ?",
                new String[]{String.valueOf(group._id), GroupMembership.CONTENT_ITEM_TYPE},
                null);
        while (c != null && c.moveToNext()) {
            contactIds.add(c.getLong(0), c.getLong(1));
        }
        if (c != null) c.close();
        return contactIds;
    }
}
