package tv.studer.smssync;

import android.content.Context;
import android.accounts.Account;
import android.accounts.AccountManager;

public class ContactAccessorPost20 implements ContactAccessor {
  public String getOwnerEmail(Context context) {
      AccountManager mgr = AccountManager.get(context);
      for (Account acc : mgr.getAccountsByType("com.google")) {
        return acc.name;
      }
      return null;
  }
}
