package com.lseboard.app;

import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.util.ArrayList;

import com.lseboard.app.Model.Emoji;
import com.lseboard.app.Model.EmojiPack;
import com.lseboard.app.Model.Sticker;
import com.lseboard.app.Model.StickerPack;
import com.lseboard.app.Util.Apng2GifCustom;
import com.lseboard.app.Util.FileHelper;
import com.lseboard.app.Util.SharedPrefHelper;

public class PackDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PACK_TYPE = "pack_type";
    public static final String EXTRA_PACK_INDEX = "pack_index";
    public static final String TYPE_STICKER = "sticker";
    public static final String TYPE_EMOJI = "emoji";

    private String packType;
    private int packIndex;
    private StickerPack stickerPack;
    private EmojiPack emojiPack;
    private RecyclerView recyclerStickers;
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pack_detail);

        packType = getIntent().getStringExtra(EXTRA_PACK_TYPE);
        packIndex = getIntent().getIntExtra(EXTRA_PACK_INDEX, 0);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        setSupportActionBar(toolbar);

        ImageView ivPackIcon = findViewById(R.id.ivPackIcon);
        TextView tvPackTitle = findViewById(R.id.tvPackTitle);
        TextView tvPackInfo = findViewById(R.id.tvPackInfo);
        recyclerStickers = findViewById(R.id.recyclerStickers);

        // 計算網格列數
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        float screenWidthDp = displayMetrics.widthPixels / displayMetrics.density;
        int spanCount = screenWidthDp < 360 ? 4 : (screenWidthDp < 600 ? 5 : 6);
        
        recyclerStickers.setLayoutManager(new GridLayoutManager(this, spanCount));

        if (TYPE_STICKER.equals(packType)) {
            ArrayList<StickerPack> packs = SharedPrefHelper.getStickerPacksFromPref(this);
            if (packIndex < packs.size()) {
                stickerPack = packs.get(packIndex);
                
                toolbar.setTitle(stickerPack.getTitle());
                tvPackTitle.setText(stickerPack.getTitle());
                tvPackInfo.setText(getString(R.string.sticker_count, stickerPack.getCount()));
                
                File iconFile = FileHelper.getPngFile(this, stickerPack.getId(0));
                Glide.with(this)
                        .load(iconFile)
                        .apply(new RequestOptions().signature(new ObjectKey(iconFile.lastModified())))
                        .into(ivPackIcon);
                
                recyclerStickers.setAdapter(new StickerPreviewAdapter());
            }
        } else if (TYPE_EMOJI.equals(packType)) {
            ArrayList<EmojiPack> packs = SharedPrefHelper.getEmojiPacksFromPref(this);
            if (packIndex < packs.size()) {
                emojiPack = packs.get(packIndex);
                
                toolbar.setTitle(emojiPack.getTitle());
                tvPackTitle.setText(emojiPack.getTitle());
                tvPackInfo.setText(getString(R.string.emoji_count, emojiPack.getCount()));
                
                File iconFile = FileHelper.getEmojiPngFile(this, emojiPack.getProductId(), emojiPack.getId(0));
                Glide.with(this)
                        .load(iconFile)
                        .apply(new RequestOptions().signature(new ObjectKey(iconFile.lastModified())))
                        .into(ivPackIcon);
                
                recyclerStickers.setAdapter(new EmojiPreviewAdapter());
            }
        }
    }

    private void showStickerPreviewDialog(Sticker sticker) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_sticker_preview);
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        ImageView ivPreview = dialog.findViewById(R.id.ivPreview);
        TextView tvStickerName = dialog.findViewById(R.id.tvStickerName);
        MaterialButton btnSendPng = dialog.findViewById(R.id.btnSendPng);
        MaterialButton btnSend = dialog.findViewById(R.id.btnSend);

        // 根據貼圖類型載入對應檔案（動態貼圖載入 GIF 以顯示動畫）
        File file = FileHelper.getFile(this, sticker);
        boolean isAnimated = sticker.getType() != Sticker.Type.STATIC && file.getName().endsWith(".gif");
        
        if (isAnimated) {
            Glide.with(this)
                    .asGif()
                    .load(file)
                    .apply(new RequestOptions().signature(new ObjectKey(file.lastModified())))
                    .into(ivPreview);
        } else {
            Glide.with(this)
                    .load(file)
                    .apply(new RequestOptions().signature(new ObjectKey(file.lastModified())))
                    .into(ivPreview);
        }

        tvStickerName.setText(stickerPack != null ? stickerPack.getTitle() : "");

        // 在設定頁面中，按鈕只是關閉對話框
        btnSendPng.setOnClickListener(v -> dialog.dismiss());
        btnSend.setOnClickListener(v -> dialog.dismiss());
        
        // 隱藏傳送按鈕（因為這是在設定頁面，不是鍵盤）
        btnSendPng.setVisibility(View.GONE);
        btnSend.setVisibility(View.GONE);

        dialog.show();
    }

    private void showEmojiPreviewDialog(Emoji emoji) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_sticker_preview);
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        ImageView ivPreview = dialog.findViewById(R.id.ivPreview);
        TextView tvStickerName = dialog.findViewById(R.id.tvStickerName);
        MaterialButton btnSendPng = dialog.findViewById(R.id.btnSendPng);
        MaterialButton btnSend = dialog.findViewById(R.id.btnSend);

        // 根據 Emoji 類型載入對應檔案（動態 Emoji 載入 GIF 以顯示動畫）
        File file = FileHelper.getEmojiFile(this, emoji);
        boolean isAnimated = emoji.getType() != Emoji.Type.STATIC && file.getName().endsWith(".gif");
        
        if (isAnimated) {
            Glide.with(this)
                    .asGif()
                    .load(file)
                    .apply(new RequestOptions().signature(new ObjectKey(file.lastModified())))
                    .into(ivPreview);
        } else {
            Glide.with(this)
                    .load(file)
                    .apply(new RequestOptions().signature(new ObjectKey(file.lastModified())))
                    .into(ivPreview);
        }

        tvStickerName.setText(emojiPack != null ? emojiPack.getTitle() : "");

        // 隱藏傳送按鈕
        btnSendPng.setVisibility(View.GONE);
        btnSend.setVisibility(View.GONE);

        dialog.show();
    }

    // 貼圖預覽 Adapter
    private class StickerPreviewAdapter extends RecyclerView.Adapter<StickerPreviewAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_sticker, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Sticker sticker = stickerPack.getSticker(position);
            // 根據貼圖類型載入對應檔案（動態貼圖載入 GIF）
            File file = FileHelper.getFile(PackDetailActivity.this, sticker);
            boolean isAnimated = sticker.getType() != Sticker.Type.STATIC && file.getName().endsWith(".gif");
            
            holder.textView.setVisibility(View.VISIBLE);
            holder.textView.setText(R.string.loading);
            
            if (isAnimated) {
                Glide.with(PackDetailActivity.this)
                        .asGif()
                        .load(file)
                        .apply(new RequestOptions().signature(new ObjectKey(file.lastModified())))
                        .listener(new RequestListener<com.bumptech.glide.load.resource.gif.GifDrawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<com.bumptech.glide.load.resource.gif.GifDrawable> target, boolean isFirstResource) {
                                holder.textView.setText(R.string.error);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(com.bumptech.glide.load.resource.gif.GifDrawable resource, Object model, Target<com.bumptech.glide.load.resource.gif.GifDrawable> target, DataSource dataSource, boolean isFirstResource) {
                                holder.textView.setVisibility(View.INVISIBLE);
                                return false;
                            }
                        })
                        .into(holder.imageView);
            } else {
                Glide.with(PackDetailActivity.this)
                        .load(file)
                        .apply(new RequestOptions().signature(new ObjectKey(file.lastModified())))
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                holder.textView.setText(R.string.error);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                holder.textView.setVisibility(View.INVISIBLE);
                                return false;
                            }
                        })
                        .into(holder.imageView);
            }

            holder.cardView.setOnClickListener(v -> showStickerPreviewDialog(sticker));
        }

        @Override
        public int getItemCount() {
            return stickerPack != null ? stickerPack.getCount() : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            TextView textView;
            View cardView;

            ViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.textView);
                textView = itemView.findViewById(R.id.status);
                cardView = itemView.findViewById(R.id.cardView);
            }
        }
    }

    // Emoji 預覽 Adapter
    private class EmojiPreviewAdapter extends RecyclerView.Adapter<EmojiPreviewAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_sticker, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Emoji emoji = emojiPack.getEmoji(position);
            // 根據 Emoji 類型載入對應檔案（動態 Emoji 載入 GIF）
            File file = FileHelper.getEmojiFile(PackDetailActivity.this, emoji);
            boolean isAnimated = emoji.getType() != Emoji.Type.STATIC && file.getName().endsWith(".gif");
            
            holder.textView.setVisibility(View.VISIBLE);
            holder.textView.setText(R.string.loading);
            
            if (isAnimated) {
                Glide.with(PackDetailActivity.this)
                        .asGif()
                        .load(file)
                        .apply(new RequestOptions().signature(new ObjectKey(file.lastModified())))
                        .listener(new RequestListener<com.bumptech.glide.load.resource.gif.GifDrawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<com.bumptech.glide.load.resource.gif.GifDrawable> target, boolean isFirstResource) {
                                holder.textView.setText(R.string.error);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(com.bumptech.glide.load.resource.gif.GifDrawable resource, Object model, Target<com.bumptech.glide.load.resource.gif.GifDrawable> target, DataSource dataSource, boolean isFirstResource) {
                                holder.textView.setVisibility(View.INVISIBLE);
                                return false;
                            }
                        })
                        .into(holder.imageView);
            } else {
                Glide.with(PackDetailActivity.this)
                        .load(file)
                        .apply(new RequestOptions().signature(new ObjectKey(file.lastModified())))
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                holder.textView.setText(R.string.error);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                holder.textView.setVisibility(View.INVISIBLE);
                                return false;
                            }
                        })
                        .into(holder.imageView);
            }

            holder.cardView.setOnClickListener(v -> showEmojiPreviewDialog(emoji));
        }

        @Override
        public int getItemCount() {
            return emojiPack != null ? emojiPack.getCount() : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            TextView textView;
            View cardView;

            ViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.textView);
                textView = itemView.findViewById(R.id.status);
                cardView = itemView.findViewById(R.id.cardView);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 只有動態貼圖/Emoji 才顯示重新轉換選項
        boolean isAnimated = false;
        if (TYPE_STICKER.equals(packType) && stickerPack != null) {
            isAnimated = stickerPack.getType() != Sticker.Type.STATIC;
        } else if (TYPE_EMOJI.equals(packType) && emojiPack != null) {
            isAnimated = emojiPack.getType() != Emoji.Type.STATIC;
        }
        
        if (isAnimated) {
            menu.add(0, 1, 0, R.string.reconvert_gif)
                    .setIcon(R.drawable.ic_m3_refresh)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            reconvertGif();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reconvertGif() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_progress_m3, null);
        LinearProgressIndicator progressIndicator = dialogView.findViewById(R.id.progressIndicator);
        
        androidx.appcompat.app.AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();
                
        progressDialog.show();

        new Thread(() -> {
            Handler handler = new Handler(Looper.getMainLooper());
            Apng2GifCustom converter = new Apng2GifCustom();
            
            if (TYPE_STICKER.equals(packType) && stickerPack != null) {
                ArrayList<Integer> ids = stickerPack.getIds();
                int total = ids.size();
                handler.post(() -> {
                    progressIndicator.setMax(total);
                    progressIndicator.setProgress(0);
                });
                
                File pngDir = new File(getFilesDir(), "png");
                File gifDir = new File(getFilesDir(), "gif");
                if (!gifDir.exists()) gifDir.mkdirs();
                
                for (int i = 0; i < total; i++) {
                    int id = ids.get(i);
                    File png = new File(pngDir, id + ".png");
                    File gif = new File(gifDir, id + ".gif");
                    
                    if (gif.exists()) gif.delete();
                    converter.start(png, gif);
                    
                    int progress = i + 1;
                    handler.post(() -> progressIndicator.setProgress(progress));
                }
            } else if (TYPE_EMOJI.equals(packType) && emojiPack != null) {
                ArrayList<Integer> ids = emojiPack.getIds();
                int total = ids.size();
                handler.post(() -> {
                    progressIndicator.setMax(total);
                    progressIndicator.setProgress(0);
                });
                
                String productId = emojiPack.getProductId();
                File pngDir = new File(getFilesDir(), "emoji/" + productId);
                File gifDir = new File(getFilesDir(), "emoji_gif/" + productId);
                if (!gifDir.exists()) gifDir.mkdirs();
                
                for (int i = 0; i < total; i++) {
                    int id = ids.get(i);
                    File png = new File(pngDir, id + ".png");
                    File gif = new File(gifDir, id + ".gif");
                    
                    if (gif.exists()) gif.delete();
                    converter.start(png, gif);
                    
                    int progress = i + 1;
                    handler.post(() -> progressIndicator.setProgress(progress));
                }
            }
            
            handler.post(() -> {
                progressDialog.dismiss();
                Toast.makeText(PackDetailActivity.this, R.string.convert_complete, Toast.LENGTH_SHORT).show();
                
                // 清除 Glide 快取並重新載入
                Glide.get(PackDetailActivity.this).clearMemory();
                if (recyclerStickers.getAdapter() != null) {
                    recyclerStickers.getAdapter().notifyDataSetChanged();
                }
            });
        });
    }
}

