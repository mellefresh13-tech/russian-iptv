package ru.russianiptv.player;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int PICK_FILE = 41;
    private final List<Channel> all = new ArrayList<>();
    private final List<Channel> shown = new ArrayList<>();
    private ChannelAdapter adapter;
    private EditText urlInput, search;
    private Spinner category;
    private TextView status;
    private String epgUrl = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 18, 20, 8);
        TextView title = new TextView(this); title.setText("Russian IPTV"); title.setTextSize(24); title.setPadding(0, 0, 0, 12);
        root.addView(title);
        LinearLayout inputRow = new LinearLayout(this);
        urlInput = new EditText(this); urlInput.setSingleLine(true); urlInput.setHint("URL M3U/M3U8");
        urlInput.setText(getPreferences(0).getString("url", "https://raw.githubusercontent.com/mellefresh13-tech/russian-iptv/main/playlist.m3u"));
        inputRow.addView(urlInput, new LinearLayout.LayoutParams(0, -2, 1));
        Button load = new Button(this); load.setText("Загрузить"); inputRow.addView(load);
        root.addView(inputRow);
        Button file = new Button(this); file.setText("Открыть локальный M3U-файл"); root.addView(file);
        LinearLayout filters = new LinearLayout(this);
        category = new Spinner(this); filters.addView(category, new LinearLayout.LayoutParams(0, -2, 1));
        search = new EditText(this); search.setSingleLine(true); search.setHint("Поиск канала"); filters.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(filters);
        status = new TextView(this); status.setText("Введите URL или откройте файл"); root.addView(status);
        RecyclerView list = new RecyclerView(this); list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChannelAdapter(); list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        load.setOnClickListener(v -> loadUrl(urlInput.getText().toString().trim()));
        file.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), PICK_FILE));
        category.setOnItemSelectedListener(new SimpleListener() { public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { filter(); }});
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { filter(); }
            public void afterTextChanged(android.text.Editable e) {}
        });
    }

    private void loadUrl(String source) {
        if (source.isEmpty()) return;
        status.setText("Загрузка плейлиста…");
        getPreferences(0).edit().putString("url", source).apply();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                java.net.URL u = new java.net.URL(source);
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(30000);
                StringBuilder text = new StringBuilder(); BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                String line; while ((line = r.readLine()) != null) text.append(line).append('\n'); r.close();
                parseAndShow(text.toString());
            } catch (Exception e) { runOnUiThread(() -> status.setText("Ошибка загрузки: " + e.getMessage())); }
        });
    }

    private void parseAndShow(String text) {
        try {
            List<Channel> parsed = M3uParser.parse(text);
            synchronized (all) { all.clear(); all.addAll(parsed); }
            Matcher m = Pattern.compile("(?i)(?:TVG-URL|x-tvg-url)=\"([^\"]+)\"").matcher(text);
            if (m.find()) epgUrl = m.group(1).split(",")[0].trim();
            runOnUiThread(() -> {
                Set<String> groups = new TreeSet<>(String.CASE_INSENSITIVE_ORDER); groups.add("Все");
                for (Channel ch : all) groups.add(ch.group);
                category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>(groups)));
                filter(); status.setText("Каналов загружено: " + all.size());
            });
        } catch (Exception e) { runOnUiThread(() -> status.setText("Ошибка разбора M3U")); }
    }

    private void filter() {
        String group = category.getSelectedItem() == null ? "Все" : category.getSelectedItem().toString();
        String query = search == null ? "" : search.getText().toString().toLowerCase(Locale.ROOT);
        synchronized (all) { shown.clear(); for (Channel ch : all)
            if (("Все".equals(group) || group.equals(ch.group)) && ch.name.toLowerCase(Locale.ROOT).contains(query)) shown.add(ch); }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_FILE && result == RESULT_OK && data != null) {
            try {
                BufferedReader r = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(data.getData()), "UTF-8"));
                StringBuilder b = new StringBuilder(); String line; while ((line = r.readLine()) != null) b.append(line).append('\n'); r.close();
                parseAndShow(b.toString());
            } catch (Exception e) { status.setText("Не удалось открыть файл"); }
        }
    }

    private class ChannelAdapter extends RecyclerView.Adapter<ChannelHolder> {
        public ChannelHolder onCreateViewHolder(android.view.ViewGroup p, int t) {
            LinearLayout box = new LinearLayout(MainActivity.this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(14, 12, 14, 12);
            TextView name = new TextView(MainActivity.this); name.setTextSize(17); box.addView(name);
            TextView group = new TextView(MainActivity.this); box.addView(group);
            return new ChannelHolder(box, name, group);
        }
        public void onBindViewHolder(ChannelHolder h, int pos) {
            Channel c = shown.get(pos); h.name.setText(c.name); h.group.setText(c.group);
            h.itemView.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, PlayerActivity.class)
                    .putExtra("name", c.name).putExtra("url", c.url).putExtra("id", c.id).putExtra("epg", epgUrl)));
        }
        public int getItemCount() { return shown.size(); }
    }
    private static class ChannelHolder extends RecyclerView.ViewHolder {
        final TextView name, group; ChannelHolder(View v, TextView n, TextView g) { super(v); name=n; group=g; }
    }
    private abstract static class SimpleListener implements AdapterView.OnItemSelectedListener {
        public void onNothingSelected(AdapterView<?> p) {}
    }
}
