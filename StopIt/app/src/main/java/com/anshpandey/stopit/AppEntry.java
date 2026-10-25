package com.anshpandey.stopit;

import android.graphics.drawable.Drawable;

final class AppEntry {

    final String pkg;
    final String label;
    final Drawable icon;

    AppEntry(String pkg, String label, Drawable icon) {
        this.pkg = pkg;
        this.label = label;
        this.icon = icon;
    }
}
