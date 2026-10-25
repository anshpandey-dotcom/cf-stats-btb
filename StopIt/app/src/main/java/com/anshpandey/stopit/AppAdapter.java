package com.anshpandey.stopit;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AppAdapter extends BaseAdapter {

    interface Listener {
        void onToggle(AppEntry entry, boolean blocked);
    }

    private static final Integer[] TITLES = {R.string.section_fun, R.string.section_used, R.string.section_other};

    private final Store store;
    private final Listener listener;
    private final List<AppEntry> all = new ArrayList<>();
    private final List<Object> shown = new ArrayList<>();
    private String query = "";

    AppAdapter(Store store, Listener listener) {
        this.store = store;
        this.listener = listener;
    }

    void setApps(List<AppEntry> apps) {
        all.clear();
        all.addAll(apps);
        apply();
    }

    void filter(String text) {
        query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        apply();
    }

    private void apply() {
        shown.clear();
        int section = -1;
        for (AppEntry entry : all) {
            if (query.isEmpty()
                    || entry.label.toLowerCase(Locale.ROOT).contains(query)
                    || entry.pkg.toLowerCase(Locale.ROOT).contains(query)) {
                if (entry.section != section) {
                    section = entry.section;
                    shown.add(TITLES[section]);
                }
                shown.add(entry);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return shown.size();
    }

    @Override
    public Object getItem(int position) {
        return shown.get(position);
    }

    @Override
    public long getItemId(int position) {
        Object item = shown.get(position);
        return item instanceof AppEntry ? ((AppEntry) item).pkg.hashCode() : -1 - (Integer) item;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return shown.get(position) instanceof AppEntry ? 0 : 1;
    }

    @Override
    public boolean isEnabled(int position) {
        return shown.get(position) instanceof AppEntry;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Object item = shown.get(position);
        if (!(item instanceof AppEntry)) {
            TextView header = (TextView) (convertView != null ? convertView
                    : LayoutInflater.from(parent.getContext()).inflate(R.layout.item_header, parent, false));
            header.setText((Integer) item);
            return header;
        }
        Holder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
            holder = new Holder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }
        AppEntry entry = (AppEntry) item;
        holder.icon.setImageDrawable(entry.icon);
        holder.name.setText(entry.label);
        holder.pkg.setText(entry.pkg);
        holder.toggle.setOnCheckedChangeListener(null);
        holder.toggle.setChecked(store.isBlocked(entry.pkg));
        holder.toggle.setOnCheckedChangeListener((button, checked) -> listener.onToggle(entry, checked));
        convertView.setOnClickListener(v -> holder.toggle.toggle());
        return convertView;
    }

    private static final class Holder {
        final ImageView icon;
        final TextView name;
        final TextView pkg;
        final Switch toggle;

        Holder(View view) {
            icon = view.findViewById(R.id.icon);
            name = view.findViewById(R.id.name);
            pkg = view.findViewById(R.id.pkg);
            toggle = view.findViewById(R.id.toggle);
        }
    }
}
