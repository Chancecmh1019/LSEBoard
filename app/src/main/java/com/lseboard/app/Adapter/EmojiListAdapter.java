package com.lseboard.app.Adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

import com.lseboard.app.FetchService;
import com.lseboard.app.Model.EmojiPack;
import com.lseboard.app.PackDetailActivity;
import com.lseboard.app.R;
import com.lseboard.app.Util.FileHelper;
import com.lseboard.app.Util.SharedPrefHelper;

public class EmojiListAdapter extends RecyclerView.Adapter<EmojiListAdapter.EmojiViewHolder> implements ItemTouchHelperAdapter {
    private Context context;
    private ArrayList<EmojiPack> emojiPacks;

    public EmojiListAdapter(Context context, ArrayList<EmojiPack> emojiPacks) {
        this.context = context;
        this.emojiPacks = emojiPacks;
    }

    @NonNull
    @Override
    public EmojiViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View itemView = LayoutInflater.from(context).inflate(R.layout.item_edit, viewGroup, false);
        return new EmojiViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull EmojiViewHolder holder, int i) {
        EmojiPack pack = emojiPacks.get(i);
        holder.itemView.setTag(pack);
        holder.textView.setText(pack.getTitle());
        
        // 根據類型選擇檔案（動態用 GIF，靜態用 PNG）
        File file;
        if (pack.getType() == com.lseboard.app.Model.Emoji.Type.STATIC) {
            file = FileHelper.getEmojiPngFile(context, pack.getProductId(), pack.getId(0));
        } else {
            File gifFile = FileHelper.getEmojiGifFile(context, pack.getProductId(), pack.getId(0));
            file = gifFile.exists() ? gifFile : FileHelper.getEmojiPngFile(context, pack.getProductId(), pack.getId(0));
        }
        
        RequestOptions requestOptions = new RequestOptions().signature(new ObjectKey(file.lastModified()));
        
        if (file.getName().endsWith(".gif")) {
            Glide.with(context).asGif().load(file).apply(requestOptions).into(holder.imageView);
        } else {
            Glide.with(context).load(file).apply(requestOptions).into(holder.imageView);
        }
        
        holder.button.setOnClickListener(v -> {
            int pos = emojiPacks.indexOf((EmojiPack) holder.itemView.getTag());
            delete(pos);
        });
        
        holder.itemView.setOnClickListener(v -> {
            int pos = emojiPacks.indexOf((EmojiPack) holder.itemView.getTag());
            Intent detailIntent = new Intent(context, PackDetailActivity.class);
            detailIntent.putExtra(PackDetailActivity.EXTRA_PACK_TYPE, PackDetailActivity.TYPE_EMOJI);
            detailIntent.putExtra(PackDetailActivity.EXTRA_PACK_INDEX, pos);
            context.startActivity(detailIntent);
        });
        
        holder.toggle.setChecked(pack.getVisible());
        holder.toggle.setOnClickListener(v -> {
            int pos = emojiPacks.indexOf((EmojiPack) holder.itemView.getTag());
            boolean isChecked = holder.toggle.isChecked();
            emojiPacks.get(pos).setVisible(isChecked);
            SharedPrefHelper.saveNewEmojiPacks(context, emojiPacks);
            Intent intent = new Intent();
            intent.setAction(FetchService.BROADCAST_ACTION);
            intent.putExtra("message", "refresh");
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        });
    }

    private void delete(final int index) {
        new AlertDialog.Builder(context)
                .setMessage(context.getString(R.string.delete_emoji))
                .setPositiveButton(context.getString(R.string.positive_confirm), (dialog, i) -> {
                    FileHelper.deleteEmojiFile(context, emojiPacks.get(index));
                    emojiPacks.remove(index);
                    SharedPrefHelper.saveNewEmojiPacks(context, emojiPacks);
                    notifyItemRemoved(index);
                    Intent intent = new Intent();
                    intent.setAction(FetchService.BROADCAST_ACTION);
                    intent.putExtra("message", "delete");
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                })
                .setNegativeButton(context.getString(R.string.negative_cancel), null)
                .show();
    }

    public void setData(ArrayList<EmojiPack> emojiPacks) {
        this.emojiPacks = emojiPacks;
    }

    @Override
    public int getItemCount() {
        return emojiPacks.size();
    }

    @Override
    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(emojiPacks, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(emojiPacks, i, i - 1);
            }
        }
        SharedPrefHelper.saveNewEmojiPacks(context, emojiPacks);
        notifyItemMoved(fromPosition, toPosition);
        Intent intent = new Intent();
        intent.setAction(FetchService.BROADCAST_ACTION);
        intent.putExtra("message", "reorder");
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @Override
    public void onItemDismiss(int position) {}

    class EmojiViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView textView;
        MaterialButton button;
        MaterialButton toggle;

        EmojiViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
            textView = itemView.findViewById(R.id.textView);
            button = itemView.findViewById(R.id.btnDelete);
            toggle = itemView.findViewById(R.id.toggle_visibility);
        }
    }
}

