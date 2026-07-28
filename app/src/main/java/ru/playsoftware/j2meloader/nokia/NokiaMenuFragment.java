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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ru.playsoftware.j2meloader.R;

/**
 * 功能表（应用网格）中间内容碎片。
 * 通过 PackageManager 枚举所有可启动的安卓应用，分页以 3 列网格展示真实 APP 图标。
 * 方向键在页内移动焦点：左/右到边界时翻到上/下一页；确认键启动对应 APP。
 * 末尾追加「百宝箱」「按键绑定」两个特殊入口，保留原功能可达性。
 */
public class NokiaMenuFragment extends Fragment implements NokiaFocusHost {

	/** 列数固定 3 列（诺基亚经典风格） */
	private static final int COLS = 3;
	/** 每格设计高度（dp），图标 36 + 标签 9 + 间距 */
	private static final int ROW_H_DP = 64;
	/** 标题预留高度（dp） */
	private static final int TITLE_H_DP = 20;
	/** 顶/底栏占用的设计高度（dp），用于估算可用网格高度 */
	private static final int BAR_H_DP = 44;

	private final ArrayList<NokiaAppItem> items = new ArrayList<>();
	private LinearLayout appGrid;
	private TextView tvPage;

	/** 每页行数（按分辨率/可用高度自适应，区间 [3,8]） */
	private int rowsPerPage = 4;
	/** 每页格子数 = COLS * rowsPerPage */
	private int perPage = COLS * rowsPerPage;
	private int totalPages = 1;
	private int pageIndex = 0;
	/** 当前页内焦点位置（0..perPage-1） */
	private int focusPos = 0;

	private View[] cellViews;
	private NokiaAppItem[] pageItems;
	private View selectedView = null;

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
		host.scaleMidContent(view, false);

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

		appGrid = view.findViewById(R.id.appGrid);
		tvPage = view.findViewById(R.id.menuPage);

		computeRowsPerPage();
		// 按 perPage 分配页内缓存数组
		cellViews = new View[perPage];
		pageItems = new NokiaAppItem[perPage];

		loadApps();
		buildCurrentPage();
		setFocusPos(0);

		NokiaLog.i("Menu", "功能表初始化完成：共 " + items.size() + " 项，"
				+ totalPages + " 页，每页 " + perPage + " 格（" + COLS + "×" + rowsPerPage + "）");
	}

	// ---- 分辨率自适应：计算每页行数 ----

	private void computeRowsPerPage() {
		android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
		float density = dm.density;
		float widthDp = dm.widthPixels / density;
		float heightDp = dm.heightPixels / density;
		float scale = widthDp / 240f;
		if (320f * scale > heightDp) {
			scale = heightDp / 320f;
		}
		// 可用网格设计高度 = (屏幕高 - 顶/底栏) / 缩放比
		float availDesign = (heightDp - BAR_H_DP) / scale;
		int rows = (int) ((availDesign - TITLE_H_DP) / ROW_H_DP);
		rows = Math.max(3, Math.min(8, rows));
		rowsPerPage = rows;
		perPage = COLS * rowsPerPage;
		NokiaLog.i("Menu", "computeRowsPerPage: rowsPerPage=" + rowsPerPage
				+ " scale=" + scale + " widthDp=" + widthDp + " heightDp=" + heightDp
				+ " availDesign=" + availDesign);
	}

	// ---- 加载真实安卓应用 ----

	private void loadApps() {
		items.clear();
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		PackageManager pm = host.getPackageManager();
		Intent main = new Intent(Intent.ACTION_MAIN, null);
		main.addCategory(Intent.CATEGORY_LAUNCHER);
		List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
		NokiaLog.i("Menu", "queryIntentActivities 返回 " + list.size() + " 个可启动应用");

		String selfPkg = host.getPackageName();
		for (ResolveInfo ri : list) {
			ActivityInfo ai = ri.activityInfo;
			if (ai == null) {
				NokiaLog.w("Menu", "跳过空 activityInfo");
				continue;
			}
			if (ai.packageName.equals(selfPkg)) {
				NokiaLog.d("Menu", "排除桌面自身: " + ai.packageName);
				continue;
			}
			CharSequence labelCs = ri.loadLabel(pm);
			String label = (labelCs != null && labelCs.length() > 0) ? labelCs.toString() : ai.name;
			Drawable icon = ri.loadIcon(pm);
			Intent launch = new Intent(Intent.ACTION_MAIN);
			launch.addCategory(Intent.CATEGORY_LAUNCHER);
			launch.setClassName(ai.packageName, ai.name);
			launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
			items.add(new NokiaAppItem(NokiaAppItem.TYPE_APP, label, icon, launch));
		}

		// 按名称排序，保证顺序稳定
		Collections.sort(items, new Comparator<NokiaAppItem>() {
			@Override
			public int compare(NokiaAppItem a, NokiaAppItem b) {
				return a.label.compareToIgnoreCase(b.label);
			}
		});

		// 追加特殊入口：百宝箱、按键绑定
		Drawable boxIcon = safeDrawable(host, R.drawable.ic_nokia_box);
		Drawable kbIcon = safeDrawable(host, R.drawable.ic_nokia_settings);
		items.add(new NokiaAppItem(NokiaAppItem.TYPE_BOX, "百宝箱", boxIcon, null));
		items.add(new NokiaAppItem(NokiaAppItem.TYPE_KEYBIND, "按键绑定", kbIcon, null));

		NokiaLog.i("Menu", "最终列表（含特殊入口）共 " + items.size() + " 项");
		totalPages = Math.max(1, (int) Math.ceil((double) items.size() / perPage));
	}

	private Drawable safeDrawable(NokiaDesktopActivity host, int resId) {
		try {
			return ContextCompat.getDrawable(host, resId);
		} catch (Exception e) {
			NokiaLog.w("Menu", "加载图标失败 res=" + resId);
			return null;
		}
	}

	// ---- 构建当前页网格 ----

	private void buildCurrentPage() {
		if (appGrid == null) return;
		appGrid.removeAllViews();
		// 重置页内缓存
		for (int i = 0; i < perPage; i++) {
			cellViews[i] = null;
			pageItems[i] = null;
		}

		int start = pageIndex * perPage;
		int count = Math.min(perPage, items.size() - start);
		NokiaLog.d("Menu", "buildCurrentPage 页=" + (pageIndex + 1) + "/" + totalPages
				+ " start=" + start + " count=" + count + " rows=" + rowsPerPage);

		for (int r = 0; r < rowsPerPage; r++) {
			LinearLayout row = new LinearLayout(requireContext());
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, dp(ROW_H_DP)));

			for (int c = 0; c < COLS; c++) {
				int pos = r * COLS + c;
				LinearLayout cell = new LinearLayout(requireContext());
				cell.setOrientation(LinearLayout.VERTICAL);
				cell.setGravity(Gravity.CENTER);
				cell.setLayoutParams(new LinearLayout.LayoutParams(
						0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
				cell.setPadding(dp(4), dp(4), dp(4), dp(4));

				if (pos < count) {
					NokiaAppItem item = items.get(start + pos);
					pageItems[pos] = item;

					ImageView iv = new ImageView(requireContext());
					iv.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
					if (item.icon != null) {
						iv.setImageDrawable(item.icon);
					}
					TextView tv = new TextView(requireContext());
					tv.setLayoutParams(new LinearLayout.LayoutParams(
							LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
					tv.setText(item.label);
					tv.setTextColor(0xFFFFFFFF);
					tv.setTextSize(9);
					tv.setSingleLine(true);
					tv.setEllipsize(TextUtils.TruncateAt.END);
					tv.setMaxWidth(dp(72));
					cell.addView(iv);
					cell.addView(tv);

					final int fpos = pos;
					cell.setClickable(true);
					cell.setOnClickListener(v -> {
						setFocusPos(fpos);
						onSelect();
					});
					cellViews[pos] = cell;
				}
				row.addView(cell);
			}
			appGrid.addView(row);
		}

		if (tvPage != null) {
			tvPage.setText((pageIndex + 1) + "/" + totalPages);
		}
	}

	private int dp(int v) {
		return (int) (v * getResources().getDisplayMetrics().density);
	}

	// ---- NokiaFocusHost 接口 ----

	@Override
	public boolean onDirection(int direction) {
		int pos = focusPos;
		int row = pos / COLS;
		int col = pos % COLS;
		int count = Math.min(perPage, items.size() - pageIndex * perPage);

		switch (direction) {
			case NokiaKeyBinding.ACTION_UP:
				if (row > 0 && (pos - COLS) < count) {
					// 页内上移
					setFocusPos(pos - COLS);
				} else if (pageIndex > 0) {
					// 已到本页顶部 → 翻上一页，保持当前列置于末行，体现"一直往上"
					pageIndex--;
					rebuildAndFocusCol(col, (rowsPerPage - 1) * COLS + col);
					NokiaLog.d("Menu", "翻页(上) -> " + (pageIndex + 1) + "/" + totalPages);
				}
				return true;
			case NokiaKeyBinding.ACTION_DOWN:
				if (row < rowsPerPage - 1 && (pos + COLS) < count) {
					// 页内下移
					setFocusPos(pos + COLS);
				} else if (pageIndex < totalPages - 1) {
					// 已到本页底部 → 翻下一页，保持当前列置于首行，体现"一直往下"
					pageIndex++;
					rebuildAndFocusCol(col, col);
					NokiaLog.d("Menu", "翻页(下) -> " + (pageIndex + 1) + "/" + totalPages);
				}
				return true;
			case NokiaKeyBinding.ACTION_LEFT:
				// 仅在本行内移动：到最左端回绕到本行最右端
				if (col > 0) {
					setFocusPos(pos - 1);
				} else {
					setFocusPos(pos + COLS - 1);
				}
				return true;
			case NokiaKeyBinding.ACTION_RIGHT:
				// 仅在本行内移动：到最右端回绕到本行最左端
				if (col < COLS - 1) {
					setFocusPos(pos + 1);
				} else {
					setFocusPos(pos - (COLS - 1));
				}
				return true;
			default:
				return false;
		}
	}

	/**
	 * 翻页后重建当前页并把焦点定位到目标位置（按列保持连续性）。
	 * @param col     要保持的列
	 * @param desired 期望的页内位置（行×COLS+col），会收敛到本页实际项数范围内
	 */
	private void rebuildAndFocusCol(int col, int desired) {
		buildCurrentPage();
		int newCount = Math.min(perPage, items.size() - pageIndex * perPage);
		int newPos = desired;
		if (newPos >= newCount) {
			newPos = Math.max(0, newCount - 1);
		}
		focusPos = newPos;
		applyFocusBackground();
	}

	@Override
	public boolean onSelect() {
		int global = pageIndex * perPage + focusPos;
		if (global < 0 || global >= items.size()) {
			NokiaLog.w("Menu", "onSelect 越界 global=" + global);
			return false;
		}
		NokiaAppItem item = items.get(global);
		if (item == null) return false;
		NokiaLog.i("Menu", "onSelect type=" + item.type + " label=" + item.label);

		if (item.type == NokiaAppItem.TYPE_BOX) {
			((NokiaDesktopActivity) requireActivity()).openBox();
			return true;
		}
		if (item.type == NokiaAppItem.TYPE_KEYBIND) {
			((NokiaDesktopActivity) requireActivity()).openFragment(new NokiaKeyBindFragment());
			return true;
		}
		if (item.launchIntent != null) {
			try {
				startActivity(item.launchIntent);
				NokiaLog.i("Menu", "启动应用 " + item.label);
			} catch (Exception e) {
				NokiaLog.e("Menu", "启动失败 " + item.label, e);
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean onSoftLeft() {
		return onSelect(); // 左软键 = "选择"
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

	// ---- 内部逻辑 ----

	private void setFocusPos(int pos) {
		if (pos < 0 || pos >= perPage) return;
		clearFocusBackground();
		focusPos = pos;
		applyFocusBackground();
	}

	private void clearFocusBackground() {
		if (selectedView != null) {
			selectedView.setBackgroundResource(0);
			selectedView = null;
		}
	}

	private void applyFocusBackground() {
		if (focusPos >= 0 && focusPos < cellViews.length) {
			View v = cellViews[focusPos];
			if (v != null) {
				v.setBackgroundResource(R.drawable.bg_nokia_selected_dark);
				selectedView = v;
				return;
			}
		}
		selectedView = null;
	}
}
