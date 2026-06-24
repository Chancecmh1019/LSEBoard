package com.lseboard.app.View;

import android.content.Context;

import com.lseboard.app.Adapter.StickerAdapter;
import com.lseboard.app.Model.StickerPack;

public class StickerPackView extends BasePackView {
    public StickerAdapter adapter;

    public StickerPackView(Context context) {
        super(context);
    }

    public StickerPackView(Context context, StickerPack stickerPack) {
        super(context);
        adapter = new StickerAdapter(context, stickerPack);
        setUpRecyclerViewForSticker(adapter);
    }

}

