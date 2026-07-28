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

	/** 兼容 API &lt; 29 的 keyCode 转可读名。 */
	public static String keyName(int keyCode) {
		if (Build.VERSION.SDK_INT >= 29) {
			return KeyEvent.keyCodeToString(keyCode);
		}
		return "KEYCODE_" + keyCode;
	}
}
