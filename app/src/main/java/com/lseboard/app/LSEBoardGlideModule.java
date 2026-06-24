package com.lseboard.app;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

/**
 * Glide 配置模組
 * 用於優化 GIF 顯示和緩存策略
 */
@GlideModule
public class LSEBoardGlideModule extends AppGlideModule {

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        // 設定記憶體快取大小 (50MB)
        int memoryCacheSizeBytes = 1024 * 1024 * 50;
        builder.setMemoryCache(new LruResourceCache(memoryCacheSizeBytes));

        // 設定磁碟快取大小 (250MB)
        int diskCacheSizeBytes = 1024 * 1024 * 250;
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, diskCacheSizeBytes));

        // 設定預設圖片格式為 RGB_565 (節省記憶體，GIF 動畫更流暢)
        builder.setDefaultRequestOptions(
                new RequestOptions()
                        .format(DecodeFormat.PREFER_RGB_565)
        );

        // 設定日誌級別
        builder.setLogLevel(Log.ERROR);
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        // 可以在這裡註冊自定義的 ModelLoader 或 ResourceDecoder
        // 目前使用預設配置
    }

    @Override
    public boolean isManifestParsingEnabled() {
        // 禁用 AndroidManifest 解析以提高效能
        return false;
    }
}

