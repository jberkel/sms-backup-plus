package com.zegoggles.smssync.utils;

import android.app.Dialog;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import android.text.format.DateFormat;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;

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
    // keep max 32k worth of logs
    private static final int MAX_SIZE = 32 * 1024;
    private static final int ID = 1;

    private @Nullable PrintWriter writer;
    private String dateFormat;

    public AppLog(Context context) {
        char[] format;
        try {
            format = DateFormat.getDateFormatOrder(context);
        } catch (IllegalArgumentException e) {
            format = new char[] { 'd' };
        }
        for (char c : format) {
            if (c == 'M') {
                dateFormat = "MM-dd kk:mm";
                break;
            }
            if (c == 'd') {
                dateFormat = "dd-MM kk:mm";
                break;
            }
        }
        File logFile = getLogFile(context);
        if (logFile != null) {
            Log.w(TAG, "logging to " + logFile);

            if (logFile.isFile() && logFile.exists()) {
                rotateAsync(logFile);
            }
            try {
                writer = new PrintWriter(new FileWriter(logFile, true));
            } catch (IOException e) {
                Log.w(TAG, "error opening app log", e);
            }
        }
    }

    public void append(String s) {
        if (writer == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append(format(new Date()))
                .append(" ").append(s);
        writer.println(sb);
        if (LOCAL_LOGV) Log.v(TAG, "[AppLog]: " + sb);
    }

    public void appendAndClose(String s) {
        append(s);
        close();
    }

    public void close() {
        if (LOCAL_LOGV) Log.v(TAG, "AppLog#close()");
        if (writer != null) writer.close();
    }

    public CharSequence format(Date d) {
        return DateFormat.format(dateFormat, d);
    }

    public static Dialog displayAsDialog(Context context) {
        File file = getLogFile(context);
        if (file != null && file.exists() && file.length() > 0) {
            return getLogDialog(context, file);
        } else {
            return logNotAvailable(context);
        }
    }

    private static Dialog logNotAvailable(Context context) {
        return new AlertDialog.Builder(context)
            .setTitle(R.string.menu_view_log)
            .setMessage(R.string.app_log_empty)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton(android.R.string.ok, null)
            .create();
    }

    private static Dialog getLogDialog(Context context, @NonNull File file) {
        final int PAD = 5;
        final TextView view = new TextView(context);
        view.setId(ID);
        view.setText(readLog(file));

        final ScrollView scrollView = new ScrollView(context) {
            {
                addView(view);
                setPadding(PAD, PAD, PAD, PAD);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                scrollTo(0, view.getHeight());
            }
        };
        return new AlertDialog.Builder(context)
            .setPositiveButton(android.R.string.ok, null)
            .setTitle(file.getPath())
            .setView(scrollView)
            .create();
    }

    private static String readLog(File f) {
        StringBuilder text = new StringBuilder();
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
        return text.toString();
    }

    private static @Nullable File getLogFile(Context context) {
        final File logDir = context.getExternalFilesDir(null);
        if (logDir != null) {
            return new File(logDir, App.LOG);
        } else {
            return null;
        }
    }

    private void rotateAsync(final File logFile) {
        new Thread() {
            @Override
            public void run() {
                rotate(logFile);
            }
        }.start();
    }

    private void rotate(final File logFile) {
        if (logFile.length() < MAX_SIZE) return;
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

                if (newFile.renameTo(logFile) && LOCAL_LOGV) {
                    Log.v(TAG, "rotated file, new size = " + logFile.length());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "error rotating file " + logFile, e);
        }
    }
}
