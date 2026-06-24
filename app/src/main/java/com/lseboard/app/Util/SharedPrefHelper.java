package com.lseboard.app.Util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;

import com.lseboard.app.Model.Emoji;
import com.lseboard.app.Model.EmojiPack;
import com.lseboard.app.Model.HistoryPack;
import com.lseboard.app.Model.Sticker;
import com.lseboard.app.Model.StickerPack;

public class SharedPrefHelper {
    private static final String SHARED_PREF = "linestickerkeyboard.pref";
    private static final String KEY_HISTORY = "linestickerkeyboard.pref.history";
    private static final String KEY_STICKERS = "linestickerkeyboard.pref.stickers";
    private static final String KEY_EMOJIS = "linestickerkeyboard.pref.emojis";
    private static final String KEY_DISCLAIMER = "linestickerkeyboard.pref.disclaimer";
    private static final String KEY_HIDE_LAUNCHER_ICON = "linestickerkeyboard.pref.hide_launcher_icon";
    private static final String KEY_DISABLE_HISTORY = "linestickerkeyboard.pref.disable_history";
    private static final String KEY_LAST_STICKER_PACK = "linestickerkeyboard.pref.last_sticker_pack";
    private static final String KEY_LAST_EMOJI_PACK = "linestickerkeyboard.pref.last_emoji_pack";
    private static final String KEY_LAST_SELECTED_TAB = "linestickerkeyboard.pref.last_selected_tab";

    public static ArrayList<StickerPack> getStickerPacksFromPref(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String json = sharedPreferences.getString(KEY_STICKERS, "");
        ArrayList<StickerPack> stickerPacks;
        if (json.equals("")) {
            stickerPacks = new ArrayList<>();
        } else {
            try {
                stickerPacks = gson.fromJson(sharedPreferences.getString(KEY_STICKERS, null),
                        new TypeToken<ArrayList<StickerPack>>() {
                        }.getType());
            } catch (JsonSyntaxException e) {
                stickerPacks = new ArrayList<>();
            }
        }
        return stickerPacks;
    }

    public static HistoryPack getHistoryFromPref(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String json = sharedPreferences.getString(KEY_HISTORY, "");
        HistoryPack historyPack;
        if (json.equals("")) {
            historyPack = new HistoryPack(new ArrayList<Sticker>());
        } else {
            try {
                historyPack = gson.fromJson(sharedPreferences.getString(KEY_HISTORY, null),
                        HistoryPack.class);
                if (historyPack == null) historyPack = new HistoryPack(new ArrayList<Sticker>());
            } catch (JsonSyntaxException e) {
                historyPack = new HistoryPack(new ArrayList<Sticker>());
            }
        }
        return historyPack;
    }

    public static void addNewStickerPack(Context context, StickerPack stickerPack) {
        ArrayList<StickerPack> stickerPacks;
        stickerPacks = getStickerPacksFromPref(context);
        boolean isContain = false;
        for (StickerPack s : stickerPacks) {
            if (s.getStoreId() == stickerPack.getStoreId()) {
                isContain = true;
                break;
            }
        }
        if (!isContain) {
            stickerPacks.add(stickerPack);
            saveNewStickerPacks(context, stickerPacks);
        }
    }


    public static void cleanHistory(Context context, StickerPack stickerPackToDelete) {
        HistoryPack historyPack = getHistoryFromPref(context);
        historyPack.removeAll(stickerPackToDelete.getIds());
        saveNewHistoryPack(context, historyPack);
    }

    public static void addStickerToHistory(Context context, Sticker sticker) {
        HistoryPack historyPack = getHistoryFromPref(context);
        historyPack.add(sticker);
        saveNewHistoryPack(context, historyPack);
    }

    public static void saveNewStickerPacks(Context context, ArrayList<StickerPack> stickerPacks) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_STICKERS, gson.toJson(stickerPacks));
        editor.apply();
    }

    public static void saveNewHistoryPack(Context context, HistoryPack historyPack) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_HISTORY, gson.toJson(historyPack));
        editor.apply();
    }

    public static void saveDisclaimerStatus(Context context, boolean agree) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_DISCLAIMER, agree);
        editor.apply();
    }

    public static boolean getDisclaimerStatus(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(KEY_DISCLAIMER, false);
    }

    // ==================== Emoji 相關方法 ====================

    public static ArrayList<EmojiPack> getEmojiPacksFromPref(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String json = sharedPreferences.getString(KEY_EMOJIS, "");
        ArrayList<EmojiPack> emojiPacks;
        if (json.equals("")) {
            emojiPacks = new ArrayList<>();
        } else {
            try {
                emojiPacks = gson.fromJson(sharedPreferences.getString(KEY_EMOJIS, null),
                        new TypeToken<ArrayList<EmojiPack>>() {
                        }.getType());
            } catch (JsonSyntaxException e) {
                emojiPacks = new ArrayList<>();
            }
        }
        return emojiPacks;
    }

    public static void addNewEmojiPack(Context context, EmojiPack emojiPack) {
        ArrayList<EmojiPack> emojiPacks;
        emojiPacks = getEmojiPacksFromPref(context);
        boolean isContain = false;
        for (EmojiPack e : emojiPacks) {
            if (e.getProductId().equals(emojiPack.getProductId())) {
                isContain = true;
                break;
            }
        }
        if (!isContain) {
            emojiPacks.add(emojiPack);
            saveNewEmojiPacks(context, emojiPacks);
        }
    }

    public static void saveNewEmojiPacks(Context context, ArrayList<EmojiPack> emojiPacks) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_EMOJIS, gson.toJson(emojiPacks));
        editor.apply();
    }

    public static void cleanEmojiHistory(Context context, EmojiPack emojiPackToDelete) {
        // Emoji 歷史紀錄清理（如果需要的話）
        // 目前 Emoji 不加入歷史紀錄
    }

    // ==================== 桌面圖示設定 ====================

    public static void setHideLauncherIcon(Context context, boolean hide) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_HIDE_LAUNCHER_ICON, hide);
        editor.apply();
    }

    public static boolean getHideLauncherIcon(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(KEY_HIDE_LAUNCHER_ICON, false);
    }

    // ==================== 歷史紀錄設定 ====================

    public static void setDisableHistory(Context context, boolean disable) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_DISABLE_HISTORY, disable);
        editor.apply();
    }

    public static boolean getDisableHistory(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(KEY_DISABLE_HISTORY, false);
    }

    // ==================== 記憶最後選擇的貼圖包/表情貼包 ====================

    /**
     * 儲存最後選擇的貼圖包 ID
     */
    public static void setLastStickerPackId(Context context, int storeId) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_LAST_STICKER_PACK, storeId);
        editor.apply();
    }

    /**
     * 取得最後選擇的貼圖包 ID (-1 表示未設定)
     */
    public static int getLastStickerPackId(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        return sharedPreferences.getInt(KEY_LAST_STICKER_PACK, -1);
    }

    /**
     * 儲存最後選擇的表情貼包 ID
     */
    public static void setLastEmojiPackId(Context context, String productId) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_LAST_EMOJI_PACK, productId);
        editor.apply();
    }

    /**
     * 取得最後選擇的表情貼包 ID (null 表示未設定)
     */
    public static String getLastEmojiPackId(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        return sharedPreferences.getString(KEY_LAST_EMOJI_PACK, null);
    }

    /**
     * 儲存最後選擇的 Tab (0=歷史, 1=第一個貼圖包, ...)
     */
    public static void setLastSelectedTab(Context context, int tabIndex) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_LAST_SELECTED_TAB, tabIndex);
        editor.apply();
    }

    /**
     * 取得最後選擇的 Tab (-1 表示未設定，預設顯示第一個)
     */
    public static int getLastSelectedTab(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        return sharedPreferences.getInt(KEY_LAST_SELECTED_TAB, -1);
    }
}

