package com.zegoggles.smssync;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Environment;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.util.Date;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

public class AppLog {
    // keep max 64k worth of logs
    static int MAX_SIZE = 64 * 1024;
    static int ID = 1;

    private PrintWriter writer;
    private String dateFormat;
    private String empty;

    public AppLog(String name, Context context) {
        for (char c : DateFormat.getDateFormatOrder(context)) {
            if (c == DateFormat.MONTH) { dateFormat = "MM-dd hh:mm"; break; }
            if (c == DateFormat.DATE)  { dateFormat = "dd-MM hh:mm"; break; }
        }

        empty = context.getString(R.string.app_log_empty);

        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            final File logFile = getFile(name);
            if (logFile.isFile() && logFile.exists()) rotate(logFile);

            try {
                writer = new PrintWriter(new FileWriter(logFile, true));
            } catch (IOException e) {
                Log.w(TAG, "error opening app log", e);
            }
        }
    }

    public void append(String s) {
        if (LOCAL_LOGV) Log.v(TAG, "[AppLog]:" + s);
        if (writer != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(format(new Date()))
              .append(": ").append(s);
            writer.println(sb);
        }
    }

    public void close() {
        if (LOCAL_LOGV) Log.v(TAG, "AppLog#close()");
        if (writer != null) writer.close();
    }

    public CharSequence format(Date d) {
        return DateFormat.format(dateFormat, d);
    }

    private boolean rotate(File logFile) {
        if (logFile.length() > MAX_SIZE) {

            if (LOCAL_LOGV) Log.v(TAG, "rotating logfile " + logFile);

            try {
                LineNumberReader r = new LineNumberReader(new FileReader(logFile));

                while (r.readLine() != null) ;
                r.close();

                int keep = Math.round(r.getLineNumber() * 0.3f);
                if (keep > 0) {
                    r = new LineNumberReader(new FileReader(logFile));

                    while (r.readLine() != null && r.getLineNumber() < keep) ;

                    File newFile = new File(logFile.getAbsolutePath() + ".new");
                    PrintWriter pw = new PrintWriter(new FileWriter(newFile));
                    String line;
                    while ((line = r.readLine()) != null) pw.println(line);

                    pw.close();
                    r.close();

                    return newFile.renameTo(logFile);
                } else {
                    return false;
                }
            } catch (IOException e) {
                Log.e(TAG, "error rotating file " + logFile, e);
                return false;
            }
        } else {
            return false;
        }
    }

    public static Dialog displayAsDialog(String name, Context context) {
        final TextView view = new TextView(context);
        view.setId(ID);

        readLogIntoView(name, view);

        final ScrollView sView = new ScrollView(context) {
            {
                addView(view);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                scrollTo(0, view.getHeight());
            }
        };

        return new AlertDialog.Builder(context)
                .setCustomTitle(null)
                .setPositiveButton(android.R.string.ok, null)
                .setView(sView)
                .create();
    }

    public static void readLogIntoView(String name, View view) {
        readLogIntoView(getFile(name), view);
    }

    public static void readLogIntoView(File f, View view) {
        StringBuilder text = new StringBuilder();
        if (f.exists() && view instanceof TextView) {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader(f));
                String line;
                while ((line = br.readLine()) != null) {
                    text.append(line);
                    text.append('\n');
                }
            } catch (IOException e) {
                Log.e(TAG, "error reading", e);
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        ((TextView)view).setText(text.length() == 0 ? "Empty" : text);
    }

    static File getFile(String name) {
        return new File(Environment.getExternalStorageDirectory(), name);
    }
}
