package tv.studer.smssync;

import android.content.Context;
import com.fsck.k9.K9;
import com.fsck.k9.Preferences;
import com.fsck.k9.Account;
import com.fsck.k9.Preferences;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Folder.FolderType;
import com.fsck.k9.mail.Folder.OpenMode;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.*;

import android.util.Log;
import android.os.Debug;
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
                String password = PrefStore.getLoginPassword(context);

                if (username == null)
                     throw new IllegalArgumentException("username is null");
                if (password == null)
                     throw new IllegalArgumentException("password is null");
                // TODO use URI ctor
                return String.format(Consts.IMAP_URI,
                     PrefStore.getServerProtocol(context),
                     URLEncoder.encode(username),
                     URLEncoder.encode(password).replace("+", "%20"),
                     PrefStore.getServerAddress(context));
            }
        });

        this.context = context;
        K9.app = context;
        K9.DEBUG = true;
    }

    public BackupFolder getBackupFolder() throws MessagingException
    {
        String label = PrefStore.getImapFolder(context);
        if (label == null)
            throw new IllegalArgumentException("label is null");

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
                        return m2.getSentDate().compareTo(m1.getSentDate());
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
