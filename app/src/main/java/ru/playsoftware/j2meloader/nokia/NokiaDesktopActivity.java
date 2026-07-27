package ru.playsoftware.j2meloader.nokia;

import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import ru.playsoftware.j2meloader.R;

/**
 * 诺基亚风格界面的单一宿主 Activity。
 * 顶部栏与底部栏（共用布局）保持不动，中间区域在三个碎片之间切换，
 * 因此导航时顶/底栏不会重建，三页顶部栏表现完全一致。
 */
public class NokiaDesktopActivity extends NokiaBaseActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_nokia);
		setupNokiaUi();
		findViewById(R.id.midPanel).setVisibility(View.VISIBLE);

		if (getSupportFragmentManager().findFragmentById(R.id.midPanel) == null) {
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.midPanel, new NokiaDesktopFragment())
					.commit();
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

	private void switchFragment(Fragment fragment) {
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.midPanel, fragment)
				.addToBackStack(null)
				.commit();
	}
}
