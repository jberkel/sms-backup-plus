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
import android.util.Log;
import com.fsck.k9.Account;
import com.fsck.k9.mail.FetchProfile;
import com.fsck.k9.mail.Folder.FolderType;
import com.fsck.k9.mail.Folder.OpenMode;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.store.ImapResponseParser.ImapResponse;
import com.fsck.k9.mail.store.ImapStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.CursorToMessage.DataType;
import static com.zegoggles.smssync.CursorToMessage.Headers;

public class BackupImapStore extends ImapStore {
    private Context context;

    static {
      // increase read timeout a bit
      com.fsck.k9.mail.Store.SOCKET_READ_TIMEOUT = 60000 * 5;
    }

    public BackupImapStore(final Context context) throws MessagingException {
        super(new Account(context) {
            @Override public String getStoreUri() {
              return PrefStore.getStoreUri(context);
            }
        });
        this.context = context;
    }

    public BackupFolder getSMSBackupFolder() throws MessagingException
    {
        String label = PrefStore.getImapFolder(context);
        return getBackupFolder(label, DataType.SMS);
    }

    public BackupFolder getCallLogBackupFolder() throws MessagingException
    {
        String label = PrefStore.getCallLogFolder(context);
        return getBackupFolder(label, DataType.CALLLOG);
    }

    public BackupFolder getWhatsAppBackupFolder() throws MessagingException
    {
        String label = PrefStore.getWhatsAppFolder(context);
        return getBackupFolder(label, DataType.WHATSAPP);
    }


    private BackupFolder getBackupFolder(String label, DataType type) throws MessagingException
    {
        if (label == null) throw new IllegalStateException("label is null");

        try {
          final BackupFolder folder = new BackupFolder(this, label, type);

          if (!folder.exists()) {
              folder.create(FolderType.HOLDS_MESSAGES);
              Log.i(TAG, "Label '" + label + "' does not exist yet. Creating.");
          }
          folder.open(OpenMode.READ_WRITE);
          return folder;
        } catch (java.lang.NumberFormatException e) {
          // thrown inside K9
          Log.e(TAG, "K9 error", e);
          throw new MessagingException(e.getMessage());
        }
    }

    public class BackupFolder extends ImapFolder {
        private final DataType type;

        public BackupFolder(ImapStore store, String name, DataType type) {
            super(store, name);
            this.type = type;
        }

        public List<Message> getMessages(final int max, final boolean flagged, final Date since)
          throws MessagingException {
            if (LOCAL_LOGV) Log.v(TAG, String.format(Locale.ENGLISH, "getMessages(%d, %b, %s)", max, flagged, since));

            final List<Message> messages;
            final ImapSearcher searcher = new ImapSearcher() {
                @Override public List<ImapResponse> search() throws IOException, MessagingException {
                    final StringBuilder sb = new StringBuilder("UID SEARCH 1:*")
                        .append(' ')
                        .append(getQuery())
                        .append(" UNDELETED");
                    if (since != null) sb.append(" SENTSINCE ").append(RFC3501_DATE.format(since));
                    if (flagged) sb.append(" FLAGGED");

                    return executeSimpleCommand(sb.toString().trim());
                }
            };

            final Message[] msgs = search(searcher, null);

            Log.i(TAG, "Found " + msgs.length + " msgs" + (since == null ? "" : " (since " + since + ")"));
            if (max > 0 && msgs.length > max) {
                if (LOCAL_LOGV) Log.v(TAG, "Fetching envelopes");

                FetchProfile fp = new FetchProfile();
                fp.add(FetchProfile.Item.DATE);
                fetch(msgs, fp, null);

                if (LOCAL_LOGV) Log.v(TAG, "Sorting");
                //Debug.startMethodTracing("sorting");
                Arrays.sort(msgs, MessageComparator.INSTANCE);
                //Debug.stopMethodTracing();
                if (LOCAL_LOGV) Log.v(TAG, "Sorting done");

                messages = new ArrayList<Message>(max);
                messages.addAll(Arrays.asList(msgs).subList(0, max));
            } else {
                messages = new ArrayList<Message>(msgs.length);
                Collections.addAll(messages, msgs);
            }

            Collections.reverse(messages);
            
            return messages;
        }

        private String getQuery() {
           switch(this.type) {
            /* MMS/SMS are special cases since we need to support legacy backup headers */
            case SMS:
              return
              String.format(Locale.ENGLISH, "(OR HEADER %s \"%s\" (NOT HEADER %s \"\" (OR HEADER %s \"%d\" HEADER %s \"%d\")))",
                            Headers.DATATYPE.toUpperCase(Locale.ENGLISH), type,
                            Headers.DATATYPE.toUpperCase(Locale.ENGLISH),
                            Headers.TYPE.toUpperCase(Locale.ENGLISH), SmsConsts.MESSAGE_TYPE_INBOX,
                            Headers.TYPE.toUpperCase(Locale.ENGLISH), SmsConsts.MESSAGE_TYPE_SENT);
            case MMS:
              return
              String.format(Locale.ENGLISH, "(OR HEADER %s \"%s\" (NOT HEADER %s \"\" HEADER %s \"%s\"))",
                            Headers.DATATYPE.toUpperCase(Locale.ENGLISH), type,
                            Headers.DATATYPE.toUpperCase(Locale.ENGLISH),
                            Headers.TYPE.toUpperCase(Locale.ENGLISH), MmsConsts.LEGACY_HEADER);

            default: return String.format(Locale.ENGLISH, "(HEADER %s \"%s\")", Headers.DATATYPE.toUpperCase(Locale.ENGLISH), type);
           }
        }

        @Override public boolean equals(Object o) { return super.equals(o); }
        @Override public int hashCode() { return super.hashCode(); }
    }

    static class MessageComparator implements Comparator<Message> {
      static final MessageComparator INSTANCE = new MessageComparator();
      static final Date EARLY = new Date(0);

      public int compare(final Message m1, final Message m2) {
          final Date d1 = m1 == null ? EARLY : m1.getSentDate() != null ? m1.getSentDate() : EARLY;
          final Date d2 = m2 == null ? EARLY : m2.getSentDate() != null ? m2.getSentDate() : EARLY;
          return d2.compareTo(d1);
      }
    }
}
