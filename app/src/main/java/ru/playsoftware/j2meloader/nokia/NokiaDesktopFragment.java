package ru.playsoftware.j2meloader.nokia;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;

/**
 * 桌面待机屏中间内容碎片。
 * 支持方向键在快捷应用栏（动态数量）、通知区（3 项）和功能表按钮之间导航。
 * 快捷栏应用由用户在桌面设置中配置，动态加载并支持左右滚动。
 */
public class NokiaDesktopFragment extends Fragment implements NokiaFocusHost {

	private final List<View> focusTargets = new ArrayList<>();
	private final List<ShortcutApp> shortcutApps = new ArrayList<>();
	private int focusIndex = -1;
	private View selectedView = null;
	private NokiaSettingsStorage settingsStorage;

	/** 快捷栏项数（动态） */
	private int shortcutCount = 0;
	/** 通知区项数（固定 3） */
	private static final int NOTIF_COUNT = 3;
	/** 快捷栏第一个焦点索引 */
	private static final int SHORTCUT_FIRST = 0;

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

		settingsStorage = new NokiaSettingsStorage(requireContext());

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
			bl.setText("功能表");
			bl.setOnClickListener(v -> host.openMenu());
		}
		TextView bc = host.findViewById(R.id.bottomCenter);
		if (bc != null) {
			bc.setText("");
			bc.setVisibility(View.GONE);
			bc.setOnClickListener(null);
		}
		TextView br = host.findViewById(R.id.bottomRight);
		if (br != null) {
			br.setText("桌面设置");
			br.setOnClickListener(v -> host.openDesktopSettings());
		}

		// 清空上一轮的焦点状态，避免旧 View 遗留导致导航错乱或崩溃
		focusTargets.clear();
		shortcutApps.clear();
		focusIndex = -1;
		selectedView = null;

		// 加载快捷栏应用并动态创建视图
		buildShortcutBar(view);
		// 收集通知区焦点目标
		collectNotifTargets(view);

		// 初始化选中第一个快捷项（如果有）
		if (shortcutCount > 0) {
			setFocusIndex(0);
		} else if (focusTargets.size() > 0) {
			setFocusIndex(0);
		}

		NokiaLog.i("Desktop", "桌面待机屏初始化完成：快捷栏 " + shortcutCount
				+ " 项，通知区 " + NOTIF_COUNT + " 项，共 " + focusTargets.size() + " 个焦点");
	}

	// ---- 构建快捷栏 ----

	private void buildShortcutBar(View view) {
		LinearLayout container = view.findViewById(R.id.shortcutContainer);
		if (container == null) {
			NokiaLog.w("Desktop", "shortcutContainer 未找到");
			return;
		}
		container.removeAllViews();

		// 初始化 S60 图标缓存，确保快捷栏图标与功能表一致
		NokiaS60IconMap.init(requireActivity().getPackageManager());

		List<ShortcutApp> apps = settingsStorage.getShortcutApps();
		shortcutApps.clear();
		shortcutApps.addAll(apps);
		shortcutCount = apps.size();

		if (apps.isEmpty()) {
			// 空状态：显示提示文字
			TextView hint = new TextView(requireContext());
			hint.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)));
			hint.setGravity(Gravity.CENTER);
			hint.setText("（无快捷应用）");
			hint.setTextColor(0xFF888888);
			hint.setTextSize(10);
			container.addView(hint);
			NokiaLog.i("Desktop", "快捷栏为空");
			return;
		}

		for (int i = 0; i < apps.size(); i++) {
			ShortcutApp app = apps.get(i);
			LinearLayout cell = createShortcutCell(app, i);
			if (cell != null) {
				container.addView(cell);
				focusTargets.add(cell);
			}
		}
		NokiaLog.i("Desktop", "快捷栏已构建：" + apps.size() + " 项");
	}

	private LinearLayout createShortcutCell(ShortcutApp app, int index) {
		Context ctx = requireContext();
		LinearLayout cell = new LinearLayout(ctx);
		cell.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(34)));
		cell.setOrientation(LinearLayout.VERTICAL);
		cell.setGravity(Gravity.CENTER);
		cell.setPadding(dp(4), dp(4), dp(4), dp(4));
		cell.setClickable(true);
		cell.setFocusable(true);
		cell.setTag(app);

		ImageView iv = new ImageView(ctx);
		iv.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(22)));
		Drawable icon = loadShortcutIcon(app);
		if (icon != null) {
			iv.setImageDrawable(icon);
		} else {
			try {
				iv.setImageDrawable(ContextCompat.getDrawable(ctx, R.mipmap.ic_launcher));
			} catch (Exception ignored) {}
		}
		cell.addView(iv);

		cell.setOnClickListener(v -> launchShortcutApp(app));
		return cell;
	}

	private Drawable loadShortcutIcon(ShortcutApp app) {
		try {
			if (app.type == ShortcutApp.TYPE_J2ME && app.iconPath != null) {
				// J2ME 图标从本地文件加载
				Drawable d = Drawable.createFromPath(app.iconPath);
				if (d != null) return d;
			}
			if (app.type == ShortcutApp.TYPE_ANDROID) {
				Intent intent = app.getLaunchIntent();
				if (intent != null && intent.getComponent() != null) {
					String pkg = intent.getComponent().getPackageName();

					// 优先使用 S60 风格图标，与功能表保持一致
					int s60Res = NokiaS60IconMap.getIcon(pkg);
					if (s60Res != 0) {
						try {
							Drawable s60Icon = ContextCompat.getDrawable(requireContext(), s60Res);
							if (s60Icon != null) {
								NokiaLog.d("Desktop", "快捷栏应用 " + app.label + " 使用 S60 图标");
								return s60Icon;
							}
						} catch (Exception e) {
							NokiaLog.w("Desktop", "加载 S60 图标失败: " + app.label);
						}
					}

					// 兜底：使用应用真实图标
					try {
						return requireActivity().getPackageManager()
								.getActivityIcon(intent.getComponent());
					} catch (Exception e) {
						NokiaLog.w("Desktop", "加载应用图标失败: " + app.label);
					}
				}
			}
		} catch (Exception e) {
			NokiaLog.w("Desktop", "加载快捷栏图标失败: " + app.label);
		}
		return null;
	}

	private void launchShortcutApp(ShortcutApp app) {
		NokiaLog.i("Desktop", "启动快捷栏应用: " + app.label + " type=" + app.type);
		try {
			if (app.type == ShortcutApp.TYPE_ANDROID) {
				Intent intent = app.getLaunchIntent();
				if (intent != null) {
					startActivity(intent);
					return;
				}
			}
			if (app.type == ShortcutApp.TYPE_J2ME) {
				Config.startApp(requireActivity(), app.label, app.appKey, false);
				return;
			}
		} catch (Exception e) {
			NokiaLog.e("Desktop", "启动快捷栏应用失败: " + app.label, e);
		}
	}

	// ---- 收集通知区焦点 ----

	private void collectNotifTargets(View view) {
		View notifMusic = view.findViewById(R.id.notifMusic);
		View notifRadio = view.findViewById(R.id.notifRadio);
		View notifCalendar = view.findViewById(R.id.notifCalendar);

		if (notifMusic != null) focusTargets.add(notifMusic);
		if (notifRadio != null) focusTargets.add(notifRadio);
		if (notifCalendar != null) focusTargets.add(notifCalendar);
	}

	// ---- NokiaFocusHost 接口 ----

	@Override
	public boolean onDirection(int direction) {
		if (focusTargets.isEmpty()) return false;
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
		if (focusIndex < 0 || focusIndex >= focusTargets.size()) return false;
		View v = focusTargets.get(focusIndex);
		if (v != null) {
			v.performClick();
		}
		return true;
	}

	@Override
	public boolean onSoftLeft() {
		NokiaLog.i("Desktop", "左软键：功能表");
		requireActivity(); // ensure attached
		((NokiaDesktopActivity) requireActivity()).openMenu();
		return true;
	}

	@Override
	public boolean onSoftRight() {
		NokiaLog.i("Desktop", "右软键：桌面设置");
		((NokiaDesktopActivity) requireActivity()).openDesktopSettings();
		return true;
	}

	@Override
	public boolean onBack() {
		// 桌面不处理返回，由 Activity 处理（回到 Android Home）
		return false;
	}

	// ---- 导航逻辑 ----

	/** 快捷栏最后一个索引（不含） */
	private int shortcutLast() { return shortcutCount; }

	/** 通知区第一个索引 */
	private int notifFirst() { return shortcutCount; }

	/** 通知区最后一个索引（不含） */
	private int notifLast() { return shortcutCount + NOTIF_COUNT; }

	private boolean isInShortcuts() {
		return focusIndex >= SHORTCUT_FIRST && focusIndex < shortcutLast();
	}

	private boolean isInNotifications() {
		return focusIndex >= notifFirst() && focusIndex < notifLast();
	}

	private boolean moveUp() {
		int newIdx = focusIndex;
		if (isInShortcuts()) {
			if (focusIndex != SHORTCUT_FIRST) {
				newIdx = SHORTCUT_FIRST;
			}
		} else if (isInNotifications()) {
			if (focusIndex > notifFirst()) {
				newIdx = focusIndex - 1;
			} else {
				// 从通知区第一项上移 → 快捷栏最后一项
				if (shortcutCount > 0) newIdx = shortcutLast() - 1;
			}
		}
		return applyFocus(newIdx);
	}

	private boolean moveDown() {
		int newIdx = focusIndex;
		if (isInShortcuts()) {
			newIdx = notifFirst(); // 快捷栏 → 通知区第一个
		} else if (isInNotifications()) {
			if (focusIndex < notifLast() - 1) {
				newIdx = focusIndex + 1;
			} else {
				// 通知区最后一个 → 快捷栏第一个
				if (shortcutCount > 0) newIdx = SHORTCUT_FIRST;
			}
		}
		return applyFocus(newIdx);
	}

	private boolean moveLeft() {
		int newIdx = focusIndex;
		if (isInShortcuts()) {
			if (focusIndex > SHORTCUT_FIRST) {
				newIdx = focusIndex - 1;
			} else {
				// 环绕到快捷栏最后一个
				if (shortcutCount > 1) newIdx = shortcutLast() - 1;
			}
		} else if (isInNotifications()) {
			if (shortcutCount > 0) newIdx = shortcutLast() - 1;
		}
		return applyFocus(newIdx);
	}

	private boolean moveRight() {
		int newIdx = focusIndex;
		if (isInShortcuts()) {
			if (focusIndex < shortcutLast() - 1) {
				newIdx = focusIndex + 1;
			} else {
				if (shortcutCount > 1) newIdx = SHORTCUT_FIRST;
			}
		} else if (isInNotifications()) {
			if (shortcutCount > 0) newIdx = SHORTCUT_FIRST;
		}
		return applyFocus(newIdx);
	}

	private boolean applyFocus(int newIdx) {
		if (newIdx < 0 || newIdx >= focusTargets.size()) return false;
		if (newIdx != focusIndex) {
			scrollToVisible(newIdx);
			setFocusIndex(newIdx);
			return true;
		}
		return false;
	}

	private void scrollToVisible(int index) {
		if (!isInShortcuts()) return;
		if (index < 0 || index >= focusTargets.size()) return;
		View target = focusTargets.get(index);
		if (target == null) return;

		// 沿父链查找 HorizontalScrollView，非 View 的父节点直接跳过
		// （防御 UnisocViewRootImpl 等特殊设备上抛 ClassCastException）
		ViewParent parent = target.getParent();
		while (parent instanceof View) {
			View pv = (View) parent;
			if (pv.getId() == R.id.shortcutBar) {
				int scrollX = target.getLeft() - pv.getPaddingLeft();
				pv.scrollTo(Math.max(0, scrollX - dp(12)), 0);
				return;
			}
			parent = pv.getParent();
		}
	}

	private void setFocusIndex(int index) {
		if (index < 0 || index >= focusTargets.size()) return;
		// 清除旧选中
		if (focusIndex >= 0 && focusIndex < focusTargets.size()) {
			View old = focusTargets.get(focusIndex);
			if (old != null) old.setBackgroundResource(0);
		}
		focusIndex = index;
		View v = focusTargets.get(index);
		if (v != null) {
			v.setBackgroundResource(R.drawable.bg_nokia_selected);
			selectedView = v;
		}
	}

	// ---- 快捷栏点击（触摸）- 已由 cell 的 onClickListener 直接处理 ----

	// ---- 电话、联系人快捷操作 ----

	private void openContacts() {
		try {
			startActivity(new Intent(Intent.ACTION_VIEW,
					android.provider.ContactsContract.Contacts.CONTENT_URI));
		} catch (Exception ignored) {}
	}

	private int dp(int v) {
		return (int) (v * getResources().getDisplayMetrics().density);
	}
}
