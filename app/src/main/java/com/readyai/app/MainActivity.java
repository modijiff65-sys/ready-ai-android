package com.readyai.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Ready AI — a tiny chat client.
 *
 * It talks to any OpenAI-compatible endpoint: a hosted API (OpenAI, Gemini-compatible
 * gateways, etc.) or your own box running Ollama. You set the URL/key/model once in
 * Settings. No libraries, no tracking, no accounts.
 */
public class MainActivity extends Activity {

    private TextView log;
    private ScrollView scroll;
    private EditText input;
    private SharedPreferences prefs;

    private final List<JSONObject> history = new ArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private static final String SYSTEM_PROMPT =
            "You are a friendly assistant. Always reply in the same language and script the "
          + "user used — Hinglish in Latin letters, Hindi in Devanagari, or English. "
          + "Keep answers short and warm.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("readyai", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // ---- top bar ----
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(14), dp(10), dp(10), dp(10));
        bar.setBackgroundColor(0xFF111A2E);
        TextView title = new TextView(this);
        title.setText("Ready AI");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button settings = new Button(this);
        settings.setText("Settings");
        bar.addView(title);
        bar.addView(settings);
        root.addView(bar);

        // ---- chat log ----
        scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        log = new TextView(this);
        log.setPadding(dp(16), dp(16), dp(16), dp(16));
        log.setTextSize(16);
        log.setTextColor(0xFF16202E);
        log.setText("Namaste! Pehle \"Settings\" khol kar apna AI endpoint set karo "
                  + "(API URL, key, model) — phir baat karo.\n\n");
        scroll.addView(log);
        root.addView(scroll);

        // ---- input row ----
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        input = new EditText(this);
        input.setHint("Kuch likho...");
        input.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button send = new Button(this);
        send.setText("Send");
        row.addView(input);
        row.addView(send);
        root.addView(row);

        setContentView(root);

        send.setOnClickListener(v -> send());
        settings.setOnClickListener(v -> openSettings());

        if (prefs.getString("base", "").isEmpty()) {
            openSettings();
        }
    }

    // ------------------------------------------------------------------ chat

    private void send() {
        final String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");
        append("you: " + text + "\n");
        try {
            JSONObject u = new JSONObject();
            u.put("role", "user");
            u.put("content", text);
            history.add(u);
        } catch (Exception ignored) { }

        append("ai: ");
        new Thread(() -> {
            try {
                final String reply = callModel();
                ui.post(() -> {
                    append(reply + "\n\n");
                    try {
                        JSONObject a = new JSONObject();
                        a.put("role", "assistant");
                        a.put("content", reply);
                        history.add(a);
                    } catch (Exception ignored) { }
                });
            } catch (final Exception e) {
                ui.post(() -> append("[error] " + e.getMessage() + "\n\n"));
            }
        }).start();
    }

    private String callModel() throws Exception {
        String base = prefs.getString("base", "").trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String key = prefs.getString("key", "").trim();
        String model = prefs.getString("model", "gpt-4o-mini").trim();
        if (base.isEmpty()) throw new Exception("Settings khol kar API URL daalo");

        JSONArray msgs = new JSONArray();
        JSONObject sys = new JSONObject();
        sys.put("role", "system");
        sys.put("content", SYSTEM_PROMPT);
        msgs.put(sys);
        int start = Math.max(0, history.size() - 12);
        for (int i = start; i < history.size(); i++) msgs.put(history.get(i));

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", msgs);
        body.put("temperature", 0.6);

        HttpURLConnection c = (HttpURLConnection) new URL(base + "/chat/completions").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        if (!key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);

        try (OutputStream os = c.getOutputStream()) {
            os.write(body.toString().getBytes("UTF-8"));
        }

        int code = c.getResponseCode();
        BufferedReader r = new BufferedReader(new InputStreamReader(
                (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();

        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + sb);

        JSONObject resp = new JSONObject(sb.toString());
        return resp.getJSONArray("choices").getJSONObject(0)
                   .getJSONObject("message").getString("content").trim();
    }

    // -------------------------------------------------------------- settings

    private void openSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(0));

        EditText eBase = new EditText(this);
        eBase.setHint("API base URL");
        eBase.setText(prefs.getString("base", ""));
        EditText eKey = new EditText(this);
        eKey.setHint("API key (khali chhod do agar local box hai)");
        eKey.setText(prefs.getString("key", ""));
        eKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText eModel = new EditText(this);
        eModel.setHint("model name");
        eModel.setText(prefs.getString("model", "gpt-4o-mini"));

        TextView help = new TextView(this);
        help.setTextSize(12);
        help.setTextColor(0xFF5C6B7E);
        help.setText("Examples:\n"
                + "  hosted:  https://api.openai.com/v1\n"
                + "  your box: http://192.168.1.5:8080/v1  (Ollama, key blank)\n"
                + "Model: gpt-4o-mini  or  qwen3:8b");

        box.addView(eBase);
        box.addView(eKey);
        box.addView(eModel);
        box.addView(help);

        new AlertDialog.Builder(this)
                .setTitle("Connect your AI")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    prefs.edit()
                            .putString("base", eBase.getText().toString().trim())
                            .putString("key", eKey.getText().toString().trim())
                            .putString("model", eModel.getText().toString().trim())
                            .apply();
                    append("[saved] " + eBase.getText().toString().trim()
                            + "  ·  " + eModel.getText().toString().trim() + "\n\n");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --------------------------------------------------------------- helpers

    private void append(String s) {
        log.append(s);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
