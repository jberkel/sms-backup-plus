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


                 return String.format("imap+ssl+://%s:%s@imap.gmail.com:993",
                     URLEncoder.encode(username),
                     URLEncoder.encode(password).replace("+", "%20"));
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
    }
}