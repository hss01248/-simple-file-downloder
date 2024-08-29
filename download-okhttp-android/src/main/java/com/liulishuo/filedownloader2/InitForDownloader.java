package com.liulishuo.filedownloader2;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;

import com.hss01248.download_okhttp.OkhttpDownloadUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @Despciption todo
 * @Author hss
 * @Date 8/29/24 5:02 PM
 * @Version 1.0
 */
public class InitForDownloader implements Initializer<String> {
    @NonNull
    @Override
    public String create(@NonNull Context context) {
        File dir = context.getExternalFilesDir("downloader");
        if(dir == null){
            dir = new File(context.getFilesDir(),"downloader");
        }
        OkhttpDownloadUtil.setGlobalSaveDir(dir.getAbsolutePath());
        return "downloaderxxx";
    }

    @NonNull
    @Override
    public List<Class<? extends Initializer<?>>> dependencies() {
        return new ArrayList<>();
    }
}
