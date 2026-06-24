package com.lseboard.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.lseboard.app.Adapter.AdapterCallback;
import com.lseboard.app.Adapter.EmojiAdapterCallback;
import com.lseboard.app.Adapter.EmojiListAdapter;
import com.lseboard.app.Adapter.ListAdapter;
import com.lseboard.app.Model.EmojiPack;
import com.lseboard.app.Model.StickerPack;
import com.lseboard.app.Util.SharedPrefHelper;

public class CollectionActivity extends AppCompatActivity {

    private static final int TAB_STICKER = 0;
    private static final int TAB_EMOJI = 1;

    // URL 解析 Pattern
    private static final Pattern STICKER_PATTERN = Pattern.compile(
            "(?:line\\.me/S/sticker/|stickershop/product/)(\\d+)");
    private static final Pattern EMOJI_PATTERN_1 = Pattern.compile(
            "emojishop/product/([a-f0-9]+)");
    private static final Pattern EMOJI_PATTERN_2 = Pattern.compile(
            "emoji/\\?id=([a-f0-9]+)");
    private static final Pattern PURE_STICKER_ID = Pattern.compile("^\\d+$");
    private static final Pattern PURE_EMOJI_ID = Pattern.compile("^([a-f0-9]{24})$");
    
    // 作者頁面 Pattern
    private static final Pattern STICKER_AUTHOR_PATTERN = Pattern.compile(
            "store\\.line\\.me/stickershop/author/(\\d+)");
    private static final Pattern STICKER_AUTHOR_PATTERN_2 = Pattern.compile(
            "line\\.me/S/shop/sticker/author/(\\d+)");
    private static final Pattern EMOJI_AUTHOR_PATTERN = Pattern.compile(
            "store\\.line\\.me/emojishop/author/(\\d+)");
    private static final Pattern EMOJI_AUTHOR_PATTERN_2 = Pattern.compile(
            "line\\.me/S/shop/emoji/author/(\\d+)");

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private ExtendedFloatingActionButton fabAdd;
    private View rootView;

    // Fragment 相關
    private PackListFragment stickerFragment;
    private PackListFragment emojiFragment;

    // 廣播接收器
    private BroadcastReceiver stickerReceiver;
    private BroadcastReceiver emojiReceiver;

    // 導入導出
    private ActivityResultLauncher<Intent> exportLauncher;
    private ActivityResultLauncher<Intent> importLauncher;
    private ActivityResultLauncher<Intent> authorParserLauncher;
    private String pendingExportJson;
    private int pendingExportType = -1;

    // 批量下載
    private AlertDialog batchProgressDialog;
    private LinearProgressIndicator progressBar;
    private TextView tvStatus;
    private TextView tvProgress;
    private Queue<Object> downloadQueue = new LinkedList<>();
    private int totalDownloadCount = 0;
    private int completedCount = 0;
    private int failedCount = 0;
    private int currentDownloadType = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 啟用 Material 3 動態顏色 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(this);
        }
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collection);

        rootView = findViewById(android.R.id.content);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // TabLayout & ViewPager
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);
        fabAdd = findViewById(R.id.fabAdd);

        setupViewPager();
        setupFab();
        setupReceivers();
        setupLaunchers();
    }


    private void setupViewPager() {
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                if (position == TAB_STICKER) {
                    stickerFragment = PackListFragment.newInstance(PackListFragment.TYPE_STICKER);
                    return stickerFragment;
                } else {
                    emojiFragment = PackListFragment.newInstance(PackListFragment.TYPE_EMOJI);
                    return emojiFragment;
                }
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        });

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == TAB_STICKER) {
                tab.setText(R.string.tab_stickers);
                tab.setIcon(R.drawable.baseline_collections_24);
            } else {
                tab.setText(R.string.tab_emojis);
                tab.setIcon(R.drawable.baseline_emoji_emotions_24);
            }
        }).attach();

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                fabAdd.extend();
            }
        });
    }

    private void setupFab() {
        fabAdd.setOnClickListener(v -> showAddDialog());
    }

    private void setupReceivers() {
        // 貼圖廣播
        stickerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String message = intent.getStringExtra("message");
                int storeId = intent.getIntExtra("storeId", 0);
                String title = intent.getStringExtra("title");
                boolean success = intent.getBooleanExtra("success", false);

                if ("add".equals(message)) {
                    if (stickerFragment != null) {
                        stickerFragment.refreshData();
                    }
                    if (storeId != 0) {
                        onDownloadResult(true);
                    }
                } else if ("failed".equals(message)) {
                    if (storeId != 0) {
                        onDownloadResult(false);
                    }
                }
            }
        };

        // Emoji 廣播
        emojiReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String message = intent.getStringExtra("message");
                String productId = intent.getStringExtra("productId");
                boolean success = intent.getBooleanExtra("success", false);

                if ("add_emoji".equals(message)) {
                    if (emojiFragment != null) {
                        emojiFragment.refreshData();
                    }
                    if (productId != null) {
                        onDownloadResult(true);
                    }
                } else if ("failed_emoji".equals(message)) {
                    if (productId != null) {
                        onDownloadResult(false);
                    }
                }
            }
        };

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(stickerReceiver, new IntentFilter(FetchService.BROADCAST_ACTION));
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(emojiReceiver, new IntentFilter(EmojiFetchService.BROADCAST_ACTION));
    }

    private void setupLaunchers() {
        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null && pendingExportJson != null) {
                            writeExportFile(uri, pendingExportJson);
                        }
                    }
                    pendingExportJson = null;
                    pendingExportType = -1;
                });

        importLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            readImportFile(uri);
                        }
                    }
                });

        // 作者頁面解析結果
        authorParserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> ids = result.getData().getStringArrayListExtra(
                                AuthorParserActivity.RESULT_IDS);
                        int type = result.getData().getIntExtra(
                                AuthorParserActivity.RESULT_TYPE, AuthorParserActivity.TYPE_STICKER);
                        
                        if (ids != null && !ids.isEmpty()) {
                            if (type == AuthorParserActivity.TYPE_STICKER) {
                                ArrayList<Integer> stickerIds = new ArrayList<>();
                                for (String id : ids) {
                                    try {
                                        stickerIds.add(Integer.parseInt(id));
                                    } catch (NumberFormatException ignored) {}
                                }
                                viewPager.setCurrentItem(TAB_STICKER);
                                startMixedBatchDownload(stickerIds, new ArrayList<>());
                            } else {
                                viewPager.setCurrentItem(TAB_EMOJI);
                                startMixedBatchDownload(new ArrayList<>(), ids);
                            }
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 確保清除螢幕保持亮起標記
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stickerReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(emojiReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.collection_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.export_data) {
            exportData();
            return true;
        } else if (id == R.id.import_data) {
            importData();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAddDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_pack, null);
        EditText etInput = view.findViewById(R.id.etInput);

        new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setPositiveButton(R.string.positive_confirm, (dialog, which) -> {
                    String input = etInput.getText().toString().trim();
                    if (input.isEmpty()) {
                        Snackbar.make(rootView, R.string.invalid_id, Snackbar.LENGTH_SHORT).show();
                        return;
                    }
                    parseAndDownload(input);
                })
                .setNegativeButton(R.string.negative_cancel, null)
                .show();
    }

    /**
     * 解析輸入並自動判斷類型，開始下載
     */
    private void parseAndDownload(String input) {
        // 先檢查是否為作者頁面 URL
        String trimmedInput = input.trim();
        
        // 檢查貼圖作者頁面（兩種格式）
        Matcher authorMatcher = STICKER_AUTHOR_PATTERN.matcher(trimmedInput);
        if (authorMatcher.find()) {
            // 啟動 WebView 解析作者頁面
            Intent intent = AuthorParserActivity.createIntent(this, trimmedInput, 
                    AuthorParserActivity.TYPE_STICKER);
            authorParserLauncher.launch(intent);
            return;
        }
        
        // 檢查貼圖作者頁面（line.me/S/shop/sticker/author/xxx 格式）
        authorMatcher = STICKER_AUTHOR_PATTERN_2.matcher(trimmedInput);
        if (authorMatcher.find()) {
            // 轉換為 store.line.me 格式
            String authorId = authorMatcher.group(1);
            String storeUrl = "https://store.line.me/stickershop/author/" + authorId;
            Intent intent = AuthorParserActivity.createIntent(this, storeUrl, 
                    AuthorParserActivity.TYPE_STICKER);
            authorParserLauncher.launch(intent);
            return;
        }
        
        // 檢查表情貼作者頁面
        authorMatcher = EMOJI_AUTHOR_PATTERN.matcher(trimmedInput);
        if (authorMatcher.find()) {
            Intent intent = AuthorParserActivity.createIntent(this, trimmedInput,
                    AuthorParserActivity.TYPE_EMOJI);
            authorParserLauncher.launch(intent);
            return;
        }
        
        // 檢查表情貼作者頁面（line.me/S/shop/emoji/author/xxx 格式）
        authorMatcher = EMOJI_AUTHOR_PATTERN_2.matcher(trimmedInput);
        if (authorMatcher.find()) {
            String authorId = authorMatcher.group(1);
            String storeUrl = "https://store.line.me/emojishop/author/" + authorId;
            Intent intent = AuthorParserActivity.createIntent(this, storeUrl,
                    AuthorParserActivity.TYPE_EMOJI);
            authorParserLauncher.launch(intent);
            return;
        }
        
        // 一般解析邏輯
        ArrayList<Integer> stickerIds = new ArrayList<>();
        ArrayList<String> emojiIds = new ArrayList<>();

        String[] lines = input.split("[\\r\\n]+");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 嘗試解析為貼圖
            Matcher m = STICKER_PATTERN.matcher(line);
            if (m.find()) {
                try {
                    stickerIds.add(Integer.parseInt(m.group(1)));
                } catch (NumberFormatException ignored) {}
                continue;
            }

            // 嘗試解析為 Emoji
            m = EMOJI_PATTERN_1.matcher(line);
            if (m.find()) {
                emojiIds.add(m.group(1));
                continue;
            }
            m = EMOJI_PATTERN_2.matcher(line);
            if (m.find()) {
                emojiIds.add(m.group(1));
                continue;
            }

            // 純數字 -> 貼圖
            if (PURE_STICKER_ID.matcher(line).matches()) {
                try {
                    stickerIds.add(Integer.parseInt(line));
                } catch (NumberFormatException ignored) {}
                continue;
            }

            // 24位 hex -> Emoji
            m = PURE_EMOJI_ID.matcher(line);
            if (m.find()) {
                emojiIds.add(m.group(1));
            }
        }

        if (stickerIds.isEmpty() && emojiIds.isEmpty()) {
            Snackbar.make(rootView, R.string.batch_add_no_valid_id, Snackbar.LENGTH_SHORT).show();
            return;
        }

        startMixedBatchDownload(stickerIds, emojiIds);
    }


    /**
     * 開始混合批量下載（貼圖 + Emoji）
     */
    private void startMixedBatchDownload(ArrayList<Integer> stickerIds, ArrayList<String> emojiIds) {
        // 下載期間保持螢幕亮起
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        downloadQueue.clear();
        
        // 先加入貼圖
        for (Integer id : stickerIds) {
            downloadQueue.add(new StickerDownloadItem(id));
        }
        // 再加入 Emoji
        for (String id : emojiIds) {
            downloadQueue.add(new EmojiDownloadItem(id));
        }

        totalDownloadCount = downloadQueue.size();
        completedCount = 0;
        failedCount = 0;

        // 顯示進度對話框
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_batch_progress, null);
        progressBar = dialogView.findViewById(R.id.progressBar);
        tvStatus = dialogView.findViewById(R.id.tvStatus);
        tvProgress = dialogView.findViewById(R.id.tvProgress);

        progressBar.setMax(totalDownloadCount);
        progressBar.setProgress(0);
        tvProgress.setText(String.format("0 / %d", totalDownloadCount));

        String summary = "";
        if (!stickerIds.isEmpty() && !emojiIds.isEmpty()) {
            summary = getString(R.string.batch_mixed_summary, stickerIds.size(), emojiIds.size());
        } else if (!stickerIds.isEmpty()) {
            summary = getString(R.string.batch_sticker_summary, stickerIds.size());
        } else {
            summary = getString(R.string.batch_emoji_summary, emojiIds.size());
        }
        tvStatus.setText(summary);

        batchProgressDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.batch_download_title)
                .setView(dialogView)
                .setCancelable(false)
                .setNegativeButton(R.string.close, (dialog, which) -> {
                    downloadQueue.clear();
                })
                .create();
        batchProgressDialog.show();

        processNextDownload();
    }

    private void processNextDownload() {
        if (downloadQueue.isEmpty()) {
            // 下載完成，清除螢幕保持亮起的標記
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            
            if (tvStatus != null) {
                tvStatus.setText(getString(R.string.batch_download_complete));
            }
            if (batchProgressDialog != null) {
                batchProgressDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText(R.string.close);
            }
            return;
        }

        Object item = downloadQueue.poll();
        if (item instanceof StickerDownloadItem) {
            StickerDownloadItem stickerItem = (StickerDownloadItem) item;
            currentDownloadType = TAB_STICKER;
            if (tvStatus != null) {
                tvStatus.setText(getString(R.string.batch_download_sticker, stickerItem.storeId));
            }
            FetchService.startActionFetchManual(this, stickerItem.storeId, false);
        } else if (item instanceof EmojiDownloadItem) {
            EmojiDownloadItem emojiItem = (EmojiDownloadItem) item;
            currentDownloadType = TAB_EMOJI;
            if (tvStatus != null) {
                tvStatus.setText(getString(R.string.batch_download_emoji, emojiItem.productId));
            }
            EmojiFetchService.startActionFetchEmojiManual(this, emojiItem.productId, false);
        }
    }

    private void onDownloadResult(boolean success) {
        if (batchProgressDialog == null || !batchProgressDialog.isShowing()) {
            return;
        }

        if (success) {
            completedCount++;
        } else {
            failedCount++;
        }

        int progress = completedCount + failedCount;
        if (progressBar != null) {
            progressBar.setProgress(progress);
        }
        if (tvProgress != null) {
            tvProgress.setText(String.format("%d / %d", progress, totalDownloadCount));
        }

        processNextDownload();
    }

    // 導出資料
    private void exportData() {
        int currentTab = viewPager.getCurrentItem();
        Gson gson = new Gson();

        if (currentTab == TAB_STICKER) {
            ArrayList<StickerPack> packs = SharedPrefHelper.getStickerPacksFromPref(this);
            if (packs == null || packs.isEmpty()) {
                Snackbar.make(rootView, R.string.no_data_to_export, Snackbar.LENGTH_SHORT).show();
                return;
            }
            pendingExportJson = gson.toJson(packs);
            pendingExportType = TAB_STICKER;

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "LSEBoard_sticker_backup.json");
            exportLauncher.launch(intent);
        } else {
            ArrayList<EmojiPack> packs = SharedPrefHelper.getEmojiPacksFromPref(this);
            if (packs == null || packs.isEmpty()) {
                Snackbar.make(rootView, R.string.no_data_to_export, Snackbar.LENGTH_SHORT).show();
                return;
            }
            pendingExportJson = gson.toJson(packs);
            pendingExportType = TAB_EMOJI;

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "LSEBoard_emoji_backup.json");
            exportLauncher.launch(intent);
        }
    }

    private void writeExportFile(Uri uri, String json) {
        try {
            OutputStream outputStream = getContentResolver().openOutputStream(uri);
            if (outputStream != null) {
                outputStream.write(json.getBytes());
                outputStream.close();
                Snackbar.make(rootView, R.string.export_success, Snackbar.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Snackbar.make(rootView, R.string.export_failed, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void importData() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        importLauncher.launch(intent);
    }

    private void readImportFile(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return;

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            inputStream.close();

            String json = sb.toString();
            Gson gson = new Gson();

            // 嘗試解析為貼圖
            try {
                ArrayList<StickerPack> stickerPacks = gson.fromJson(json,
                        new TypeToken<ArrayList<StickerPack>>() {}.getType());
                if (stickerPacks != null && !stickerPacks.isEmpty() 
                        && stickerPacks.get(0).getStoreId() > 0) {
                    importStickerPacks(stickerPacks);
                    return;
                }
            } catch (Exception ignored) {}

            // 嘗試解析為 Emoji
            try {
                ArrayList<EmojiPack> emojiPacks = gson.fromJson(json,
                        new TypeToken<ArrayList<EmojiPack>>() {}.getType());
                if (emojiPacks != null && !emojiPacks.isEmpty()
                        && emojiPacks.get(0).getProductId() != null) {
                    importEmojiPacks(emojiPacks);
                    return;
                }
            } catch (Exception ignored) {}

            Snackbar.make(rootView, R.string.import_failed, Snackbar.LENGTH_SHORT).show();

        } catch (Exception e) {
            Snackbar.make(rootView, R.string.import_failed, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void importStickerPacks(ArrayList<StickerPack> importedPacks) {
        ArrayList<StickerPack> existing = SharedPrefHelper.getStickerPacksFromPref(this);
        Set<Integer> existingIds = new HashSet<>();
        for (StickerPack pack : existing) {
            existingIds.add(pack.getStoreId());
        }

        ArrayList<Integer> toDownload = new ArrayList<>();
        for (StickerPack pack : importedPacks) {
            if (!existingIds.contains(pack.getStoreId())) {
                toDownload.add(pack.getStoreId());
            }
        }

        if (toDownload.isEmpty()) {
            Snackbar.make(rootView, R.string.import_all_exists, Snackbar.LENGTH_SHORT).show();
        } else {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.batch_download_title)
                    .setMessage(getString(R.string.import_confirm, toDownload.size()))
                    .setPositiveButton(R.string.positive_confirm, (d, w) -> {
                        viewPager.setCurrentItem(TAB_STICKER);
                        startMixedBatchDownload(toDownload, new ArrayList<>());
                    })
                    .setNegativeButton(R.string.negative_cancel, null)
                    .show();
        }
    }

    private void importEmojiPacks(ArrayList<EmojiPack> importedPacks) {
        ArrayList<EmojiPack> existing = SharedPrefHelper.getEmojiPacksFromPref(this);
        Set<String> existingIds = new HashSet<>();
        for (EmojiPack pack : existing) {
            existingIds.add(pack.getProductId());
        }

        ArrayList<String> toDownload = new ArrayList<>();
        for (EmojiPack pack : importedPacks) {
            if (!existingIds.contains(pack.getProductId())) {
                toDownload.add(pack.getProductId());
            }
        }

        if (toDownload.isEmpty()) {
            Snackbar.make(rootView, R.string.import_all_exists, Snackbar.LENGTH_SHORT).show();
        } else {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.batch_download_title)
                    .setMessage(getString(R.string.import_confirm, toDownload.size()))
                    .setPositiveButton(R.string.positive_confirm, (d, w) -> {
                        viewPager.setCurrentItem(TAB_EMOJI);
                        startMixedBatchDownload(new ArrayList<>(), toDownload);
                    })
                    .setNegativeButton(R.string.negative_cancel, null)
                    .show();
        }
    }

    // 下載項目類別
    private static class StickerDownloadItem {
        int storeId;
        StickerDownloadItem(int storeId) { this.storeId = storeId; }
    }

    private static class EmojiDownloadItem {
        String productId;
        EmojiDownloadItem(String productId) { this.productId = productId; }
    }
}

