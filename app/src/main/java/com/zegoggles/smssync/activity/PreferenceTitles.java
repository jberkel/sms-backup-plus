package com.zegoggles.smssync.activity;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.XmlRes;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Standard Android preferences don't expose the title resource id. Reparse preferences XML,
 * key title resources by preference key.
 */
class PreferenceTitles {
    private static final String NS = "http://schemas.android.com/apk/res/android";
    private static final String PREFERENCE_SCREEN = "PreferenceScreen";
    private Map<String, Integer> titleResources = new HashMap<String, Integer>();

    PreferenceTitles(@NonNull Resources resources, @XmlRes int preferenceRes) {
        final XmlResourceParser parser = resources.getXml(preferenceRes);
        try {
            while (true) {
                int type;
                do {
                    type = parser.next();
                } while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT);

                if (type == XmlPullParser.END_DOCUMENT) {
                    break;
                } else if (PREFERENCE_SCREEN.equals(parser.getName()) && parser.getAttributeCount() > 0) {
                    final @StringRes int titleRes = parser.getAttributeResourceValue(NS, "title", 0);
                    final String key = parser.getAttributeValue(NS, "key");
                    if (titleRes != 0 && key != null) {
                        titleResources.put(key, titleRes);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (XmlPullParserException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the string resource id or 0 if not found
     */
    @StringRes int getTitleRes(String preferenceKey) {
        final Integer res = titleResources.get(preferenceKey);
        return res == null ? 0 : res;
    }
}
