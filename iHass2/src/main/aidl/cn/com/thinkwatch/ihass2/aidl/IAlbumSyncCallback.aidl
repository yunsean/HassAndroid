package cn.com.thinkwatch.ihass2.aidl;

interface IAlbumSyncCallback {
    void onUploadChanged();
    void onActionProgress(in int percent, in String message);
    void onActionCompleted();
    void onFileDownloading(in long id, in int percent);
    void onFileDownloaded(in long id);
    void onFileDownloadPaused(in boolean paused);
}
