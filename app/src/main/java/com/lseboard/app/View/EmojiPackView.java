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

import com.lseboard.app.Adapter.EmojiAdapter;
import com.lseboard.app.Model.EmojiPack;
import com.lseboard.app.R;

public class EmojiPackView extends LinearLayout {
    public EmojiAdapter adapter;
    protected RecyclerView recyclerView;
    protected int span;
    protected int emojiSize;

    public EmojiPackView(Context context) {
        super(context);
        init(context);
    }

    public EmojiPackView(Context context, EmojiPack emojiPack) {
        super(context);
        init(context);
        adapter = new EmojiAdapter(context, emojiPack);
        adapter.setEmojiSize(emojiSize);
        recyclerView.setAdapter(adapter);
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
        
        int totalPadding = (int) (12 * density);
        int availableWidth = screenWidth - totalPadding;
        
        // Emoji 通常比貼圖小，所以每行顯示更多
        float screenWidthDp = screenWidth / density;
        if (screenWidthDp < 360) {
            span = 5;
        } else if (screenWidthDp < 480) {
            span = 6;
        } else if (screenWidthDp < 600) {
            span = 7;
        } else {
            span = 8;
        }
        
        emojiSize = (availableWidth / span) - (int)(4 * density);
        
        final GridLayoutManager gridLayoutManager = new GridLayoutManager(context, span);
        recyclerView.setLayoutManager(gridLayoutManager);
    }
}

