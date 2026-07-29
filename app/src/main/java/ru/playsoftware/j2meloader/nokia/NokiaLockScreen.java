package ru.playsoftware.j2meloader.nokia;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * 一键锁屏工具。依赖设备管理员权限（DevicePolicyManager.lockNow）。
 * 未授权时跳转到系统设备管理员激活页，引导用户授权后才能在桌面通过绑定按键锁屏。
 */
public final class NokiaLockScreen {

	private NokiaLockScreen() {
	}

	/** 执行锁屏：已激活设备管理员则直接 lockNow；否则跳转激活页引导授权。 */
	public static void lock(Context context) {
		if (context == null) return;
		NokiaLog.i("LockScreen", "锁屏请求");
		DevicePolicyManager dpm =
				(DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
		ComponentName admin = new ComponentName(context, NokiaDeviceAdminReceiver.class);
		if (dpm != null && dpm.isAdminActive(admin)) {
			NokiaLog.i("LockScreen", "设备管理员已激活，执行 lockNow 锁屏");
			dpm.lockNow();
		} else {
			NokiaLog.i("LockScreen", "设备管理员未激活，跳转系统激活页");
			Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
			intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
			intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
					"启用后可通过桌面「锁屏」一键锁屏息屏");
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			try {
				context.startActivity(intent);
			} catch (Exception e) {
				NokiaLog.e("LockScreen", "跳转设备管理员激活页失败", e);
			}
		}
	}
}
