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

public class ImapStore extends com.fsck.k9.mail.store.ImapStore {
    private Context context;

    public ImapStore(final Context context) throws MessagingException {
        super(new Account(Preferences.getPreferences(context), null) {
            @Override
            public String getStoreUri() {
              return PrefStore.getStoreUri(context);
            }
        });
        this.context = context;
    }

    public BackupFolder getBackupFolder() throws MessagingException
    {
        String label = PrefStore.getImapFolder(context);
        if (label == null)
            throw new IllegalStateException("label is null");

        BackupFolder folder = new BackupFolder(this, label);

        if (!folder.exists()) {
            folder.create(FolderType.HOLDS_MESSAGES);
            Log.i(Consts.TAG, "Label '" + label + "' does not exist yet. Creating.");
        }
        folder.open(OpenMode.READ_WRITE);
        return folder;
    }


    public class BackupFolder extends ImapFolder {

        public BackupFolder(ImapStore store, String name) {
            super(store, name);
        }

        public Message[] getMessagesSince(final Date since, int max) throws MessagingException  {
            ImapSearcher searcher = new ImapSearcher()
            {
                public List<ImapResponse> search() throws IOException, MessagingException
                {
                    String sentSince = since != null ? " SENTSINCE " + RFC3501_DATE.format(since) : "";
                    return executeSimpleCommand("UID SEARCH 1:* NOT DELETED" + sentSince);
                }
            };

            Message[] msgs = search(searcher, null);

            Log.i(Consts.TAG, "Found " + msgs.length + " msgs" + (since == null ? "" : " (since " + since + ")"));
            if (max != -1 && msgs.length > max) {
                if (LOCAL_LOGV) Log.v(Consts.TAG, "Fetching envelopes");

                FetchProfile fp = new FetchProfile();
                fp.add(FetchProfile.Item.DATE);
                fetch(msgs, fp, null);

                if (LOCAL_LOGV) Log.v(Consts.TAG, "Sorting");
                //Debug.startMethodTracing("sorting");
                Arrays.sort(msgs, new Comparator<Message>() {
                    public int compare(Message m1, Message m2) {
                        return (m2 != null && m2.getSentDate() != null) ?
                                m2.getSentDate().compareTo(m1.getSentDate()) : -1;
                    }
                });
                //Debug.stopMethodTracing();
                if (LOCAL_LOGV) Log.v(Consts.TAG, "Sorting done");

                Message[] recent = new Message[max];
                System.arraycopy(msgs, 0, recent, 0, max);

                return recent;
            }
            return msgs;
        }
    }
}
