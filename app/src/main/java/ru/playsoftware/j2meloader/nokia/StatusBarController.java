package ru.playsoftware.j2meloader.nokia;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;

import java.util.List;

import ru.playsoftware.j2meloader.R;

/**
 * 驱动顶部状态栏的系统信息显示：双卡信号强度、WiFi、蓝牙、飞行模式。
 * 电池与时钟由布局/基类负责，这里不处理。
 * <p>
 * 双卡信号通过 SubscriptionManager 取得活动 SIM 列表，为每个 SIM 创建独立的
 * TelephonyManager 并监听其信号强度变化；单卡/无权限时退化为默认 TelephonyManager。
 * WiFi、蓝牙、飞行模式通过广播 + 轮询实时更新。
 */
public class StatusBarController {
	private static final int REQ_PHONE_STATE = 1001;

	private final NokiaBaseActivity activity;
	private ImageView ivSignal1, ivSignal2, ivWifi, ivBluetooth, ivAirplane;
	private LinearLayout sim1Container, sim2Container;
	private TelephonyManager telephonyManager;
	private SubscriptionManager subscriptionManager;
	private WifiManager wifiManager;

	private final SignalListener listener1 = new SignalListener(0);
	private final SignalListener listener2 = new SignalListener(1);

	// 飞行模式：部分机型不会派发 ACTION_AIRPLANE_MODE_CHANGED 广播，
	// 故同时用 ContentObserver 监听 Settings.Global.AIRPLANE_MODE_ON 作为兜底。
	private final ContentObserver airplaneObserver = new ContentObserver(new Handler()) {
		@Override
		public void onChange(boolean selfChange) {
			updateAirplane();
			updateWifi();
			refreshSignals();
		}
	};

	private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
				updateBluetooth();
			} else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
				updateAirplane();
				updateWifi();
				refreshSignals();
			} else if (WifiManager.RSSI_CHANGED_ACTION.equals(action)
					|| WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)
					|| WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
				updateWifi();
			}
		}
	};

	public StatusBarController(NokiaBaseActivity activity) {
		this.activity = activity;
	}

	/** 绑定视图并注册监听。需在 Activity 的 onResume 中调用。 */
	@SuppressLint("MissingPermission")
	public void start() {
		ivSignal1 = activity.findViewById(R.id.ivSignal1);
		ivSignal2 = activity.findViewById(R.id.ivSignal2);
		ivWifi = activity.findViewById(R.id.ivWifi);
		ivBluetooth = activity.findViewById(R.id.ivBluetooth);
		ivAirplane = activity.findViewById(R.id.ivAirplane);
		sim1Container = activity.findViewById(R.id.sim1Container);
		sim2Container = activity.findViewById(R.id.sim2Container);

		telephonyManager = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
		subscriptionManager = (SubscriptionManager) activity.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
		wifiManager = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

		// 双卡信号需要 READ_PHONE_STATE，缺失则请求（缺失时退化为单卡监听）。
		if (Build.VERSION.SDK_INT >= 23
				&& activity.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)
				!= PackageManager.PERMISSION_GRANTED) {
			activity.requestPermissions(
					new String[]{android.Manifest.permission.READ_PHONE_STATE}, REQ_PHONE_STATE);
		}

		registerSignalListeners();
		updateAirplane();
		updateWifi();
		updateBluetooth();

		IntentFilter filter = new IntentFilter();
		filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
		filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
		filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		activity.registerReceiver(stateReceiver, filter);

		// 兜底：监听飞行模式设置变化。
		ContentResolver cr = activity.getContentResolver();
		cr.registerContentObserver(
				Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON), false, airplaneObserver);
	}

	/** 取消监听与广播。需在 Activity 的 onPause 中调用。 */
	public void stop() {
		unregisterSignalListeners();
		try {
			activity.unregisterReceiver(stateReceiver);
		} catch (Exception ignore) {
			// 未注册或已注销，忽略
		}
		try {
			activity.getContentResolver().unregisterContentObserver(airplaneObserver);
		} catch (Exception ignore) {
			// 忽略
		}
	}

	@SuppressLint("MissingPermission")
	private void registerSignalListeners() {
		if (telephonyManager == null) {
			return;
		}
		if (Build.VERSION.SDK_INT >= 22 && subscriptionManager != null) {
			List<SubscriptionInfo> subs = getActiveSubs();
			for (int i = 0; i < subs.size() && i < 2; i++) {
				int subId = subs.get(i).getSubscriptionId();
				TelephonyManager tm = telephonyManager.createForSubscriptionId(subId);
				PhoneStateListener l = (i == 0) ? listener1 : listener2;
				tm.listen(l, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
			}
			// 仅有 1 张卡时，第二张卡图标保持空。
			if (subs.size() <= 1) {
				listener2.setLevel(0);
			}
		} else {
			telephonyManager.listen(listener1, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
		}
		refreshSignals();
	}

	private void unregisterSignalListeners() {
		if (telephonyManager == null) {
			return;
		}
		if (Build.VERSION.SDK_INT >= 22 && subscriptionManager != null) {
			List<SubscriptionInfo> subs = getActiveSubs();
			for (int i = 0; i < subs.size() && i < 2; i++) {
				int subId = subs.get(i).getSubscriptionId();
				TelephonyManager tm = telephonyManager.createForSubscriptionId(subId);
				PhoneStateListener l = (i == 0) ? listener1 : listener2;
				tm.listen(l, PhoneStateListener.LISTEN_NONE);
			}
		} else {
			telephonyManager.listen(listener1, PhoneStateListener.LISTEN_NONE);
		}
	}

	@SuppressLint("MissingPermission")
	private List<SubscriptionInfo> getActiveSubs() {
		try {
			List<SubscriptionInfo> subs = subscriptionManager.getActiveSubscriptionInfoList();
			return subs == null ? java.util.Collections.<SubscriptionInfo>emptyList() : subs;
		} catch (Exception e) {
			return java.util.Collections.emptyList();
		}
	}

	private void refreshSignals() {
		listener1.apply();
		listener2.apply();
	}

	@SuppressLint("MissingPermission")
	private void updateWifi() {
		if (ivWifi == null) {
			return;
		}
		// 飞行模式下用户可手动重新开启 WiFi，此时仍应显示图标。
		boolean enabled = isWifiEnabled();
		boolean connected = isWifiConnected(activity);
		if (!enabled && !connected) {
			ivWifi.setVisibility(View.GONE);
			return;
		}
		ivWifi.setVisibility(View.VISIBLE);
		// 根据真实 RSSI 设置 WiFi 信号等级（0-3），而非始终满格。
		int level = getWifiLevel();
		ivWifi.setImageResource(wifiLevelToDrawable(level));
	}

	private int getWifiLevel() {
		if (wifiManager == null) {
			return 3;
		}
		try {
			android.net.wifi.WifiInfo info = wifiManager.getConnectionInfo();
			if (info == null) {
				return 0;
			}
			int rssi = info.getRssi();
			// calculateSignalLevel(rssi, 4) 返回 0..3
			int lvl = WifiManager.calculateSignalLevel(rssi, 4);
			return Math.max(0, Math.min(3, lvl));
		} catch (Exception e) {
			return 3;
		}
	}

	private void updateBluetooth() {
		if (ivBluetooth == null) {
			return;
		}
		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		boolean on = adapter != null && adapter.isEnabled();
		ivBluetooth.setVisibility(on ? View.VISIBLE : View.GONE);
	}

	private void updateAirplane() {
		boolean airplane = isAirplaneModeOn();
		if (ivAirplane != null) {
			ivAirplane.setVisibility(airplane ? View.VISIBLE : View.GONE);
		}
		// 飞行模式下隐藏信号栏（SIM 图标 + 标号）。
		int sigVis = airplane ? View.GONE : View.VISIBLE;
		if (sim1Container != null) {
			sim1Container.setVisibility(sigVis);
		}
		if (sim2Container != null) {
			sim2Container.setVisibility(sigVis);
		}
	}

	@SuppressLint("MissingPermission")
	private boolean isWifiEnabled() {
		try {
			WifiManager wm = (WifiManager)
					activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
			return wm != null && wm.isWifiEnabled();
		} catch (Exception e) {
			return false;
		}
	}

	@SuppressLint("MissingPermission")
	private boolean isWifiConnected(Context c) {
		android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
				c.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (cm == null) {
			return false;
		}
		if (Build.VERSION.SDK_INT >= 23) {
			android.net.Network network = cm.getActiveNetwork();
			if (network == null) {
				return false;
			}
			android.net.NetworkCapabilities cap = cm.getNetworkCapabilities(network);
			return cap != null && cap.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
		} else {
			android.net.NetworkInfo ni = cm.getNetworkInfo(android.net.ConnectivityManager.TYPE_WIFI);
			return ni != null && ni.isConnected();
		}
	}

	private boolean isAirplaneModeOn() {
		try {
			return Settings.Global.getInt(activity.getContentResolver(),
					Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
		} catch (Exception e) {
			return false;
		}
	}

	private static int asuToLevel(int asu) {
		if (asu <= 0 || asu == 99) {
			return 0;
		}
		if (asu < 8) {
			return 1;
		}
		if (asu < 16) {
			return 2;
		}
		if (asu < 24) {
			return 3;
		}
		return 4;
	}

	@DrawableRes
	private static int signalLevelToDrawable(int level) {
		switch (level) {
			case 1:
				return R.drawable.ic_signal_1;
			case 2:
				return R.drawable.ic_signal_2;
			case 3:
				return R.drawable.ic_signal_3;
			case 4:
				return R.drawable.ic_signal_4;
			default:
				return R.drawable.ic_signal_0;
		}
	}

	@DrawableRes
	private static int wifiLevelToDrawable(int level) {
		switch (level) {
			case 1:
				return R.drawable.ic_wifi_1;
			case 2:
				return R.drawable.ic_wifi_2;
			case 3:
				return R.drawable.ic_wifi_3;
			default:
				return R.drawable.ic_wifi_0;
		}
	}

	/** 每张 SIM 一个监听，按 slot 索引更新对应 ImageView。 */
	private class SignalListener extends PhoneStateListener {
		private final int slot;
		private int level = 0;

		SignalListener(int slot) {
			this.slot = slot;
		}

		void setLevel(int level) {
			this.level = level;
			apply();
		}

		@Override
		public void onSignalStrengthsChanged(SignalStrength signalStrength) {
			super.onSignalStrengthsChanged(signalStrength);
			if (signalStrength != null) {
				if (Build.VERSION.SDK_INT >= 29) {
					level = signalStrength.getLevel();
				} else {
					level = asuToLevel(signalStrength.getGsmSignalStrength());
				}
			}
			apply();
		}

		void apply() {
			ImageView iv = (slot == 0) ? ivSignal1 : ivSignal2;
			if (iv != null) {
				iv.setImageResource(signalLevelToDrawable(level));
			}
		}
	}
}
