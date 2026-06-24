package com.lseboard.app;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import com.lseboard.app.Adapter.AdapterCallback;
import com.lseboard.app.Adapter.EmojiAdapterCallback;
import com.lseboard.app.Adapter.EmojiListAdapter;
import com.lseboard.app.Adapter.ListAdapter;
import com.lseboard.app.Model.EmojiPack;
import com.lseboard.app.Model.StickerPack;
import com.lseboard.app.Util.SharedPrefHelper;

public class PackListFragment extends Fragment {

    public static final int TYPE_STICKER = 0;
    public static final int TYPE_EMOJI = 1;

    private static final String ARG_TYPE = "type";

    private int type;
    private RecyclerView recyclerView;
    private View emptyView;

    // 貼圖相關
    private ListAdapter stickerAdapter;
    private ArrayList<StickerPack> stickerPacks;

    // Emoji 相關
    private EmojiListAdapter emojiAdapter;
    private ArrayList<EmojiPack> emojiPacks;

    public static PackListFragment newInstance(int type) {
        PackListFragment fragment = new PackListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            type = getArguments().getInt(ARG_TYPE, TYPE_STICKER);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pack_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerView);
        emptyView = view.findViewById(R.id.emptyView);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.addItemDecoration(new ItemOffsetDecoration(requireContext(),
                R.dimen.item_offset_x, R.dimen.item_offset_y));

        if (type == TYPE_STICKER) {
            setupStickerList();
        } else {
            setupEmojiList();
        }

        updateEmptyView();
    }

    private void setupStickerList() {
        stickerPacks = SharedPrefHelper.getStickerPacksFromPref(requireContext());
        stickerAdapter = new ListAdapter(requireActivity(), stickerPacks);

        ItemTouchHelper.Callback callback = new AdapterCallback(stickerAdapter);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        recyclerView.setAdapter(stickerAdapter);
    }

    private void setupEmojiList() {
        emojiPacks = SharedPrefHelper.getEmojiPacksFromPref(requireContext());
        emojiAdapter = new EmojiListAdapter(requireActivity(), emojiPacks);

        ItemTouchHelper.Callback callback = new EmojiAdapterCallback(emojiAdapter);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        recyclerView.setAdapter(emojiAdapter);
    }

    public void refreshData() {
        if (type == TYPE_STICKER && stickerAdapter != null) {
            stickerPacks = SharedPrefHelper.getStickerPacksFromPref(requireContext());
            stickerAdapter.setData(stickerPacks);
            stickerAdapter.notifyDataSetChanged();
        } else if (type == TYPE_EMOJI && emojiAdapter != null) {
            emojiPacks = SharedPrefHelper.getEmojiPacksFromPref(requireContext());
            emojiAdapter.setData(emojiPacks);
            emojiAdapter.notifyDataSetChanged();
        }
        updateEmptyView();
    }

    private void updateEmptyView() {
        if (emptyView == null || recyclerView == null) return;

        boolean isEmpty;
        if (type == TYPE_STICKER) {
            isEmpty = stickerPacks == null || stickerPacks.isEmpty();
        } else {
            isEmpty = emojiPacks == null || emojiPacks.isEmpty();
        }

        if (isEmpty) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    static class ItemOffsetDecoration extends RecyclerView.ItemDecoration {
        private int mItemOffsetX;
        private int mItemOffsetY;

        ItemOffsetDecoration(@NonNull Context context, @DimenRes int itemOffsetIdX, @DimenRes int itemOffsetIdY) {
            mItemOffsetX = context.getResources().getDimensionPixelSize(itemOffsetIdX);
            mItemOffsetY = context.getResources().getDimensionPixelSize(itemOffsetIdY);
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            outRect.set(mItemOffsetX, mItemOffsetY, mItemOffsetX, mItemOffsetY / 2);
        }
    }
}

