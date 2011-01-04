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

import android.content.Context;
import android.database.Cursor;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.util.Log;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;

import static com.zegoggles.smssync.App.*;

public class ContactAccessorPost20 implements ContactAccessor {
  public String getOwnerEmail(Context context) {
      AccountManager mgr = AccountManager.get(context);
      for (Account acc : mgr.getAccountsByType("com.google")) {
        return acc.name;
      }
      return null;
  }

  public GroupContactIds getGroupContactIds(Context context, ContactGroup group) {
      final GroupContactIds contactIds = new GroupContactIds();
      Cursor c = null;
      switch (group.type) {
       case GROUP:
           c = context.getContentResolver().query(
              Data.CONTENT_URI,
              new String[] { GroupMembership.CONTACT_ID, GroupMembership.RAW_CONTACT_ID,
                             GroupMembership.GROUP_ROW_ID },
              GroupMembership.GROUP_ROW_ID + " = ? AND " + GroupMembership.MIMETYPE + " = ?",
              new String[] { String.valueOf(group._id), GroupMembership.CONTENT_ITEM_TYPE },
              null);
           break;
      }
      while (c != null && c.moveToNext()) {
        contactIds.ids.add(c.getLong(0));
        contactIds.rawIds.add(c.getLong(1));
      }

      if (c!=null) c.close();
      return contactIds;
  }

  public Map<Integer, Group> getGroups(Context context) {
    final Map<Integer, Group> map = new LinkedHashMap<Integer, Group>();

    map.put(Integer.valueOf(EVERYBODY_ID), new Group(EVERYBODY_ID, context.getString(R.string.everybody), 0));

    final Cursor c = context.getContentResolver().query(
              Groups.CONTENT_SUMMARY_URI,
              new String[] { Groups._ID, Groups.TITLE, Groups.SUMMARY_COUNT },
              null,
              null,
              Groups.TITLE + " ASC");

    while (c != null && c.moveToNext()) {
      map.put(c.getInt(0), new Group(c.getInt(0), c.getString(1), c.getInt(2)));
    }

    if (c != null) c.close();
    return map;
  }
}
