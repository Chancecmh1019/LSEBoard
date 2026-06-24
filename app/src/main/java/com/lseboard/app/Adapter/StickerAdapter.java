package com.lseboard.app.Adapter;

import android.content.Context;

import java.io.File;

import com.lseboard.app.Model.Sticker;
import com.lseboard.app.Model.StickerPack;
import com.lseboard.app.Util.FileHelper;

public class StickerAdapter extends BasePackAdapter {
    private StickerPack stickerPack;

    public StickerAdapter(Context context, StickerPack stickerPack) {
        super(context);
        this.stickerPack = stickerPack;
    }

    @Override
    public int getItemCount() {
        if (stickerPack != null)
            return stickerPack.getCount();
        else return 0;
    }

    @Override
    protected File getFile(Context context, int position) {
        return FileHelper.getFile(context, new Sticker(stickerPack.getType(), stickerPack.getId(position)));
    }

    @Override
    protected Sticker getSticker(int position) {
        return stickerPack.getSticker(position);
    }

    @Override
    protected boolean saveHistory() {
        return true;
    }

    public void update(StickerPack stickerPack) {
        this.stickerPack = stickerPack;
        notifyDataSetChanged();
    }
}

