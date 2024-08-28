/*
 * Copyright (c) 2015 LingoChamp Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liulishuo.filedownloader2;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.blankj.utilcode.util.ActivityUtils;
import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.blankj.utilcode.util.Utils;
import com.hss01248.download_okhttp.DownloadConfig;
import com.hss01248.openuri2.OpenUri2;

import java.io.File;

/**
 * @Despciption todo
 * @Author hss
 * @Date 8/28/24 2:48 PM
 * @Version 1.0
 */
public class AndroidDownloader {


    public static DownloadConfig.Builder prepareDownload(String url,boolean wifiRequired){
        return DownloadConfig.newBuilder()
                .url(url)
                .requestSync(false)
                .wifiRequire(wifiRequired);
    }

    public static void openFile(String path) {
        Intent intent = new Intent("android.intent.action.VIEW");
        intent.addCategory("android.intent.category.DEFAULT");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Uri uri = OpenUri2.fromFile(Utils.getApp(),new File(path));
        OpenUri2.addPermissionR(intent);
        //
        String name = new File(path).getName();
        if(name.contains(".")){
            name = name.substring(name.lastIndexOf(".")+1);
        }
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name);
        if(!TextUtils.isEmpty(type)){
            intent.setDataAndType(uri, type);
        }
        try {
            ActivityUtils.getTopActivity().startActivity(intent);
        }catch (Throwable throwable){
            LogUtils.w(throwable);
            ToastUtils.showShort(throwable.getMessage());
        }
    }
}
