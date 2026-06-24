package com.lseboard.app;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.lseboard.app.Model.Emoji;
import com.lseboard.app.Model.EmojiPack;
import com.lseboard.app.Util.Apng2GifCustom;
import com.lseboard.app.Util.SharedPrefHelper;

public class EmojiFetchService extends IntentService {
    private static final String TAG = "EmojiFetchService";
    private static final String ACTION_FETCH_EMOJI = "com.lseboard.app.action.FETCH_EMOJI";
    private static final String EXTRA_PARAM1 = "com.lseboard.app.extra.EMOJI_PARAM1";
    private static final String EXTRA_PARAM2 = "com.lseboard.app.extra.EMOJI_PARAM2";

    private static final String CHANNEL_ID = "LSEBoard_download";
    private static final int NOTIF_ID = 1002;

    // LINE Emoji CDN URL 格式
    // 靜態: https://stickershop.line-scdn.net/sticonshop/v1/sticon/{productId}/iphone/{id:03d}.png
    // 動態: https://stickershop.line-scdn.net/sticonshop/v1/sticon/{productId}/iphone/{id:03d}_animation.png
    private static final String EMOJI_BASE_URL = "https://stickershop.line-scdn.net/sticonshop/v1/sticon/%s/iphone/%03d.png";
    private static final String EMOJI_ANIM_URL = "https://stickershop.line-scdn.net/sticonshop/v1/sticon/%s/iphone/%03d_animation.png";

    public static final String BROADCAST_ACTION = "com.lseboard.app.REFRESH_EMOJI";

    String productId = "";
    ArrayList<Integer> ids = new ArrayList<>();
    String title = "";
    Emoji.Type type = Emoji.Type.STATIC;
    File emojiDir;
    File emojiGifDir;
    Apng2GifCustom apng2GifCustom;

    public EmojiFetchService() {
        super("EmojiFetchService");
    }

    public static void startActionFetchEmoji(Context context, String emojiUrl) {
        Intent intent = new Intent(context, EmojiFetchService.class);
        intent.setAction(ACTION_FETCH_EMOJI);
        intent.putExtra(EXTRA_PARAM1, emojiUrl);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void startActionFetchEmojiManual(Context context, String productId, boolean isFallback) {
        Intent intent = new Intent(context, EmojiFetchService.class);
        intent.setAction(ACTION_FETCH_EMOJI);
        intent.putExtra(EXTRA_PARAM1, productId);
        intent.putExtra(EXTRA_PARAM2, isFallback);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    // ─── Foreground Service Setup ─────────────────────────────────────────────

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("正在下載表情貼...")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
        startForeground(NOTIF_ID, notification);
        return super.onStartCommand(intent, flags, startId);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "LSEBoard 下載",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("貼圖與表情下載進度");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    // ─── Intent Handling ──────────────────────────────────────────────────────

    @Override
    protected void onHandleIntent(Intent intent) {
        apng2GifCustom = new Apng2GifCustom();

        try {
            if (intent != null) {
                final String action = intent.getAction();
                if (ACTION_FETCH_EMOJI.equals(action)) {
                    showToast("LSEBoard: 開始解析下載表情貼...");
                    final String param1 = intent.getStringExtra(EXTRA_PARAM1);
                    handleActionFetchEmoji(param1);
                }
            }
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        }
    }

    private void handleActionFetchEmoji(String input) {
        // 解析 productId
        productId = extractProductId(input);
        if (productId == null || productId.isEmpty()) {
            send("failed_emoji", "", "", false);
            return;
        }
        
        Log.d(TAG, "Product ID: " + productId);
        
        // 取得標題
        title = fetchTitle();
        Log.d(TAG, "Title: " + title);
        
        // 偵測 Emoji 數量和類型
        int emojiCount = detectEmojiCountAndType();
        Log.d(TAG, "Detected emoji count: " + emojiCount + ", type: " + type);
        
        if (emojiCount <= 0) {
            send("failed_emoji", productId, title, false);
            return;
        }
        
        // 建立 ID 列表
        ids.clear();
        for (int i = 1; i <= emojiCount; i++) {
            ids.add(i);
        }
        
        // 建立目錄
        emojiDir = new File(getFilesDir(), "emoji/" + productId);
        emojiGifDir = new File(getFilesDir(), "emoji_gif/" + productId);
        
        // 下載
        if (downloadEmoji()) {
            if (type == Emoji.Type.ANIMATED) {
                convertEmoji();
            }
            
            SharedPrefHelper.addNewEmojiPack(this, new EmojiPack(title, productId, type, ids));
            send("add_emoji", productId, title, true);
        } else {
            send("failed_emoji", productId, title, false);
        }
    }
    
    /**
     * 從輸入中提取 productId
     */
    private String extractProductId(String input) {
        if (input == null || input.isEmpty()) return null;
        
        // 模式1: emojishop/product/{id}
        Pattern p1 = Pattern.compile("emojishop/product/([a-f0-9]+)");
        Matcher m1 = p1.matcher(input);
        if (m1.find()) return m1.group(1);
        
        // 模式2: emoji/?id={id}
        Pattern p2 = Pattern.compile("emoji/\\?id=([a-f0-9]+)");
        Matcher m2 = p2.matcher(input);
        if (m2.find()) return m2.group(1);
        
        // 模式3: 純 24 位 hex ID
        String trimmed = input.trim();
        if (trimmed.matches("^[a-f0-9]{24}$")) {
            return trimmed;
        }

        // 模式4: lin.ee 短網址 → 展開後重新解析
        if (input.contains("lin.ee") || input.contains("liff.line.me")) {
            String expanded = expandUrl(input);
            if (!expanded.equals(input)) {
                return extractProductId(expanded);
            }
        }

        return null;
    }

    /**
     * 嘗試展開短網址（如 lin.ee），回傳最終落地的 URL 字串。
     * 若非短網址或展開失敗，原樣回傳。
     */
    private String expandUrl(String urlStr) {
        try {
            java.util.regex.Pattern urlPat = java.util.regex.Pattern.compile("https?://\\S+");
            java.util.regex.Matcher urlMat = urlPat.matcher(urlStr);
            String candidate = urlMat.find() ? urlMat.group() : urlStr;

            HttpURLConnection conn = (HttpURLConnection) new URL(candidate).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();

            int status = conn.getResponseCode();
            int redirects = 0;
            while ((status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == 307 || status == 308)
                    && redirects < 5) {
                String location = conn.getHeaderField("Location");
                if (location == null) break;
                conn.disconnect();
                conn = (HttpURLConnection) new URL(location).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.connect();
                status = conn.getResponseCode();
                candidate = conn.getURL().toString();
                redirects++;
            }
            conn.disconnect();
            Log.d(TAG, "Expanded URL: " + candidate);
            return candidate;
        } catch (Exception e) {
            Log.e(TAG, "expandUrl failed: " + e.getMessage());
            return urlStr;
        }
    }
    
    /**
     * 從 LINE 商店頁面取得標題
     */
    private String fetchTitle() {
        try {
            String url = "https://store.line.me/emojishop/product/" + productId + "/zh-Hant";
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get();
            
            Element titleEl = doc.selectFirst("p.mdCMN38Item01Ttl");
            if (titleEl != null) {
                return titleEl.text();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch title: " + e.getMessage());
        }
        return "Emoji #" + productId.substring(0, 8);
    }
    
    /**
     * 偵測 Emoji 數量和類型
     */
    private int detectEmojiCountAndType() {
        // 先檢查是否為動態 Emoji
        String animUrl = String.format(Locale.getDefault(), EMOJI_ANIM_URL, productId, 1);
        if (checkUrlExists(animUrl)) {
            type = Emoji.Type.ANIMATED;
            Log.d(TAG, "Detected ANIMATED emoji");
        } else {
            type = Emoji.Type.STATIC;
            Log.d(TAG, "Detected STATIC emoji");
        }
        
        // 檢查靜態版本是否存在
        String staticUrl = String.format(Locale.getDefault(), EMOJI_BASE_URL, productId, 1);
        if (!checkUrlExists(staticUrl)) {
            Log.e(TAG, "First emoji not found at: " + staticUrl);
            return 0;
        }
        
        // 二分搜尋找出最大 ID
        int low = 1, high = 100, maxFound = 1;
        
        // 先快速檢查常見數量
        int[] commonCounts = {40, 24, 32, 16};
        for (int count : commonCounts) {
            String testUrl = String.format(Locale.getDefault(), EMOJI_BASE_URL, productId, count);
            if (checkUrlExists(testUrl)) {
                maxFound = Math.max(maxFound, count);
            }
        }
        
        // 如果找到 40，檢查是否有更多
        if (maxFound >= 40) {
            for (int i = 41; i <= 50; i++) {
                String testUrl = String.format(Locale.getDefault(), EMOJI_BASE_URL, productId, i);
                if (checkUrlExists(testUrl)) {
                    maxFound = i;
                } else {
                    break;
                }
            }
        }
        
        return maxFound;
    }
    
    /**
     * 檢查 URL 是否存在
     */
    private boolean checkUrlExists(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean downloadEmoji() {
        if (!emojiDir.exists() && !emojiDir.mkdirs()) {
            Log.e(TAG, "Failed to create emoji directory");
            return false;
        }
        
        int count = ids.size();
        int successCount = 0;
        ArrayList<Integer> successIds = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            int id = ids.get(i);
            
            // 下載 URL (使用 3 位數格式)
            // 如果是動態 Emoji，下載動畫版本
            String urlStr;
            if (type == Emoji.Type.ANIMATED) {
                urlStr = String.format(Locale.getDefault(), EMOJI_ANIM_URL, productId, id);
            } else {
                urlStr = String.format(Locale.getDefault(), EMOJI_BASE_URL, productId, id);
            }
            
            // 儲存檔案 (使用普通數字)
            File outputFile = new File(emojiDir, id + ".png");
            
            Log.d(TAG, "Downloading: " + urlStr + " -> " + outputFile.getName());
            
            if (downloadFile(urlStr, outputFile)) {
                successCount++;
                successIds.add(id);
            }
        }
        
        // 更新 ids 為實際成功下載的
        if (successCount > 0 && successCount < count) {
            ids = successIds;
        }
        
        Log.d(TAG, "Download complete. Success: " + successCount + "/" + count);
        return successCount > 0;
    }
    
    /**
     * 下載單個檔案
     */
    private boolean downloadFile(String urlStr, File outputFile) {
        InputStream in = null;
        OutputStream out = null;
        
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();
            
            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return false;
            }
            
            in = conn.getInputStream();
            out = new FileOutputStream(outputFile);
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            
            out.flush();
            conn.disconnect();
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Download failed: " + e.getMessage());
            return false;
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
            } catch (Exception ignored) {}
        }
    }

    private void convertEmoji() {
        if (!emojiGifDir.exists() && !emojiGifDir.mkdirs()) {
            Log.e(TAG, "Failed to create emoji gif directory");
            return;
        }
        
        int count = ids.size();
        
        for (int i = 0; i < count; i++) {
            int id = ids.get(i);
            File png = new File(emojiDir, id + ".png");
            if (!png.exists()) continue;
            
            File gif = new File(emojiGifDir, id + ".gif");
            apng2GifCustom.start(png, gif);
        }
    }

    private void send(String message, String productId, String title, boolean success) {
        Intent intent = new Intent();
        intent.setAction(BROADCAST_ACTION);
        intent.putExtra("message", message);
        intent.putExtra("productId", productId);
        intent.putExtra("title", title);
        intent.putExtra("success", success);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        // 同時發送到主廣播，讓鍵盤也能收到更新
        Intent mainIntent = new Intent();
        mainIntent.setAction(FetchService.BROADCAST_ACTION);
        mainIntent.putExtra("message", message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(mainIntent);

        final String toastMsg = success
                ? "下載完成：" + title
                : "下載失敗：" + (title.isEmpty() ? "無法解析連結，請確認表情貼是否為免費或已購買" : title);
        showToast(toastMsg);
    }

    private void showToast(String msg) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                android.widget.Toast.makeText(getApplicationContext(), msg, android.widget.Toast.LENGTH_LONG).show()
        );
    }
}

