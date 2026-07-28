package ru.playsoftware.j2meloader.nokia;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import ru.playsoftware.j2meloader.R;

/**
 * 桌面设置主菜单。纵向列表展示各项设置入口。
 * 支持方向键导航（实现 NokiaFocusHost），风格延续 S60 菜单。
 */
public class NokiaDesktopSettingsFragment extends Fragment implements NokiaFocusHost {

	private static final int[] ITEM_ICONS = {
			R.drawable.ic_nokia_settings,   // 快捷栏设置
			R.drawable.s60_gallery,          // 壁纸设置
			R.drawable.s60_settings_alt,     // 桌面组件设置
			R.drawable.s60_settings,         // 按键绑定
	};

	private static final String[] ITEM_NAMES = {
			"顶部快捷栏设置",
			"壁纸设置",
			"桌面组件设置",
			"按键绑定",
	};

	private View[] itemViews;
	private int focusIndex = -1;
	private View selectedView = null;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
							 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_nokia_desktop_settings, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		host.scaleMidContent(view, true);

		View wall = host.findViewById(R.id.wallpaper);
		if (wall != null) {
			wall.setBackgroundResource(R.drawable.bg_nokia_menu);
		}
		TextView title = host.findViewById(R.id.topTitle);
		if (title != null) {
			title.setText("桌面设置");
		}
		host.setBottomBar("选择", null, "返回");

		// 构建设置列表
		LinearLayout listLayout = view.findViewById(R.id.settingsList);
		if (listLayout == null) return;

		itemViews = new View[ITEM_NAMES.length];
		for (int i = 0; i < ITEM_NAMES.length; i++) {
			LinearLayout row = new LinearLayout(requireContext());
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));
			row.setPadding(dp(10), dp(4), dp(10), dp(4));
			row.setClickable(true);
			row.setFocusable(true);

			// 图标
			ImageView ivIcon = new ImageView(requireContext());
			ivIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(22)));
			try {
				ivIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), ITEM_ICONS[i]));
			} catch (Exception ignored) {}
			row.addView(ivIcon);

			// 间距
			row.addView(spaceView(dp(8), 1));

			// 名称
			TextView tvName = new TextView(requireContext());
			tvName.setLayoutParams(new LinearLayout.LayoutParams(
					0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
			tvName.setText(ITEM_NAMES[i]);
			tvName.setTextColor(0xFFFFFFFF);
			tvName.setTextSize(12);
			row.addView(tvName);

			// 箭头
			TextView tvArrow = new TextView(requireContext());
			tvArrow.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
			tvArrow.setText(">");
			tvArrow.setTextColor(0xFFAAAAAA);
			tvArrow.setTextSize(14);
			row.addView(tvArrow);

			final int index = i;
			row.setOnClickListener(v -> {
				setFocusIndex(index);
				onSelect();
			});

			listLayout.addView(row);
			itemViews[i] = row;
		}

		// 默认选中第一项
		setFocusIndex(0);
		NokiaLog.i("DesktopSettings", "桌面设置菜单初始化完成");
	}

	// ---- NokiaFocusHost ----

	@Override
	public boolean onDirection(int direction) {
		int count = itemViews != null ? itemViews.length : 0;
		if (count == 0) return false;
		if (focusIndex < 0) {
			setFocusIndex(0);
			return true;
		}
		switch (direction) {
			case NokiaKeyBinding.ACTION_UP:
				if (focusIndex > 0) setFocusIndex(focusIndex - 1);
				return true;
			case NokiaKeyBinding.ACTION_DOWN:
				if (focusIndex < count - 1) setFocusIndex(focusIndex + 1);
				return true;
			case NokiaKeyBinding.ACTION_LEFT:
			case NokiaKeyBinding.ACTION_RIGHT:
				return true; // 列表项不响应左右
			default:
				return false;
		}
	}

	@Override
	public boolean onSelect() {
		if (focusIndex < 0) return false;
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		switch (focusIndex) {
			case 0:
				NokiaLog.i("DesktopSettings", "进入快捷栏设置");
				host.openFragment(new NokiaShortcutSettingsFragment());
				return true;
			case 1:
				NokiaLog.i("DesktopSettings", "壁纸设置（待实现）");
				// TODO: 壁纸设置
				return true;
			case 2:
				NokiaLog.i("DesktopSettings", "桌面组件设置（待实现）");
				// TODO: 桌面组件设置
				return true;
			case 3:
				NokiaLog.i("DesktopSettings", "按键绑定");
				host.openFragment(new NokiaKeyBindFragment());
				return true;
			default:
				return false;
		}
	}

	@Override
	public boolean onSoftLeft() {
		return onSelect();
	}

	@Override
	public boolean onSoftRight() {
		((NokiaDesktopActivity) requireActivity()).exitCurrent();
		return true;
	}

	@Override
	public boolean onBack() {
		((NokiaDesktopActivity) requireActivity()).exitCurrent();
		return true;
	}

	// ---- 焦点管理 ----

	private void setFocusIndex(int index) {
		if (itemViews == null || index < 0 || index >= itemViews.length) return;
		clearFocusBackground();
		focusIndex = index;
		applyFocusBackground();
	}

	private void clearFocusBackground() {
		if (selectedView != null) {
			selectedView.setBackgroundResource(0);
			selectedView = null;
		}
	}

	private void applyFocusBackground() {
		if (focusIndex >= 0 && focusIndex < itemViews.length && itemViews[focusIndex] != null) {
			itemViews[focusIndex].setBackgroundResource(R.drawable.bg_nokia_selected_dark);
			selectedView = itemViews[focusIndex];
		}
	}

	private int dp(int v) {
		return (int) (v * getResources().getDisplayMetrics().density);
	}

	private View spaceView(int w, int h) {
		View v = new View(requireContext());
		v.setLayoutParams(new LinearLayout.LayoutParams(w, h));
		return v;
	}
}
