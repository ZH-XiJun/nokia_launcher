package ru.playsoftware.j2meloader.nokia;

import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;

/**
 * 诺基亚桌面统一调试日志工具。
 * <p>
 * 所有 nokia 包内的调试输出都走这里，统一 TAG 与格式（[子类] 消息），
 * 并可通过 {@link #setEnabled(boolean)} 全局开关。发布版本可关闭以减少噪音。
 */
public final class NokiaLog {

	private static final String TAG = "NokiaDesktop";
	private static volatile boolean enabled = true;

	private NokiaLog() {
	}

	/** 全局开关。默认开启（调试）。 */
	public static void setEnabled(boolean e) {
		enabled = e;
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void d(String sub, String msg) {
		if (enabled) Log.d(TAG, "[" + sub + "] " + msg);
	}

	public static void i(String sub, String msg) {
		if (enabled) Log.i(TAG, "[" + sub + "] " + msg);
	}

	public static void w(String sub, String msg) {
		if (enabled) Log.w(TAG, "[" + sub + "] " + msg);
	}

	public static void e(String sub, String msg) {
		if (enabled) Log.e(TAG, "[" + sub + "] " + msg);
	}

	public static void e(String sub, String msg, Throwable t) {
		if (enabled) Log.e(TAG, "[" + sub + "] " + msg, t);
	}

	/** 把 keyCode 转成面向用户的中文键名（日志与 UI 通用）。 */
	public static String keyName(int keyCode) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_UP:      return "上";
			case KeyEvent.KEYCODE_DPAD_DOWN:    return "下";
			case KeyEvent.KEYCODE_DPAD_LEFT:    return "左";
			case KeyEvent.KEYCODE_DPAD_RIGHT:   return "右";
			case KeyEvent.KEYCODE_DPAD_CENTER:  return "确认";
			case KeyEvent.KEYCODE_ENTER:        return "确定";
			case KeyEvent.KEYCODE_SPACE:        return "空格";
			case KeyEvent.KEYCODE_BUTTON_A:     return "A";
			case KeyEvent.KEYCODE_SOFT_LEFT:    return "左软键";
			case KeyEvent.KEYCODE_SOFT_RIGHT:   return "右软键";
			case KeyEvent.KEYCODE_MENU:         return "菜单";
			case KeyEvent.KEYCODE_BACK:         return "返回";
			case KeyEvent.KEYCODE_ENDCALL:      return "挂机";
			case KeyEvent.KEYCODE_CALL:         return "通话";
			case KeyEvent.KEYCODE_CAMERA:       return "相机";
			case KeyEvent.KEYCODE_VOLUME_UP:    return "音量加";
			case KeyEvent.KEYCODE_VOLUME_DOWN:  return "音量减";
			case KeyEvent.KEYCODE_POWER:        return "电源";
			case KeyEvent.KEYCODE_HOME:         return "Home";
			case KeyEvent.KEYCODE_UNKNOWN:      return "未绑定";
			default:
				if (Build.VERSION.SDK_INT >= 29) {
					return KeyEvent.keyCodeToString(keyCode);
				}
				return "KEYCODE_" + keyCode;
		}
	}
}
