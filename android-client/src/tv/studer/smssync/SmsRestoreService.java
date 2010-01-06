
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
import android.os.AsyncTask;
import android.content.ContentValues;

import com.android.email.mail.Folder;
import com.android.email.mail.Message;
import com.android.email.mail.FetchProfile;
import com.android.email.mail.internet.BinaryTempFileBody;
import com.android.email.mail.MessageRetrievalListener;
import com.android.email.mail.MessagingException;
import com.android.email.mail.Folder.FolderType;
import com.android.email.mail.Folder.OpenMode;
import com.android.email.mail.store.ImapStore;

public class SmsRestoreService extends ServiceBase {
  
  public static final String TAG = "SmsRestoreService";
  
  
  @Override
  public void onCreate() {
    BinaryTempFileBody.setTempDirectory(getCacheDir());     
  }
  
  @Override
  public void onStart(final Intent intent, int startId) {
     super.onStart(intent, startId);
      
     AsyncTask task = new AsyncTask<Void, Integer, Integer>() {
       protected Integer doInBackground(Void... params) {
          try {
            restore();
          } catch (Throwable t) {
            Log.e(Consts.TAG, "error", t);
          }
          return 0;
       }

       protected void onProgressUpdate(Integer... progress) {
         
       }

       protected void onPostExecute(Integer result) {

       }       
     };        
       
      task.execute(null);  
   }
   
   private void restore() 
     throws MessagingException, AuthenticationErrorException {

     Folder folder = getBackupFolder();

     Message[] msgs = folder.getMessages(new MessageRetrievalListener() {        
       public void messageStarted(String uid, int number, int ofTotal) {}
       public void messageFinished(Message message, int number, int ofTotal) {
         Log.d(TAG, "messageFinished: " + message);   
         
         
         FetchProfile fp = new FetchProfile();                    
         fp.add(FetchProfile.Item.BODY);
         
         try {
           message.getFolder().fetch(new Message[] { message }, fp, null);         
          
            
           ContentValues values = messageToContentValues(message);
           Uri uri = getContentResolver().insert(Uri.parse("content://sms"), values);
           
           Log.d(TAG, "inserted " + uri);
           
      
         } catch (java.io.IOException e) {
           Log.e(TAG, "error", e);
         } catch (MessagingException e) {
           Log.e(TAG, "error", e);
         }
       }
       public void messagesFinished(int total) {
         Log.d(TAG, "retrieved " + total + " messages");              
       }         
     });
     
     Log.d(TAG, "finished:" + msgs.length);
   }   
   
   private ContentValues messageToContentValues(Message message) 
    throws java.io.IOException, MessagingException {
      ContentValues values = new ContentValues();
      
      String body = org.apache.commons.io.IOUtils.toString(message.getBody().getInputStream());
      
      values.put(SmsConsts.BODY, body);
      values.put("address", getHeader(message, "X-smssync-address"));
      values.put(SmsConsts.TYPE, getHeader(message, "X-smssync-type"));
      values.put(SmsConsts.PROTOCOL, getHeader(message, "X-smssync-protocol"));
      values.put(SmsConsts.READ, getHeader(message, "X-smssync-read"));
      values.put(SmsConsts.SERVICE_CENTER, getHeader(message, "X-smssync-service_center"));
      values.put(SmsConsts.DATE, getHeader(message, "X-smssync-date"));
      values.put(SmsConsts.STATUS, getHeader(message, "X-smssync-status"));

      return values;
   }
}