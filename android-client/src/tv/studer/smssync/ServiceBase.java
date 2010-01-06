
package tv.studer.smssync;

import java.net.URLEncoder;
import java.util.List;

import tv.studer.smssync.CursorToMessage.ConversionResult;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import com.android.email.mail.Folder;
import com.android.email.mail.Message;
import com.android.email.mail.MessageRetrievalListener;
import com.android.email.mail.MessagingException;
import com.android.email.mail.Folder.FolderType;
import com.android.email.mail.Folder.OpenMode;
import com.android.email.mail.store.ImapStore;

public abstract class ServiceBase extends Service {
  
    public static String getHeader(Message msg, String header) {
      try {
        String[] hdrs = msg.getHeader(header);
        if (hdrs != null && hdrs.length > 0) {
          return hdrs[0];        
        } 
      } catch (MessagingException e) {        
      }
      return null;
    }

    @Override
    public IBinder onBind(Intent arg0) {
       return null;
    }
    
    protected Folder getBackupFolder() 
      throws AuthenticationErrorException {
      
      String username = PrefStore.getLoginUsername(this);
      String password = PrefStore.getLoginPassword(this);      
      String label = PrefStore.getImapFolder(this);

      if (username == null) 
        throw new IllegalArgumentException("username is null");
      if (password == null) 
        throw new IllegalArgumentException("password is null");
      if (label == null) 
        throw new IllegalArgumentException("label is null");        
        
      try {
        ImapStore imapStore = new ImapStore(String.format(Consts.IMAP_URI, URLEncoder.encode(username),
                URLEncoder.encode(password).replace("+", "%20")));
        Folder folder = imapStore.getFolder(label);
       
        if (!folder.exists()) {
            Log.i(Consts.TAG, "Label '" + label + "' does not exist yet. Creating.");
            folder.create(FolderType.HOLDS_MESSAGES);
        }
        folder.open(OpenMode.READ_WRITE);
        return folder;
      } catch (MessagingException e) {
        throw new AuthenticationErrorException(e);
      }
    }    
 
    /**
      * Exception indicating an error while synchronizing.
      */
     public static class GeneralErrorException extends Exception {
         private static final long serialVersionUID = 1L;

         public GeneralErrorException(String msg, Throwable t) {
             super(msg, t);
         }

         public GeneralErrorException(Context ctx, int msgId, Throwable t) {
             super(ctx.getString(msgId), t);
         }
     }

     public static class AuthenticationErrorException extends Exception {
         private static final long serialVersionUID = 1L;

         public AuthenticationErrorException(Throwable t) {
             super(t.getLocalizedMessage(), t);
         }
     }
  
}