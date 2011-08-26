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
import java.util.Set;
import java.util.HashSet;
import java.util.Map;

public interface ContactAccessor {
  int EVERYBODY_ID = -1;

  static class ContactGroup {
    public final long _id;
    public final Type type;

    enum Type { EVERYBODY, GROUP }

    public ContactGroup(final long id) {
      this._id  = id;
      this.type = (id == EVERYBODY_ID ? Type.EVERYBODY : Type.GROUP);
    }
  }

  static class Group {
    String title;
    int _id, count;

    public Group(int id, String title, int count) {
      this._id = id; this.title = title; this.count = count;
    }
    public String toString() { return count > 0 ? String.format("%s (%d)", title, count) : title; }
  }

  static class GroupContactIds {
    public Set<Long> ids    = new HashSet<Long>();
    public Set<Long> rawIds = new HashSet<Long>();
    public String toString() {
      return getClass().getSimpleName() + "[ids: " + ids + " rawIds: " + rawIds + "]";
    }
  }

    /**
     * @param context the context
     * @return the email address of the Android phone owner, or null if not known
     */
    String getOwnerEmail(Context context);

    /**
     * @param context the context
     * @param group the group
     * @return All contacts from a group
     */
  GroupContactIds getGroupContactIds(Context context, ContactGroup group);

  /**
   * All groups a user has
   *
   * @param ctxt the context
   * @return the ids and groups
   */
  Map<Integer, Group> getGroups(Context ctxt);
}
