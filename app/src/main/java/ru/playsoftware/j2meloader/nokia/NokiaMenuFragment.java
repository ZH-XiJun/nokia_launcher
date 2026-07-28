package ru.playsoftware.j2meloader.nokia;

import android.os.Bundle;

import ru.playsoftware.j2meloader.R;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * 功能表（应用网格）中间内容碎片。
 * 3×3 网格 + 底部"按键绑定"入口，支持方向键导航。
 */
public class NokiaMenuFragment extends Fragment implements NokiaFocusHost {

	private View[] focusTargets = new View[10];
	private int focusIndex = 0;

	/** 3x3 网格每行 3 列 */
	private static final int COLS = 3;
	/** 3x3 网格共 3 行 */
	private static final int GRID_ROWS = 3;
	/** 按键绑定单独放在第4行 */
	private static final int IDX_KEY_BIND = 9;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
							 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_nokia_menu, container, false);
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
			title.setText("");
		}
		TextView bc = host.findViewById(R.id.bottomCenter);
		if (bc != null) {
			bc.setText("");
		}
		TextView bl = host.findViewById(R.id.bottomLeft);
		if (bl != null) {
			bl.setText("选择");
		}
		TextView br = host.findViewById(R.id.bottomRight);
		if (br != null) {
			br.setText("退出");
			br.setOnClickListener(v -> host.exitCurrent());
		}

		// ----- 初始化焦点目标列表 (3x3 网格 + 按键绑定) -----
		// Row 1: [0]信息  [1]联系人  [2]通话记录
		focusTargets[0] = view.findViewById(R.id.cellMsg);
		focusTargets[1] = view.findViewById(R.id.cellContact);
		focusTargets[2] = view.findViewById(R.id.cellCallLog);
		// Row 2: [3]日历  [4]相册  [5]影音天地
		focusTargets[3] = view.findViewById(R.id.cellCalendar);
		focusTargets[4] = view.findViewById(R.id.cellGallery);
		focusTargets[5] = view.findViewById(R.id.cellMedia);
		// Row 3: [6]相机  [7]一键通  [8]百宝箱
		focusTargets[6] = view.findViewById(R.id.cellCamera);
		focusTargets[7] = view.findViewById(R.id.cellPtt);
		focusTargets[8] = view.findViewById(R.id.cellBox);
		// Row 4: [9]按键绑定
		focusTargets[9] = view.findViewById(R.id.cellKeyBind);

		// 绑定点击事件
		for (int i = 0; i < focusTargets.length; i++) {
			final int idx = i;
			if (focusTargets[i] != null) {
				focusTargets[i].setOnClickListener(v -> onCellClicked(idx));
			}
		}

		setFocusIndex(4); // 默认选中"相册"（第2行第2列）
	}

	// ---- NokiaFocusHost 接口 ----

	@Override
	public boolean onDirection(int direction) {
		if (focusIndex < 0) {
			setFocusIndex(0);
			return true;
		}

		// 按键绑定（索引9）单独处理
		if (focusIndex == IDX_KEY_BIND) {
			switch (direction) {
				case NokiaKeyBinding.ACTION_UP:
					setFocusIndex(7); // 一键通（第3行中间）
					return true;
				case NokiaKeyBinding.ACTION_DOWN:
					setFocusIndex(1); // 联系人（第1行中间）
					return true;
				case NokiaKeyBinding.ACTION_LEFT:
					setFocusIndex(8); // 百宝箱
					return true;
				case NokiaKeyBinding.ACTION_RIGHT:
					setFocusIndex(6); // 相机
					return true;
				default:
					return false;
			}
		}

		// 3x3 网格通用导航
		int row = focusIndex / COLS;
		int col = focusIndex % COLS;

		switch (direction) {
			case NokiaKeyBinding.ACTION_UP: {
				if (row == 0) {
					// 第一行往上 → 跳到按键绑定
					setFocusIndex(IDX_KEY_BIND);
				} else {
					setFocusIndex(focusIndex - COLS);
				}
				return true;
			}
			case NokiaKeyBinding.ACTION_DOWN: {
				if (row == GRID_ROWS - 1) {
					// 第3行往下 → 跳到按键绑定
					setFocusIndex(IDX_KEY_BIND);
				} else {
					setFocusIndex(focusIndex + COLS);
				}
				return true;
			}
			case NokiaKeyBinding.ACTION_LEFT: {
				if (col == 0) {
					setFocusIndex(focusIndex + (COLS - 1));
				} else {
					setFocusIndex(focusIndex - 1);
				}
				return true;
			}
			case NokiaKeyBinding.ACTION_RIGHT: {
				if (col == COLS - 1) {
					setFocusIndex(focusIndex - (COLS - 1));
				} else {
					setFocusIndex(focusIndex + 1);
				}
				return true;
			}
			default:
				return false;
		}
	}

	@Override
	public boolean onSelect() {
		if (focusIndex < 0 || focusIndex >= focusTargets.length) return false;
		View v = focusTargets[focusIndex];
		if (v != null) {
			v.performClick();
		}
		return true;
	}

	@Override
	public boolean onSoftLeft() {
		return onSelect(); // 左软键 = "选择" → 等同确认键
	}

	@Override
	public boolean onSoftRight() {
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		host.exitCurrent();
		return true;
	}

	@Override
	public boolean onBack() {
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		host.exitCurrent();
		return true;
	}

	// ---- 内部逻辑 ----

	private void setFocusIndex(int index) {
		if (index < 0 || index >= focusTargets.length) return;
		if (focusIndex >= 0 && focusIndex < focusTargets.length && focusTargets[focusIndex] != null) {
			focusTargets[focusIndex].setBackgroundResource(0);
		}
		focusIndex = index;
		if (focusTargets[focusIndex] != null) {
			focusTargets[focusIndex].setBackgroundResource(R.drawable.bg_nokia_selected_dark);
		}
	}

	private void onCellClicked(int index) {
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		switch (index) {
			case 0: // 信息
				openSystemApp(android.content.Intent.ACTION_MAIN, android.content.Intent.CATEGORY_APP_MESSAGING);
				break;
			case 1: // 联系人
				openSystemApp(android.content.Intent.ACTION_VIEW, android.content.Intent.CATEGORY_DEFAULT);
				break;
			case 2: // 通话记录
				try {
					startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
							android.provider.CallLog.Calls.CONTENT_URI));
				} catch (Exception e) {
					openDialer();
				}
				break;
			case 3: // 日历
				openSystemApp(android.content.Intent.ACTION_MAIN, android.content.Intent.CATEGORY_APP_CALENDAR);
				break;
			case 4: // 相册
				openSystemApp(android.content.Intent.ACTION_MAIN, android.content.Intent.CATEGORY_APP_GALLERY);
				break;
			case 5: // 影音天地
				openSystemApp(android.content.Intent.ACTION_MAIN, android.content.Intent.CATEGORY_APP_MUSIC);
				break;
			case 6: // 相机
				try {
					startActivity(new android.content.Intent("android.media.action.STILL_IMAGE_CAMERA"));
				} catch (Exception e) {
					try {
						startActivity(new android.content.Intent("android.media.action.IMAGE_CAPTURE"));
					} catch (Exception ignored) {}
				}
				break;
			case 7: // 一键通
				break;
			case 8: // 百宝箱
				host.openBox();
				break;
			case 9: // 按键绑定
				openKeyBindSettings();
				break;
		}
	}

	private void openDialer() {
		try {
			startActivity(new android.content.Intent(android.content.Intent.ACTION_DIAL));
		} catch (Exception ignored) {}
	}

	private void openSystemApp(String action, String category) {
		try {
			android.content.Intent intent = new android.content.Intent(action);
			intent.addCategory(category);
			startActivity(intent);
		} catch (Exception ignored) {}
	}

	private void openKeyBindSettings() {
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		host.openFragment(new NokiaKeyBindFragment());
	}
}
