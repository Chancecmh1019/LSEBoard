package com.lseboard.app.Adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

import com.lseboard.app.Model.StickerPack;
import com.lseboard.app.PackDetailActivity;
import com.lseboard.app.R;
import com.lseboard.app.Util.FileHelper;
import com.lseboard.app.Util.SharedPrefHelper;

import static com.lseboard.app.FetchService.BROADCAST_ACTION;

public class ListAdapter extends RecyclerView.Adapter<ListAdapter.ListViewHolder> implements ItemTouchHelperAdapter {
    private Context context;
    private ArrayList<StickerPack> stickerPacks;

    public ListAdapter(Context context, ArrayList<StickerPack> stickerPacks) {
        this.context = context;
        this.stickerPacks = stickerPacks;
    }

    @NonNull
    @Override
    public ListViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View itemView = LayoutInflater.from(context).inflate(R.layout.item_edit, viewGroup, false);
        return new ListViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ListViewHolder stickerViewHolder, int i) {
        StickerPack pack = stickerPacks.get(i);
        stickerViewHolder.itemView.setTag(pack);
        stickerViewHolder.textView.setText(pack.getTitle());
        
        // 根據類型選擇檔案（動態用 GIF，靜態用 PNG）
        File file;
        if (pack.getType() == com.lseboard.app.Model.Sticker.Type.STATIC) {
            file = FileHelper.getPngFile(context, pack.getId(0));
        } else {
            File gifFile = FileHelper.getGifFile(context, pack.getId(0));
            file = gifFile.exists() ? gifFile : FileHelper.getPngFile(context, pack.getId(0));
        }
        
        RequestOptions requestOptions = new RequestOptions().signature(new ObjectKey(file.lastModified()));
        
        if (file.getName().endsWith(".gif")) {
            Glide.with(context).asGif().load(file).apply(requestOptions).into(stickerViewHolder.imageView);
        } else {
            Glide.with(context).load(file).apply(requestOptions).into(stickerViewHolder.imageView);
        }
        
        stickerViewHolder.button.setOnClickListener(v -> {
            int pos = stickerPacks.indexOf((StickerPack) stickerViewHolder.itemView.getTag());
            delete(pos);
        });
        stickerViewHolder.itemView.setOnClickListener(v -> {
            int pos = stickerPacks.indexOf((StickerPack) stickerViewHolder.itemView.getTag());
            // 打開貼圖包詳情頁面
            Intent detailIntent = new Intent(context, PackDetailActivity.class);
            detailIntent.putExtra(PackDetailActivity.EXTRA_PACK_TYPE, PackDetailActivity.TYPE_STICKER);
            detailIntent.putExtra(PackDetailActivity.EXTRA_PACK_INDEX, pos);
            context.startActivity(detailIntent);
        });
        stickerViewHolder.toggle.setChecked(pack.getVisible());
        stickerViewHolder.toggle.setOnClickListener(v -> {
            int pos = stickerPacks.indexOf((StickerPack) stickerViewHolder.itemView.getTag());
            boolean isChecked = stickerViewHolder.toggle.isChecked();
            stickerPacks.get(pos).setVisible(isChecked);
            SharedPrefHelper.saveNewStickerPacks(context, stickerPacks);
            Intent intent = new Intent();
            intent.setAction(BROADCAST_ACTION);
            intent.putExtra("message", "refresh");
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        });
    }

    private void delete(final int index) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.delete)
                .setMessage(context.getString(R.string.delete_sticker))
                .setPositiveButton(context.getString(R.string.positive_confirm), (dialog, i) -> {
                    SharedPrefHelper.cleanHistory(context, stickerPacks.get(index));
                    FileHelper.deleteFile(context, stickerPacks.get(index));
                    stickerPacks.remove(index);
                    SharedPrefHelper.saveNewStickerPacks(context, stickerPacks);
                    notifyItemRemoved(index);
                    Intent intent = new Intent();
                    intent.setAction(BROADCAST_ACTION);
                    intent.putExtra("message", "delete");
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                })
                .setNegativeButton(context.getString(R.string.negative_cancel), null)
                .show();
    }

    public void setData(ArrayList<StickerPack> stickerPacks) {
        this.stickerPacks = stickerPacks;
    }

    @Override
    public int getItemCount() {
        return stickerPacks.size();
    }

    @Override
    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(stickerPacks, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(stickerPacks, i, i - 1);
            }
        }
        SharedPrefHelper.saveNewStickerPacks(context, stickerPacks);
        notifyItemMoved(fromPosition, toPosition);
        Intent intent = new Intent();
        intent.setAction(BROADCAST_ACTION);
        intent.putExtra("message", "reorder");
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @Override
    public void onItemDismiss(int position) {}

    class ListViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView textView;
        MaterialButton button;
        MaterialButton toggle;

        ListViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
            textView = itemView.findViewById(R.id.textView);
            button = itemView.findViewById(R.id.btnDelete);
            toggle = itemView.findViewById(R.id.toggle_visibility);
        }
    }
}

