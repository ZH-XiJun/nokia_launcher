package ru.playsoftware.j2meloader.nokia;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/**
 * 功能表中的一项。可以是真实安卓应用（TYPE_APP），也可以是特殊入口
 * （TYPE_BOX 百宝箱 / TYPE_KEYBIND 按键绑定）。
 */
public class NokiaAppItem {

	public static final int TYPE_APP = 0;
	public static final int TYPE_BOX = 1;
	public static final int TYPE_KEYBIND = 2;

	/** 类型：TYPE_APP / TYPE_BOX / TYPE_KEYBIND */
	public final int type;
	/** 显示名称 */
	public final String label;
	/** 图标 */
	public final Drawable icon;
	/** 启动该应用的 Intent（特殊入口为 null） */
	public final Intent launchIntent;

	public NokiaAppItem(int type, String label, Drawable icon, Intent launchIntent) {
		this.type = type;
		this.label = label;
		this.icon = icon;
		this.launchIntent = launchIntent;
	}
}
