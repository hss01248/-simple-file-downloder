# simple-file-downloder

* simplely base on okhttp and http range, and  java thread

* no more depend on the fucking changeable android service.



download-okhttp  is a java module ,can use in java server code;

download-okhttp-android is a simple callback wrapper and enter code wrapper for android.



# usage

## download-okhttp

```java
DownloadConfig.newBuilder()
                .url(url)
  							....
  							.callback();
//with callback() called , the download begain start with a 20 thread pool, which can be change by:

OkhttpDownloadUtil.setThreadCount(int threadCount)
```

the download can config as this:

![image-20240828160943129](https://cdn.jsdelivr.net/gh/shuiniuhss/myimages@main/imagemac3/image-20240828160943129.png)

//todo:

add interceptors  , custom okhttpclient

### callback:

```java
public interface IDownloadCallback{

    void onSuccess(String url, String path);

    void onFailed(String url, String path, String code, String msg, Throwable e);

    default  void onProgress(String url, String path, long total, long alreadyReceived){}

    default  void onSpeed(String url, String path, long speed){}
}
```

### pause or stop the downloding:

```java
OkhttpDownloadUtil.pauseOrStop(String url)
```



## download-okhttp-android

```java
public static DownloadConfig.Builder prepareDownload(String url,boolean wifiRequired){
        return DownloadConfig.newBuilder()
                .url(url)
                .wifiRequire(wifiRequired);
    }

//use the callback wrapper:
  .callback(new DownloadCallbackOnMainThreadWrapper(new IDownloadCallback() {}));
```



# the old FileDownloader lib:

Fork from https://github.com/lingochamp/FileDownloader 1.7.7 

and has adapte android 14 service type.

