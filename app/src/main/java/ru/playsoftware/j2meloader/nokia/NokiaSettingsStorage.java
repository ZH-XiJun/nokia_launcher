package ru.playsoftware.j2meloader.nokia;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 诺基亚桌面设置的 SharedPreferences 封装。
 * 管理快捷栏应用列表、壁纸、软键映射等设置项的读写。
 */
public class NokiaSettingsStorage {

	private static final String PREFS_NAME = "nokia_desktop_settings";
	private static final String KEY_SHORTCUT_APPS = "shortcut_apps";
	private static final String KEY_WALLPAPER = "wallpaper";
	private static final String KEY_SOFT_LEFT_ACTION = "soft_left_action";
	private static final String KEY_SOFT_RIGHT_ACTION = "soft_right_action";

	private final SharedPreferences prefs;
	private final Context context;

	public NokiaSettingsStorage(Context context) {
		this.context = context.getApplicationContext();
		prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
	}

	// ── 快捷栏应用 ──

	/**
	 * 默认快捷应用清单（按用户期望的顺序）。
	 * 前四项为系统隐式 Intent（相机/电话/短信/浏览器），后四项为已知包名应用。
	 */
	private static final String[][] DEFAULT_APPS = {
			{"action_camera", "相机"},
			{"action_dial", "电话"},
			{"action_sms", "短信"},
			{"action_browser", "浏览器"},
			{"com.tencent.mobileqq", "QQ"},
			{"com.tencent.mm", "微信"},
			{"com.ss.android.ugc.aweme", "抖音"},
			{"tv.danmaku.bili", "bilibili"},
	};

	/**
	 * 音乐类 app 优先级清单。多个音乐 app 并存时仅取第一个已安装的，
	 * 避免快捷栏出现多个音乐入口。
	 */
	private static final String[][] MUSIC_APP_PRIORITY = {
			{"com.netease.cloudmusic", "网易云音乐"},
			{"com.tencent.qqmusic", "QQ音乐"},
			{"com.kugou.android", "酷狗音乐"},
			{"cn.kuwo.player", "酷我音乐"},
			{"cmccwm.mobilemusic", "咪咕音乐"},
			{"com.spotify.music", "Spotify"},
	};

	/** 获取已选择的快捷栏应用列表 */
	public List<ShortcutApp> getShortcutApps() {
		List<ShortcutApp> result = new ArrayList<>();
		String json = prefs.getString(KEY_SHORTCUT_APPS, null);
		if (json == null) {
			// 首次启动：生成默认快捷应用（仅已安装的应用会被加入），并持久化
			NokiaLog.i("SettingsStorage", "shortcut_apps 未配置，生成默认快捷应用");
			result = buildDefaultShortcutApps();
			setShortcutApps(result);
			return result;
		}
		if (json.isEmpty()) {
			return result;
		}
		try {
			JSONArray arr = new JSONArray(json);
			for (int i = 0; i < arr.length(); i++) {
				result.add(ShortcutApp.fromJson(arr.getJSONObject(i)));
			}
			NokiaLog.i("SettingsStorage", "getShortcutApps: 从存储读取 " + result.size() + " 个应用");
		} catch (JSONException e) {
			NokiaLog.e("SettingsStorage", "getShortcutApps 解析失败", e);
		}
		return result;
	}

	/**
	 * 根据已安装应用生成默认快捷栏：遍历 DEFAULT_APPS，
	 * - "action_*" 前缀：用对应的系统隐式 Intent 解析出可用 Activity；
	 * - 包名：检查是否已安装，取主启动 Activity。
	 * 未安装/无可用 Activity 的则跳过。
	 */
	private List<ShortcutApp> buildDefaultShortcutApps() {
		List<ShortcutApp> defaults = new ArrayList<>();
		PackageManager pm = context.getPackageManager();

		for (String[] entry : DEFAULT_APPS) {
			String key = entry[0];
			String label = entry[1];
			if (key.startsWith("action_")) {
				addActionApp(pm, defaults, key, label);
			} else {
				addPackageApp(pm, defaults, key, label);
			}
		}

		// 音乐：多个音乐 app 仅取第一个已安装的
		addMusicApp(pm, defaults);

		NokiaLog.i("SettingsStorage", "默认快捷应用生成完成: " + defaults.size() + " 个");
		return defaults;
	}

	/** 按优先级取第一个已安装的音乐 app 加入默认列表（只加一个） */
	private void addMusicApp(PackageManager pm, List<ShortcutApp> out) {
		for (String[] entry : MUSIC_APP_PRIORITY) {
			String pkg = entry[0];
			String label = entry[1];
			try {
				pm.getPackageInfo(pkg, 0);
			} catch (PackageManager.NameNotFoundException e) {
				continue; // 未安装，尝试下一个
			}
			Intent launch = pm.getLaunchIntentForPackage(pkg);
			if (launch == null || launch.getComponent() == null) {
				continue;
			}
			launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
			String appKey = launch.getComponent().getPackageName() + "/"
					+ launch.getComponent().getClassName();
			out.add(new ShortcutApp(ShortcutApp.TYPE_ANDROID, label, appKey, launch));
			NokiaLog.i("SettingsStorage", "默认音乐应用已加入: " + label + " -> " + appKey);
			return; // 仅取第一个
		}
		NokiaLog.i("SettingsStorage", "未找到已安装的音乐 app，跳过音乐快捷项");
	}

	/** 通过包名检查是否已安装，并取主启动 Activity 加入默认列表 */
	private void addPackageApp(PackageManager pm, List<ShortcutApp> out, String pkg, String label) {
		try {
			pm.getPackageInfo(pkg, 0);
		} catch (PackageManager.NameNotFoundException e) {
			NokiaLog.i("SettingsStorage", "默认应用未安装，跳过: " + label + " (" + pkg + ")");
			return;
		}
		Intent launch = pm.getLaunchIntentForPackage(pkg);
		if (launch == null || launch.getComponent() == null) {
			NokiaLog.w("SettingsStorage", "默认应用无启动 Intent，跳过: " + label + " (" + pkg + ")");
			return;
		}
		launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		String appKey = launch.getComponent().getPackageName() + "/"
				+ launch.getComponent().getClassName();
		out.add(new ShortcutApp(ShortcutApp.TYPE_ANDROID, label, appKey, launch));
		NokiaLog.i("SettingsStorage", "默认应用已加入: " + label + " -> " + appKey);
	}

	/** 通过系统隐式 Intent 解析出可用 Activity 并加入默认列表 */
	private void addActionApp(PackageManager pm, List<ShortcutApp> out, String key, String label) {
		Intent intent = buildActionIntent(key);
		if (intent == null) return;
		ResolveInfo ri = pm.resolveActivity(intent, 0);
		if (ri == null || ri.activityInfo == null) {
			NokiaLog.i("SettingsStorage", "默认应用无可用 Activity，跳过: " + label + " (" + key + ")");
			return;
		}
		ActivityInfo ai = ri.activityInfo;
		Intent launch = new Intent(Intent.ACTION_MAIN);
		launch.addCategory(Intent.CATEGORY_LAUNCHER);
		launch.setClassName(ai.packageName, ai.name);
		launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		String appKey = ai.packageName + "/" + ai.name;
		out.add(new ShortcutApp(ShortcutApp.TYPE_ANDROID, label, appKey, launch));
		NokiaLog.i("SettingsStorage", "默认应用已加入: " + label + " -> " + appKey);
	}

	/** 根据 action key 构造对应的隐式 Intent */
	private Intent buildActionIntent(String key) {
		switch (key) {
			case "action_camera":
				return new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			case "action_dial":
				return new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"));
			case "action_sms":
				return new Intent(Intent.ACTION_VIEW, Uri.parse("sms:"));
			case "action_browser":
				return new Intent(Intent.ACTION_VIEW, Uri.parse("http://"));
			default:
				return null;
		}
	}

	/** 保存快捷栏应用列表 */
	public void setShortcutApps(List<ShortcutApp> apps) {
		JSONArray arr = new JSONArray();
		for (ShortcutApp app : apps) {
			try {
				arr.put(app.toJson());
			} catch (JSONException e) {
				NokiaLog.e("SettingsStorage", "setShortcutApps 序列化失败: " + app.label, e);
			}
		}
		prefs.edit().putString(KEY_SHORTCUT_APPS, arr.toString()).apply();
		NokiaLog.i("SettingsStorage", "setShortcutApps: 保存 " + apps.size() + " 个应用");
	}

	// ── 壁纸 ──

	public String getWallpaper() {
		return prefs.getString(KEY_WALLPAPER, "default");
	}

	public void setWallpaper(String wallpaperId) {
		prefs.edit().putString(KEY_WALLPAPER, wallpaperId).apply();
		NokiaLog.i("SettingsStorage", "setWallpaper: " + wallpaperId);
	}

	// ── 左右软键 ──

	public String getSoftLeftAction() {
		return prefs.getString(KEY_SOFT_LEFT_ACTION, "album");
	}

	public void setSoftLeftAction(String action) {
		prefs.edit().putString(KEY_SOFT_LEFT_ACTION, action).apply();
		NokiaLog.i("SettingsStorage", "setSoftLeftAction: " + action);
	}

	public String getSoftRightAction() {
		return prefs.getString(KEY_SOFT_RIGHT_ACTION, "contacts");
	}

	public void setSoftRightAction(String action) {
		prefs.edit().putString(KEY_SOFT_RIGHT_ACTION, action).apply();
		NokiaLog.i("SettingsStorage", "setSoftRightAction: " + action);
	}
}
