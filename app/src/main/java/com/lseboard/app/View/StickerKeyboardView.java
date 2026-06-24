package com.lseboard.app.View;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.viewpager.widget.ViewPager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.util.ArrayList;

import com.lseboard.app.Adapter.StickerViewPagerAdapter;
import com.lseboard.app.BuildConfig;
import com.lseboard.app.IMService;
import com.lseboard.app.Model.EmojiPack;
import com.lseboard.app.Model.HistoryPack;
import com.lseboard.app.Model.Sticker;
import com.lseboard.app.Model.StickerPack;
import com.lseboard.app.R;
import com.lseboard.app.Util.FileHelper;
import com.lseboard.app.Util.SharedPrefHelper;

public class StickerKeyboardView extends LinearLayout implements View.OnClickListener {
    ArrayList<View> views = new ArrayList<>();
    HistoryPackView historyPackView;
    private HistoryPack historyPack;
    private Context imService;
    private View panel;
    
    // 用於追蹤分類
    private int stickerStartIndex = 1; // 貼圖開始的索引（0 是歷史紀錄）
    private int emojiStartIndex = 0;   // Emoji 開始的索引
    
    // 分類標籤
    private ImageButton btnSwitchCategory;
    private ViewPager viewPager;
    private TabLayout tabLayoutStickers;
    private TabLayout tabLayoutEmojis;
    
    // 過濾後的列表
    private ArrayList<StickerPack> stickerPacksFiltered = new ArrayList<>();
    private ArrayList<EmojiPack> emojiPacksFiltered = new ArrayList<>();
    
    private boolean historyDisabled = false;

    public StickerKeyboardView(Context context) {
        super(context);
        this.imService = context;
        init();
    }

    private void init() {
        setOrientation(LinearLayout.VERTICAL);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // 套用動態顏色到鍵盤主題
        Context themedContext = new ContextThemeWrapper(imService, R.style.Theme_LSEBoard_Keyboard);
        Context ctx = DynamicColors.wrapContextIfAvailable(themedContext);
        LayoutInflater.from(ctx).inflate(R.layout.keyboard_layout, this, true);

        // 檢查是否關閉歷史紀錄
        historyDisabled = SharedPrefHelper.getDisableHistory(imService);

        // History Pack（只在啟用時加入）
        if (!historyDisabled) {
            historyPack = SharedPrefHelper.getHistoryFromPref(imService);
            historyPackView = new HistoryPackView(imService, historyPack);
            views.add(historyPackView);
            stickerStartIndex = 1;
        } else {
            stickerStartIndex = 0;
        }

        // Sticker Packs
        for (StickerPack stickerPack : SharedPrefHelper.getStickerPacksFromPref(imService)) {
            if (stickerPack.getVisible()) {
                stickerPacksFiltered.add(stickerPack);
                views.add(new StickerPackView(imService, stickerPack));
            }
        }
        
        // 記錄 Emoji 開始的索引
        emojiStartIndex = views.size();
        
        // Emoji Packs
        for (EmojiPack emojiPack : SharedPrefHelper.getEmojiPacksFromPref(imService)) {
            if (emojiPack.getVisible()) {
                emojiPacksFiltered.add(emojiPack);
                views.add(new EmojiPackView(imService, emojiPack));
            }
        }

        // 分類標籤
        btnSwitchCategory = findViewById(R.id.btnSwitchCategory);
        
        // 設定分類切換按鈕點擊事件
        if (btnSwitchCategory != null) {
            btnSwitchCategory.setOnClickListener(v -> toggleCategory());
        }

        // ViewPager 和 TabLayout
        viewPager = findViewById(R.id.container);
        tabLayoutStickers = findViewById(R.id.tabLayoutStickers);
        tabLayoutEmojis = findViewById(R.id.tabLayoutEmojis);

        StickerViewPagerAdapter adapter = new StickerViewPagerAdapter(views);
        viewPager.setAdapter(adapter);

        LayoutInflater inflater = LayoutInflater.from(ctx);
        
        // 設定貼圖 TabLayout
        int stickerTabCount = emojiStartIndex; // 歷史 + 貼圖
        for (int i = 0; i < stickerTabCount; i++) {
            TabLayout.Tab tab = tabLayoutStickers.newTab();
            View view = inflater.inflate(R.layout.item_tab_icon, null);
            ImageView icon = view.findViewById(R.id.textView);
            
            if (!historyDisabled && i == 0) {
                // 歷史紀錄
                icon.setImageResource(R.drawable.baseline_history_white_36);
            } else {
                // 貼圖
                int stickerIndex = historyDisabled ? i : i - 1;
                if (stickerIndex >= 0 && stickerIndex < stickerPacksFiltered.size()) {
                    File file = FileHelper.getPngFile(imService, stickerPacksFiltered.get(stickerIndex).getId(0));
                    Glide.with(this)
                            .load(file)
                            .apply(new RequestOptions().signature(new ObjectKey(file.lastModified())))
                            .into(icon);
                }
            }
            
            tab.setCustomView(view);
            tabLayoutStickers.addTab(tab);
        }
        
        // 設定 Emoji TabLayout
        for (int i = 0; i < emojiPacksFiltered.size(); i++) {
            TabLayout.Tab tab = tabLayoutEmojis.newTab();
            View view = inflater.inflate(R.layout.item_tab_icon, null);
            ImageView icon = view.findViewById(R.id.textView);
            
            EmojiPack emojiPack = emojiPacksFiltered.get(i);
            File file = FileHelper.getEmojiPngFile(imService, emojiPack.getProductId(), emojiPack.getId(0));
            Glide.with(this)
                    .load(file)
                    .apply(new RequestOptions().signature(new ObjectKey(file.lastModified())))
                    .into(icon);
            
            tab.setCustomView(view);
            tabLayoutEmojis.addTab(tab);
        }
        
        // 貼圖 Tab 點擊事件
        tabLayoutStickers.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                viewPager.setCurrentItem(position, true);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                viewPager.setCurrentItem(position, true);
            }
        });
        
        // Emoji Tab 點擊事件
        tabLayoutEmojis.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = emojiStartIndex + tab.getPosition();
                viewPager.setCurrentItem(position, true);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                int position = emojiStartIndex + tab.getPosition();
                viewPager.setCurrentItem(position, true);
            }
        });
        
        // 監聽頁面切換以更新分類標籤狀態和儲存記憶
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                // 使底部標籤能隨手指滑動平滑移動 (Smooth scroll tabs)
                if (position < emojiStartIndex) {
                    tabLayoutStickers.setScrollPosition(position, positionOffset, true);
                } else {
                    tabLayoutEmojis.setScrollPosition(position - emojiStartIndex, positionOffset, true);
                }
            }

            @Override
            public void onPageSelected(int position) {
                updateCategoryTabSelection(position);
                updateBottomTabSelection(position);
                
                // 儲存最後選擇的 Tab - 2026 新功能
                SharedPrefHelper.setLastSelectedTab(imService, position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {}
        });

        // 記憶最後選擇的 Tab - 2026 新功能
        int lastSelectedTab = SharedPrefHelper.getLastSelectedTab(imService);
        boolean tabRestored = false;
        
        if (lastSelectedTab >= 0 && lastSelectedTab < views.size()) {
            // 嘗試還原最後選擇的 Tab
            viewPager.setCurrentItem(lastSelectedTab);
            tabRestored = true;
        }
        
        // 如果沒有記憶或記憶的 Tab 無效，使用預設規則
        if (!tabRestored) {
            if (!historyDisabled && historyPack != null && historyPack.size() != 0) {
                viewPager.setCurrentItem(0);
            } else if (stickerPacksFiltered.size() > 0) {
                viewPager.setCurrentItem(stickerStartIndex);
            } else if (emojiPacksFiltered.size() > 0) {
                viewPager.setCurrentItem(emojiStartIndex);
            }
        }
        
        // 初始化分類標籤選中狀態
        updateCategoryTabSelection(viewPager.getCurrentItem());
        updateBottomTabSelection(viewPager.getCurrentItem());

        // Setting Button
        ImageButton button = findViewById(R.id.imageButton);
        button.setOnClickListener(this);
        button.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (imService instanceof IMService) ((IMService) imService).showIMPicker();
                return true;
            }
        });

        // Setting Panel and buttons
        panel = findViewById(R.id.panel);
        panel.setVisibility(INVISIBLE);
        MaterialButton btnMenu = findViewById(R.id.btnMenu);
        MaterialButton btnSwitch = findViewById(R.id.btnSwitch);
        panel.setOnClickListener(this);
        btnMenu.setOnClickListener(this);
        btnSwitch.setOnClickListener(this);
        
        TextView tvVersion = findViewById(R.id.tvVersion);
        String verInfo = String.format("%s %s", imService.getString(R.string.app_name), BuildConfig.VERSION_NAME);
        tvVersion.setText(verInfo);
    }
    
    /**
     * 切換分類（貼圖 <-> Emoji）
     */
    private void toggleCategory() {
        if (viewPager == null) return;
        
        int currentPosition = viewPager.getCurrentItem();
        boolean isCurrentlyEmoji = currentPosition >= emojiStartIndex;
        
        if (isCurrentlyEmoji) {
            // 切換到貼圖
            scrollToStickers();
        } else {
            // 切換到 Emoji
            scrollToEmojis();
        }
    }
    
    /**
     * 更新底部 Tab 選中狀態和顯示
     */
    private void updateBottomTabSelection(int position) {
        boolean isEmoji = position >= emojiStartIndex;
        
        // 更新分類切換按鈕圖示
        if (btnSwitchCategory != null) {
            if (isEmoji) {
                btnSwitchCategory.setImageResource(R.drawable.baseline_collections_24);
                btnSwitchCategory.setContentDescription(imService.getString(R.string.tab_stickers));
            } else {
                btnSwitchCategory.setImageResource(R.drawable.baseline_emoji_emotions_24);
                btnSwitchCategory.setContentDescription(imService.getString(R.string.tab_emojis));
            }
        }
        
        if (isEmoji) {
            // 顯示 Emoji TabLayout，隱藏貼圖 TabLayout
            findViewById(R.id.tabLayoutStickersContainer).setVisibility(GONE);
            findViewById(R.id.tabLayoutEmojisContainer).setVisibility(VISIBLE);
            
            int emojiPosition = position - emojiStartIndex;
            if (emojiPosition >= 0 && emojiPosition < tabLayoutEmojis.getTabCount()) {
                tabLayoutEmojis.selectTab(tabLayoutEmojis.getTabAt(emojiPosition));
            }
        } else {
            // 顯示貼圖 TabLayout，隱藏 Emoji TabLayout
            findViewById(R.id.tabLayoutStickersContainer).setVisibility(VISIBLE);
            findViewById(R.id.tabLayoutEmojisContainer).setVisibility(GONE);
            
            if (position >= 0 && position < tabLayoutStickers.getTabCount()) {
                tabLayoutStickers.selectTab(tabLayoutStickers.getTabAt(position));
            }
        }
    }
    
    /**
     * 更新分類標籤選中狀態
     */
    private void updateCategoryTabSelection(int position) {
        // 不需要額外處理，由 updateBottomTabSelection 統一處理
    }
    
    /**
     * 滾動到貼圖區域
     */
    private void scrollToStickers() {
        if (viewPager == null) return;
        
        if (stickerPacksFiltered.size() > 0) {
            viewPager.setCurrentItem(stickerStartIndex, true);
        } else if (stickerStartIndex == 0) {
            viewPager.setCurrentItem(0, true);
        } else {
            viewPager.setCurrentItem(0, true);
        }
        updateCategoryTabSelection(viewPager.getCurrentItem());
    }
    
    /**
     * 滾動到 Emoji 區域
     */
    private void scrollToEmojis() {
        if (viewPager == null) return;
        
        if (emojiPacksFiltered.size() > 0 && emojiStartIndex < views.size()) {
            viewPager.setCurrentItem(emojiStartIndex, true);
            updateCategoryTabSelection(emojiStartIndex);
        }
    }

    public void refreshHistoryAdapter(Sticker sticker) {
        // 檢查是否關閉歷史紀錄
        if (SharedPrefHelper.getDisableHistory(imService)) {
            return;
        }
        historyPack.add(sticker);
        historyPackView.adapter.update(historyPack);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.panel) {
            panel.setVisibility(INVISIBLE);
        } else if (id == R.id.imageButton) {
            panel.setVisibility(VISIBLE);
        } else if (id == R.id.btnMenu) {
            if (imService instanceof IMService) ((IMService) imService).launchMainMenu();
            panel.setVisibility(INVISIBLE);
        } else if (id == R.id.btnSwitch) {
            if (imService instanceof IMService) ((IMService) imService).showIMPicker();
            panel.setVisibility(INVISIBLE);
        }
    }
}

