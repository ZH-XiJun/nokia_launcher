package ru.playsoftware.j2meloader.nokia;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 桌面组件的 SharedPreferences 持久化。
 * 组件列表上限 {@link #MAX_COUNT} 项，以 JSON 数组形式存储。
 */
public class NokiaWidgetStorage {

	private static final String PREFS_NAME = "nokia_desktop_widgets";
	private static final String KEY_WIDGETS = "widget_list";
	public static final int MAX_COUNT = 15;

	private final SharedPreferences prefs;

	public NokiaWidgetStorage(Context context) {
		prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
	}

	/** 读取全部已添加组件，按存储顺序返回。 */
	public List<NokiaWidgetItem> getWidgets() {
		List<NokiaWidgetItem> result = new ArrayList<>();
		String json = prefs.getString(KEY_WIDGETS, null);
		if (json == null || json.isEmpty()) {
			NokiaLog.i("WidgetStorage", "getWidgets: 无已配置组件");
			return result;
		}
		try {
			JSONArray arr = new JSONArray(json);
			for (int i = 0; i < arr.length(); i++) {
				result.add(NokiaWidgetItem.fromJson(arr.getJSONObject(i)));
			}
			NokiaLog.i("WidgetStorage", "getWidgets: 读取 " + result.size() + " 个组件");
		} catch (JSONException e) {
			NokiaLog.e("WidgetStorage", "getWidgets 解析失败", e);
		}
		return result;
	}

	/** 整体保存组件列表（新增/删除/排序共用）。 */
	public void setWidgets(List<NokiaWidgetItem> widgets) {
		JSONArray arr = new JSONArray();
		for (NokiaWidgetItem item : widgets) {
			try {
				arr.put(item.toJson());
			} catch (JSONException e) {
				NokiaLog.e("WidgetStorage", "setWidgets 序列化失败: " + item.label, e);
			}
		}
		prefs.edit().putString(KEY_WIDGETS, arr.toString()).apply();
		NokiaLog.i("WidgetStorage", "setWidgets: 保存 " + widgets.size() + " 个组件");
	}

	/** 追加一个组件；已达上限时拒绝并返回 false。 */
	public boolean addWidget(NokiaWidgetItem item) {
		List<NokiaWidgetItem> list = getWidgets();
		if (list.size() >= MAX_COUNT) {
			NokiaLog.w("WidgetStorage", "addWidget: 已达上限 " + MAX_COUNT + "，拒绝添加 " + item.label);
			return false;
		}
		list.add(item);
		setWidgets(list);
		return true;
	}

	/** 删除指定组件（删除模式「删除已选」使用）。 */
	public void removeWidgets(List<NokiaWidgetItem> toRemove) {
		if (toRemove == null || toRemove.isEmpty()) return;
		List<NokiaWidgetItem> list = getWidgets();
		list.removeAll(toRemove);
		setWidgets(list);
		NokiaLog.i("WidgetStorage", "removeWidgets: 删除 " + toRemove.size() + " 个组件");
	}

	/** 是否已达组件数量上限。 */
	public boolean isFull() {
		return getWidgets().size() >= MAX_COUNT;
	}
}
