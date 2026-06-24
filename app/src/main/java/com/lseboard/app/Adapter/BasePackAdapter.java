package com.lseboard.app.Adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.material.card.MaterialCardView;

import java.io.File;

import com.lseboard.app.IMService;
import com.lseboard.app.Model.Sticker;
import com.lseboard.app.R;
import com.lseboard.app.Util.FileHelper;

public abstract class BasePackAdapter extends RecyclerView.Adapter<BasePackAdapter.StickerViewHolder> {

    private static final String TAG = "BasePackAdapter";
    private Context context;
    private IMService service;
    private int stickerSize = 0;

    BasePackAdapter(Context context) {
        this.context = context;
        if (context instanceof IMService)
            service = (IMService) context;
    }

    public void setStickerSize(int size) {
        this.stickerSize = size;
    }

    @NonNull
    @Override
    public StickerViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View itemView = View.inflate(viewGroup.getContext(), R.layout.item_sticker, null);
        
        // 動態設定貼圖大小
        if (stickerSize > 0) {
            MaterialCardView cardView = itemView.findViewById(R.id.cardView);
            if (cardView != null) {
                ViewGroup.LayoutParams params = cardView.getLayoutParams();
                params.width = stickerSize;
                params.height = stickerSize;
                cardView.setLayoutParams(params);
            }
        }
        
        return new StickerViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull final StickerViewHolder stickerViewHolder, int position) {
        Sticker sticker = getSticker(position);
        stickerViewHolder.itemView.setTag(sticker);
        stickerViewHolder.textView.setVisibility(View.VISIBLE);
        stickerViewHolder.textView.setText(R.string.loading);
        stickerViewHolder.textView.setTextColor(0xFF9E9E9E);

        // 取得檔案路徑
        File pngFile = FileHelper.getPngFile(context, sticker.getId());
        File gifFile = FileHelper.getGifFile(context, sticker.getId());
        
        // 判斷是否應該載入 GIF
        boolean isAnimated = sticker.getType() != Sticker.Type.STATIC;
        boolean gifExists = gifFile.exists() && gifFile.length() > 1000; // GIF 至少要有 1KB 才可能是有效動畫
        boolean useGif = isAnimated && gifExists;
        
        File file = useGif ? gifFile : pngFile;
        
        Log.d(TAG, "Loading sticker: " + sticker.getId() + 
              ", type: " + sticker.getType() + 
              ", useGif: " + useGif + 
              ", gifExists: " + gifFile.exists() +
              ", gifSize: " + (gifFile.exists() ? gifFile.length() : 0) +
              ", file: " + file.getName());

        if (!file.exists()) {
            Log.e(TAG, "Sticker file not found: " + file.getAbsolutePath());
            stickerViewHolder.textView.setText(R.string.error);
            stickerViewHolder.textView.setTextColor(0xFFF2B8B5);
            stickerViewHolder.imageView.setImageResource(R.drawable.error);
            return;
        }

        RequestOptions requestOptions = new RequestOptions()
                .error(R.drawable.error)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .signature(new ObjectKey(file.lastModified()));

        // 根據是否使用 GIF 來載入
        if (useGif) {
            Glide.with(context)
                    .asGif()
                    .load(file)
                    .apply(requestOptions)
                    .listener(new RequestListener<com.bumptech.glide.load.resource.gif.GifDrawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<com.bumptech.glide.load.resource.gif.GifDrawable> target, boolean isFirstResource) {
                            Log.e(TAG, "Failed to load GIF: " + (e != null ? e.getMessage() : "unknown error"));
                            stickerViewHolder.textView.setText(R.string.error);
                            stickerViewHolder.textView.setTextColor(0xFFF2B8B5);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(com.bumptech.glide.load.resource.gif.GifDrawable resource, Object model, Target<com.bumptech.glide.load.resource.gif.GifDrawable> target, DataSource dataSource, boolean isFirstResource) {
                            stickerViewHolder.textView.setVisibility(View.INVISIBLE);
                            return false;
                        }
                    })
                    .into(stickerViewHolder.imageView);
        } else {
            Glide.with(context)
                    .load(file)
                    .apply(requestOptions)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e(TAG, "Failed to load image: " + (e != null ? e.getMessage() : "unknown error"));
                            stickerViewHolder.textView.setText(R.string.error);
                            stickerViewHolder.textView.setTextColor(0xFFF2B8B5);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            stickerViewHolder.textView.setVisibility(View.INVISIBLE);
                            return false;
                        }
                    })
                    .into(stickerViewHolder.imageView);
        }

        stickerViewHolder.cardView.setOnClickListener(view -> {
            if (service != null) {
                service.postSticker((Sticker) stickerViewHolder.itemView.getTag(), saveHistory(), false);
            }
        });
    }

    protected abstract File getFile(Context context, int position);

    protected abstract Sticker getSticker(int position);

    protected abstract boolean saveHistory();

    // ViewHolder
    class StickerViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView textView;
        View cardView;

        StickerViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.textView);
            textView = itemView.findViewById(R.id.status);
            cardView = itemView.findViewById(R.id.cardView);
        }
    }
}

