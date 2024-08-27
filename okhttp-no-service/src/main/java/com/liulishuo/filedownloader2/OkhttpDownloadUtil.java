package com.liulishuo.filedownloader2;

import android.text.TextUtils;
import android.util.Log;


import androidx.annotation.Nullable;

import com.blankj.utilcode.util.ConvertUtils;
import com.blankj.utilcode.util.FileIOUtils;
import com.blankj.utilcode.util.FileUtils;
import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.ThreadUtils;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @Despciption todo
 * @Author hss
 * @Date 8/27/24 3:01 PM
 * @Version 1.0
 */
public class OkhttpDownloadUtil {

    volatile static  OkHttpClient client;

  public static void downLoad(String url, String filePath,
                          boolean forceRedownload,
                          boolean wifiRequire,
                  @Nullable Map<String,String> headers,
                          @Nullable Long fileSizeAlreadyKnown,
                          IDownloadCallback callback)  {
        ThreadUtils.executeByIo(new ThreadUtils.SimpleTask<Object>() {
            @Override
            public Object doInBackground() throws Throwable {
                downLoadInThread(url, filePath, forceRedownload, wifiRequire, headers, fileSizeAlreadyKnown, callback);
                return 1;
            }

            @Override
            public void onSuccess(Object result) {

            }
        });

    }

   private static void downLoadInThread(String url, String filePath,
                  boolean forceRedownload,
                  boolean wifiRequire,
                 Map<String,String> headers,
                 @Nullable Long fileSizeAlreadyKnown,
                  IDownloadCallback callback)  {
        initClient();

        File file = new File(filePath);

        //todo wifiRequire

        Request.Builder builder = new Request.Builder()
                .url(url)
                .get();
        if(headers !=null){
            for (String s : headers.keySet()) {
                builder.header(s,headers.get(s)+"");
            }
        }
        if(file.exists() && file.isDirectory()){
            callback.onFailed(url,filePath,"","file path is dir, please rename it or your file path",null);
            return;
        }
        boolean isRangeRequest = false;
        if(file.exists() && file.isFile() && file.length() >0){
            if(forceRedownload){
                file.delete();
            }else {
                //断点续传:
                boolean supportRanges = true;
                if(fileSizeAlreadyKnown ==null || fileSizeAlreadyKnown==0){
                    supportRanges = false;
                    Request.Builder builder1 = new Request.Builder()
                            .url(url)
                            .head();

                    if(headers !=null){
                        for (String s : headers.keySet()) {
                            builder1.header(s,headers.get(s)+"");
                        }
                    }
                    Request request = builder1.build();
                    try{
                        Response response = client.newCall(request).execute();
                        if(response.isSuccessful()){
                            String lenStr =   response.header("Content-Length");
                            supportRanges = "bytes".equals(response.header("Accept-Ranges"));
                            if(!TextUtils.isEmpty(lenStr) && TextUtils.isDigitsOnly(lenStr)){
                                fileSizeAlreadyKnown = Long.parseLong(lenStr);
                            }
                        }else {
                            LogUtils.w("head() request failed0",url,response.code(),response.message());
                        }
                    }catch (Throwable throwable){
                        LogUtils.w("head() request failed",url,throwable);
                    }

                }
                if(fileSizeAlreadyKnown !=null && fileSizeAlreadyKnown >0){
                    // args[0] = file.length:43373950
                    // │ args[1] = content-length:37629451
                    LogUtils.d("file.length:"+file.length(), "content-length:"+fileSizeAlreadyKnown);
                    if(file.length() == fileSizeAlreadyKnown){
                        //已经是下载成功的
                        LogUtils.d("file already exist and same bytes as header", filePath,url);
                        callback.onSuccess(url,filePath);
                        return;
                    }else {

                        if(supportRanges){
                            builder.header("Range","bytes="+(file.length())+"-");
                            isRangeRequest = true;
                        }
                    }
                }
            }
        }
        if(!isRangeRequest){
            file.delete();
        }
        Request request = builder
                .build();
        try{
            Response response = client.newCall(request).execute();
            if(!response.isSuccessful()){
                LogUtils.w("download failed0",url,response.code(),response.message());
                callback.onFailed(url,filePath,response.code()+"","download failed: "+response.message(),null);
                return;
            }
            if(response.body() == null ){
                LogUtils.w("download failed: request success but response body is empty!",url,response.code(),response.message());
                callback.onFailed(url,filePath,"","request success but response body is empty! ",null);
                return;
            }

            String lenStr =   response.header("Content-Length");
            if(!TextUtils.isEmpty(lenStr) && TextUtils.isDigitsOnly(lenStr)){
                fileSizeAlreadyKnown = Long.parseLong(lenStr);
            }
            //httpcode!=206 且响应头没有Content-Range的话,就说明还是全部文件,而不是部分文件:

            InputStream inputStream = response.body().byteStream();
            boolean append = file.exists() && file.length() > 0 && isRangeRequest;
            //inputStream.available():1049256  返回的和content-length不一致
            LogUtils.d("download as append: "+append,url,"inputStream.available():"+
                    inputStream.available(),"content-length:"+fileSizeAlreadyKnown);
            if(fileSizeAlreadyKnown ==null){
                if(!append){
                    fileSizeAlreadyKnown = (long) inputStream.available();
                }
            }

            int all = inputStream.available();
            Long finalFileSizeAlreadyKnown1 = fileSizeAlreadyKnown;
            long len = file.length();
            boolean success = writeFileFromIS(file, inputStream, append, new IDownloadCallback() {
                @Override
                public void onSuccess(String url, String path) {

                }

                @Override
                public void onFailed(String url, String path, String code, String msg, Throwable e) {

                }

                @Override
                public void onProgress(String url,String path,long total,long alreadyReceived) {
                    Log.v("download-progress",((alreadyReceived+len)*100.0/finalFileSizeAlreadyKnown1)+"%, "
                            +url+", "+ ConvertUtils.byte2FitMemorySize(alreadyReceived+len,1));
                    callback.onProgress(url, filePath, finalFileSizeAlreadyKnown1, alreadyReceived+len);

                }
            });
            if(success){
                callback.onSuccess(url,filePath);
                LogUtils.d("download success",url,response.code(),response.message(),filePath,
                        "file.length:"+file.length(),"content-length:"+finalFileSizeAlreadyKnown1);
            }else {
                LogUtils.w("download failed4",url,response.code(),response.message());
                callback.onFailed(url,filePath,"","download file write failed: ",null);
            }
        }catch (Throwable throwable){
            LogUtils.w("download failed1",url,throwable);
            callback.onFailed(url,filePath,"","download file  failed: "+ throwable.getMessage(),throwable);
        }
    }

    private static void initClient() {
        if(client ==null){
            client = new OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build();
        }
    }

    private static int sBufferSize = 524288;
    public static boolean writeFileFromIS(final File file,
                                          final InputStream is,
                                          final boolean append,
                                          final IDownloadCallback listener) {
        if (is == null || !FileUtils.createOrExistsFile(file)) {
            Log.e("FileIOUtils", "create file <" + file + "> failed.");
            return false;
        }
        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(file, append), sBufferSize);
            if (listener == null) {
                byte[] data = new byte[sBufferSize];
                for (int len; (len = is.read(data)) != -1; ) {
                    os.write(data, 0, len);
                }
            } else {
                int totalSize = is.available() ;
                //对于网络流量,这个api不准确
                int curSize = 0;
                listener.onProgress("","",0,0);
                byte[] data = new byte[sBufferSize];
                for (int len; (len = is.read(data)) != -1; ) {
                    os.write(data, 0, len);
                    curSize += len;
                    listener.onProgress("","",0,curSize);
                }
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (os != null) {
                    os.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
