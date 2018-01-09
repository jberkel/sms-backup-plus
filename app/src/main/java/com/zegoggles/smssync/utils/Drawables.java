package com.zegoggles.smssync.utils;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.graphics.drawable.DrawableCompat;

public class Drawables {
    private Drawables() {}

    @SuppressWarnings("deprecation") @NonNull
    public static
    Drawable getTinted(Resources resources, int resource, int color) {
        Drawable drawable = resources.getDrawable(resource);
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable.mutate(), color);
        return drawable;
    }
}
