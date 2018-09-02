package cn.com.thinkwatch.ihass2.aidl;
import cn.com.thinkwatch.ihass2.model.JsonEntity;

interface IDataSyncCallback {
    void onEntityChanged(in cn.com.thinkwatch.ihass2.model.JsonEntity entity);
}
