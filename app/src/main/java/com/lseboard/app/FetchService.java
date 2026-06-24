package com.lseboard.app;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageDecoder;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import com.lseboard.app.Model.Sticker;
import com.lseboard.app.Model.StickerPack;
import com.lseboard.app.Util.Apng2GifCustom;
import com.lseboard.app.Util.SharedPrefHelper;

public class FetchService extends IntentService {
    private static final String ACTION_FETCH = "com.lseboard.app.action.FETCH";
    private static final String EXTRA_PARAM1 = "com.lseboard.app.extra.PARAM1";
    private static final String EXTRA_PARAM2 = "com.lseboard.app.extra.PARAM2";

    private static final String CHANNEL_ID = "LSEBoard_download";
    private static final int NOTIF_ID = 1001;

    private static final String URL_COMMON = "https://stickershop.line-scdn.net/stickershop/v1/sticker/";
    private static final String STATIC_URL_FORMAT = URL_COMMON + "%d/IOS/sticker@2x.png;compress=true";
    private static final String ANIMATED_URL_FORMAT = URL_COMMON + "%d/IOS/sticker_animation@2x.png;compress=true";
    private static final String POPUP_URL_FORMAT = URL_COMMON + "%d/IOS/sticker_popup.png;compress=true";

    // Fallback URL ("android" has a lower resolution than "IOS" or "iphone")
    private static final String STATIC_URL_FORMAT_2 = URL_COMMON + "%d/android/sticker.png;compress=true";
    private static final String ANIMATED_URL_FORMAT_2 = URL_COMMON + "%d/android/sticker_animation.png;compress=true";
    private static final String POPUP_URL_FORMAT_2 = URL_COMMON + "%d/android/sticker_popup.png;compress=true";

    public static final String BROADCAST_ACTION = "com.lseboard.app.REFRESH";

    int storeId = 0;
    ArrayList<Integer> ids;
    String title = "";
    Sticker.Type type;
    File pngDir;
    File gifDir;
    Apng2GifCustom apng2GifCustom;

    public FetchService() {
        super("FetchService");
    }

    public static void startActionFetch(Context context, String lineShare) {
        Intent intent = new Intent(context, FetchService.class);
        intent.setAction(ACTION_FETCH);
        intent.putExtra(EXTRA_PARAM1, lineShare);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void startActionFetchManual(Context context, int storeId, boolean isFallback) {
        Intent intent = new Intent(context, FetchService.class);
        intent.setAction(ACTION_FETCH);
        intent.putExtra(EXTRA_PARAM1,
                String.format(Locale.getDefault(), "https://line.me/S/sticker/%d", storeId));
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
                .setContentText("正在下載貼圖...")
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
        pngDir = new File(getFilesDir(), "png");
        gifDir = new File(getFilesDir(), "gif");
        apng2GifCustom = new Apng2GifCustom();

        try {
            if (intent != null) {
                final String action = intent.getAction();
                if (ACTION_FETCH.equals(action)) {
                    showToast("LSEBoard: 開始解析下載貼圖...");
                    final String param1 = intent.getStringExtra(EXTRA_PARAM1);
                    final boolean param2 = intent.getBooleanExtra(EXTRA_PARAM2, false);
                    handleActionFetch(param1, param2);
                }
            }
        } finally {
            // 確保前景服務一定被終止，不論成功或失敗
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        }
    }


    private void handleActionFetch(String param1, Boolean param2) {
        if (fetch(param1)) {
            if (download(param2)) {
                if (type == Sticker.Type.STATIC) {
                    SharedPrefHelper.addNewStickerPack(this,
                            new StickerPack(title, storeId, type, ids));
                    send("add", storeId, title, true);
                } else {
                    if (convert()) {
                        SharedPrefHelper.addNewStickerPack(this,
                                new StickerPack(title, storeId, type, ids));
                        send("add", storeId, title, true);
                    } else {
                        send("failed", storeId, title, false);
                    }
                }
            } else {
                send("failed", storeId, title, false);
            }
        } else {
            send("failed", storeId, title, false);
        }
    }

    /**
     * 嘗試展開短網址（如 lin.ee），回傳最終落地的 URL 字串。
     * 若非短網址或展開失敗，原樣回傳。
     */
    private String expandUrl(String urlStr) {
        try {
            // 只針對可能是短網址的情況才展開
            if (!urlStr.contains("lin.ee") && !urlStr.contains("liff.line.me")) {
                return urlStr;
            }
            // 找出 URL（支援文字中夾帶 URL 的格式）
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
            // 跟隨最多 5 次重定向
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
            Log.d("Fetcher", "Expanded URL: " + candidate);
            return candidate;
        } catch (Exception e) {
            Log.e("Fetcher", "expandUrl failed: " + e.getMessage());
            return urlStr;
        }
    }

    private boolean fetch(String param1) {
        // 1. 先嘗試展開短網址（lin.ee 等）
        String expanded = expandUrl(param1);
        Log.d("Fetcher", "Input: " + param1 + " | After expand: " + expanded);

        // 2. 從展開後的 URL 或原始文字中解析 storeId
        Pattern pattern = Pattern.compile("(?:sticker/|product/)(\\d+)");
        Matcher matcher = pattern.matcher(expanded);
        if (!matcher.find()) {
            // 若展開後仍找不到，再用原始文字試一次（防止展開失敗的情況）
            matcher = pattern.matcher(param1);
            if (!matcher.find()) {
                Log.e("Fetcher", "Cannot find sticker ID in: " + param1);
                return false;
            }
        }

        try {
            storeId = Integer.parseInt(matcher.group(1));
            String apiUrl = "https://stickershop.line-scdn.net/stickershop/v1/product/" + storeId + "/iphone/productInfo.meta";
            Log.d("Fetcher", "API URL: " + apiUrl);

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                Log.e("Fetcher", "Failed to get API response, code: " + responseCode);
                return false;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONObject json = new JSONObject(sb.toString());

            // 取得標題
            if (json.has("title")) {
                JSONObject titleObj = json.getJSONObject("title");
                if (titleObj.has("zh-Hant")) {
                    title = titleObj.getString("zh-Hant");
                } else if (titleObj.has("en")) {
                    title = titleObj.getString("en");
                } else if (titleObj.keys().hasNext()) {
                    title = titleObj.getString(titleObj.keys().next());
                } else {
                    title = "Sticker #" + storeId;
                }
            } else {
                title = "Sticker #" + storeId;
            }

            // 取得類型
            if (json.optBoolean("hasPopup", false)) {
                type = Sticker.Type.POPUP;
                Log.d("Fetcher", "Popup type detected via JSON API");
            } else if (json.optBoolean("hasAnimation", false)) {
                type = Sticker.Type.ANIMATED;
                Log.d("Fetcher", "Animated type detected via JSON API");
            } else {
                type = Sticker.Type.STATIC;
                Log.d("Fetcher", "Static type detected via JSON API");
            }

            // 取得貼圖 IDs
            if (json.has("stickers")) {
                JSONArray stickers = json.getJSONArray("stickers");
                ids = new ArrayList<>();
                for (int i = 0; i < stickers.length(); i++) {
                    ids.add(stickers.getJSONObject(i).getInt("id"));
                    Log.d("Fetcher", "Id: " + stickers.getJSONObject(i).getInt("id"));
                }
            }

            if (ids != null && ids.size() > 0) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean download(Boolean isFallback) {
        if (pngDir.mkdirs()) Log.d("Download", "pngDir created");
        int count = ids.size();
        int i = 0;
        for (int id : ids) {
            final File outputFile = new File(pngDir, id + ".png");
            final byte[] buffer = new byte[1024];
            InputStream inputStream = null;
            OutputStream outputStream = null;
            URL url;
            Log.d("Test", String.valueOf(isFallback));
            try {
                try {
                    switch (type) {
                        case STATIC:
                            if (isFallback) {
                                url = new URL(String.format(Locale.getDefault(), STATIC_URL_FORMAT_2, id));
                            } else {
                                url = new URL(String.format(Locale.getDefault(), STATIC_URL_FORMAT, id));
                            }
                            break;
                        case ANIMATED:
                            if (isFallback) {
                                url = new URL(String.format(Locale.getDefault(), ANIMATED_URL_FORMAT_2, id));
                            } else {
                                url = new URL(String.format(Locale.getDefault(), ANIMATED_URL_FORMAT, id));
                            }
                            break;
                        case POPUP:
                            if (isFallback) {
                                url = new URL(String.format(Locale.getDefault(), POPUP_URL_FORMAT_2, id));
                            } else {
                                url = new URL(String.format(Locale.getDefault(), POPUP_URL_FORMAT, id));
                            }
                            break;
                        default:
                            continue;
                    }
                    Log.d("Test", String.valueOf(url));
                    URLConnection urlConnection = url.openConnection();
                    urlConnection.connect();
                    outputStream = new FileOutputStream(outputFile);
                    inputStream = urlConnection.getInputStream();
                    while (true) {
                        final int numRead = inputStream.read(buffer);
                        if (numRead <= 0) {
                            break;
                        }
                        outputStream.write(buffer, 0, numRead);
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.createSource(outputFile);
                    }
                    Log.d("Downloader", id + " downloaded");
                    i++;
                } finally {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (outputStream != null) {
                        outputStream.flush();
                        outputStream.close();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    private boolean convert() {
        if (gifDir.mkdirs()) Log.d("Download", "gifDir created");
        File png, gif;
        int count = ids.size();
        int successCount = 0;
        for (int id : ids) {
            png = new File(pngDir, id + ".png");
            gif = new File(gifDir, id + ".gif");
            Log.d("Convert", "Converting sticker " + id + " (" + (successCount + 1) + "/" + count + ")");
            apng2GifCustom.start(png, gif);
            if (gif.exists() && gif.length() > 0) {
                successCount++;
                Log.d("Convert", "Sticker " + id + " converted successfully, GIF size: " + gif.length());
            } else {
                Log.e("Convert", "Sticker " + id + " conversion failed or GIF is empty");
            }
        }
        Log.d("Convert", "Conversion complete: " + successCount + "/" + count + " successful");
        return true;
    }

    private void send() {
        send("add", storeId, title, true);
    }

    private void send(String message, int storeId, String title, boolean success) {
        Intent intent = new Intent(BROADCAST_ACTION);
        intent.putExtra("message", message);
        intent.putExtra("storeId", storeId);
        intent.putExtra("title", title);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        final String toastMsg = success
                ? "下載完成：" + title
                : "下載失敗：" + (title.isEmpty() ? "無法解析連結，請確認貼圖是否為免費或已購買" : title);
        showToast(toastMsg);
    }

    private void showToast(String msg) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                android.widget.Toast.makeText(getApplicationContext(), msg, android.widget.Toast.LENGTH_LONG).show()
        );
    }
}

