package cn.com.thinkwatch.ihass2.aidl;
import cn.com.thinkwatch.ihass2.aidl.IAlbumSyncCallback;

interface IAlbumSyncService {
    void configChanged();
    void resumeUpload();
    void itemChanged();
    void scanFolder(in String path);
    void addFile(in String path);
    void scanAll(in boolean arefresh);
    void registerCallback(IAlbumSyncCallback cb);
    void unregisterCallback(IAlbumSyncCallback cb);
    void resumeDownload();
    void pauseDownload();
    boolean isDownloadPaused();
    long downloadingId();
}
