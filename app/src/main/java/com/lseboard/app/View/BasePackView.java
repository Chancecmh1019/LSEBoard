package com.lseboard.app.View;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.DynamicColors;

import com.lseboard.app.Adapter.HistoryAdapter;
import com.lseboard.app.Adapter.StickerAdapter;
import com.lseboard.app.R;

public class BasePackView extends LinearLayout {
    protected RecyclerView recyclerView;
    protected int span;
    protected int stickerSize;

    public BasePackView(Context context) {
        super(context);
        init(context);
    }

    protected void init(Context context) {
        setOrientation(LinearLayout.HORIZONTAL);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setGravity(Gravity.CENTER_HORIZONTAL);

        // 套用動態顏色
        Context themedContext = new ContextThemeWrapper(context, R.style.Theme_LSEBoard_Keyboard);
        Context ctx = DynamicColors.wrapContextIfAvailable(themedContext);
        LayoutInflater.from(ctx).inflate(R.layout.view_pack, this, true);
        recyclerView = findViewById(R.id.recycler);

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        float density = displayMetrics.density;
        
        // 總邊距：RecyclerView padding (8dp) + item padding (4dp) = 12dp per side
        int totalHorizontalPadding = (int) (24 * density);
        int availableWidth = screenWidth - totalHorizontalPadding;
        
        // 根據螢幕寬度決定每行顯示幾個貼圖
        // 目標：讓貼圖更大，減少每行數量
        float screenWidthDp = screenWidth / density;
        if (screenWidthDp < 360) {
            // 小螢幕：3 個貼圖/行
            span = 3;
        } else if (screenWidthDp < 480) {
            // 中螢幕：4 個貼圖/行
            span = 4;
        } else if (screenWidthDp < 600) {
            // 大螢幕：4 個貼圖/行
            span = 4;
        } else {
            // 超大螢幕：5 個貼圖/行
            span = 5;
        }
        
        // 計算每個貼圖的大小
        // 減去 item 之間的間距 (4dp per item)
        int totalItemSpacing = (int) (4 * density * span);
        stickerSize = (availableWidth - totalItemSpacing) / span;
        
        final GridLayoutManager gridLayoutManager = new GridLayoutManager(context, span);
        recyclerView.setLayoutManager(gridLayoutManager);
    }

    protected void setUpRecyclerViewForHistory(HistoryAdapter adapter) {
        adapter.setStickerSize(stickerSize);
        recyclerView.setAdapter(adapter);
    }

    protected void setUpRecyclerViewForSticker(StickerAdapter adapter) {
        adapter.setStickerSize(stickerSize);
        recyclerView.setAdapter(adapter);
    }
}

