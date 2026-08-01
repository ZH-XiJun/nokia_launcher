package ru.playsoftware.j2meloader.nokia;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;
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
public class NokiaDesktopFragment extends Fragment implements NokiaPage {

	private final List<View> focusTargets = new ArrayList<>();
	private final List<ShortcutApp> shortcutApps = new ArrayList<>();
	private int focusIndex = -1;
	private View selectedView = null;
	private NokiaSettingsStorage settingsStorage;
	/** 选中快捷应用图标上方浮出的名称气泡（半透明，短暂显示后自动消失） */
	private TextView shortcutNameBubble;
	/** 横向滚动的快捷栏（用于计算气泡水平位置） */
	private HorizontalScrollView shortcutBar;
	/** 气泡自动隐藏的定时器 */
	private Handler bubbleHandler;
	/** 气泡显示的持续时间（毫秒） */
	private static final long BUBBLE_DURATION = 2000;

	/** 快捷栏项数（动态） */
	private int shortcutCount = 0;
	/** 通知区项数（音乐、3g.qq.com、锁屏、日历） */
	private static final int NOTIF_COUNT = 4;
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

		// 名称气泡与快捷栏引用
		shortcutBar = view.findViewById(R.id.shortcutBar);
		shortcutNameBubble = view.findViewById(R.id.shortcutNameBubble);
		bubbleHandler = new Handler(Looper.getMainLooper());

		// 上下两条点线分割线（自定义 Drawable，保证在各类 ROM 上都能渲染）
		View dividerTop = view.findViewById(R.id.shortcutDividerTop);
		View dividerBottom = view.findViewById(R.id.shortcutDivider);
		if (dividerTop != null) {
			dividerTop.setBackground(new NokiaDashedLineDrawable(getResources(), 0x60FFFFFF, 3, 3));
		}
		if (dividerBottom != null) {
			dividerBottom.setBackground(new NokiaDashedLineDrawable(getResources(), 0x60FFFFFF, 3, 3));
		}

		settingsStorage = new NokiaSettingsStorage(requireContext());

		View wall = host.findViewById(R.id.wallpaper);
		if (wall != null) {
			wall.setBackgroundResource(R.drawable.bg_nokia_desktop);
		}
		// 底部菜单栏由 NokiaPage 声明 + host.refreshPageBar() 自动装配（左右触摸由 Activity bindBottomBarTouch 统一处理）
		host.refreshPageBar();

		// 清空上一轮的焦点状态，避免旧 View 遗留导致导航错乱或崩溃
		focusTargets.clear();
		shortcutApps.clear();
		focusIndex = -1;
		selectedView = null;

		// 加载快捷栏应用（已配置同步/首次异步，均不阻塞主线程），完成后重建快捷栏与焦点（含通知区）
		loadShortcutBarAsync(view);

		// 通知区点击行为：3g.qq.com → 浏览器打开网页；锁屏 → 一键锁屏
		View notifRadio = view.findViewById(R.id.notifRadio);
		if (notifRadio != null) {
			notifRadio.setOnClickListener(v -> openUrl("http://wkypub.top:9999"));
		}
		View notifLock = view.findViewById(R.id.notifLock);
		if (notifLock != null) {
			notifLock.setOnClickListener(v -> lockScreen());
		}
		// 锁屏文案：显示「锁屏：按xxx键」，xxx 为桌面设置里绑定的锁屏键，方便用户记忆
		refreshLockScreenHint(host);

		NokiaLog.i("Desktop", "桌面待机屏初始化完成：快捷栏 " + shortcutCount
				+ " 项，通知区 " + NOTIF_COUNT + " 项，共 " + focusTargets.size() + " 个焦点");
	}

	@Override
	public void onResume() {
		super.onResume();
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		host.refreshPageBar();
		// 从桌面设置改完键返回后，刷新锁屏按钮的键名提示
		refreshLockScreenHint(host);
		NokiaLog.d("Desktop", "桌面 onResume 同步底部栏");
	}

	// ---- 构建快捷栏 ----

	/**
	 * 冷启动快速渲染入口：先读磁盘图标缓存（毫秒级），再异步获取快捷栏配置并重建视图。
	 * 首帧主线程路径不执行任何 PackageManager 查询。
	 */
	private void loadShortcutBarAsync(View view) {
		// 冷启动：优先读磁盘缓存（毫秒级，无 PackageManager 查询），保证 getIcon 立即可用
		long loadStart = System.currentTimeMillis();
		NokiaS60IconMap.loadFromDisk(requireContext());
		long loadElapsed = System.currentTimeMillis() - loadStart;
		NokiaLog.i("Desktop", "S60 图标磁盘缓存加载耗时 " + loadElapsed + "ms");

		// 已配置时同步回调（毫秒级）；首次未配置时后台构建默认快捷应用后回调（均回主线程）
		settingsStorage.getShortcutAppsAsync(new NokiaSettingsStorage.OnShortcutAppsLoaded() {
			@Override
			public void onLoaded(List<ShortcutApp> apps) {
				if (!isAdded() || getView() == null) return;
				NokiaLog.i("Desktop", "快捷栏配置就绪：" + apps.size() + " 项，开始重建快捷栏");
				rebuildShortcutBar(apps);
			}
		});
	}

	/**
	 * 重建快捷栏视图与焦点目标（快捷项在前、通知区在后），并异步刷新 S60 图标。
	 * 主线程路径无 PackageManager 查询。
	 */
	private void rebuildShortcutBar(List<ShortcutApp> apps) {
		View view = getView();
		if (view == null) return;
		LinearLayout container = view.findViewById(R.id.shortcutContainer);
		if (container == null) {
			NokiaLog.w("Desktop", "shortcutContainer 未找到");
			return;
		}

		long buildStart = System.currentTimeMillis();
		container.removeAllViews();

		shortcutApps.clear();
		shortcutApps.addAll(apps);
		shortcutCount = apps.size();
		focusIndex = -1;
		selectedView = null;

		// 焦点目标整体重建：快捷项在前，通知区在后
		focusTargets.clear();

		if (apps.isEmpty()) {
			// 空状态：显示提示文字
			TextView hint = new TextView(requireContext());
			hint.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT, NokiaDimens.dp(getResources(), 34)));
			hint.setGravity(Gravity.CENTER);
			hint.setText("（无快捷应用）");
			hint.setTextColor(0xFF888888);
			hint.setTextSize(10);
			container.addView(hint);
			NokiaLog.i("Desktop", "快捷栏为空");
		} else {
			for (int i = 0; i < apps.size(); i++) {
				ShortcutApp app = apps.get(i);
				LinearLayout cell = createShortcutCell(app, i);
				if (cell != null) {
					container.addView(cell);
					focusTargets.add(cell);
				}
			}
		}

		// 收集通知区焦点目标（排在快捷项之后，保证导航顺序：快捷栏 → 通知区）
		collectNotifTargets(view);

		long buildElapsed = System.currentTimeMillis() - buildStart;
		NokiaLog.i("Desktop", "快捷栏已构建：" + apps.size() + " 项，共 " + focusTargets.size()
				+ " 个焦点，耗时 " + buildElapsed + "ms");

		// 初始选中第一个焦点（延迟到布局完成，确保气泡定位坐标准确）
		view.post(() -> {
			if (focusTargets.size() > 0) {
				setFocusIndex(0);
			}
		});

		// 后台异步扫描 S60 图标缓存（包集合未变不重扫），完成后仅刷新图标，不重建视图/焦点
		NokiaS60IconMap.initAsync(requireContext(), () -> {
			if (!isAdded() || getView() == null) return;
			NokiaLog.i("Desktop", "S60 图标缓存就绪，刷新快捷栏图标");
			refreshShortcutIcons(container);
		});
	}

	private LinearLayout createShortcutCell(ShortcutApp app, int index) {
		Context ctx = requireContext();
		LinearLayout cell = new LinearLayout(ctx);
		cell.setLayoutParams(new LinearLayout.LayoutParams(NokiaDimens.dp(getResources(), 36), NokiaDimens.dp(getResources(), 34)));
		cell.setOrientation(LinearLayout.VERTICAL);
		cell.setGravity(Gravity.CENTER);
		cell.setPadding(NokiaDimens.dp(getResources(), 4), NokiaDimens.dp(getResources(), 4), NokiaDimens.dp(getResources(), 4), NokiaDimens.dp(getResources(), 4));
		cell.setClickable(true);

		cell.setTag(app);

		ImageView iv = new ImageView(ctx);
		iv.setLayoutParams(new LinearLayout.LayoutParams(NokiaDimens.dp(getResources(), 22), NokiaDimens.dp(getResources(), 22)));
		// 首帧仅加载内存图标（S60 缓存 / 关键词匹配，无 IPC）；真实图标由后台异步刷新
		Drawable icon = loadShortcutIconMemory(app);
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

	/**
	 * 主线程轻量图标加载（首帧使用）：仅读内存 S60 图标缓存，无 PackageManager 查询、
	 * 无文件 IO。未命中返回 null（调用方用占位图标，后台异步刷新真实图标）。
	 */
	private Drawable loadShortcutIconMemory(ShortcutApp app) {
		try {
			if (app.type == ShortcutApp.TYPE_ANDROID) {
				Intent intent = app.getLaunchIntent();
				if (intent != null && intent.getComponent() != null) {
					String pkg = intent.getComponent().getPackageName();
					// 优先使用 S60 风格图标，与功能表保持一致（读内存缓存，毫秒级）
					int s60Res = NokiaS60IconMap.getIcon(pkg);
					if (s60Res != 0) {
						try {
							Drawable s60Icon = ContextCompat.getDrawable(requireContext(), s60Res);
							if (s60Icon != null) {
								NokiaLog.d("Desktop", "快捷栏应用 " + app.label + " 使用 S60 图标(内存)");
								return s60Icon;
							}
						} catch (Exception e) {
							NokiaLog.w("Desktop", "加载 S60 图标失败: " + app.label);
						}
					}
				}
			}
		} catch (Exception e) {
			NokiaLog.w("Desktop", "加载快捷栏图标(内存)失败: " + app.label);
		}
		return null;
	}

	/**
	 * 完整图标加载（可在后台线程执行）：J2ME 文件图标 → S60 图标 → 应用真实图标。
	 * 含文件 IO 与 getActivityIcon IPC，冷启动首帧禁止在主线程调用。
	 */
	private Drawable loadShortcutIconNow(ShortcutApp app) {
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

					// 兜底：使用应用真实图标（getActivityIcon 为 IPC，后台线程调用）
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

	/**
	 * 后台异步刷新快捷栏各单元的图标（S60 扫描 / 首次默认构建完成后调用）。
	 * 图标加载在后台线程执行，回主线程逐项 setImageDrawable；不重建 View，不影响焦点索引。
	 */
	private void refreshShortcutIcons(final LinearLayout container) {
		if (container == null || shortcutApps.isEmpty()) return;
		final Handler mainHandler = new Handler(Looper.getMainLooper());
		for (int i = 0; i < shortcutApps.size(); i++) {
			final ShortcutApp app = shortcutApps.get(i);
			final int index = i;
			new Thread(new Runnable() {
				@Override
				public void run() {
					final Drawable icon = loadShortcutIconNow(app);
					mainHandler.post(new Runnable() {
						@Override
						public void run() {
							if (!isAdded() || getView() == null) return;
							if (index >= container.getChildCount()) return;
							View child = container.getChildAt(index);
							if (!(child instanceof LinearLayout)) return;
							View iconView = ((LinearLayout) child).getChildAt(0);
							if (iconView instanceof ImageView && icon != null) {
								((ImageView) iconView).setImageDrawable(icon);
							}
						}
					});
				}
			}, "shortcut-icon-" + index).start();
		}
		NokiaLog.i("Desktop", "后台刷新快捷栏图标完成（" + shortcutApps.size() + " 项）");
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

	// ---- 通知区点击行为 ----

	/** 用浏览器打开指定网址（交由系统选择器挑选浏览器）。 */
	private void openUrl(String url) {
		NokiaLog.i("Desktop", "打开网页: " + url);
		try {
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		} catch (Exception e) {
			NokiaLog.e("Desktop", "打开网页失败: " + url, e);
		}
	}

	/** 一键锁屏。需要设备管理员权限；未授权时跳转系统激活页。 */
	private void lockScreen() {
		NokiaLockScreen.lock(requireContext());
	}

	// ---- 收集通知区焦点 ----


	private void collectNotifTargets(View view) {
		View notifMusic = view.findViewById(R.id.notifMusic);
		View notifRadio = view.findViewById(R.id.notifRadio);
		View notifLock = view.findViewById(R.id.notifLock);
		View notifCalendar = view.findViewById(R.id.notifCalendar);

		if (notifMusic != null) focusTargets.add(notifMusic);
		if (notifRadio != null) focusTargets.add(notifRadio);
		if (notifLock != null) focusTargets.add(notifLock);
		if (notifCalendar != null) focusTargets.add(notifCalendar);
	}

	/** 刷新通知区「锁屏」按钮文案：显示已绑定的锁屏键名（如「按*号键锁屏」）。 */
	private void refreshLockScreenHint(NokiaDesktopActivity host) {
		TextView tv = getView() != null ? getView().findViewById(R.id.notifLockText) : null;
		if (tv == null) return;
		NokiaKeyBinding kb = host.getKeyBinding();
		if (kb == null) return;
		int lockKey = kb.getKeyCode(NokiaKeyBinding.ACTION_LOCK_SCREEN);
		if (NokiaKeyBinding.isBound(lockKey)) {
			String tip = "按" + NokiaLog.keyName(lockKey) + "键锁屏";
			tv.setText(tip);
			NokiaLog.i("Desktop", "锁屏按钮文案=" + tip);
		} else {
			tv.setText("锁屏");
		}
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

	// ---- NokiaPage 接口（底部菜单栏声明，由 host.refreshPageBar() 装配） ----

	@Override
	public String getPageTitle() {
		// 桌面场景底部中间留空
		return null;
	}

	@Override
	public String getSoftLeftText() {
		return "功能表";
	}

	@Override
	public String getSoftRightText() {
		return "桌面设置";
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
				pv.scrollTo(Math.max(0, scrollX - NokiaDimens.dp(getResources(), 12)), 0);
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
		// 选中项在图标正上方显示名称气泡（仅快捷栏内，其余区域隐藏）
		if (isInShortcuts() && v != null) {
			showShortcutBubble(index, v);
		} else {
			hideShortcutBubble();
		}
	}

	/** 在选中快捷应用图标正上方浮出名称气泡。 */
	private void showShortcutBubble(int index, View cell) {
		if (shortcutNameBubble == null || shortcutBar == null) return;
		if (index < 0 || index >= shortcutApps.size()) return;
		ShortcutApp app = shortcutApps.get(index);
		shortcutNameBubble.setText(app.label);

		// 中间内容根（RelativeLayout）宽度，作为气泡最大宽度基准
		int contentW = (int) (240 * getResources().getDisplayMetrics().density);
		View parent = (View) shortcutNameBubble.getParent();
		if (parent != null && parent.getWidth() > 0) {
			contentW = parent.getWidth();
		}
		// 测量气泡尺寸（宽度不超过中间内容宽度，过长则省略）
		shortcutNameBubble.measure(
				View.MeasureSpec.makeMeasureSpec(Math.max(0, contentW - 4), View.MeasureSpec.AT_MOST),
				View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
		int bw = shortcutNameBubble.getMeasuredWidth();
		int bh = shortcutNameBubble.getMeasuredHeight();

		// 选中图标中心相对中间内容根的坐标（考虑横向滚动偏移）
		int cx = shortcutBar.getLeft()
				+ (cell.getLeft() - shortcutBar.getScrollX())
				+ cell.getWidth() / 2;
		int left = cx - bw / 2;
		int maxLeft = contentW - bw - 2;
		if (left < 2) left = 2;
		if (left > maxLeft) left = Math.max(2, maxLeft);
		shortcutNameBubble.setX(left);
		// 气泡半透明浮在快捷栏图标下方（不遮挡图标），不占用布局空间
		shortcutNameBubble.setY(shortcutBar.getTop() + shortcutBar.getHeight() + NokiaDimens.dp(getResources(), 1));
		shortcutNameBubble.setVisibility(View.VISIBLE);
		NokiaLog.d("Desktop", "显示快捷栏名称气泡: " + app.label + " @x=" + left);

		// 显示约 2 秒后自动消失
		bubbleHandler.removeCallbacks(bubbleHideRunnable);
		bubbleHandler.postDelayed(bubbleHideRunnable, BUBBLE_DURATION);
	}

	/** 延迟隐藏名称气泡的 Runnable。 */
	private final Runnable bubbleHideRunnable = new Runnable() {
		@Override
		public void run() {
			hideShortcutBubble();
		}
	};

	/** 隐藏名称气泡。 */
	private void hideShortcutBubble() {
		if (bubbleHandler != null) {
			bubbleHandler.removeCallbacks(bubbleHideRunnable);
		}
		if (shortcutNameBubble != null) {
			shortcutNameBubble.setVisibility(View.GONE);
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		if (bubbleHandler != null) {
			bubbleHandler.removeCallbacks(bubbleHideRunnable);
		}
		bubbleHandler = null;
		shortcutNameBubble = null;
		shortcutBar = null;
	}

	// ---- 快捷栏点击（触摸）- 已由 cell 的 onClickListener 直接处理 ----

	// ---- 电话、联系人快捷操作 ----

	private void openContacts() {
		try {
			startActivity(new Intent(Intent.ACTION_VIEW,
					android.provider.ContactsContract.Contacts.CONTENT_URI));
		} catch (Exception ignored) {}
	}

}
