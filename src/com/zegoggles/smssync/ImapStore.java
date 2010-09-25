package com.zegoggles.smssync;

import android.content.Context;
import com.fsck.k9.Account;
import com.fsck.k9.Preferences;
import com.fsck.k9.mail.Folder.FolderType;
import com.fsck.k9.mail.Folder.OpenMode;
import com.fsck.k9.mail.*;

import android.util.Log;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;
import java.util.Comparator;
import java.util.Arrays;
import com.fsck.k9.mail.store.ImapResponseParser.ImapResponse;
import java.io.IOException;

public class ImapStore extends com.fsck.k9.mail.store.ImapStore {
    private Context context;

    public ImapStore(final Context context) throws MessagingException {

        super(new Account(Preferences.getPreferences(context), null) {
            @Override
            public String getStoreUri() {
                String username = PrefStore.getLoginUsername(context);
                if (username == null)
                     throw new IllegalStateException("username is null");

                if (PrefStore.useXOAuth(context)) {
                  return String.format(Consts.IMAP_URI,
                       PrefStore.getServerProtocol(context),
                        "xoauth:" + URLEncoder.encode(username),
                       URLEncoder.encode(PrefStore.getOAuthConsumer(context).generateXOAuthString()),
                       PrefStore.getServerAddress(context));
                } else {
                    String password = PrefStore.getLoginPassword(context);
                    if (password == null)
                        throw new IllegalStateException("password is null");

                    return String.format(Consts.IMAP_URI,
                       PrefStore.getServerProtocol(context),
                       URLEncoder.encode(username),
                       URLEncoder.encode(password).replace("+", "%20"),
                       PrefStore.getServerAddress(context));
                 }
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
            Log.i(Consts.TAG, "Label '" + label + "' does not exist yet. Creating.");
            folder.create(FolderType.HOLDS_MESSAGES);
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

            Log.d(Consts.TAG, "Found " + msgs.length + " msgs" + (since == null ? "" : " (since " + since + ")"));

            if (max != -1 && msgs.length > max) {
                Log.d(Consts.TAG, "Fetching envelopes");

                FetchProfile fp = new FetchProfile();
                fp.add(FetchProfile.Item.DATE);
                fetch(msgs, fp, null);

                Log.d(Consts.TAG, "Sorting");
                //Debug.startMethodTracing("sorting");
                Arrays.sort(msgs, new Comparator<Message>() {
                    public int compare(Message m1, Message m2) {
                        return (m2 != null && m2.getSentDate() != null) ?
                                m2.getSentDate().compareTo(m1.getSentDate()) : -1;
                    }
                });
                //Debug.stopMethodTracing();

                Message[] recent = new Message[max];
                System.arraycopy(msgs, 0, recent, 0, max);

                return recent;
            }
            return msgs;
        }
    }
}
