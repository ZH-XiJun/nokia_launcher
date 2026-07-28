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
import android.view.MotionEvent;
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

	/**
	 * 第一页固定槽位（参照诺基亚 S60 功能表布局）。
	 * 每个槽位是一组候选包名（优先级从高到低），命中第一个即固定到前排；
	 * 全部候选都不存在则跳过该槽位（不占位，后面应用自动补上）。
	 * 显示名与图标沿用真实应用，保证可识别。
	 */
	private static final String[][] PINNED_SLOTS = {
			// 1 日历
			{"com.android.calendar", "com.google.android.calendar", "com.miui.calendar",
					"com.samsung.android.calendar", "com.huawei.calendar"},
			// 2 名片夹（联系人）
			{"com.android.contacts", "com.google.android.contacts",
					"com.samsung.android.app.contacts"},
			// 3 通讯记录（拨号/电话）
			{"com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer"},
			// 4 网络（浏览器）
			{"com.android.browser", "com.android.chrome", "com.mi.globalbrowser",
					"com.huawei.browser", "com.UCMobile", "com.tencent.mtt"},
			// 5 信息
			{"com.android.mms", "com.google.android.apps.messaging", "com.android.messaging",
					"com.samsung.android.messaging"},
			// 6 多媒体（图库/相册）
			{"com.android.gallery3d", "com.miui.gallery", "com.google.android.apps.photos",
					"com.huawei.photos", "com.samsung.android.gallery"},
			// 7 文件（参考图"共享"位 → 安卓文件管理器）
			{"com.android.fileexplorer", "com.mi.android.globalFileexplorer",
					"com.android.documentsui", "com.google.android.documentsui",
					"com.huawei.hidisk"},
			// 8 商店
			{"com.android.vending", "com.xiaomi.market", "com.huawei.appmarket",
					"com.heytap.market", "com.oppo.market", "com.bbk.appstore"},
			// 9 相机
			{"com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera",
					"com.huawei.camera", "com.samsung.android.camera"},
			// 10 设置
			{"com.android.settings"},
	};

	/** 与 PINNED_SLOTS 一一对应的 S60 风格图标资源 ID */
	private static final int[] PINNED_SLOT_ICONS = {
			R.drawable.s60_calendar,   // 1 日历
			R.drawable.s60_contacts,   // 2 名片夹
			R.drawable.s60_call_log,   // 3 通讯记录
			R.drawable.s60_browser,    // 4 网络
			R.drawable.s60_mms,        // 5 信息
			R.drawable.s60_gallery,    // 6 多媒体
			R.drawable.s60_files,      // 7 文件
			R.drawable.s60_app,        // 8 商店
			R.drawable.s60_camera,     // 9 相机
			R.drawable.s60_settings,   // 10 设置
	};

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

	/** 滑动翻页阈值（px，由 dp 换算）与最小速度（px/ms） */
	private float swipeThreshold;
	private float swipeMinVel;
	/** 复用于根视图与每个 cell 的滑动手势监听 */
	private View.OnTouchListener swipeTouchListener;

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
		// 功能表页：左"选择"、右"退出"，中间按钮隐藏避免蓝色空块
		TextView bl = host.findViewById(R.id.bottomLeft);
		if (bl != null) bl.setText("选择");
		TextView br = host.findViewById(R.id.bottomRight);
		if (br != null) {
			br.setText("退出");
			br.setOnClickListener(v -> host.exitCurrent());
		}
		host.setBottomBar("选择", null, "退出");

		appGrid = view.findViewById(R.id.appGrid);
		tvPage = view.findViewById(R.id.menuPage);

		computeRowsPerPage();
		// 按 perPage 分配页内缓存数组
		cellViews = new View[perPage];
		pageItems = new NokiaAppItem[perPage];

		// 先初始化滑动监听（在 buildCurrentPage 之前，使每个 cell 都能挂载）
		initSwipeListener(view);

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

		// 初始化 S60 图标缓存（仅在应用变化时重新扫描意图）
		NokiaS60IconMap.init(pm);

		Intent main = new Intent(Intent.ACTION_MAIN, null);
		main.addCategory(Intent.CATEGORY_LAUNCHER);
		List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
		NokiaLog.i("Menu", "queryIntentActivities 返回 " + list.size() + " 个可启动应用");

		// 先全部放入临时池 pool，后续再按固定槽位提取
		List<NokiaAppItem> pool = new ArrayList<>();
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
			NokiaAppItem item = new NokiaAppItem(NokiaAppItem.TYPE_APP, label, icon, launch);

			// 尝试替换为 S60 风格图标
			int s60IconRes = NokiaS60IconMap.getIcon(ai.packageName);
			if (s60IconRes != 0) {
				Drawable s60Icon = safeDrawable(host, s60IconRes);
				if (s60Icon != null) {
					item.icon = s60Icon;
				}
			}

			pool.add(item);
		}

		// 按名称排序，保证 pool 中的顺序稳定
		Collections.sort(pool, new Comparator<NokiaAppItem>() {
			@Override
			public int compare(NokiaAppItem a, NokiaAppItem b) {
				return a.label.compareToIgnoreCase(b.label);
			}
		});

		// —— 第一页固定槽位：按参考图顺序从应用池点名 ——
		List<NokiaAppItem> pinned = new ArrayList<>();
		for (int s = 0; s < PINNED_SLOTS.length; s++) {
			NokiaAppItem hit = null;
			for (String pkg : PINNED_SLOTS[s]) {
				hit = pollByPackage(pool, pkg);
			if (hit != null) {
				NokiaLog.d("Menu", "固定槽位 " + (s + 1) + " 命中: " + pkg + " -> " + hit.label);
				// 替换为 S60 风格图标
				Drawable s60icon = safeDrawable(host, PINNED_SLOT_ICONS[s]);
				if (s60icon != null) {
					hit.icon = s60icon;
					NokiaLog.d("Menu", "  -> 已替换为 S60 图标");
				}
				break;
			}
		}
		if (hit != null) {
			pinned.add(hit);
			} else {
				NokiaLog.d("Menu", "固定槽位 " + (s + 1) + " 未命中，跳过（候选包均不存在）");
			}
		}

		// 最终顺序：固定槽位 → 百宝箱 → 按键绑定 → S60匹配应用（按名） → 未匹配应用（按名）
		items.addAll(pinned);

		// 百宝箱图标：优先用 S60 应用程序图标
		Drawable boxIcon = safeDrawable(host, R.drawable.s60_app);
		if (boxIcon == null) boxIcon = safeDrawable(host, R.drawable.ic_nokia_box);
		Drawable kbIcon = safeDrawable(host, R.drawable.ic_nokia_settings);
		items.add(new NokiaAppItem(NokiaAppItem.TYPE_BOX, "百宝箱", boxIcon, null));
		items.add(new NokiaAppItem(NokiaAppItem.TYPE_KEYBIND, "按键绑定", kbIcon, null));

		// 将 pool 拆分为已匹配 S60 图标 和 未匹配，匹配的排在前面
		List<NokiaAppItem> matchedPool = new ArrayList<>();
		List<NokiaAppItem> unmatchedPool = new ArrayList<>();
		for (NokiaAppItem app : pool) {
			int resId = NokiaS60IconMap.getIconForItem(app);
			if (resId != 0) {
				matchedPool.add(app);
			} else {
				unmatchedPool.add(app);
			}
		}
		// 两组内部均按名称排序
		Comparator<NokiaAppItem> labelCmp = (a, b) -> a.label.compareToIgnoreCase(b.label);
		Collections.sort(matchedPool, labelCmp);
		Collections.sort(unmatchedPool, labelCmp);

		items.addAll(matchedPool);
		items.addAll(unmatchedPool);

		NokiaLog.i("Menu", "最终列表（固定槽位 " + pinned.size() + " + 特殊入口 + 匹配 " + matchedPool.size() + " + 未匹配 " + unmatchedPool.size() + "）共 " + items.size() + " 项");
		totalPages = Math.max(1, (int) Math.ceil((double) items.size() / perPage));
	}

	/** 在应用池中按包名查找第一个命中的项并移除，返回之；未命中返回 null。 */
	@Nullable
	private static NokiaAppItem pollByPackage(List<NokiaAppItem> pool, String pkg) {
		for (int i = 0; i < pool.size(); i++) {
			NokiaAppItem app = pool.get(i);
			if (app.launchIntent != null && app.launchIntent.getComponent() != null
					&& pkg.equals(app.launchIntent.getComponent().getPackageName())) {
				pool.remove(i);
				return app;
			}
		}
		return null;
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
					cell.setOnTouchListener(swipeTouchListener);
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
					// 已到本页顶部 → 翻上一页
					pagePrev();
				}
				return true;
			case NokiaKeyBinding.ACTION_DOWN:
				if (row < rowsPerPage - 1 && (pos + COLS) < count) {
					// 页内下移
					setFocusPos(pos + COLS);
				} else if (pageIndex < totalPages - 1) {
					// 已到本页底部 → 翻下一页
					pageNext();
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

	// ---- 翻页（方向键 / 滑动共用） ----

	/** 翻到下一页，保持当前焦点列置于下一页首行（"一直往下"的延续）。 */
	private void pageNext() {
		if (!isAdded() || getView() == null) return;
		int col = focusPos % COLS;
		if (pageIndex < totalPages - 1) {
			pageIndex++;
			rebuildAndFocusCol(col, col);
			NokiaLog.d("Menu", "翻页(下/左滑) -> " + (pageIndex + 1) + "/" + totalPages
					+ " col=" + col);
		}
	}

	/** 翻到上一页，保持当前焦点列置于上一页末行（"一直往上"的延续）。 */
	private void pagePrev() {
		if (!isAdded() || getView() == null) return;
		int col = focusPos % COLS;
		if (pageIndex > 0) {
			pageIndex--;
			rebuildAndFocusCol(col, (rowsPerPage - 1) * COLS + col);
			NokiaLog.d("Menu", "翻页(上/右滑) -> " + (pageIndex + 1) + "/" + totalPages
					+ " col=" + col);
		}
	}

	/**
	 * 初始化滑动翻页手势监听并挂载到根视图。
	 * 同一监听实例也会在 buildCurrentPage() 中挂载到每个 cell，
	 * 因为 cell 是 clickable 的、会消费触摸事件，若不挂载到 cell 则滑过图标时无法翻页。
	 * 判定规则：上滑/左滑 → 下一页；下滑/右滑 → 上一页。
	 * 位移或速度任一达到阈值即判定为滑动并消费事件（避免误触 item 点击）；
	 * 否则不消费，事件继续下发，cell 的 onClick 正常启动应用。
	 */
	private void initSwipeListener(View root) {
		swipeThreshold = dp(24);   // 位移阈值（dp）
		swipeMinVel = 0.35f;       // 速度阈值（px/ms，快速轻扫也翻页）
		swipeTouchListener = new View.OnTouchListener() {
			private float downX, downY;
			private long downTime;

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						downX = event.getX();
						downY = event.getY();
						downTime = event.getEventTime();
						// clickable 的 app cell 不消费 down，留给 onClick；
						// 非 clickable 的视图（根布局/midPanel/空白区）必须消费 down，
						// 否则系统不再下发后续 MOVE/UP，空白处滑动失效。
						return !v.isClickable();
					case MotionEvent.ACTION_UP: {
						float dx = event.getX() - downX;
						float dy = event.getY() - downY;
						long dt = event.getEventTime() - downTime;
						float dist = Math.max(Math.abs(dx), Math.abs(dy));
						float vel = dt > 0 ? dist / (float) dt : 0f;
						if (dist >= swipeThreshold
								|| (dist >= swipeThreshold * 0.5f && vel >= swipeMinVel)) {
							if (Math.abs(dx) >= Math.abs(dy)) {
								if (dx < 0) pageNext(); else pagePrev();
							} else {
								if (dy < 0) pageNext(); else pagePrev();
							}
							return true; // 消费：阻止本次抬起触发 item 点击
						}
						return false; // 非滑动：交给 cell 的 onClick 启动应用
					}
					default:
						return false;
				}
			}
		};
		root.setOnTouchListener(swipeTouchListener);
		// 同时挂载到 midPanel，覆盖碎片根视图没铺满的空白壁纸区域
		View mid = requireActivity().findViewById(R.id.midPanel);
		if (mid != null) {
			mid.setOnTouchListener(swipeTouchListener);
			NokiaLog.d("Menu", "initSwipeListener 已挂载到 midPanel（覆盖空白区）");
		}
		NokiaLog.d("Menu", "initSwipeListener 已挂载滑动翻页监听（根视图 + midPanel + 每个 cell 复用）");
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
