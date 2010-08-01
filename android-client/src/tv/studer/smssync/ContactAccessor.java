package tv.studer.smssync;

import android.content.Context;

public interface ContactAccessor {
  /** Returns the email address of the Android phone owner, or null if not known */
  String getOwnerEmail(Context context);
}
