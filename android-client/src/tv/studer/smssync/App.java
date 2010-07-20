package tv.studer.smssync;

import android.app.Application;
import com.fsck.k9.K9;

public class App extends Application {
    public void onCreate() {
        super.onCreate();
        K9.app = this;
        K9.DEBUG = true;
    }
}
