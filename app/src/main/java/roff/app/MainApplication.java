package roff.app;

import android.app.Application;
import android.content.Context;
import android.os.Environment;

import com.alipay.euler.andfix.patch.PatchManager;

import java.io.IOException;

/**
 * Created by Wilbur Wu on 16/4/17.
 */
public class MainApplication extends Application {

    final String version_name = "1.0"; //version name with bug

    private static final String APATCH_PATH = "/out.apatch";

    private PatchManager patchManager;

    @Override
    public void onCreate() {
        super.onCreate();

        //initialize
        patchManager = new PatchManager(this);
        patchManager.init(version_name);//current version

        //load patch
        patchManager.loadPatch();

        //add patch at runtime
        try {
            //.apatch file path
            String patchFileString = Environment.getExternalStorageDirectory().getAbsolutePath() + APATCH_PATH;
            patchManager.addPatch(patchFileString);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }
}
