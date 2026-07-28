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
 * 桌面待机屏中间内容碎片。
 * 支持方向键在快捷应用栏（5 项）、通知区（3 项）和功能表按钮之间导航。
 */
public class NokiaDesktopFragment extends Fragment implements NokiaFocusHost {

	private View[] focusTargets = new View[9];
	private int focusIndex = -1;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
							 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_nokia_desktop, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		host.scaleMidContent(view, true);

		View wall = host.findViewById(R.id.wallpaper);
		if (wall != null) {
			wall.setBackgroundResource(R.drawable.bg_nokia_desktop);
		}
		TextView title = host.findViewById(R.id.topTitle);
		if (title != null) {
			title.setText("");
		}
		TextView bl = host.findViewById(R.id.bottomLeft);
		if (bl != null) {
			bl.setText("相册");
		}
		TextView bc = host.findViewById(R.id.bottomCenter);
		if (bc != null) {
			bc.setText("功能表");
			bc.setOnClickListener(v -> host.openMenu());
		}
		TextView br = host.findViewById(R.id.bottomRight);
		if (br != null) {
			br.setText("联系人");
		}

		// ----- 初始化焦点目标列表 -----
		// 快捷应用栏 5 项 (索引 0-4)
		focusTargets[0] = view.findViewById(R.id.shortcutPhone);
		focusTargets[1] = view.findViewById(R.id.shortcutMusic);
		focusTargets[2] = view.findViewById(R.id.shortcutVideo);
		focusTargets[3] = view.findViewById(R.id.shortcutCalendar);
		focusTargets[4] = view.findViewById(R.id.shortcutFolder);
		// 通知区 3 项 (索引 5-7)
		focusTargets[5] = view.findViewById(R.id.notifMusic);
		focusTargets[6] = view.findViewById(R.id.notifRadio);
		focusTargets[7] = view.findViewById(R.id.notifCalendar);
		// 功能表按钮 (索引 8) — Activity 级别的底部中间按钮
		focusTargets[8] = host.findViewById(R.id.bottomCenter);

		// 为快捷栏项绑定点击（触摸时也生效）
		if (focusTargets[0] != null) focusTargets[0].setOnClickListener(v -> onShortcutClicked(0));
		if (focusTargets[1] != null) focusTargets[1].setOnClickListener(v -> onShortcutClicked(1));
		if (focusTargets[2] != null) focusTargets[2].setOnClickListener(v -> onShortcutClicked(2));
		if (focusTargets[3] != null) focusTargets[3].setOnClickListener(v -> onShortcutClicked(3));
		if (focusTargets[4] != null) focusTargets[4].setOnClickListener(v -> onShortcutClicked(4));

		// 初始化选中第一个快捷项
		setFocusIndex(0);
	}

	// ---- NokiaFocusHost 接口 ----

	@Override
	public boolean onDirection(int direction) {
		if (focusIndex < 0) {
			setFocusIndex(0);
			return true;
		}

		switch (direction) {
			case NokiaKeyBinding.ACTION_UP:
				return moveUp();
			case NokiaKeyBinding.ACTION_DOWN:
				return moveDown();
			case NokiaKeyBinding.ACTION_LEFT:
				return moveLeft();
			case NokiaKeyBinding.ACTION_RIGHT:
				return moveRight();
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
		// 桌面左软键 = "相册"
		return true; // 消费事件，防止系统音量 UI 弹出
	}

	@Override
	public boolean onSoftRight() {
		// 桌面右软键 = "联系人"
		openContacts();
		return true;
	}

	@Override
	public boolean onBack() {
		// 桌面不处理返回，由 Activity 处理（回到 Android Home）
		return false;
	}

	// ---- 内部导航逻辑 ----

	/** 9 个焦点目标：5 快捷栏 + 3 通知 + 1 功能表按钮 */
	private static final int SHORTCUT_FIRST = 0;
	private static final int SHORTCUT_LAST = 4;
	private static final int NOTIF_FIRST = 5;
	private static final int NOTIF_LAST = 7;
	private static final int MENU_BTN = 8;

	private boolean moveUp() {
		int newIdx = focusIndex;
		if (isInShortcuts()) {
			if (focusIndex != SHORTCUT_FIRST) {
				newIdx = SHORTCUT_FIRST;
			}
		} else if (isInNotifications()) {
			if (focusIndex > NOTIF_FIRST) {
				newIdx = focusIndex - 1;
			} else {
				newIdx = SHORTCUT_LAST;
			}
		} else if (focusIndex == MENU_BTN) {
			newIdx = NOTIF_LAST;
		}
		return applyFocus(newIdx);
	}

	private boolean moveDown() {
		int newIdx = focusIndex;
		if (isInShortcuts()) {
			newIdx = NOTIF_FIRST;
		} else if (isInNotifications()) {
			if (focusIndex < NOTIF_LAST) {
				newIdx = focusIndex + 1;
			} else {
				newIdx = MENU_BTN;
			}
		} else if (focusIndex == MENU_BTN) {
			// 在功能表按钮按"下" → 回到快捷栏开头
			newIdx = SHORTCUT_FIRST;
		}
		return applyFocus(newIdx);
	}

	private boolean moveLeft() {
		int newIdx = focusIndex;
		if (isInShortcuts()) {
			if (focusIndex > SHORTCUT_FIRST) {
				newIdx = focusIndex - 1;
			} else {
				newIdx = SHORTCUT_LAST;
			}
		} else if (isInNotifications()) {
			newIdx = SHORTCUT_LAST;
		} else if (focusIndex == MENU_BTN) {
			newIdx = NOTIF_LAST;
		}
		return applyFocus(newIdx);
	}

	private boolean moveRight() {
		int newIdx = focusIndex;
		if (isInShortcuts()) {
			if (focusIndex < SHORTCUT_LAST) {
				newIdx = focusIndex + 1;
			} else {
				newIdx = SHORTCUT_FIRST;
			}
		} else if (isInNotifications()) {
			newIdx = SHORTCUT_FIRST;
		} else if (focusIndex == MENU_BTN) {
			newIdx = SHORTCUT_FIRST;
		}
		return applyFocus(newIdx);
	}

	private boolean applyFocus(int newIdx) {
		if (newIdx != focusIndex) {
			scrollToVisible(newIdx);
			setFocusIndex(newIdx);
			return true;
		}
		return false;
	}

	private boolean isInShortcuts() {
		return focusIndex >= SHORTCUT_FIRST && focusIndex <= SHORTCUT_LAST;
	}

	private boolean isInNotifications() {
		return focusIndex >= NOTIF_FIRST && focusIndex <= NOTIF_LAST;
	}

	private void scrollToVisible(int index) {
		if (index >= SHORTCUT_FIRST && index <= SHORTCUT_LAST && focusTargets[index] != null) {
			View parent = (View) focusTargets[index].getParent();
			while (parent != null) {
				if (parent.getId() == R.id.shortcutBar) {
					View target = focusTargets[index];
					int scrollX = target.getLeft() - parent.getPaddingLeft();
					parent.scrollTo(Math.max(0, scrollX - 12), 0);
					break;
				}
				parent = (View) parent.getParent();
			}
		}
	}

	private void setFocusIndex(int index) {
		if (index < 0 || index >= focusTargets.length) return;
		if (focusIndex >= 0 && focusIndex < focusTargets.length && focusTargets[focusIndex] != null) {
			focusTargets[focusIndex].setBackgroundResource(0);
		}
		focusIndex = index;
		if (focusTargets[focusIndex] != null) {
			focusTargets[focusIndex].setBackgroundResource(R.drawable.bg_nokia_selected);
		}
	}

	// ---- 快捷栏点击处理 ----

	private void onShortcutClicked(int index) {
		switch (index) {
			case 0:
				openDialer();
				break;
			case 1:
				openMusicPlayer();
				break;
			case 2:
				break;
			case 3:
				break;
			case 4:
				break;
		}
	}

	private void openDialer() {
		try {
			startActivity(new android.content.Intent(android.content.Intent.ACTION_DIAL));
		} catch (Exception ignored) {}
	}

	private void openContacts() {
		try {
			startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
					android.provider.ContactsContract.Contacts.CONTENT_URI));
		} catch (Exception ignored) {}
	}

	private void openMusicPlayer() {
		try {
			startActivity(new android.content.Intent("android.intent.action.MUSIC_PLAYER"));
		} catch (Exception e) {
			try {
				android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_MAIN);
				intent.addCategory(android.content.Intent.CATEGORY_APP_MUSIC);
				startActivity(intent);
			} catch (Exception ignored) {}
		}
	}
}
