package ru.playsoftware.j2meloader.nokia;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.nokia.NokiaGlobalProfile;

/**
 * 诺基亚风格界面的单一宿主 Activity。
 * 顶部栏与底部栏（共用布局）保持不动，中间区域在碎片之间切换。
 * <p>
 * 作为系统桌面 Launcher（intent-filter 含 HOME/DEFAULT），
 * 每次从其他应用按 Home 键返回时都会触发 onNewIntent()，
 * 此时应清除返回栈并回到桌面待机屏。
 */
public class NokiaDesktopActivity extends NokiaBaseActivity {

	private static final String ACTION_HOME = Intent.ACTION_MAIN;
	private static final String CATEGORY_HOME = Intent.CATEGORY_HOME;
	private StatusBarController statusBarController;
	private NokiaKeyBinding keyBinding;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_nokia);
		setupNokiaUi();
		findViewById(R.id.midPanel).setVisibility(View.VISIBLE);

		// 底部软键触摸点击：等效于对应物理软键（修复「桌面设置」等页触摸返回无效）
		bindBottomBarTouch();

		statusBarController = new StatusBarController(this);
		keyBinding = new NokiaKeyBinding(this);

		// 确保全局 JAR 设置 profile 存在并设为默认
		NokiaGlobalProfile.ensureGlobalProfile(this);

		// 首次启动：若按键绑定向导未完成，则进入向导（清数据后 isWizardDone 复位会再次弹出）
		Fragment existing = getSupportFragmentManager().findFragmentById(R.id.midPanel);
		if (existing == null) {
			if (!keyBinding.isWizardDone()) {
				NokiaLog.i("Desktop", "首次启动：进入按键绑定向导");
				loadWizardFragment();
			} else {
				loadDesktopFragment();
			}
		}
	}

	/** 重新从 SharedPreferences 加载按键绑定到内存（向导/绑定界面完成后调用，确保立即生效）。 */
	public void reloadKeyBindings() {
		if (keyBinding != null) {
			keyBinding.reload();
			NokiaLog.i("Desktop", "reloadKeyBindings 完成");
		}
	}

	// ---- 生命周期 ----

	@Override
	protected void onResume() {
		super.onResume();
		// 桌面始终竖屏：从横屏游戏返回时必须强制旋转回竖屏，
		// 否则系统会沿用游戏的横屏方向导致桌面横向显示。
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		// 从按键绑定设置返回后，重新加载最新绑定，避免 Activity 缓存旧映射。
		if (keyBinding != null) {
			keyBinding.reload();
		}
		if (statusBarController != null) {
			statusBarController.start();
		}
	}


	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			// onResume 时窗口尚未完全就绪，部分 ROM 会忽略 setRequestedOrientation，
			// 改用窗口获得焦点的时机再次强制竖屏，作为可靠兜底。
			int cur = getResources().getConfiguration().orientation;
			NokiaLog.i("Desktop", "onWindowFocusChanged hasFocus=true, 当前方向="
					+ (cur == android.content.res.Configuration.ORIENTATION_LANDSCAPE ? "LANDSCAPE" : "PORTRAIT"));
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (statusBarController != null) {
			statusBarController.stop();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (statusBarController != null
				&& requestCode == 1001
				&& grantResults.length > 0
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			statusBarController.onPermissionGranted();
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		if (isHomeIntent(intent)) {
			NokiaLog.i("Desktop", "收到 HOME intent，回到桌面待机");
			goHome();
		} else {
			NokiaLog.d("Desktop", "onNewIntent 非 HOME intent，忽略");
		}
	}

	// ---- 物理按键分发（核心） ----

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (event.getAction() != KeyEvent.ACTION_DOWN) {
			return super.dispatchKeyEvent(event);
		}

		NokiaLog.d("Desktop", "dispatchKeyEvent 收到按下 " + NokiaLog.keyName(event.getKeyCode()));

		// 每次按键前重新加载绑定，确保从按键绑定设置返回后的修改立即生效
		if (keyBinding != null) {
			keyBinding.reload();
		}

		// 如果当前 Fragment 处于录制态（按键绑定设置 / 首次启动向导），
		// 优先把任意物理键直接喂给它捕获，再走 resolveAction 分发。
		Fragment curForRec = getSupportFragmentManager().findFragmentById(R.id.midPanel);
		if (curForRec instanceof NokiaKeyRecorder
				&& ((NokiaKeyRecorder) curForRec).isRecording()) {
			NokiaLog.i("Desktop", "录制态捕获按键 " + NokiaLog.keyName(event.getKeyCode()));
			((NokiaKeyRecorder) curForRec).onKeyRecorded(event.getKeyCode());
			return true;
		}

		int action = keyBinding.resolveAction(event);

		if (action < 0) {
			// 返回键未绑定时兜底为导航返回（非桌面 Fragment），否则交给系统。
			if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
				Fragment backHost = getSupportFragmentManager().findFragmentById(R.id.midPanel);
				if (backHost instanceof NokiaFocusHost
						&& !(backHost instanceof NokiaDesktopFragment)) {
					NokiaLog.d("Desktop", "未绑定返回键 -> host.onBack()");
					((NokiaFocusHost) backHost).onBack();
					return true;
				}
			}
			// 未绑定的按键：允许系统继续处理（如音量键仍然调整音量）
			NokiaLog.d("Desktop", "未绑定的按键 " + NokiaLog.keyName(event.getKeyCode())
					+ "，交给系统处理");
			return super.dispatchKeyEvent(event);
		}

		NokiaLog.d("Desktop", "解析动作 " + NokiaKeyBinding.getActionName(action)
				+ "(" + action + ")");

		// 锁屏动作：仅在桌面待机屏生效，按下所绑定的按键（默认挂机键 ENDCALL）即锁屏。
		// 挂机键在无通话时通常能送达前台 Activity；若某些 ROM 拦截，可用其它按键重新绑定。
		if (action == NokiaKeyBinding.ACTION_LOCK_SCREEN) {
			Fragment lockHost = getSupportFragmentManager().findFragmentById(R.id.midPanel);
			if (lockHost instanceof NokiaDesktopFragment) {
				NokiaLog.i("Desktop", "锁屏动作触发（桌面）：执行锁屏");
				lockScreen();
				return true;
			}
			NokiaLog.d("Desktop", "锁屏动作当前非桌面，交由系统处理");
			return super.dispatchKeyEvent(event);
		}

		// 底部软键按下视觉反馈（触摸点击不经过此处，由底部栏点击监听处理）
		flashBottomBar(action);

		// 将动作分发给当前中间面板 Fragment；被消费则拦截
		if (dispatchActionToHost(action)) {
			NokiaLog.d("Desktop", "动作 " + NokiaKeyBinding.getActionName(action)
					+ " 已被当前 Fragment 消费");
			return true;
		}

		NokiaLog.d("Desktop", "dispatchKeyEvent 未消费 " + NokiaLog.keyName(event.getKeyCode())
				+ "，交给系统");
		return super.dispatchKeyEvent(event);
	}

	/**
	 * 把动作分发给当前中间面板 Fragment（NokiaFocusHost）。
	 * 物理按键与底部软键触摸点击共用此入口，保证两套交互行为一致。
	 *
	 * @return 是否被 Fragment 消费
	 */
	private boolean dispatchActionToHost(int action) {
		Fragment current = getSupportFragmentManager().findFragmentById(R.id.midPanel);
		if (!(current instanceof NokiaFocusHost)) {
			NokiaLog.d("Desktop", "dispatchActionToHost: 当前非 FocusHost，忽略 action="
					+ NokiaKeyBinding.getActionName(action));
			return false;
		}
		NokiaFocusHost host = (NokiaFocusHost) current;
		boolean handled;
		switch (action) {
			case NokiaKeyBinding.ACTION_UP:
			case NokiaKeyBinding.ACTION_DOWN:
			case NokiaKeyBinding.ACTION_LEFT:
			case NokiaKeyBinding.ACTION_RIGHT:
				handled = host.onDirection(action);
				break;
			case NokiaKeyBinding.ACTION_SELECT:
				handled = host.onSelect();
				break;
			case NokiaKeyBinding.ACTION_SOFT_LEFT:
				handled = host.onSoftLeft();
				break;
			case NokiaKeyBinding.ACTION_SOFT_RIGHT:
				handled = host.onSoftRight();
				break;
			default:
				handled = false;
		}
		NokiaLog.d("Desktop", "dispatchActionToHost action="
				+ NokiaKeyBinding.getActionName(action) + " handled=" + handled);
		return handled;
	}

	/** 底部软键按下时的视觉反馈（左/确认/右软键），触摸与物理按键共用。 */
	private void flashBottomBar(int action) {
		int id = -1;
		switch (action) {
			case NokiaKeyBinding.ACTION_SOFT_LEFT:
				id = R.id.bottomLeft;
				break;
			case NokiaKeyBinding.ACTION_SOFT_RIGHT:
				id = R.id.bottomRight;
				break;
			case NokiaKeyBinding.ACTION_SELECT:
				id = R.id.bottomCenter;
				break;
			default:
				break;
		}
		if (id <= 0) return;
		View v = findViewById(id);
		if (v != null) {
			v.setPressed(true);
			v.postDelayed(() -> v.setPressed(false), 100);
		}
	}

	/** 为底部三个软键绑定触摸点击：点击等效于对应物理软键（左/确认/右）。 */
	private void bindBottomBarTouch() {
		View left = findViewById(R.id.bottomLeft);
		View center = findViewById(R.id.bottomCenter);
		View right = findViewById(R.id.bottomRight);
		if (left != null) {
			left.setOnClickListener(v -> {
				NokiaLog.i("Desktop", "触摸点击 -> 左软键");
				flashBottomBar(NokiaKeyBinding.ACTION_SOFT_LEFT);
				dispatchActionToHost(NokiaKeyBinding.ACTION_SOFT_LEFT);
			});
		}
		if (center != null) {
			center.setOnClickListener(v -> {
				NokiaLog.i("Desktop", "触摸点击 -> 确认键");
				flashBottomBar(NokiaKeyBinding.ACTION_SELECT);
				dispatchActionToHost(NokiaKeyBinding.ACTION_SELECT);
			});
		}
		if (right != null) {
			right.setOnClickListener(v -> {
				NokiaLog.i("Desktop", "触摸点击 -> 右软键");
				flashBottomBar(NokiaKeyBinding.ACTION_SOFT_RIGHT);
				dispatchActionToHost(NokiaKeyBinding.ACTION_SOFT_RIGHT);
			});
		}
	}

	/** 一键锁屏（委托给 NokiaLockScreen 工具类）。 */
	private void lockScreen() {
		NokiaLockScreen.lock(this);
	}

	private void loadWizardFragment() {
		NokiaLog.i("Desktop", "加载首次启动按键绑定向导 Fragment");
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.midPanel, new NokiaKeyBindWizardFragment())
				.commit();
	}

	// ---- 导航方法 ----

	public void openMenu() {
		NokiaLog.i("Desktop", "导航 -> 功能表");
		switchFragment(new NokiaMenuFragment());
	}

	public void openBox() {
		NokiaLog.i("Desktop", "导航 -> 应用程序");
		switchFragment(new NokiaBoxFragment());
	}

	/** 打开桌面设置界面 */
	public void openDesktopSettings() {
		NokiaLog.i("Desktop", "导航 -> 桌面设置");
		switchFragment(new NokiaDesktopSettingsFragment());
	}

	/** 通用打开一个 Fragment 并加入返回栈。 */
	public void openFragment(Fragment fragment) {
		switchFragment(fragment);
	}

	public void exitCurrent() {
		FragmentManager fm = getSupportFragmentManager();
		if (fm.getBackStackEntryCount() > 0) {
			NokiaLog.i("Desktop", "exitCurrent 出栈返回上一层");
			fm.popBackStack();
		} else {
			NokiaLog.i("Desktop", "exitCurrent 无返回栈，finish()");
			finish();
		}
	}

	// ---- 内部方法 ----

	private void goHome() {
		NokiaLog.i("Desktop", "goHome 清空返回栈并加载桌面");
		FragmentManager fm = getSupportFragmentManager();
		fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
		fm.beginTransaction()
				.replace(R.id.midPanel, new NokiaDesktopFragment())
				.commit();
	}

	private boolean isHomeIntent(Intent intent) {
		if (intent == null) return false;
		String action = intent.getAction();
		if (action == null) return false;
		if (!action.equals(ACTION_HOME)) return false;
		return intent.getCategories() != null
				&& intent.getCategories().contains(CATEGORY_HOME);
	}

	private void loadDesktopFragment() {
		NokiaLog.i("Desktop", "加载初始桌面 Fragment");
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.midPanel, new NokiaDesktopFragment())
				.commit();
	}

	private void switchFragment(Fragment fragment) {
		NokiaLog.i("Desktop", "切换中间面板 -> "
				+ (fragment != null ? fragment.getClass().getSimpleName() : "null"));
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.midPanel, fragment)
				.addToBackStack(null)
				.commit();
	}
}
