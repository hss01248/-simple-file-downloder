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

import com.blankj.utilcode.util.ThreadUtils;
import com.hss01248.download_okhttp.IDownloadCallback;

/**
 * @Despciption todo
 * @Author hss
 * @Date 8/28/24 2:45 PM
 * @Version 1.0
 */
public class DownloadCallbackOnMainThreadWrapper implements IDownloadCallback {

    public DownloadCallbackOnMainThreadWrapper(IDownloadCallback callback) {
        this.callback = callback;
    }

    IDownloadCallback callback;
    @Override
    public void onSuccess(String url, String path) {
        ThreadUtils.getMainHandler().post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(url, path);
            }
        });

    }

    @Override
    public void onFailed(String url, String path, String code, String msg, Throwable e) {
        ThreadUtils.getMainHandler().post(new Runnable() {
            @Override
            public void run() {
                callback.onFailed(url, path, code, msg, e);
            }
        });
    }

    @Override
    public void onProgress(String url, String path, long total, long alreadyReceived,long speed) {
        ThreadUtils.getMainHandler().post(new Runnable() {
            @Override
            public void run() {
                callback.onProgress(url, path, total, alreadyReceived,speed);
            }
        });
    }

    @Override
    public void onStartReal(String url, String path) {
        ThreadUtils.getMainHandler().post(new Runnable() {
            @Override
            public void run() {
                callback.onStartReal(url, path);
            }
        });
    }

    @Override
    public void onCodeStart(String url, String path) {
        ThreadUtils.getMainHandler().post(new Runnable() {
            @Override
            public void run() {
                callback.onCodeStart(url, path);
            }
        });
    }

    @Override
    public void onCancel(String url, String path) {
        ThreadUtils.getMainHandler().post(new Runnable() {
            @Override
            public void run() {
                callback.onCancel(url, path);
            }
        });
    }
}
