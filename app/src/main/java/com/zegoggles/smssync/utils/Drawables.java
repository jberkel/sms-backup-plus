package com.zegoggles.smssync.utils;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;

public class Drawables {
    private Drawables() {}

    @NonNull
    public static Drawable getTinted(Resources resources, int resource, int color) {
        Drawable drawable = ResourcesCompat.getDrawable(resources, resource, null);
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable.mutate(), color);
        return drawable;
    }
}
