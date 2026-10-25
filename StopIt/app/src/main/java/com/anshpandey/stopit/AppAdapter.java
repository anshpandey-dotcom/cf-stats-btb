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

    private final Store store;
    private final Listener listener;
    private final List<AppEntry> all = new ArrayList<>();
    private final List<AppEntry> shown = new ArrayList<>();
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
        for (AppEntry entry : all) {
            if (query.isEmpty()
                    || entry.label.toLowerCase(Locale.ROOT).contains(query)
                    || entry.pkg.toLowerCase(Locale.ROOT).contains(query)) {
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
    public AppEntry getItem(int position) {
        return shown.get(position);
    }

    @Override
    public long getItemId(int position) {
        return shown.get(position).pkg.hashCode();
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
            holder = new Holder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }
        AppEntry entry = getItem(position);
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
