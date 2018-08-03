package cn.com.thinkwatch.ihass2.global;

import android.view.View;

import cn.com.thinkwatch.ihass2.R;
import com.dylan.uiparts.layout.LoadableLayout;

public class GlobalConfig {
    public static class LoadingConfig implements LoadableLayout.OnShowLoadingListener {
        @Override
        public int getLoadingResId() {
            return R.layout.layout_loading;
        }
        @Override
        public void onShowLoading(View loadinger) {
            loadinger.findViewById(com.dylan.uiparts.R.id.dyn_loading_background).setBackgroundColor(0xffffffff);
        }
    }
    public static class NetErrorConfig implements LoadableLayout.OnShowNetErrorListener {
        @Override
        public void onShowNetError(View neterror) {
            neterror.findViewById(com.dylan.uiparts.R.id.dyn_net_error_background).setBackgroundColor(0xffffffff);
        }
    }
}
