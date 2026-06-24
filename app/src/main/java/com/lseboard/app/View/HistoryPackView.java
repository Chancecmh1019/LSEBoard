package com.lseboard.app.View;

import android.content.Context;

import com.lseboard.app.Adapter.HistoryAdapter;
import com.lseboard.app.Model.HistoryPack;

public class HistoryPackView extends BasePackView {
    public HistoryAdapter adapter;

    public HistoryPackView(Context context) {
        super(context);
    }

    public HistoryPackView(Context context, HistoryPack historyPack) {
        super(context);
        adapter = new HistoryAdapter(context, historyPack);
        setUpRecyclerViewForHistory(adapter);
    }

}

