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
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.material.card.MaterialCardView;

import java.io.File;

import com.lseboard.app.IMService;
import com.lseboard.app.Model.Emoji;
import com.lseboard.app.Model.EmojiPack;
import com.lseboard.app.R;
import com.lseboard.app.Util.FileHelper;

public class EmojiAdapter extends RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder> {

    private static final String TAG = "EmojiAdapter";
    private Context context;
    private IMService service;
    private EmojiPack emojiPack;
    private int emojiSize = 0;

    public EmojiAdapter(Context context, EmojiPack emojiPack) {
        this.context = context;
        this.emojiPack = emojiPack;
        if (context instanceof IMService)
            service = (IMService) context;
    }

    public void setEmojiSize(int size) {
        this.emojiSize = size;
    }

    @NonNull
    @Override
    public EmojiViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View itemView = View.inflate(viewGroup.getContext(), R.layout.item_sticker, null);
        
        // 動態設定 Emoji 大小
        if (emojiSize > 0) {
            MaterialCardView cardView = itemView.findViewById(R.id.cardView);
            if (cardView != null) {
                ViewGroup.LayoutParams params = cardView.getLayoutParams();
                params.width = emojiSize;
                params.height = emojiSize;
                cardView.setLayoutParams(params);
            }
        }
        
        return new EmojiViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull final EmojiViewHolder emojiViewHolder, int position) {
        Emoji emoji = emojiPack.getEmoji(position);
        emojiViewHolder.itemView.setTag(emoji);
        emojiViewHolder.textView.setVisibility(View.VISIBLE);
        emojiViewHolder.textView.setText(R.string.loading);
        emojiViewHolder.textView.setTextColor(0xFF9E9E9E);

        // 根據類型選擇檔案（動態用 GIF，靜態用 PNG）
        File file = FileHelper.getEmojiFile(context, emoji);
        File pngFile = FileHelper.getEmojiPngFile(context, emojiPack.getProductId(), emoji.getId());
        
        // 如果 GIF 不存在，fallback 到 PNG
        if (!file.exists() && pngFile.exists()) {
            file = pngFile;
        }
        
        Log.d(TAG, "Loading emoji: " + emoji.getId() + ", type: " + emoji.getType() + ", file: " + file.getAbsolutePath() + ", exists: " + file.exists());

        if (!file.exists()) {
            Log.e(TAG, "Emoji file not found: " + file.getAbsolutePath());
            emojiViewHolder.textView.setText(R.string.error);
            emojiViewHolder.textView.setTextColor(0xFFF2B8B5);
            emojiViewHolder.imageView.setImageResource(R.drawable.error);
            return;
        }

        RequestOptions requestOptions = new RequestOptions()
                .error(R.drawable.error)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .signature(new ObjectKey(file.lastModified()));

        // 根據類型載入
        if (emoji.getType() == Emoji.Type.ANIMATED) {
            Glide.with(context)
                    .asGif()
                    .load(file)
                    .apply(requestOptions)
                    .listener(new RequestListener<GifDrawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<GifDrawable> target, boolean isFirstResource) {
                            Log.e(TAG, "Failed to load emoji GIF: " + (e != null ? e.getMessage() : "unknown error"));
                            emojiViewHolder.textView.setText(R.string.error);
                            emojiViewHolder.textView.setTextColor(0xFFF2B8B5);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(GifDrawable resource, Object model, Target<GifDrawable> target, DataSource dataSource, boolean isFirstResource) {
                            emojiViewHolder.textView.setVisibility(View.INVISIBLE);
                            return false;
                        }
                    })
                    .into(emojiViewHolder.imageView);
        } else {
            Glide.with(context)
                    .load(file)
                    .apply(requestOptions)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e(TAG, "Failed to load emoji image: " + (e != null ? e.getMessage() : "unknown error"));
                            emojiViewHolder.textView.setText(R.string.error);
                            emojiViewHolder.textView.setTextColor(0xFFF2B8B5);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            emojiViewHolder.textView.setVisibility(View.INVISIBLE);
                            return false;
                        }
                    })
                    .into(emojiViewHolder.imageView);
        }

        emojiViewHolder.cardView.setOnClickListener(view -> {
            if (service != null) {
                service.postEmoji((Emoji) emojiViewHolder.itemView.getTag(), false);
            }
        });
    }

    @Override
    public int getItemCount() {
        if (emojiPack != null)
            return emojiPack.getCount();
        else return 0;
    }

    public void update(EmojiPack emojiPack) {
        this.emojiPack = emojiPack;
        notifyDataSetChanged();
    }

    // ViewHolder
    class EmojiViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView textView;
        View cardView;

        EmojiViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.textView);
            textView = itemView.findViewById(R.id.status);
            cardView = itemView.findViewById(R.id.cardView);
        }
    }
}

