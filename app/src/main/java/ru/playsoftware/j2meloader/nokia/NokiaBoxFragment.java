package ru.playsoftware.j2meloader.nokia;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.applist.AppListModel;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.filepicker.FilteredFilePickerFragment;
import ru.playsoftware.j2meloader.util.Constants;
import ru.playsoftware.j2meloader.util.FileUtils;
import ru.woesss.j2me.installer.InstallerDialog;

/**
 * 应用程序中间内容碎片。
 * 双模式：网格模式展示"安装jar"入口 + 已装 JAR 应用网格；
 * 次级菜单模式展示"启动"/"设置"两个选项。
 * 通过方向键导航，确认键交互，复用 J2ME-Loader 原有的安装与启动逻辑。
 */
public class NokiaBoxFragment extends Fragment implements NokiaFocusHost {

	// ---- 模式常量 ----
	private static final int MODE_GRID = 0;
	private static final int MODE_SUBMENU = 1;

	private static final int SUBMENU_LAUNCH = 0;
	private static final int SUBMENU_SETTINGS = 1;

	// ---- 网格常量 ----
	private static final int COLS = 3;
	private static final int ROW_H_DP = 64;
	private static final int TITLE_H_DP = 20;
	private static final int BAR_H_DP = 44;

	// ---- 视图 ----
	private ScrollView appScroll;
	private LinearLayout appContainer;

	// ---- 网格模式 ----
	private View[] gridCellViews;
	private int rowsPerPage = 4;
	private int perPage = COLS * rowsPerPage;
	private int totalGridCells = 0;
	private int focusIndex = -1;
	private View selectedView = null;

	// ---- 次级菜单 ----
	private View[] subMenuItemViews;
	private AppItem selectedAppItem;
	private int subFocusIndex = -1;

	// ---- 数据 ----
	private AppRepository appRepository;
	private List<AppItem> appItems = new ArrayList<>();
	private SharedPreferences preferences;

	// ---- 文件选择器 ----
	private ActivityResultLauncher<String> openFileLauncher;

	// ---- 当前模式 ----
	private int mode = MODE_GRID;

	// ============================
	// 生命周期
	// ============================

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		NokiaLog.i("Box", "onCreate");

		// 注册文件选择器（必须在 onCreate 前/内注册）
		openFileLauncher = registerForActivityResult(
				FileUtils.getFilePicker(), this::onPickFileResult);

		preferences = PreferenceManager.getDefaultSharedPreferences(requireActivity());

		// 获取 AppRepository
		AppListModel appListModel = new ViewModelProvider(requireActivity()).get(AppListModel.class);
		appRepository = appListModel.getAppRepository();
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
							 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_nokia_box, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		host.scaleMidContent(view, false);

		View wall = host.findViewById(R.id.wallpaper);
		if (wall != null) {
			wall.setBackgroundResource(R.drawable.bg_nokia_box);
		}
		TextView title = host.findViewById(R.id.topTitle);
		if (title != null) {
			title.setText("应用程序");
		}
		host.setBottomBar("选择", null, "退出");

		appScroll = view.findViewById(R.id.appScroll);
		appContainer = view.findViewById(R.id.appContainer);

		computeRowsPerPage();

		// 订阅已安装 JAR 应用数据
		appRepository.observeApps(getViewLifecycleOwner(), this::onDbUpdated);

		NokiaLog.i("Box", "应用程序初始化完成，等待数据加载…");
	}

	// ============================
	// 分辨率自适应
	// ============================

	private void computeRowsPerPage() {
		android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
		float density = dm.density;
		float widthDp = dm.widthPixels / density;
		float heightDp = dm.heightPixels / density;
		float scale = widthDp / 240f;
		if (320f * scale > heightDp) {
			scale = heightDp / 320f;
		}
		float availDesign = (heightDp - BAR_H_DP) / scale;
		int rows = (int) ((availDesign - TITLE_H_DP) / ROW_H_DP);
		rows = Math.max(3, Math.min(8, rows));
		rowsPerPage = rows;
		perPage = COLS * rowsPerPage;
		NokiaLog.i("Box", "computeRowsPerPage: rowsPerPage=" + rowsPerPage
				+ " scale=" + scale + " widthDp=" + widthDp
				+ " heightDp=" + heightDp + " availDesign=" + availDesign);
	}

	// ============================
	// 数据回调
	// ============================

	private void onDbUpdated(List<AppItem> items) {
		NokiaLog.i("Box", "onDbUpdated 收到 " + (items != null ? items.size() : 0) + " 个应用");
		appItems = items != null ? items : new ArrayList<>();
		if (mode == MODE_GRID) {
			buildGrid();
		}
	}

	// ============================
	// 构建网格模式
	// ============================

	private void buildGrid() {
		if (appContainer == null) return;
		appContainer.removeAllViews();
		totalGridCells = 2 + appItems.size(); // 安装 + JAR 全局设置 + 已装应用
		int totalRows = (int) Math.ceil((double) totalGridCells / COLS);
		gridCellViews = new View[totalGridCells];
		subMenuItemViews = null;

		NokiaLog.i("Box", "buildGrid: totalCells=" + totalGridCells
				+ " rows=" + totalRows + " apps=" + appItems.size());

		for (int r = 0; r < totalRows; r++) {
			LinearLayout row = createGridRow();

			for (int c = 0; c < COLS; c++) {
				int pos = r * COLS + c;
				LinearLayout cell = createGridCell();

				if (pos < totalGridCells) {
					cell.setClickable(true);
					final int fpos = pos;
					cell.setOnClickListener(v -> {
						setFocusIndex(fpos);
						onSelect();
					});

					if (pos == 0) {
						// "安装" 入口
						populateInstallCell(cell);
					} else if (pos == 1) {
						// "JAR 全局设置" 入口
						populateGlobalProfileCell(cell);
					} else {
						// JAR 应用
						AppItem app = appItems.get(pos - 2);
						populateAppCell(cell, app);
					}
					gridCellViews[pos] = cell;
				}
				row.addView(cell);
			}
			appContainer.addView(row);
		}

		// 恢复焦点
		if (focusIndex >= totalGridCells) focusIndex = totalGridCells - 1;
		if (focusIndex < 0 && totalGridCells > 0) focusIndex = 0;
		applyFocusGrid();
	}

	private LinearLayout createGridRow() {
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, dp(ROW_H_DP)));
		return row;
	}

	private LinearLayout createGridCell() {
		LinearLayout cell = new LinearLayout(requireContext());
		cell.setOrientation(LinearLayout.VERTICAL);
		cell.setGravity(Gravity.CENTER);
		cell.setLayoutParams(new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
		cell.setPadding(dp(4), dp(4), dp(4), dp(4));
		return cell;
	}

	private void populateInstallCell(LinearLayout cell) {
		ImageView iv = new ImageView(requireContext());
		iv.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
		try {
			Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.s60_app);
			if (icon != null) iv.setImageDrawable(icon);
		} catch (Exception ignored) {}
		cell.addView(iv);

		TextView tv = new TextView(requireContext());
		tv.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		tv.setText("安装");
		tv.setTextColor(0xFFFFFFFF);
		tv.setTextSize(9);
		tv.setSingleLine(true);
		tv.setEllipsize(TextUtils.TruncateAt.END);
		tv.setMaxWidth(dp(72));
		cell.addView(tv);
	}

	private void populateGlobalProfileCell(LinearLayout cell) {
		ImageView iv = new ImageView(requireContext());
		iv.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
		try {
			Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.s60_settings);
			if (icon != null) iv.setImageDrawable(icon);
		} catch (Exception ignored) {}
		cell.addView(iv);

		TextView tv = new TextView(requireContext());
		tv.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		tv.setText("JAR 全局设置");
		tv.setTextColor(0xFFFFFFFF);
		tv.setTextSize(9);
		tv.setSingleLine(true);
		tv.setEllipsize(TextUtils.TruncateAt.END);
		tv.setMaxWidth(dp(72));
		cell.addView(tv);
	}

	private void populateAppCell(LinearLayout cell, AppItem app) {
		ImageView iv = new ImageView(requireContext());
		iv.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
		// 加载 JAR 图标
		String imgPath = app.getImagePathExt();
		if (imgPath != null) {
			try {
				Drawable icon = Drawable.createFromPath(imgPath);
				if (icon != null) iv.setImageDrawable(icon);
			} catch (Exception e) {
				NokiaLog.w("Box", "加载图标失败: " + imgPath + " " + e.getMessage());
			}
		}
		cell.addView(iv);

		TextView tv = new TextView(requireContext());
		tv.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		tv.setText(app.getTitle());
		tv.setTextColor(0xFFFFFFFF);
		tv.setTextSize(9);
		tv.setSingleLine(true);
		tv.setEllipsize(TextUtils.TruncateAt.END);
		tv.setMaxWidth(dp(72));
		cell.addView(tv);
	}

	// ============================
	// 构建次级菜单模式
	// ============================

	private void enterSubMenu(AppItem app) {
		selectedAppItem = app;
		mode = MODE_SUBMENU;

		appContainer.removeAllViews();
		subMenuItemViews = new View[2];
		subFocusIndex = -1;
		gridCellViews = null;

		NokiaLog.i("Box", "enterSubMenu: " + app.getTitle());

		// 更新标题为应用名
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		TextView title = host.findViewById(R.id.topTitle);
		if (title != null) {
			title.setText(app.getTitle());
		}
		// 软键：左"选择"、右"返回"
		host.setBottomBar("选择", null, "返回");

		// 创建"启动"行
		LinearLayout rowLaunch = createSubMenuItem("启动", R.drawable.s60_app, SUBMENU_LAUNCH);
		appContainer.addView(rowLaunch);

		// 创建"设置"行
		LinearLayout rowSettings = createSubMenuItem("设置", R.drawable.s60_settings, SUBMENU_SETTINGS);
		appContainer.addView(rowSettings);

		// 默认焦点在"启动"
		setSubFocusIndex(0);
	}

	private LinearLayout createSubMenuItem(String label, int iconRes, int index) {
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
		row.setPadding(dp(14), dp(6), dp(14), dp(6));
		row.setClickable(true);

		ImageView ivIcon = new ImageView(requireContext());
		ivIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
		try {
			ivIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), iconRes));
		} catch (Exception ignored) {}
		row.addView(ivIcon);

		row.addView(spaceView(dp(10), 1));

		TextView tvName = new TextView(requireContext());
		tvName.setLayoutParams(new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
		tvName.setText(label);
		tvName.setTextColor(0xFFFFFFFF);
		tvName.setTextSize(12);
		row.addView(tvName);

		final int fIdx = index;
		row.setOnClickListener(v -> {
			setSubFocusIndex(fIdx);
			onSelectSubMenu();
		});

		subMenuItemViews[index] = row;
		return row;
	}

	private void exitSubMenu() {
		NokiaLog.i("Box", "exitSubMenu 回到网格");
		mode = MODE_GRID;
		selectedAppItem = null;
		subMenuItemViews = null;
		subFocusIndex = -1;
		focusIndex = -1;

		// 恢复标题和软键
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		TextView title = host.findViewById(R.id.topTitle);
		if (title != null) {
			title.setText("应用程序");
		}
		host.setBottomBar("选择", null, "退出");

		buildGrid();
	}

	// ============================
	// 焦点管理 —— 网格模式
	// ============================

	private void setFocusIndex(int index) {
		if (gridCellViews == null || index < 0 || index >= gridCellViews.length) return;
		clearFocusGrid();
		focusIndex = index;
		applyFocusGrid();
		scrollToVisibleGrid(index);
	}

	private void clearFocusGrid() {
		if (selectedView != null) {
			selectedView.setBackgroundResource(0);
			selectedView = null;
		}
	}

	private void applyFocusGrid() {
		if (focusIndex >= 0 && focusIndex < gridCellViews.length
				&& gridCellViews[focusIndex] != null) {
			gridCellViews[focusIndex].setBackgroundResource(R.drawable.bg_nokia_selected_dark);
			selectedView = gridCellViews[focusIndex];
		}
	}

	private void scrollToVisibleGrid(int index) {
		if (appScroll == null || gridCellViews == null
				|| index < 0 || index >= gridCellViews.length) return;
		View item = gridCellViews[index];
		if (item == null) return;
		appScroll.post(() -> {
			int scrollY = appScroll.getScrollY();
			int itemTop = item.getTop();
			int itemBottom = item.getBottom();
			int svHeight = appScroll.getHeight();
			if (svHeight <= 0) return;
			if (itemTop < scrollY) {
				appScroll.smoothScrollTo(0, itemTop);
			} else if (itemBottom > scrollY + svHeight) {
				appScroll.smoothScrollTo(0, itemBottom - svHeight);
			}
		});
	}

	// ============================
	// 焦点管理 —— 次级菜单
	// ============================

	private void setSubFocusIndex(int index) {
		if (subMenuItemViews == null || index < 0 || index >= subMenuItemViews.length) return;
		clearSubFocus();
		subFocusIndex = index;
		applySubFocus();
	}

	private void clearSubFocus() {
		if (selectedView != null) {
			selectedView.setBackgroundResource(0);
			selectedView = null;
		}
	}

	private void applySubFocus() {
		if (subFocusIndex >= 0 && subFocusIndex < subMenuItemViews.length
				&& subMenuItemViews[subFocusIndex] != null) {
			subMenuItemViews[subFocusIndex].setBackgroundResource(R.drawable.bg_nokia_selected_dark);
			selectedView = subMenuItemViews[subFocusIndex];
		}
	}

	// ============================
	// 文件选择与安装
	// ============================

	private void launchFilePicker() {
		NokiaLog.i("Box", "启动文件选择器");
		String path = preferences.getString(Constants.PREF_LAST_PATH, null);
		if (path == null) {
			File dir = Environment.getExternalStorageDirectory();
			if (dir.canRead()) {
				path = dir.getAbsolutePath();
			}
		}
		try {
			openFileLauncher.launch(path);
		} catch (Exception e) {
			NokiaLog.e("Box", "启动文件选择器失败", e);
		}
	}

	private void onPickFileResult(android.net.Uri uri) {
		if (uri == null) {
			NokiaLog.i("Box", "文件选择器返回 null（用户取消）");
			return;
		}
		NokiaLog.i("Box", "文件选择器返回: " + uri);
		preferences.edit()
				.putString(Constants.PREF_LAST_PATH, FilteredFilePickerFragment.getLastPath())
				.apply();
		InstallerDialog.newInstance(uri).show(getChildFragmentManager(), "installer");
	}

	// ============================
	// NokiaFocusHost —— 方向键
	// ============================

	@Override
	public boolean onDirection(int direction) {
		if (mode == MODE_GRID) return onDirectionGrid(direction);
		else return onDirectionSubMenu(direction);
	}

	private boolean onDirectionGrid(int direction) {
		if (gridCellViews == null || totalGridCells == 0) return false;
		if (focusIndex < 0) {
			setFocusIndex(0);
			return true;
		}
		int row = focusIndex / COLS;
		int col = focusIndex % COLS;
		int totalRows = (int) Math.ceil((double) totalGridCells / COLS);
		int newIdx = focusIndex;

		switch (direction) {
			case NokiaKeyBinding.ACTION_UP:
				if (row > 0) {
					newIdx = focusIndex - COLS;
				}
				break;
			case NokiaKeyBinding.ACTION_DOWN:
				if (row < totalRows - 1) {
					int below = focusIndex + COLS;
					if (below < totalGridCells) newIdx = below;
				}
				break;
			case NokiaKeyBinding.ACTION_LEFT:
				if (col > 0) {
					newIdx = focusIndex - 1;
				} else {
					// 回绕到本行最右
					int rightOfRow = Math.min(row * COLS + COLS - 1, totalGridCells - 1);
					newIdx = rightOfRow;
				}
				break;
			case NokiaKeyBinding.ACTION_RIGHT:
				int rightOfRow = Math.min(row * COLS + COLS - 1, totalGridCells - 1);
				if (col < (rightOfRow % COLS) || focusIndex < rightOfRow) {
					newIdx = focusIndex + 1;
					if (newIdx >= totalGridCells) newIdx = row * COLS; // 回绕到本行最左
				} else {
					newIdx = row * COLS; // 回绕到本行最左
				}
				break;
			default:
				return false;
		}

		if (newIdx != focusIndex) {
			setFocusIndex(newIdx);
		}
		return true;
	}

	private boolean onDirectionSubMenu(int direction) {
		if (subMenuItemViews == null) return false;
		if (subFocusIndex < 0) {
			setSubFocusIndex(0);
			return true;
		}
		switch (direction) {
			case NokiaKeyBinding.ACTION_UP:
				if (subFocusIndex > 0) setSubFocusIndex(subFocusIndex - 1);
				return true;
			case NokiaKeyBinding.ACTION_DOWN:
				if (subFocusIndex < subMenuItemViews.length - 1) setSubFocusIndex(subFocusIndex + 1);
				return true;
			case NokiaKeyBinding.ACTION_LEFT:
			case NokiaKeyBinding.ACTION_RIGHT:
				return true; // 次级菜单不响应左右
			default:
				return false;
		}
	}

	// ============================
	// NokiaFocusHost —— 确认键
	// ============================

	@Override
	public boolean onSelect() {
		if (mode == MODE_GRID) return onSelectGrid();
		else return onSelectSubMenu();
	}

	private boolean onSelectGrid() {
		if (focusIndex < 0 || totalGridCells == 0) return false;

		if (focusIndex == 0) {
			// 安装
			NokiaLog.i("Box", "onSelect: 安装");
			launchFilePicker();
			return true;
		}
		if (focusIndex == 1) {
			// JAR 全局设置
			NokiaLog.i("Box", "onSelect: JAR 全局设置");
			NokiaGlobalProfile.openGlobalSettings(requireContext());
			return true;
		}

		// JAR 应用 → 进入次级菜单
		int appIdx = focusIndex - 2;
		if (appIdx >= 0 && appIdx < appItems.size()) {
			AppItem app = appItems.get(appIdx);
			NokiaLog.i("Box", "onSelect: 进入次级菜单 " + app.getTitle());
			enterSubMenu(app);
			return true;
		}
		return false;
	}

	private boolean onSelectSubMenu() {
		if (subFocusIndex < 0 || selectedAppItem == null) return false;
		NokiaLog.i("Box", "onSelectSubMenu: idx=" + subFocusIndex + " app=" + selectedAppItem.getTitle());

		switch (subFocusIndex) {
			case SUBMENU_LAUNCH:
				NokiaLog.i("Box", "启动应用: " + selectedAppItem.getTitle());
				Config.startApp(requireContext(), selectedAppItem.getTitle(),
						selectedAppItem.getPathExt(), false);
				return true;
			case SUBMENU_SETTINGS:
				NokiaLog.i("Box", "应用设置: " + selectedAppItem.getTitle());
				Config.startApp(requireContext(), selectedAppItem.getTitle(),
						selectedAppItem.getPathExt(), true);
				return true;
			default:
				return false;
		}
	}

	// ============================
	// NokiaFocusHost —— 软键
	// ============================

	@Override
	public boolean onSoftLeft() {
		if (mode == MODE_GRID) return onSelectGrid();
		else return onSelectSubMenu();
	}

	@Override
	public boolean onSoftRight() {
		if (mode == MODE_SUBMENU) {
			exitSubMenu();
		} else {
			((NokiaDesktopActivity) requireActivity()).exitCurrent();
		}
		return true;
	}

	@Override
	public boolean onBack() {
		if (mode == MODE_SUBMENU) {
			exitSubMenu();
			return true;
		}
		((NokiaDesktopActivity) requireActivity()).exitCurrent();
		return true;
	}

	// ============================
	// 工具方法
	// ============================

	private int dp(int v) {
		return (int) (v * getResources().getDisplayMetrics().density);
	}

	private View spaceView(int w, int h) {
		View v = new View(requireContext());
		v.setLayoutParams(new LinearLayout.LayoutParams(w, h));
		return v;
	}
}
