package com.lseboard.app.Util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.lseboard.app.Model.Emoji;
import com.lseboard.app.Model.EmojiPack;
import com.lseboard.app.Model.Sticker;
import com.lseboard.app.Model.StickerPack;

public class FileHelper {
    public static File getPngFile(Context context, int id) {
        File pngDir = new File(context.getFilesDir(), "png");
        return new File(pngDir, id + ".png");
    }

    public static File getGifFile(Context context, int id) {
        File gifDir = new File(context.getFilesDir(), "gif");
        return new File(gifDir, id + ".gif");
    }

    public static File getFile(Context context, Sticker sticker) {
        if (sticker.getType() == Sticker.Type.STATIC) {
            File pngDir = new File(context.getFilesDir(), "png");
            return new File(pngDir, sticker.getId() + ".png");
        } else {
            File gifDir = new File(context.getFilesDir(), "gif");
            return new File(gifDir, sticker.getId() + ".gif");
        }
    }

    /**
     * 取得加上白底的 PNG 檔案（用於傳送）
     * 如果原圖有透明背景，會加上白底
     * 如果原圖已經有不透明背景，則直接返回原檔案
     */
    public static File getPngFileWithBackground(Context context, int id) {
        File originalFile = getPngFile(context, id);
        if (!originalFile.exists()) {
            return originalFile;
        }

        Bitmap original = null;
        Bitmap withBackground = null;
        FileOutputStream fos = null;
        
        try {
            // 讀取原圖，使用較小的選項避免 OOM
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            original = BitmapFactory.decodeFile(originalFile.getAbsolutePath(), options);
            
            if (original == null) {
                Log.e("FileHelper", "Failed to decode bitmap");
                return originalFile;
            }

            // 檢查是否有透明像素
            if (!hasTransparency(original)) {
                return originalFile;
            }

            // 建立白底圖片
            withBackground = Bitmap.createBitmap(
                    original.getWidth(),
                    original.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            Canvas canvas = new Canvas(withBackground);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(original, 0, 0, null);

            // 儲存到暫存目錄
            File cacheDir = new File(context.getCacheDir(), "sticker_send");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File outputFile = new File(cacheDir, id + "_bg.png");
            
            fos = new FileOutputStream(outputFile);
            withBackground.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();

            Log.d("FileHelper", "Added white background to sticker: " + id);
            return outputFile;
            
        } catch (Exception e) {
            Log.e("FileHelper", "Failed to add background: " + e.getMessage(), e);
            return originalFile;
        } finally {
            // 確保資源被釋放
            if (original != null && !original.isRecycled()) {
                original.recycle();
            }
            if (withBackground != null && !withBackground.isRecycled()) {
                withBackground.recycle();
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 檢查圖片是否有透明像素（抽樣檢查以提升效能）
     */
    private static boolean hasTransparency(Bitmap bitmap) {
        try {
            if (bitmap == null || bitmap.isRecycled()) {
                return false;
            }
            
            if (!bitmap.hasAlpha()) {
                return false;
            }

            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            
            if (width <= 0 || height <= 0) {
                return false;
            }
            
            // 抽樣檢查：檢查邊緣和一些內部點
            // 檢查四個角落
            if (isTransparent(bitmap, 0, 0)) return true;
            if (isTransparent(bitmap, width - 1, 0)) return true;
            if (isTransparent(bitmap, 0, height - 1)) return true;
            if (isTransparent(bitmap, width - 1, height - 1)) return true;

            // 檢查邊緣（每隔幾個像素抽樣）
            int step = Math.max(1, Math.min(width, height) / 20);
            for (int x = 0; x < width; x += step) {
                if (isTransparent(bitmap, x, 0)) return true;
                if (isTransparent(bitmap, x, height - 1)) return true;
            }
            for (int y = 0; y < height; y += step) {
                if (isTransparent(bitmap, 0, y)) return true;
                if (isTransparent(bitmap, width - 1, y)) return true;
            }

            return false;
        } catch (Exception e) {
            Log.e("FileHelper", "Error checking transparency: " + e.getMessage());
            return false;
        }
    }

    private static boolean isTransparent(Bitmap bitmap, int x, int y) {
        try {
            if (x < 0 || x >= bitmap.getWidth() || y < 0 || y >= bitmap.getHeight()) {
                return false;
            }
            int pixel = bitmap.getPixel(x, y);
            return Color.alpha(pixel) < 250; // 允許一點點誤差
        } catch (Exception e) {
            return false;
        }
    }

    public static void deleteFile(Context context, StickerPack stickerPack) {
        File pngDir = new File(context.getFilesDir(), "png");
        for (int id : stickerPack.getIds()) {
            File file = new File(pngDir, id + ".png");
            if (!file.delete()) {
                Log.d("FileHelper", id + ".png delete failed");
            }
            if (stickerPack.getType() != Sticker.Type.STATIC) {
                File gifDir = new File(context.getFilesDir(), "gif");
                file = new File(gifDir, id + ".gif");
                if (!file.delete()) {
                    Log.d("FileHelper", id + ".gif delete failed");
                }
            }
        }
    }

    // ==================== Emoji 相關方法 ====================

    /**
     * 取得 Emoji PNG 檔案
     */
    public static File getEmojiPngFile(Context context, String productId, int id) {
        File emojiDir = new File(context.getFilesDir(), "emoji/" + productId);
        return new File(emojiDir, id + ".png");
    }

    /**
     * 取得 Emoji GIF 檔案
     */
    public static File getEmojiGifFile(Context context, String productId, int id) {
        File gifDir = new File(context.getFilesDir(), "emoji_gif/" + productId);
        return new File(gifDir, id + ".gif");
    }

    /**
     * 取得 Emoji 檔案（根據類型）
     */
    public static File getEmojiFile(Context context, Emoji emoji) {
        if (emoji.getType() == Emoji.Type.STATIC) {
            return getEmojiPngFile(context, emoji.getProductId(), emoji.getId());
        } else {
            File gifDir = new File(context.getFilesDir(), "emoji_gif/" + emoji.getProductId());
            return new File(gifDir, emoji.getId() + ".gif");
        }
    }

    /**
     * 取得加上白底的 Emoji PNG 檔案
     */
    public static File getEmojiPngFileWithBackground(Context context, String productId, int id) {
        File originalFile = getEmojiPngFile(context, productId, id);
        if (!originalFile.exists()) {
            return originalFile;
        }

        Bitmap original = null;
        Bitmap withBackground = null;
        FileOutputStream fos = null;
        
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            original = BitmapFactory.decodeFile(originalFile.getAbsolutePath(), options);
            
            if (original == null) {
                Log.e("FileHelper", "Failed to decode emoji bitmap");
                return originalFile;
            }

            if (!hasTransparency(original)) {
                return originalFile;
            }

            withBackground = Bitmap.createBitmap(
                    original.getWidth(),
                    original.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            Canvas canvas = new Canvas(withBackground);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(original, 0, 0, null);

            File cacheDir = new File(context.getCacheDir(), "emoji_send");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File outputFile = new File(cacheDir, productId + "_" + id + "_bg.png");
            
            fos = new FileOutputStream(outputFile);
            withBackground.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();

            Log.d("FileHelper", "Added white background to emoji: " + productId + "/" + id);
            return outputFile;
            
        } catch (Exception e) {
            Log.e("FileHelper", "Failed to add background to emoji: " + e.getMessage(), e);
            return originalFile;
        } finally {
            if (original != null && !original.isRecycled()) {
                original.recycle();
            }
            if (withBackground != null && !withBackground.isRecycled()) {
                withBackground.recycle();
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 刪除 Emoji 包檔案
     */
    public static void deleteEmojiFile(Context context, EmojiPack emojiPack) {
        File emojiDir = new File(context.getFilesDir(), "emoji/" + emojiPack.getProductId());
        for (int id : emojiPack.getIds()) {
            File file = new File(emojiDir, id + ".png");
            if (!file.delete()) {
                Log.d("FileHelper", "emoji " + id + ".png delete failed");
            }
            if (emojiPack.getType() != Emoji.Type.STATIC) {
                File gifDir = new File(context.getFilesDir(), "emoji_gif/" + emojiPack.getProductId());
                file = new File(gifDir, id + ".gif");
                if (!file.delete()) {
                    Log.d("FileHelper", "emoji " + id + ".gif delete failed");
                }
            }
        }
        // 嘗試刪除空目錄
        if (emojiDir.exists() && emojiDir.isDirectory()) {
            emojiDir.delete();
        }
    }
}

