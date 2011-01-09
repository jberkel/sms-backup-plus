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
import com.fsck.k9.Account;
import com.fsck.k9.Preferences;
import com.fsck.k9.mail.Folder.FolderType;
import com.fsck.k9.mail.Folder.OpenMode;
import com.fsck.k9.mail.*;

import android.util.Log;
import java.util.Date;
import java.util.List;
import java.util.Comparator;
import java.util.Arrays;
import com.fsck.k9.mail.store.ImapResponseParser.ImapResponse;
import java.io.IOException;

import static com.zegoggles.smssync.App.*;
import static com.zegoggles.smssync.CursorToMessage.Headers;
import static com.zegoggles.smssync.CursorToMessage.DataType;

public class ImapStore extends com.fsck.k9.mail.store.ImapStore {
    private Context context;

    static {
      // increase read timeout a bit
      com.fsck.k9.mail.Store.SOCKET_READ_TIMEOUT = 60000 * 3;
    }

    public ImapStore(final Context context) throws MessagingException {
        super(new Account(context) {
            @Override
            public String getStoreUri() {
              return PrefStore.getStoreUri(context);
            }
        });
        this.context = context;
    }

    public BackupFolder getSMSBackupFolder() throws MessagingException
    {
        String label = PrefStore.getImapFolder(context);
        return getBackupFolder(label);
    }
    public BackupFolder getCalllogBackupFolder() throws MessagingException
    {
        String label = PrefStore.getCalllogFolder(context);
        return getBackupFolder(label);
    }

    private BackupFolder getBackupFolder(String label) throws MessagingException
    {
        if (label == null)
            throw new IllegalStateException("label is null");

        try {
          BackupFolder folder = new BackupFolder(this, label);

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

        public BackupFolder(ImapStore store, String name) {
            super(store, name);
        }

        public Message[] getMessagesSince(final Date since, final int max, final boolean flagged)
          throws MessagingException  {
            if (LOCAL_LOGV) Log.v(TAG, String.format("getMessagesSince(%s, %d, %b)", since, max, flagged));

            final ImapSearcher searcher = new ImapSearcher() {
                @Override public List<ImapResponse> search() throws IOException, MessagingException {
                   final String HEADER_Q = String.format("OR HEADER %s \"%s\" (NOT HEADER %s \"\" (OR HEADER %s \"%d\" HEADER %s \"%d\"))",
                                                          Headers.DATATYPE, DataType.SMS,
                                                          Headers.DATATYPE,
                                                          Headers.TYPE, SmsConsts.MESSAGE_TYPE_INBOX,
                                                          Headers.TYPE, SmsConsts.MESSAGE_TYPE_SENT);
                    StringBuilder sb = new StringBuilder(String.format("UID SEARCH 1:* (%s) UNDELETED", HEADER_Q));
                    if (since != null) sb.append(" SENTSINCE ").append(RFC3501_DATE.format(since));
                    if (flagged) sb.append(" FLAGGED");

                    return executeSimpleCommand(sb.toString());
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

                final Message[] recent = new Message[max];
                System.arraycopy(msgs, 0, recent, 0, max);

                return recent;
            }
            return msgs;
        }
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
