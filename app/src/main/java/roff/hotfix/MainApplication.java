package roff.hotfix;

import android.app.Application;
import android.content.Context;
import android.os.Environment;

import com.alipay.euler.andfix.patch.PatchManager;

import java.io.File;
import java.io.IOException;

/**
 * Created by Wilbur Wu on 16/4/17.
 */
public class MainApplication extends Application {

    final String version_name = "1"; //version name with bug

    final String patch_name = "roff_apk.patch";

    @Override
    public void onCreate() {
        super.onCreate();

        PatchManager patchManager = new PatchManager(this);
        patchManager.init(version_name);//current version

        patchManager.loadPatch();

        File patch = new File(Environment.getExternalStorageDirectory(), patch_name);
        if (patch.exists()) {
            try {
                patchManager.addPatch(patch.getPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }
}
