package ru.playsoftware.j2meloader.nokia;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import ru.playsoftware.j2meloader.R;

/**
 * 诺基亚风格界面的单一宿主 Activity。
 * 顶部栏与底部栏（共用布局）保持不动，中间区域在三个碎片之间切换，
 * 因此导航时顶/底栏不会重建，三页顶部栏表现完全一致。
 * <p>
 * 作为系统桌面 Launcher（intent-filter 含 HOME/DEFAULT），
 * 每次从其他应用按 Home 键返回时都会触发 onNewIntent()，
 * 此时应清除返回栈并回到桌面待机屏。
 */
public class NokiaDesktopActivity extends NokiaBaseActivity {

	private static final String ACTION_HOME = Intent.ACTION_MAIN;
	private static final String CATEGORY_HOME = Intent.CATEGORY_HOME;

	private StatusBarController statusBarController;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_nokia);
		setupNokiaUi();
		findViewById(R.id.midPanel).setVisibility(View.VISIBLE);

		statusBarController = new StatusBarController(this);

		if (getSupportFragmentManager().findFragmentById(R.id.midPanel) == null) {
			loadDesktopFragment();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (statusBarController != null) {
			statusBarController.start();
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
		// 运行时授予 READ_PHONE_STATE 后，重新注册双卡信号监听。
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
		// 当从其他应用按 Home 键返回时，系统会发送 HOME intent 到此 Activity（singleTask）。
		// 此时应回到桌面待机屏，清除所有功能表/百宝箱的返回栈。
		if (isHomeIntent(intent)) {
			goHome();
		}
	}

	/** 跳到功能表。 */
	public void openMenu() {
		switchFragment(new NokiaMenuFragment());
	}

	/** 跳到百宝箱。 */
	public void openBox() {
		switchFragment(new NokiaBoxFragment());
	}

	/** 退出当前页：有返回栈则回退，否则关闭 Activity。 */
	public void exitCurrent() {
		FragmentManager fm = getSupportFragmentManager();
		if (fm.getBackStackEntryCount() > 0) {
			fm.popBackStack();
		} else {
			finish();
		}
	}

	/**
	 * 回到桌面待机屏：清除所有 Fragment 返回栈，
	 * 将中间面板替换为 NokiaDesktopFragment。
	 */
	private void goHome() {
		FragmentManager fm = getSupportFragmentManager();
		// 清除所有返回栈条目
		fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
		// 用桌面 Fragment 替换当前内容（不加 back stack，桌面是最底层）
		fm.beginTransaction()
				.replace(R.id.midPanel, new NokiaDesktopFragment())
				.commit();
	}

	/**
	 * 判断当前 intent 是否为系统 HOME intent（用户按 Home 键返回桌面）。
	 */
	private boolean isHomeIntent(Intent intent) {
		if (intent == null) {
			return false;
		}
		String action = intent.getAction();
		if (action == null) {
			return false;
		}
		if (!action.equals(ACTION_HOME)) {
			return false;
		}
		return intent.getCategories() != null
				&& intent.getCategories().contains(CATEGORY_HOME);
	}

	private void loadDesktopFragment() {
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.midPanel, new NokiaDesktopFragment())
				.commit();
	}

	private void switchFragment(Fragment fragment) {
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.midPanel, fragment)
				.addToBackStack(null)
				.commit();
	}
}
