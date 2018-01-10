package com.zegoggles.smssync.utils;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.graphics.drawable.DrawableCompat;

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
