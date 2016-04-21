/*
 * 
 * Copyright (c) 2015, alipay.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package roff.hotfix.patch;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import roff.hotfix.AndFixManager;
import roff.hotfix.util.FileUtil;

/**
 * patch manager
 * 
 * @author sanping.li@alipay.com
 * 
 */
public class PatchManager {
	private static final String TAG = "PatchManager";
	// patch extension
	private static final String SUFFIX = ".apatch";
	private static final String DIR = "apatch";
	private static final String SP_NAME = "_andfix_";
	private static final String SP_VERSION = "version";

	/**
	 * context
	 */
	private final Context mContext;
	/**
	 * AndFix manager
	 */
	private final AndFixManager mAndFixManager;
	/**
	 * patch directory
	 */
	private final File mPatchDir;
	/**
	 * patchs
	 */
	private final SortedSet<roff.hotfix.patch.Patch> mPatchs;
	/**
	 * classloaders
	 */
	private final Map<String, ClassLoader> mLoaders;

	/**
	 * @param context
	 *            context
	 */
	public PatchManager(Context context) {
		mContext = context;
		mAndFixManager = new AndFixManager(mContext);

		/**
		 * patch文件存储目录/data/data/<package_name>/files/apatch
		 */
		mPatchDir = new File(mContext.getFilesDir(), DIR);

		/**
		 * 使用 {@link ConcurrentSkipListSet}是为了对patch文件排序，
		 * 排序依据是 {@link roff.hotfix.patch.Patch.CREATED_TIME} 是在制作patch文件时写入的
		 * 所以制作patch文件一定要注意时间戳
		 */
		mPatchs = new ConcurrentSkipListSet<roff.hotfix.patch.Patch>();

		/**
		 * ??? 为什么需要保存 ClassLoader ???
		 */
		mLoaders = new ConcurrentHashMap<String, ClassLoader>();
	}

	/**
	 * initialize
	 *
	 * @param appVersion
	 *            App version
	 */
	public void init(String appVersion) {
		if (!mPatchDir.exists() && !mPatchDir.mkdirs()) {// make directory fail
			Log.e(TAG, "patch dir create error.");
			return;
		} else if (!mPatchDir.isDirectory()) {// not directory
			mPatchDir.delete();
			return;
		}
		SharedPreferences sp = mContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
		String ver = sp.getString(SP_VERSION, null);
		if (ver == null || !ver.equalsIgnoreCase(appVersion)) {
			/**
                         * 全新安装或升级的时候要清理patch文件
			 */
			cleanPatch();
			/**
                         * 使用{@link android.content.SharedPreferences.Editor.apply()}更好
			 * 源码此处使用了{.commit()}
			 * 项目中确实遇到apply()导致的IO错误
			 */
			sp.edit().putString(SP_VERSION, appVersion).apply();
		} else {
			initPatchs();
		}
	}

	/**
	 * 读取patch目录下的全部.apatch文件存放在{@link roff.hotfix.patch.PatchManager ::mPatchs}里
	 */
	private void initPatchs() {
		File[] files = mPatchDir.listFiles();
		for (File file : files) {
			addPatch(file);
		}
	}

	/**
	 * add patch file
	 *
	 * @param file
	 * @return patch
	 */
	private roff.hotfix.patch.Patch addPatch(File file) {
		roff.hotfix.patch.Patch patch = null;
		if (file.getName().endsWith(SUFFIX)) {
			try {
				patch = new roff.hotfix.patch.Patch(file);
				mPatchs.add(patch);
			} catch (IOException e) {
				Log.e(TAG, "addPatch", e);
			}
		}
		return patch;
	}

	private void cleanPatch() {
		File[] files = mPatchDir.listFiles();
		for (File file : files) {
			mAndFixManager.removeOptFile(file);
			if (!FileUtil.deleteFile(file)) {
				Log.e(TAG, file.getName() + " delete error.");
			}
		}
	}

	/**
	 * add patch at runtime
	 *
	 * @param path
	 *            patch path
	 * @throws IOException
	 */
	public void addPatch(String path) throws IOException {
		File src = new File(path);
		File dest = new File(mPatchDir, src.getName());
		if(!src.exists()){
			throw new FileNotFoundException(path);
		}
		if (dest.exists()) {
			Log.d(TAG, "patch [" + path + "] has be loaded.");
			return;
		}
		FileUtil.copyFile(src, dest);// copy to patch's directory
		roff.hotfix.patch.Patch patch = addPatch(dest);
		if (patch != null) {
			loadPatch(patch);
		}
	}

	/**
	 * remove all patchs
	 */
	public void removeAllPatch() {
		cleanPatch();
		SharedPreferences sp = mContext.getSharedPreferences(SP_NAME,
								     Context.MODE_PRIVATE);
		sp.edit().clear().commit();
	}

	/**
	 * load patch,call when plugin be loaded. used for plugin architecture.</br>
	 *
	 * need name and classloader of the plugin
	 *
	 * @param patchName
	 *            patch name
	 * @param classLoader
	 *            classloader
	 */
	public void loadPatch(String patchName, ClassLoader classLoader) {
		mLoaders.put(patchName, classLoader);
		Set<String> patchNames;
		List<String> classes;
		for (roff.hotfix.patch.Patch patch : mPatchs) {
			patchNames = patch.getPatchNames();
			if (patchNames.contains(patchName)) {
				classes = patch.getClasses(patchName);
				mAndFixManager.fix(patch.getFile(), classLoader, classes);
			}
		}
	}

	/**
	 * load patch,call when application start
	 *
	 */
	public void loadPatch() {
		/**
		 * 因为是在Application中初始化，所以应该只有一个mContext和对应的一个ClassLoader
		 */
		mLoaders.put("*", mContext.getClassLoader());// wildcard
		Set<String> patchNames;
		List<String> classes;
		for (roff.hotfix.patch.Patch patch : mPatchs) {
			patchNames = patch.getPatchNames();
			for (String patchName : patchNames) {
				classes = patch.getClasses(patchName);
				mAndFixManager.fix(patch.getFile(), mContext.getClassLoader(), classes);
			}
		}
	}

	/**
	 * load specific patch
	 *
	 * @param patch
	 *            patch
	 */
	private void loadPatch(Patch patch) {
		Set<String> patchNames = patch.getPatchNames();
		ClassLoader cl;
		List<String> classes;
		for (String patchName : patchNames) {
			if (mLoaders.containsKey("*")) {
				cl = mContext.getClassLoader();
			} else {
				cl = mLoaders.get(patchName);
			}
			if (cl != null) {
				classes = patch.getClasses(patchName);
				mAndFixManager.fix(patch.getFile(), cl, classes);
			}
		}
	}

}
