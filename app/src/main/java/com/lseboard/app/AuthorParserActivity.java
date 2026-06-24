package com.lseboard.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 使用 WebView 解析作者頁面，提取所有貼圖/表情貼 ID
 */
public class AuthorParserActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TYPE = "type";
    public static final String RESULT_IDS = "ids";
    public static final String RESULT_TYPE = "type";

    public static final int TYPE_STICKER = 0;
    public static final int TYPE_EMOJI = 1;

    private WebView webView;
    private View progressBar;
    private TextView tvStatus;
    private int type = TYPE_STICKER;
    private boolean hasResult = false;

    // 用於提取 ID 的 Pattern
    private static final Pattern STICKER_PRODUCT_PATTERN = Pattern.compile(
            "stickershop/product/(\\d+)");
    private static final Pattern EMOJI_PRODUCT_PATTERN = Pattern.compile(
            "emojishop/product/([a-f0-9]+)");

    public static Intent createIntent(Context context, String url, int type) {
        Intent intent = new Intent(context, AuthorParserActivity.class);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_TYPE, type);
        return intent;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 啟用動態顏色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(this);
        }
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_author_parser);

        // 保持螢幕亮起（解析和下載期間）
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);

        String url = getIntent().getStringExtra(EXTRA_URL);
        type = getIntent().getIntExtra(EXTRA_TYPE, TYPE_STICKER);

        if (url == null || url.isEmpty()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        setupWebView();
        loadUrl(url);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        webView.addJavascriptInterface(new JsInterface(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 等待 JavaScript 載入完成
                tvStatus.setText(R.string.author_parsing);
                new Handler(Looper.getMainLooper()).postDelayed(() -> extractIds(), 2000);
            }
        });
    }

    private void loadUrl(String url) {
        tvStatus.setText(R.string.author_loading);
        webView.loadUrl(url);
    }

    private void extractIds() {
        if (hasResult) return;

        // 使用 JavaScript 提取頁面中的所有連結
        String js;
        if (type == TYPE_STICKER) {
            js = "javascript:(function() {" +
                    "var links = document.querySelectorAll('a');" +
                    "var ids = [];" +
                    "for (var i = 0; i < links.length; i++) {" +
                    "  var href = links[i].getAttribute('href');" +
                    "  if (!href) continue;" +
                    "  var match = href.match(/(?:stickershop\\/product\\/|S\\/sticker\\/)(\\d+)/);" +
                    "  if (match) ids.push(match[1]);" +
                    "}" +
                    "Android.onIdsFound(ids.join(','));" +
                    "})()";
        } else {
            js = "javascript:(function() {" +
                    "var links = document.querySelectorAll('a');" +
                    "var ids = [];" +
                    "for (var i = 0; i < links.length; i++) {" +
                    "  var href = links[i].getAttribute('href');" +
                    "  if (!href) continue;" +
                    "  var match = href.match(/(?:emojishop\\/product\\/|S\\/emoji\\/\\?id=)([a-f0-9]+)/);" +
                    "  if (match) ids.push(match[1]);" +
                    "}" +
                    "Android.onIdsFound(ids.join(','));" +
                    "})()";
        }
        webView.evaluateJavascript(js, null);
    }

    private class JsInterface {
        @JavascriptInterface
        public void onIdsFound(String idsStr) {
            if (hasResult) return;
            hasResult = true;

            runOnUiThread(() -> {
                if (idsStr == null || idsStr.isEmpty()) {
                    tvStatus.setText(R.string.author_no_items);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        setResult(RESULT_CANCELED);
                        finish();
                    }, 1500);
                    return;
                }

                String[] idArray = idsStr.split(",");
                Set<String> uniqueIds = new HashSet<>();
                for (String id : idArray) {
                    if (!id.isEmpty()) {
                        uniqueIds.add(id);
                    }
                }

                if (uniqueIds.isEmpty()) {
                    tvStatus.setText(R.string.author_no_items);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        setResult(RESULT_CANCELED);
                        finish();
                    }, 1500);
                    return;
                }

                ArrayList<String> idList = new ArrayList<>(uniqueIds);
                tvStatus.setText(getString(R.string.author_found_items, idList.size()));

                Intent result = new Intent();
                result.putStringArrayListExtra(RESULT_IDS, idList);
                result.putExtra(RESULT_TYPE, type);
                setResult(RESULT_OK, result);

                new Handler(Looper.getMainLooper()).postDelayed(() -> finish(), 800);
            });
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}

