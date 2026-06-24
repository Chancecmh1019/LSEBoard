package com.lseboard.app.Util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.ImageDecoder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

/**
 * APNG to GIF converter with proper animation support
 * 每一幀都是完整的獨立圖像，避免殘影問題
 */
public class Apng2GifCustom {

    private static final String TAG = "Apng2GifCustom";
    
    // PNG signature
    private static final byte[] PNG_SIGNATURE = {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    
    // Chunk types
    private static final int CHUNK_IHDR = 0x49484452;
    private static final int CHUNK_acTL = 0x6163544C;
    private static final int CHUNK_fcTL = 0x6663544C;
    private static final int CHUNK_fdAT = 0x66644154;
    private static final int CHUNK_IDAT = 0x49444154;
    private static final int CHUNK_IEND = 0x49454E44;
    
    private int width, height;
    private int bitDepth, colorType;
    private int numFrames = 1;
    private int numPlays = 0;
    
    public void start(File apng, File gif) {
        try {
            // First try using Android's ImageDecoder for API 28+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (convertWithImageDecoder(apng, gif)) {
                    Log.d(TAG, "GIF created with ImageDecoder: " + gif.getAbsolutePath());
                    return;
                }
            }
            
            // Fallback: Try to parse APNG manually
            if (convertApngManually(apng, gif)) {
                Log.d(TAG, "GIF created with manual APNG parser: " + gif.getAbsolutePath());
                return;
            }
            
            // Last resort: Create single-frame GIF
            createSingleFrameGif(apng, gif);
            Log.d(TAG, "Single-frame GIF created: " + gif.getAbsolutePath());
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting to GIF: " + e.getMessage(), e);
            try {
                createSingleFrameGif(apng, gif);
            } catch (Exception e2) {
                Log.e(TAG, "Failed to create even single-frame GIF", e2);
            }
        }
    }
    
    private boolean convertWithImageDecoder(File apng, File gif) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false;
        }
        
        try {
            ImageDecoder.Source source = ImageDecoder.createSource(apng);
            
            // 使用 OnHeaderDecodedListener 來設置解碼選項
            final int[] dimensions = new int[2];
            Drawable drawable = ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
                dimensions[0] = info.getSize().getWidth();
                dimensions[1] = info.getSize().getHeight();
                // 不要縮放
                decoder.setTargetSampleSize(1);
            });
            
            if (drawable instanceof AnimatedImageDrawable) {
                AnimatedImageDrawable animDrawable = (AnimatedImageDrawable) drawable;
                
                int w = dimensions[0] > 0 ? dimensions[0] : drawable.getIntrinsicWidth();
                int h = dimensions[1] > 0 ? dimensions[1] : drawable.getIntrinsicHeight();
                
                Log.d(TAG, "AnimatedImageDrawable detected, size: " + w + "x" + h + ", file: " + apng.getName());
                
                // 使用 Choreographer 來同步幀捕捉
                final List<Bitmap> capturedFrames = new ArrayList<>();
                final List<Integer> capturedDelays = new ArrayList<>();
                final CountDownLatch latch = new CountDownLatch(1);
                
                // 創建專用的 HandlerThread
                HandlerThread handlerThread = new HandlerThread("AnimCapture", android.os.Process.THREAD_PRIORITY_DISPLAY);
                handlerThread.start();
                Handler handler = new Handler(handlerThread.getLooper());
                
                final int frameInterval = 50; // 50ms 間隔
                final int maxFrames = 60;
                final int maxSameFrames = 10;
                
                handler.post(() -> {
                    try {
                        Bitmap renderBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        Canvas renderCanvas = new Canvas(renderBitmap);
                        
                        animDrawable.setBounds(0, 0, w, h);
                        animDrawable.setRepeatCount(0); // 無限循環
                        animDrawable.start();
                        
                        // 給動畫一點時間初始化
                        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                        
                        long[] lastHash = {-1};
                        int sameCount = 0;
                        
                        for (int i = 0; i < maxFrames && sameCount < maxSameFrames; i++) {
                            // 清除並繪製
                            renderCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                            
                            // 強制動畫更新
                            animDrawable.draw(renderCanvas);
                            
                            // 計算哈希
                            long hash = computeBitmapHash(renderBitmap);
                            
                            if (hash == lastHash[0] && capturedFrames.size() > 0) {
                                sameCount++;
                                // 延長最後一幀
                                if (capturedDelays.size() > 0) {
                                    int idx = capturedDelays.size() - 1;
                                    capturedDelays.set(idx, capturedDelays.get(idx) + frameInterval);
                                }
                            } else {
                                sameCount = 0;
                                lastHash[0] = hash;
                                
                                // 創建帶白底的幀副本
                                Bitmap frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                                Canvas frameCanvas = new Canvas(frame);
                                frameCanvas.drawColor(Color.WHITE);
                                frameCanvas.drawBitmap(renderBitmap, 0, 0, null);
                                
                                capturedFrames.add(frame);
                                capturedDelays.add(frameInterval);
                            }
                            
                            // 等待下一幀
                            try { Thread.sleep(frameInterval); } catch (InterruptedException ignored) {}
                        }
                        
                        animDrawable.stop();
                        renderBitmap.recycle();
                        
                        Log.d(TAG, "Captured " + capturedFrames.size() + " frames for " + apng.getName());
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Frame capture error: " + e.getMessage(), e);
                    } finally {
                        latch.countDown();
                    }
                });
                
                // 等待完成
                try {
                    latch.await(6, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}
                
                handlerThread.quitSafely();
                
                if (capturedFrames.size() > 1) {
                    encodeGif(capturedFrames, capturedDelays, gif);
                    for (Bitmap frame : capturedFrames) {
                        frame.recycle();
                    }
                    Log.d(TAG, "GIF created with " + capturedFrames.size() + " frames: " + gif.getName());
                    return true;
                } else {
                    for (Bitmap frame : capturedFrames) {
                        frame.recycle();
                    }
                    Log.d(TAG, "Only " + capturedFrames.size() + " frame(s), trying manual parsing");
                    return false;
                }
            } else {
                Log.d(TAG, "Not an AnimatedImageDrawable for " + apng.getName());
            }
        } catch (Exception e) {
            Log.e(TAG, "ImageDecoder failed for " + apng.getName() + ": " + e.getMessage(), e);
        }
        return false;
    }
    
    /**
     * 計算 Bitmap 的簡單哈希值用於比較
     */
    private long computeBitmapHash(Bitmap bitmap) {
        long hash = 0;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        
        // 採樣 16 個點計算哈希
        int stepX = Math.max(1, w / 4);
        int stepY = Math.max(1, h / 4);
        
        for (int x = stepX / 2; x < w; x += stepX) {
            for (int y = stepY / 2; y < h; y += stepY) {
                int pixel = bitmap.getPixel(x, y);
                hash = hash * 31 + pixel;
            }
        }
        
        return hash;
    }
    
    private boolean convertApngManually(File apng, File gif) {
        try {
            byte[] data = readFile(apng);
            if (!isValidPng(data)) {
                Log.d(TAG, "Not a valid PNG file: " + apng.getName());
                return false;
            }
            
            // 先檢查是否是 APNG（有 acTL chunk）
            if (!hasApngChunk(data)) {
                Log.d(TAG, "No acTL chunk found, not an APNG: " + apng.getName());
                return false;
            }
            
            List<ApngFrame> apngFrames = parseApng(data);
            Log.d(TAG, "Parsed " + (apngFrames != null ? apngFrames.size() : 0) + " frames from " + apng.getName());
            
            if (apngFrames == null || apngFrames.size() <= 1) {
                Log.d(TAG, "Not enough frames for animation: " + apng.getName());
                return false;
            }
            
            List<Bitmap> frames = new ArrayList<>();
            List<Integer> delays = new ArrayList<>();
            
            // 創建合成畫布
            Bitmap compositeCanvas = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(compositeCanvas);
            c.drawColor(Color.WHITE);
            
            // 用於保存前一幀狀態（用於 APNG_DISPOSE_OP_PREVIOUS）
            Bitmap previousCanvas = null;
            
            int frameIndex = 0;
            for (ApngFrame apngFrame : apngFrames) {
                try {
                    // 保存當前狀態（用於 APNG_DISPOSE_OP_PREVIOUS）
                    if (apngFrame.disposeOp == 2) {
                        previousCanvas = Bitmap.createBitmap(compositeCanvas);
                    }
                    
                    Bitmap frameBitmap = decodeFrame(apngFrame, data);
                    if (frameBitmap != null) {
                        Log.d(TAG, "Frame " + frameIndex + ": " + apngFrame.width + "x" + apngFrame.height + 
                              " at (" + apngFrame.xOffset + "," + apngFrame.yOffset + "), delay=" + apngFrame.delayMs + 
                              ", dispose=" + apngFrame.disposeOp + ", blend=" + apngFrame.blendOp);
                        
                        // 根據 blend 操作處理
                        if (apngFrame.blendOp == 0) { // APNG_BLEND_OP_SOURCE
                            // 清除目標區域
                            Paint clearPaint = new Paint();
                            clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                            c.drawRect(apngFrame.xOffset, apngFrame.yOffset,
                                       apngFrame.xOffset + apngFrame.width,
                                       apngFrame.yOffset + apngFrame.height, clearPaint);
                            clearPaint.setXfermode(null);
                            
                            // 先填充白色背景
                            Paint whitePaint = new Paint();
                            whitePaint.setColor(Color.WHITE);
                            c.drawRect(apngFrame.xOffset, apngFrame.yOffset,
                                       apngFrame.xOffset + apngFrame.width,
                                       apngFrame.yOffset + apngFrame.height, whitePaint);
                        }
                        
                        // 繪製幀
                        c.drawBitmap(frameBitmap, apngFrame.xOffset, apngFrame.yOffset, null);
                        
                        // 創建完整的輸出幀（獨立圖像）
                        Bitmap outputFrame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        Canvas outputCanvas = new Canvas(outputFrame);
                        outputCanvas.drawColor(Color.WHITE);
                        outputCanvas.drawBitmap(compositeCanvas, 0, 0, null);
                        
                        frames.add(outputFrame);
                        delays.add(Math.max(apngFrame.delayMs, 30)); // 最小 30ms
                        
                        // 處理 dispose 操作
                        if (apngFrame.disposeOp == 1) { // APNG_DISPOSE_OP_BACKGROUND
                            Paint clearPaint = new Paint();
                            clearPaint.setColor(Color.WHITE);
                            c.drawRect(apngFrame.xOffset, apngFrame.yOffset,
                                       apngFrame.xOffset + apngFrame.width,
                                       apngFrame.yOffset + apngFrame.height, clearPaint);
                        } else if (apngFrame.disposeOp == 2 && previousCanvas != null) { // APNG_DISPOSE_OP_PREVIOUS
                            c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                            c.drawBitmap(previousCanvas, 0, 0, null);
                            previousCanvas.recycle();
                            previousCanvas = null;
                        }
                        
                        frameBitmap.recycle();
                    } else {
                        Log.e(TAG, "Failed to decode frame " + frameIndex);
                    }
                    frameIndex++;
                } catch (Exception e) {
                    Log.e(TAG, "Error decoding frame " + frameIndex + ": " + e.getMessage());
                }
            }
            
            compositeCanvas.recycle();
            if (previousCanvas != null) {
                previousCanvas.recycle();
            }
            
            Log.d(TAG, "Successfully decoded " + frames.size() + " frames for " + apng.getName());
            
            if (frames.size() > 1) {
                encodeGif(frames, delays, gif);
                for (Bitmap frame : frames) {
                    frame.recycle();
                }
                Log.d(TAG, "GIF created with manual parser: " + gif.getName() + ", frames: " + frames.size());
                return true;
            }
            
            for (Bitmap frame : frames) {
                frame.recycle();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Manual APNG parsing failed for " + apng.getName() + ": " + e.getMessage(), e);
        }
        return false;
    }
    
    /**
     * 檢查 PNG 檔案是否包含 acTL chunk（APNG 標記）
     */
    private boolean hasApngChunk(byte[] data) {
        int pos = 8;
        try {
            while (pos < data.length - 12) {
                int length = readInt(data, pos);
                int type = readInt(data, pos + 4);
                
                if (type == CHUNK_acTL) {
                    return true;
                }
                if (type == CHUNK_IEND) {
                    break;
                }
                
                pos += 12 + length;
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }
    
    private void createSingleFrameGif(File apng, File gif) throws IOException {
        Bitmap bitmap = BitmapFactory.decodeFile(apng.getAbsolutePath());
        if (bitmap == null) {
            throw new IOException("Failed to decode PNG file");
        }
        
        Bitmap outputBitmap = Bitmap.createBitmap(
                bitmap.getWidth(),
                bitmap.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(outputBitmap);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(bitmap, 0, 0, null);
        
        List<Bitmap> frames = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        frames.add(outputBitmap);
        delays.add(100);
        
        encodeGif(frames, delays, gif);
        
        bitmap.recycle();
        outputBitmap.recycle();
    }
    
    private void encodeGif(List<Bitmap> frames, List<Integer> delays, File gif) throws IOException {
        FileOutputStream fos = new FileOutputStream(gif);
        AnimatedGifEncoder encoder = new AnimatedGifEncoder();
        encoder.setRepeat(0); // Loop forever
        encoder.setQuality(10); // 較高品質
        encoder.setDispose(2); // 每幀後恢復到背景
        encoder.start(fos);
        
        for (int i = 0; i < frames.size(); i++) {
            int frameDelay = delays.get(i);
            if (frameDelay < 20) frameDelay = 50;
            encoder.setDelay(frameDelay);
            encoder.addFrame(frames.get(i));
        }
        
        encoder.finish();
        fos.close();
    }
    
    private byte[] readFile(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = fis.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        fis.close();
        return baos.toByteArray();
    }
    
    private boolean isValidPng(byte[] data) {
        if (data.length < 8) return false;
        for (int i = 0; i < 8; i++) {
            if (data[i] != PNG_SIGNATURE[i]) return false;
        }
        return true;
    }
    
    private List<ApngFrame> parseApng(byte[] data) {
        List<ApngFrame> frames = new ArrayList<>();
        ApngFrame currentFrame = null;
        ByteArrayOutputStream currentFrameData = null;
        
        int pos = 8;
        
        try {
            while (pos < data.length - 12) {
                int length = readInt(data, pos);
                int type = readInt(data, pos + 4);
                
                if (type == CHUNK_IHDR) {
                    width = readInt(data, pos + 8);
                    height = readInt(data, pos + 12);
                    bitDepth = data[pos + 16] & 0xFF;
                    colorType = data[pos + 17] & 0xFF;
                }
                else if (type == CHUNK_acTL) {
                    numFrames = readInt(data, pos + 8);
                    numPlays = readInt(data, pos + 12);
                }
                else if (type == CHUNK_fcTL) {
                    if (currentFrame != null && currentFrameData != null) {
                        currentFrame.imageData = currentFrameData.toByteArray();
                        frames.add(currentFrame);
                    }
                    
                    currentFrame = new ApngFrame();
                    currentFrameData = new ByteArrayOutputStream();
                    
                    currentFrame.width = readInt(data, pos + 12);
                    currentFrame.height = readInt(data, pos + 16);
                    currentFrame.xOffset = readInt(data, pos + 20);
                    currentFrame.yOffset = readInt(data, pos + 24);
                    int delayNum = readShort(data, pos + 28);
                    int delayDen = readShort(data, pos + 30);
                    if (delayDen == 0) delayDen = 100;
                    currentFrame.delayMs = (delayNum * 1000) / delayDen;
                    if (currentFrame.delayMs < 20) currentFrame.delayMs = 50;
                    currentFrame.disposeOp = data[pos + 32] & 0xFF;
                    currentFrame.blendOp = data[pos + 33] & 0xFF;
                }
                else if (type == CHUNK_IDAT) {
                    if (currentFrameData != null) {
                        currentFrameData.write(data, pos + 8, length);
                    }
                }
                else if (type == CHUNK_fdAT) {
                    if (currentFrameData != null) {
                        currentFrameData.write(data, pos + 12, length - 4);
                    }
                }
                else if (type == CHUNK_IEND) {
                    if (currentFrame != null && currentFrameData != null) {
                        currentFrame.imageData = currentFrameData.toByteArray();
                        frames.add(currentFrame);
                    }
                    break;
                }
                
                pos += 12 + length;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing APNG: " + e.getMessage());
        }
        
        return frames;
    }
    
    private Bitmap decodeFrame(ApngFrame frame, byte[] originalData) {
        try {
            ByteArrayOutputStream pngData = new ByteArrayOutputStream();
            
            // 寫入 PNG 簽名
            pngData.write(PNG_SIGNATURE);
            
            // 寫入 IHDR（使用幀的尺寸）
            writeChunk(pngData, "IHDR", createIHDR(frame.width, frame.height));
            
            // 複製原始 PNG 中的輔助 chunks（PLTE, tRNS, gAMA, cHRM, sRGB, iCCP 等）
            copyAuxiliaryChunks(originalData, pngData);
            
            // 寫入 IDAT
            writeChunk(pngData, "IDAT", frame.imageData);
            
            // 寫入 IEND
            writeChunk(pngData, "IEND", new byte[0]);
            
            byte[] png = pngData.toByteArray();
            
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            
            Bitmap result = BitmapFactory.decodeByteArray(png, 0, png.length, options);
            
            if (result == null) {
                Log.e(TAG, "Failed to decode frame PNG, data size: " + png.length);
            }
            
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Error decoding frame: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 從原始 PNG 複製輔助 chunks 到新的 PNG
     */
    private void copyAuxiliaryChunks(byte[] originalData, ByteArrayOutputStream out) throws IOException {
        int pos = 8; // 跳過 PNG 簽名
        
        while (pos < originalData.length - 12) {
            int length = readInt(originalData, pos);
            int type = readInt(originalData, pos + 4);
            
            // 複製這些輔助 chunks
            if (type == 0x504C5445 || // PLTE
                type == 0x74524E53 || // tRNS
                type == 0x67414D41 || // gAMA
                type == 0x6348524D || // cHRM
                type == 0x73524742 || // sRGB
                type == 0x69434350) { // iCCP
                
                // 直接複製整個 chunk（length + type + data + crc）
                out.write(originalData, pos, 12 + length);
            }
            
            if (type == CHUNK_IEND) {
                break;
            }
            
            pos += 12 + length;
        }
    }
    
    private byte[] createIHDR(int w, int h) {
        ByteBuffer buffer = ByteBuffer.allocate(13);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(w);
        buffer.putInt(h);
        buffer.put((byte) bitDepth);
        buffer.put((byte) colorType);
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        return buffer.array();
    }
    
    private void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        ByteBuffer lengthBuf = ByteBuffer.allocate(4);
        lengthBuf.order(ByteOrder.BIG_ENDIAN);
        lengthBuf.putInt(data.length);
        out.write(lengthBuf.array());
        
        byte[] typeBytes = type.getBytes("ISO-8859-1");
        out.write(typeBytes);
        out.write(data);
        
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        ByteBuffer crcBuf = ByteBuffer.allocate(4);
        crcBuf.order(ByteOrder.BIG_ENDIAN);
        crcBuf.putInt((int) crc.getValue());
        out.write(crcBuf.array());
    }
    
    private int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) |
               ((data[offset + 1] & 0xFF) << 16) |
               ((data[offset + 2] & 0xFF) << 8) |
               (data[offset + 3] & 0xFF);
    }
    
    private int readShort(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }
    
    private static class ApngFrame {
        int width, height;
        int xOffset, yOffset;
        int delayMs;
        int disposeOp;
        int blendOp;
        byte[] imageData;
    }
}

