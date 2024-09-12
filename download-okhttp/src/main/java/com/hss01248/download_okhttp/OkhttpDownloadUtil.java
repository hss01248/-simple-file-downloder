package com.hss01248.download_okhttp;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    volatile static Set<String> runningTask = new CopyOnWriteArraySet<>();

    public static  void pauseOrStop(String url){
        runningTask.remove(url);
    }


    volatile  static ExecutorService service;

    public static void setThreadCount(int threadCount) {
        OkhttpDownloadUtil.threadCount = threadCount;
    }

    static int threadCount = 30;

    public static void setLogEnable(boolean logEnable) {
        OkhttpDownloadUtil.logEnable = logEnable;
    }

    static boolean logEnable = false;

    public static void setGlobalSaveDir(String globalSaveDir) {
        OkhttpDownloadUtil.globalSaveDir = globalSaveDir;
    }

    static String globalSaveDir;

    public static void downloadAsync(DownloadConfig config){
        if(service ==null){
            service = Executors.newFixedThreadPool(threadCount);
        }
        try{
            service.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        downloadSync(config);
                    }catch (Throwable throwable){
                        w(throwable,config.getUrl());
                        config.getCallback().onFailed(config.getUrl(), config.getFilePath(),
                                throwable.getClass().getSimpleName(),throwable.getMessage(),throwable);
                    }
                }
            });
        }catch (Throwable throwable){
            //等待队列超出长度,栈溢出等
            w(throwable);
            config.getCallback().onFailed(config.getUrl(), config.getFilePath(),
                    throwable.getClass().getSimpleName(),throwable.getMessage(),throwable);
        }


    }


   public static void downloadSync(DownloadConfig config)  {

       String url = config.getUrl();
       String filePath = config.getFilePath();
       boolean forceRedownload = config.isForceRedownload();
       boolean notAcceptRanges = config.isNotAcceptRanges();

       Map<String,String> headers = config.getHeaders();
       Long fileSizeAlreadyKnown = config.getFileSizeAlreadyKnown();
       IDownloadCallback callback = config.getCallback();
       callback.onStartReal(url,filePath);

        initClient();
        if(runningTask.contains(url)){
            w("该url已经在下载中",url);
            callback.onFailed(url,filePath,"","该url已经在下载中",null);
            return;
        }


       try {
           filePath = FileAndDirUtil.dealFilePath(config);
           config.setFilePath(filePath);
       } catch (Throwable e) {
           callback.onFailed(url,filePath,"",e.getMessage(),null);
           return;
       }

       File file = new File(filePath);

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
        runningTask.add(url);
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
                            if(lenStr !=null && !"".equals(lenStr)){
                                try{
                                    fileSizeAlreadyKnown = Long.parseLong(lenStr);
                                }catch (Throwable throwable){
                                }
                            }
                        }else {
                            w("head() request failed0",url,response.code(),response.message());
                        }
                    }catch (Throwable throwable){
                        w("head() request failed",url,throwable);
                    }
                }
                d("file.length:"+file.length(), "content-length:" +fileSizeAlreadyKnown,
                        "服务端是否支持断点续传:"+supportRanges,"客户端是否允许断点续传:"+(!notAcceptRanges));
                if(fileSizeAlreadyKnown !=null && fileSizeAlreadyKnown >0){
                    // args[0] = file.length:43373950
                    // │ args[1] = content-length:37629451

                    if(file.length() == fileSizeAlreadyKnown){
                        //已经是下载成功的
                        d("file already exist and same bytes as header", filePath,url);
                        runningTask.remove(url);
                        callback.onSuccess(url,filePath);
                        return;
                    }else {
                        if(supportRanges && !notAcceptRanges){
                            //服务端支持+ 客户端允许
                            builder.header("Range","bytes="+(file.length())+"-");
                            isRangeRequest = true;
                        }
                    }
                }
            }
        }
        /*if(!isRangeRequest){
            file.delete();
        }*/
        Request request = builder
                .build();
        try{
            Response response = client.newCall(request).execute();
            if(!response.isSuccessful()){
                w("download failed0",url,response.code(),response.message());
                runningTask.remove(url);
                callback.onFailed(url,filePath,response.code()+"","download failed: "+response.message(),null);
                return;
            }
            if(response.body() == null ){
                w("download failed: request success but response body is empty!",url,response.code(),response.message());
                runningTask.remove(url);
                callback.onFailed(url,filePath,"","request success but response body is empty! ",null);
                return;
            }

            if(!isRangeRequest){
                String lenStr =   response.header("Content-Length");
                if(lenStr !=null && !"".equals(lenStr)){
                    try{
                        fileSizeAlreadyKnown = Long.parseLong(lenStr);
                    }catch (Throwable throwable){
                    }
                }
            }
            if(file.exists() && file.length()>0 ){
                if(!isRangeRequest){
                    if(fileSizeAlreadyKnown !=null && fileSizeAlreadyKnown >0){
                        if(file.length() == fileSizeAlreadyKnown){
                            callback.onSuccess(url,file.getAbsolutePath());
                            d("文件大小与远程一致2,"+url);
                            runningTask.remove(url);
                            return;
                        }else {
                            file.delete();
                        }
                    }else {
                        file.delete();
                    }
                }
            }



            //httpcode!=206 且响应头没有Content-Range的话,就说明还是全部文件,而不是部分文件:

            InputStream inputStream = response.body().byteStream();
            boolean append = file.exists() && file.length() > 0 && isRangeRequest;
            //inputStream.available():1049256  返回的和content-length不一致, 因为服务端用的buffered
            d("download as append: "+append,url,"inputStream.available():"+
                    inputStream.available(),"content-length:"+fileSizeAlreadyKnown);
            if(fileSizeAlreadyKnown ==null){
                if(!append){
                   // fileSizeAlreadyKnown = (long) inputStream.available();
                    w("没有返回cotent-length,尝试获取inputStream.available:"+inputStream.available());
                }
            }
            Long finalFileSizeAlreadyKnown1 = fileSizeAlreadyKnown;
            long len = file.length();
            try{
                boolean success = writeFileFromIS(url,file, inputStream, append,config, new IDownloadCallback() {
                    @Override
                    public void onSuccess(String url, String path) {

                    }

                    @Override
                    public void onFailed(String url, String path, String code, String msg, Throwable e) {

                    }

                    long lastReceived = 0;
                    long lastProgressTime = 0;
                    @Override
                    public void onProgress(String url,String path,long total,long alreadyReceived,long s) {

                        if(finalFileSizeAlreadyKnown1 ==null){
                            total = 0;
                        }else {
                            total = finalFileSizeAlreadyKnown1;
                        }
                        alreadyReceived = alreadyReceived+len;

                        if(lastReceived ==0){
                            lastReceived = alreadyReceived;
                            lastProgressTime = System.currentTimeMillis();
                            callback.onProgress(url,path,total,alreadyReceived,0L);
                        }else {
                            long changed = alreadyReceived - lastReceived;
                            long speed = 0;
                            if(System.currentTimeMillis() != lastProgressTime){
                                speed = changed*1000 /(System.currentTimeMillis() - lastProgressTime);
                            }
                            lastProgressTime = System.currentTimeMillis();
                            lastReceived = alreadyReceived;
                            callback.onProgress(url,path,total,alreadyReceived,speed);
                            if(finalFileSizeAlreadyKnown1 !=null){
                                d("download-progress",((alreadyReceived+len)*100.0/finalFileSizeAlreadyKnown1)+"%, "
                                        +url, (alreadyReceived+len)/1024+"KB, speed: "+speed/1024+"KB/s");
                            }
                        }
                    }
                });
                runningTask.remove(url);
                if(success){
                    //对比一下文件大小,相等才是成功:
                    if(fileSizeAlreadyKnown !=null){
                        if(file.length() != fileSizeAlreadyKnown){
                            w("download failed6",url,"size not same",
                                    "file size not same as the content-length: "+file.length()+", "+fileSizeAlreadyKnown);
                            callback.onFailed(url,filePath,"size not same",
                                    "file size not same as the content-length: "+file.length()+", "+fileSizeAlreadyKnown,null);
                            return;
                        }
                    }
                    callback.onSuccess(url,filePath);
                    d("download success",url,response.code(),response.message(),filePath,
                            "file.length:"+file.length(),"content-length:"+finalFileSizeAlreadyKnown1);
                }else {
                    w("download failed4",url,response.code(),response.message());
                    callback.onFailed(url,filePath,"","download file write failed: ",null);
                }
            }catch (InterruptedException e){
                w(url,e,"请求被取消/暂停");
                callback.onCancel(url,filePath);
            }

        }catch (Throwable throwable){
            w("download failed-reqeust failed",url,filePath,throwable);
            runningTask.remove(url);
            callback.onFailed(url,filePath,"","download file  failed: "+ throwable.getMessage(),throwable);
        }
    }




    private static void initClient() {
        if(client ==null){
            client = new OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .retryOnConnectionFailure(true)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10,TimeUnit.SECONDS)
                    .writeTimeout(10,TimeUnit.SECONDS)
                    .build();
        }
    }

    private static int sBufferSize = 524288;
    public static boolean writeFileFromIS(String url,final File file,
                                          final InputStream is,
                                          final boolean append,
                                          DownloadConfig config,
                                          final IDownloadCallback listener) throws InterruptedException, IOException {
        if (is == null || file.isDirectory()) {
            w("FileIOUtils, create file <" + file + "> failed.");
            return false;
        }
        if(!file.exists()){
            file.getParentFile().mkdirs();
            file.createNewFile();
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
                //对于网络流量,这个api不准确
                int curSize = 0;
                listener.onProgress(url,file.getAbsolutePath(),0,0,0);
                byte[] data = new byte[sBufferSize];
                long lastProgress = 0;

                for (int len; (len = is.read(data)) != -1; ) {
                    os.write(data, 0, len);
                    curSize += len;
                    if(System.currentTimeMillis() - lastProgress > config.getProgressCallbackIntervalMills()){
                        lastProgress = System.currentTimeMillis();
                        listener.onProgress(url,file.getAbsolutePath(),0,curSize,0);
                    }
                    if(!runningTask.contains(url)){
                        //中断读取
                        throw  new InterruptedException("paused or stop");
                    }
                }
                listener.onProgress("","",0,curSize,0);
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

     static void w(Object... args) {
        if(!logEnable){
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("warn:  ");
        for (Object arg : args) {
            sb.append(arg)
                    .append("\n");
            if(arg instanceof Throwable){
                ((Throwable) arg).printStackTrace();
            }
        }
        System.out.println(sb.toString());
    }

     static void d(Object... args) {
         if(!logEnable){
             return;
         }
        StringBuilder sb = new StringBuilder();
        sb.append("debug:  ");
        for (Object arg : args) {
            sb.append(arg)
                    .append("\n");
            if(arg instanceof Throwable){
                ((Throwable) arg).printStackTrace();
            }
        }
        System.out.println(sb.toString());
    }


    public static void main(String[] args) {
        OkhttpDownloadUtil.logEnable = true;
        String  url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4";
        DownloadConfig.newBuilder()
                .url(url)
                .saveDir("/Users/hss/Downloads")
                .start(new IDownloadCallback() {
                    @Override
                    public void onSuccess(String url, String path) {

                    }

                    @Override
                    public void onFailed(String url, String path, String code, String msg, Throwable e) {

                    }
                });
    }
}
