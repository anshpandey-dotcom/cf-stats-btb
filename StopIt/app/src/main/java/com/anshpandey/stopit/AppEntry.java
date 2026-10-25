package com.anshpandey.stopit;

import android.graphics.drawable.Drawable;

final class AppEntry {

    static final int FUN = 0;
    static final int USED = 1;
    static final int OTHER = 2;

    final String pkg;
    final String label;
    final Drawable icon;
    final int section;
    final long usage;

    AppEntry(String pkg, String label, Drawable icon, int section, long usage) {
        this.pkg = pkg;
        this.label = label;
        this.icon = icon;
        this.section = section;
        this.usage = usage;
    }
}
