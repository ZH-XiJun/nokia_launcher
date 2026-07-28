package ru.playsoftware.j2meloader.nokia;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.sqlite.db.SupportSQLiteProgram;
import androidx.sqlite.db.SupportSQLiteQuery;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.appsdb.AppDatabase;
import ru.playsoftware.j2meloader.appsdb.AppItemDao;
import ru.playsoftware.j2meloader.config.Config;

/**
 * 快捷栏应用选择界面。展示所有可选应用（安卓 + J2ME），多选后保存。
 * 支持方向键导航，SELECT 切换选中状态，左软键保存，右软键返回。
 */
public class NokiaShortcutSettingsFragment extends Fragment implements NokiaFocusHost {

	private LinearLayout appListLayout;
	private ScrollView appScroll;
	private final List<NokiaAppItem> allApps = new ArrayList<>();
	private final Set<String> selectedKeys = new HashSet<>(); // "type:appKey"
	private NokiaSettingsStorage settingsStorage;
	private View[] itemViews;
	private int focusIndex = -1;
	private View selectedView = null;
	private TextView tvSelectedCount;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
							 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_nokia_shortcut_settings, container, false);
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
			title.setText("快捷栏设置");
		}
		host.setBottomBar("保存", null, "返回");

		settingsStorage = new NokiaSettingsStorage(requireContext());
		appListLayout = view.findViewById(R.id.appListLayout);
		appScroll = view.findViewById(R.id.appScroll);
		tvSelectedCount = view.findViewById(R.id.tvSelectedCount);

		// 运行时约束 ScrollView 高度，使列表底部正好落在可视区底边
		view.post(() -> {
			if (appScroll == null) return;
			View parent = (View) view.getParent();
			if (!(parent instanceof View)) {
				NokiaLog.w("ShortcutSettings", "parent is not a View, skip height constraint");
				return;
			}
			int panelH = ((View) parent).getHeight();
			float scale = view.getScaleX();
			if (scale <= 0) scale = 1;
			int visibleH = (int) (panelH / scale);
			int headH = appScroll.getTop();
			int scrollH = visibleH - headH;
			if (scrollH > 0) {
				ViewGroup.LayoutParams lp = appScroll.getLayoutParams();
				lp.height = scrollH;
				appScroll.setLayoutParams(lp);
				NokiaLog.i("ShortcutSettings", "约束ScrollView高度: panelH=" + panelH
						+ " scale=" + scale + " visibleH=" + visibleH
						+ " headH=" + headH + " scrollH=" + scrollH);
			} else {
				NokiaLog.w("ShortcutSettings", "scrollH <= 0, skip height constraint: scrollH=" + scrollH);
			}
		});

		// 加载已选中的应用
		List<ShortcutApp> current = settingsStorage.getShortcutApps();
		for (ShortcutApp app : current) {
			selectedKeys.add(makeKey(app.type, app.appKey));
		}
		NokiaLog.i("ShortcutSettings", "已加载 " + selectedKeys.size() + " 个已选应用");

		// 异步加载应用列表
		loadAppsAsync();

		NokiaLog.i("ShortcutSettings", "快捷栏设置初始化完成");
	}

	// ---- 异步加载应用 ----

	private void loadAppsAsync() {
		Single.fromCallable(() -> {
					List<NokiaAppItem> result = new ArrayList<>();
					loadAndroidApps(result);
					loadJ2meApps(result);
					return result;
				})
				.subscribeOn(Schedulers.io())
				.observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
				.subscribe(
						apps -> {
							allApps.clear();
							allApps.addAll(apps);
							buildAppList();
							NokiaLog.i("ShortcutSettings", "应用列表加载完成：共 " + apps.size() + " 个（安卓 + J2ME）");
						},
						error -> {
							NokiaLog.e("ShortcutSettings", "加载应用列表失败", error);
							// 降级：至少加载安卓应用
							allApps.clear();
							loadAndroidApps(allApps);
							buildAppList();
						}
				);
	}

	/** 加载安卓可启动应用 */
	private void loadAndroidApps(List<NokiaAppItem> out) {
		PackageManager pm = requireActivity().getPackageManager();
		Intent main = new Intent(Intent.ACTION_MAIN, null);
		main.addCategory(Intent.CATEGORY_LAUNCHER);
		List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
		String selfPkg = requireActivity().getPackageName();

		for (ResolveInfo ri : list) {
			ActivityInfo ai = ri.activityInfo;
			if (ai == null) continue;
			if (ai.packageName.equals(selfPkg)) continue;

			CharSequence labelCs = ri.loadLabel(pm);
			String label = (labelCs != null && labelCs.length() > 0) ? labelCs.toString() : ai.name;
			Drawable icon = ri.loadIcon(pm);
			Intent launch = new Intent(Intent.ACTION_MAIN);
			launch.addCategory(Intent.CATEGORY_LAUNCHER);
			launch.setClassName(ai.packageName, ai.name);
			launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

			String appKey = ai.packageName + "/" + ai.name;
			NokiaAppItem item = new NokiaAppItem(NokiaAppItem.TYPE_APP, label, icon, launch);
			// 覆盖 label 用于 key 映射
			out.add(item);
		}

		NokiaLog.i("ShortcutSettings", "加载安卓应用: " + out.size() + " 个");
	}

	/** 加载 J2ME 已安装的应用 */
	private void loadJ2meApps(List<NokiaAppItem> out) {
		try {
			String emulatorDir = Config.getEmulatorDir();
			File dbFile = new File(emulatorDir, "J2ME-apps.db");
			if (!dbFile.exists()) {
				NokiaLog.i("ShortcutSettings", "J2ME 数据库不存在，跳过 JAR 应用加载");
				return;
			}

			AppDatabase db = AppDatabase.open(requireActivity().getApplicationContext(), emulatorDir);
			AppItemDao dao = db.appItemDao();
			List<AppItem> j2meApps = dao.getAllSingle(new SimpleSortQuery()).blockingGet();
			db.close();

			for (AppItem app : j2meApps) {
				String label = app.getTitle();
				Drawable icon = null;
				String iconPath = app.getImagePathExt();
				if (iconPath != null) {
					try {
						icon = Drawable.createFromPath(iconPath);
					} catch (Exception e) {
						NokiaLog.w("ShortcutSettings", "加载 J2ME 图标失败: " + iconPath);
					}
				}
				// 使用 pathExt 作为唯一标识
				String appKey = app.getPathExt();
				NokiaAppItem item = new NokiaAppItem(NokiaAppItem.TYPE_BOX, label, icon, null);
				// 劫持 launchIntent 的 component 来存储 J2ME 信息（临时代码结构）
				// 用一个特殊 Intent 来携带 J2ME 数据
				Intent placeholder = new Intent();
				placeholder.setClassName(requireActivity().getPackageName(),
						"j2me:" + label + ":" + app.getPathExt());
				item.launchIntent = placeholder;
				out.add(item);
			}

			NokiaLog.i("ShortcutSettings", "加载 J2ME 应用: " + j2meApps.size() + " 个");
		} catch (Exception e) {
			NokiaLog.e("ShortcutSettings", "加载 J2ME 应用失败", e);
		}
	}

	/** Simple SupportSQLiteQuery for "SELECT * FROM apps ORDER BY title" */
	private static class SimpleSortQuery implements SupportSQLiteQuery {
		@Override
		public String getSql() {
			return "SELECT * FROM apps ORDER BY title ASC";
		}

		@Override
		public void bindTo(SupportSQLiteProgram statement) {}

		@Override
		public int getArgCount() { return 0; }
	}

	// ---- 构建应用列表 UI ----

	private void buildAppList() {
		if (appListLayout == null) return;
		appListLayout.removeAllViews();

		if (allApps.isEmpty()) {
			TextView empty = new TextView(requireContext());
			empty.setText("未找到可添加的应用");
			empty.setTextColor(0xFFAAAAAA);
			empty.setTextSize(12);
			empty.setGravity(Gravity.CENTER);
			empty.setPadding(0, dp(20), 0, 0);
			appListLayout.addView(empty);
			itemViews = new View[0];
			updateCountText();
			return;
		}

		itemViews = new View[allApps.size()];
		for (int i = 0; i < allApps.size(); i++) {
			NokiaAppItem app = allApps.get(i);
			String key = makeKeyForItem(app);

			LinearLayout row = new LinearLayout(requireContext());
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, dp(38)));
			row.setPadding(dp(8), dp(3), dp(8), dp(3));
			row.setClickable(true);
			row.setFocusable(true);

			// 图标
			ImageView iv = new ImageView(requireContext());
			iv.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
			if (app.icon != null) {
				iv.setImageDrawable(app.icon);
			} else {
				try {
					iv.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.mipmap.ic_launcher));
				} catch (Exception ignored) {}
			}
			row.addView(iv);

			// 间距
			View space = new View(requireContext());
			space.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));
			row.addView(space);

			// 应用名
			TextView tv = new TextView(requireContext());
			tv.setLayoutParams(new LinearLayout.LayoutParams(
					0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
			tv.setText(app.label);
			tv.setTextColor(0xFFFFFFFF);
			tv.setTextSize(12);
			tv.setSingleLine(true);
			tv.setEllipsize(TextUtils.TruncateAt.END);
			row.addView(tv);

			// 选中/未选中标记
			TextView tvCheck = new TextView(requireContext());
			tvCheck.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
			tvCheck.setWidth(dp(24));
			tvCheck.setHeight(dp(24));
			tvCheck.setGravity(Gravity.CENTER);
			tvCheck.setTextSize(14);
			if (selectedKeys.contains(key)) {
				tvCheck.setText("[✓]");
				tvCheck.setTextColor(0xFF4CAF50);
			} else {
				tvCheck.setText("[ ]");
				tvCheck.setTextColor(0xFF888888);
			}
			tvCheck.setTag("check_" + i);
			row.addView(tvCheck);

			row.setTag(key);
			final int index = i;
			row.setOnClickListener(v -> {
				setFocusIndex(index);
				toggleSelection(index);
			});

			appListLayout.addView(row);
			itemViews[i] = row;
		}

		updateCountText();
		setFocusIndex(0);
	}

	private String makeKeyForItem(NokiaAppItem app) {
		if (app.launchIntent != null && app.launchIntent.getComponent() != null) {
			String cls = app.launchIntent.getComponent().getClassName();
			if (cls != null && cls.startsWith("j2me:")) {
				return ShortcutApp.TYPE_J2ME + ":" + cls; // 用 className 作为 J2ME 标识
			}
			return ShortcutApp.TYPE_ANDROID + ":"
					+ app.launchIntent.getComponent().getPackageName() + "/"
					+ app.launchIntent.getComponent().getClassName();
		}
		return "unknown:" + app.label;
	}

	private static String makeKey(int type, String appKey) {
		return type + ":" + appKey;
	}

	private void toggleSelection(int index) {
		if (index < 0 || index >= allApps.size()) return;
		NokiaAppItem app = allApps.get(index);
		String key = makeKeyForItem(app);

		if (selectedKeys.contains(key)) {
			selectedKeys.remove(key);
			NokiaLog.d("ShortcutSettings", "取消选中: " + app.label);
		} else {
			selectedKeys.add(key);
			NokiaLog.d("ShortcutSettings", "选中: " + app.label);
		}

		// 刷新当前行的勾选标记
		if (itemViews != null && index < itemViews.length && itemViews[index] != null) {
			View row = itemViews[index];
			TextView check = row.findViewWithTag("check_" + index);
			if (check != null) {
				if (selectedKeys.contains(key)) {
					check.setText("[✓]");
					check.setTextColor(0xFF4CAF50);
				} else {
					check.setText("[ ]");
					check.setTextColor(0xFF888888);
				}
			}
		}
		updateCountText();
	}

	private void updateCountText() {
		if (tvSelectedCount != null) {
			tvSelectedCount.setText("已选 " + selectedKeys.size() + " / " + allApps.size() + " 项");
		}
	}

	// ---- 保存选择 ----

	private void saveSelection() {
		List<ShortcutApp> result = new ArrayList<>();
		for (NokiaAppItem app : allApps) {
			String key = makeKeyForItem(app);
			if (!selectedKeys.contains(key)) continue;

			if (app.launchIntent != null && app.launchIntent.getComponent() != null) {
				String cls = app.launchIntent.getComponent().getClassName();
				if (cls != null && cls.startsWith("j2me:")) {
					// J2ME 应用格式: j2me:label:pathExt
					String[] parts = cls.split(":", 3);
					String j2meLabel = parts.length > 1 ? parts[1] : app.label;
					String j2mePath = parts.length > 2 ? parts[2] : "";
					String iconPathStr = null;
					// 查找图标路径
					String imgPath = Config.getAppDir() + new File(j2mePath).getName() + "/icon.png";
					if (new File(imgPath).exists()) {
						iconPathStr = imgPath;
					}
					result.add(new ShortcutApp(ShortcutApp.TYPE_J2ME, j2meLabel, j2mePath, iconPathStr));
				} else {
					// 安卓应用
					Intent launch = new Intent(Intent.ACTION_MAIN);
					launch.addCategory(Intent.CATEGORY_LAUNCHER);
					launch.setClassName(
							app.launchIntent.getComponent().getPackageName(),
							app.launchIntent.getComponent().getClassName());
					launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
					String appKey = app.launchIntent.getComponent().getPackageName()
							+ "/" + app.launchIntent.getComponent().getClassName();
					result.add(new ShortcutApp(ShortcutApp.TYPE_ANDROID, app.label, appKey, launch));
				}
			}
		}

		settingsStorage.setShortcutApps(result);
		NokiaLog.i("ShortcutSettings", "保存 " + result.size() + " 个快捷栏应用");
		// 保存后返回上一级
		((NokiaDesktopActivity) requireActivity()).exitCurrent();
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
				return true;
			default:
				return false;
		}
	}

	@Override
	public boolean onSelect() {
		if (focusIndex >= 0 && focusIndex < allApps.size()) {
			toggleSelection(focusIndex);
		}
		return true;
	}

	@Override
	public boolean onSoftLeft() {
		NokiaLog.i("ShortcutSettings", "左软键：保存并返回");
		saveSelection();
		return true;
	}

	@Override
	public boolean onSoftRight() {
		NokiaLog.i("ShortcutSettings", "右软键：不保存返回");
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
		scrollToVisible(index);
	}

	/**
	 * 确保焦点行在 ScrollView 可见区域内，方向键导航时自动跟随滚动。
	 */
	private void scrollToVisible(int index) {
		if (appScroll == null || itemViews == null || index < 0 || index >= itemViews.length) return;
		View item = itemViews[index];
		if (item == null) return;
		appScroll.post(() -> {
			int scrollY = appScroll.getScrollY();
			int itemTop = item.getTop();
			int itemBottom = item.getBottom();
			int svHeight = appScroll.getHeight();
			if (svHeight <= 0) return;
			if (itemTop < scrollY) {
				appScroll.smoothScrollTo(0, itemTop);
				NokiaLog.d("ShortcutSettings", "↑ 滚动至 item " + index + " top=" + itemTop);
			} else if (itemBottom > scrollY + svHeight) {
				appScroll.smoothScrollTo(0, itemBottom - svHeight);
				NokiaLog.d("ShortcutSettings", "↓ 滚动至 item " + index + " bottom=" + itemBottom + " svH=" + svHeight);
			}
		});
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
}
