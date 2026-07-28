package ru.playsoftware.j2meloader.nokia;

import android.content.Context;
import android.content.SharedPreferences;

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

	public NokiaSettingsStorage(Context context) {
		prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
	}

	// ── 快捷栏应用 ──

	/** 获取已选择的快捷栏应用列表 */
	public List<ShortcutApp> getShortcutApps() {
		List<ShortcutApp> result = new ArrayList<>();
		String json = prefs.getString(KEY_SHORTCUT_APPS, null);
		if (json == null || json.isEmpty()) {
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
