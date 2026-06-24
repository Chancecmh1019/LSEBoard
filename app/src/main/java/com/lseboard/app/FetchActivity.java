package com.lseboard.app;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.DynamicColors;

public class FetchActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 啟用動態顏色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(this);
        }

        super.onCreate(savedInstanceState);

        // 保持螢幕亮起（下載期間）
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                android.widget.Toast.makeText(this, R.string.downloading_sticker, android.widget.Toast.LENGTH_SHORT).show();
                dispatchFetch(text);
            }
        } else if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            String data = intent.getData().toString();
            android.widget.Toast.makeText(this, R.string.downloading_sticker, android.widget.Toast.LENGTH_SHORT).show();
            dispatchFetch(data);
        }
        finish();
    }

    /**
     * 根據 URL 內容決定派送給哪個 Service。
     * 若是 lin.ee / liff.line.me 短網址，無法事先判斷類型，
     * 統一交給 FetchService 嘗試，FetchService 內部會展開短網址。
     * Emoji 的 lin.ee 短網址極為罕見；
     * 若貼圖失敗，使用者可改用「直接輸入 ID」功能。
     */
    private void dispatchFetch(String text) {
        boolean isShortUrl = text.contains("lin.ee") || text.contains("liff.line.me");
        if (!isShortUrl && (text.contains("emojishop") || text.contains("emoji/?id=") || text.contains("/S/emoji/"))) {
            EmojiFetchService.startActionFetchEmoji(this, text);
        } else {
            // 包含短網址或一般貼圖連結，交給 FetchService（內部會展開）
            FetchService.startActionFetch(this, text);
        }
    }
}


