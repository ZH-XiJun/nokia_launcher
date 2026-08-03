package ru.playsoftware.j2meloader.nokia;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import ru.playsoftware.j2meloader.R;

/**
 * 桌面顶部快捷开关栏控制器。
 * 仅保留普通应用可稳定实现的开关，避免跳设置页的假开关：
 * <ul>
 *   <li><b>WiFi</b>（index 0）：Android 9 及以下直接 {@code setWifiEnabled} 切换；
 *       Android 10+（API 29+）该方法废弃、普通应用无效，降级打开 WiFi 设置页。</li>
 *   <li><b>锁屏</b>（index 1）：动作型开关。设备管理员已激活时执行 {@code lockNow()} 一键锁屏；
 *       未激活时跳转系统激活页引导授权。图标亮 = 管理员已激活（可锁屏）。</li>
 *   <li><b>灯光/手电筒</b>（index 2）：通过 {@code CameraManager.setTorchMode} 切换闪光灯。
 *       需 CAMERA 运行时权限，未授权时发起权限请求，授权后自动重试。</li>
 * </ul>
 * 图标开关的"亮/暗"由 {@link #isEnabled(int)} 决定，宿主据此设置图片透明度。
 */
@android.annotation.SuppressLint("NewApi")
public class TopQuickToggleController {

	public static final int TOGGLE_WIFI = 0;
	public static final int TOGGLE_LOCK = 1;
	public static final int TOGGLE_TORCH = 2;

	public static final int TOGGLE_COUNT = 3;

	/** CAMERA 权限请求码（Android 6+ 切换手电筒需该运行时权限） */
	private static final int REQ_CAMERA = 2002;

	private final NokiaBaseActivity activity;
	private WifiManager wifiManager;
	private CameraManager cameraManager;

	/** 待授权后继续切换的开关索引；-1 表示无待切换项。 */
	private int pendingToggle = -1;

	public TopQuickToggleController(@NonNull NokiaBaseActivity activity) {
		this.activity = activity;
		Context ctx = activity.getApplicationContext();
		wifiManager = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
		if (Build.VERSION.SDK_INT >= 21) {
			cameraManager = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
		}
	}

	/** 开关图标资源（同一图标，亮/暗由透明度体现）。 */
	@DrawableRes
	public static int getIcon(int index) {
		switch (index) {
			case TOGGLE_WIFI:  return R.drawable.ic_nokia_wifi;
			case TOGGLE_LOCK:  return R.drawable.ic_nokia_lock;
			case TOGGLE_TORCH: return R.drawable.ic_nokia_torch;
			default:           return R.drawable.ic_nokia_wifi;
		}
	}

	/** 开关名称（当前不含文字，仅图标；此处预留备用）。 */
	public static String getName(int index) {
		switch (index) {
			case TOGGLE_WIFI:  return "WiFi";
			case TOGGLE_LOCK:  return "锁屏";
			case TOGGLE_TORCH: return "灯光";
			default:           return "";
		}
	}

	/** 读取开关当前是否开启。 */
	public boolean isEnabled(int index) {
		switch (index) {
			case TOGGLE_WIFI:
				return isWifiEnabled();
			case TOGGLE_LOCK:
				return isLockAdminActive();
			case TOGGLE_TORCH:
				return isTorchOn();
			default:
				return false;
		}
	}

	/**
	 * 切换开关。
	 *
	 * @return true 表示已处理（锁屏动作 / 直接切换成功）；false 表示打开设置页或发起权限请求。
	 */
	public boolean toggle(int index) {
		NokiaLog.i("TopToggle", "切换开关 index=" + index + " " + getName(index));
		switch (index) {
			case TOGGLE_WIFI:
				return toggleWifi();
			case TOGGLE_LOCK:
				NokiaLockScreen.lock(activity);
				return true;
			case TOGGLE_TORCH:
				return toggleTorch();
			default:
				return false;
		}
	}

	/**
	 * 运行时权限授权结果回调（由宿主 Activity 转发）。
	 * CAMERA 权限授权成功后自动重试之前未完成的手电筒切换。
	 */
	public void onRequestPermissionsResult(int requestCode, int[] grantResults) {
		if (requestCode == REQ_CAMERA && pendingToggle >= 0) {
			int idx = pendingToggle;
			pendingToggle = -1;
			if (grantResults.length > 0
					&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				NokiaLog.i("TopToggle", "CAMERA 已授权，重试切换 " + getName(idx));
				toggle(idx);
			} else {
				NokiaLog.i("TopToggle", "CAMERA 授权被拒绝");
			}
		}
	}

	// ---- WiFi ----

	private boolean isWifiEnabled() {
		try {
			return wifiManager != null && wifiManager.isWifiEnabled();
		} catch (Exception e) {
			return false;
		}
	}

	private boolean toggleWifi() {
		try {
			if (wifiManager == null) return false;
			boolean next = !wifiManager.isWifiEnabled();
			boolean ok = wifiManager.setWifiEnabled(next);
			NokiaLog.i("TopToggle", "setWifiEnabled(" + next + ") -> " + ok
					+ " (sdk=" + Build.VERSION.SDK_INT + ")");
			if (ok) return true;
			NokiaLog.i("TopToggle", "setWifiEnabled 返回 false，打开 WiFi 设置页");
			openSettings(Settings.ACTION_WIFI_SETTINGS);
			return false;
		} catch (Exception e) {
			NokiaLog.e("TopToggle", "WiFi 切换失败，打开设置页", e);
			openSettings(Settings.ACTION_WIFI_SETTINGS);
			return false;
		}
	}

	// ---- 锁屏 ----

	private boolean isLockAdminActive() {
		try {
			android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager)
					activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
			android.content.ComponentName admin = new android.content.ComponentName(
					activity, NokiaDeviceAdminReceiver.class);
			return dpm != null && dpm.isAdminActive(admin);
		} catch (Exception e) {
			return false;
		}
	}

	// ---- 手电筒（灯光） ----

	private boolean isTorchOn() {
		if (cameraManager == null || Build.VERSION.SDK_INT < 23) return false;
		try {
			// CameraManager 系列方法为 API 21+，getTorchMode 为 API 23+，
			// 统一用反射调用，规避编译期 API 检查与低版本设备类加载问题。
			String id = getTorchCameraId();
			if (id == null) return false;
			java.lang.reflect.Method m = CameraManager.class.getMethod("getTorchMode", String.class);
			return (Boolean) m.invoke(cameraManager, id);
		} catch (Exception e) {
			return false;
		}
	}

	/** 反射获取第一个摄像头 id（API 21+）。失败返回 null。 */
	private String getTorchCameraId() {
		try {
			java.lang.reflect.Method m = CameraManager.class.getMethod("getCameraIdList");
			String[] ids = (String[]) m.invoke(cameraManager);
			return (ids != null && ids.length > 0) ? ids[0] : null;
		} catch (Exception e) {
			return null;
		}
	}

	private boolean toggleTorch() {
		// setTorchMode 需 API 23+（手电筒功能最低要求）
		if (cameraManager == null || Build.VERSION.SDK_INT < 23) {
			openSettings(Settings.ACTION_WIRELESS_SETTINGS);
			return false;
		}
		// Android 6+ 使用手电筒需要 CAMERA 运行时权限
		if (Build.VERSION.SDK_INT >= 23
				&& activity.checkSelfPermission(Manifest.permission.CAMERA)
				!= PackageManager.PERMISSION_GRANTED) {
			NokiaLog.i("TopToggle", "手电筒切换需 CAMERA 权限，发起请求");
			pendingToggle = TOGGLE_TORCH;
			activity.requestPermissions(
					new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
			return false;
		}
		try {
			String id = getTorchCameraId();
			if (id == null) return false;
			boolean next = !isTorchOn();
			// setTorchMode 为 API 23 方法，用反射调用规避编译期 API 检查与低版本类加载
			java.lang.reflect.Method m = CameraManager.class.getMethod(
					"setTorchMode", String.class, boolean.class);
			m.invoke(cameraManager, id, next);
			NokiaLog.i("TopToggle", "setTorchMode(" + next + ")");
			return true;
		} catch (Exception e) {
			NokiaLog.e("TopToggle", "手电筒切换失败", e);
			return false;
		}
	}

	// ---- 通用 ----

	private void openSettings(String action) {
		try {
			Intent intent = new Intent(action);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			activity.startActivity(intent);
		} catch (Exception e) {
			NokiaLog.e("TopToggle", "打开系统设置页失败 action=" + action, e);
		}
	}
}
