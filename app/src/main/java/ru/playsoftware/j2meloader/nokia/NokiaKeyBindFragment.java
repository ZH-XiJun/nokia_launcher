package ru.playsoftware.j2meloader.nokia;

import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.nokia.NokiaGlobalProfile;

/**
 * 按键绑定设置界面。
 * 列出所有 8 个动作及对应按键，支持方向键导航选中 + 确认键进入录制模式，
 * 按下任意物理键即完成绑定。
 */
public class NokiaKeyBindFragment extends Fragment implements NokiaPage, NokiaKeyRecorder {

	private NokiaKeyBinding keyBinding;
	private View[] itemViews = new View[NokiaKeyBinding.ACTION_COUNT];
	private int focusIndex = 0;
	private boolean recording = false;
	private int recordingAction = -1;

	// 冲突确认模式状态（复用 Fragment 自身导航，不依赖 AlertDialog）
	private boolean confirming = false;
	private int confirmAction = -1;
	private int confirmKeycode = -1;
	private int confirmOccupied = -1;
	private int confirmChoice = 0; // 0=取消, 1=覆盖

	// 进入防抖：用户按确认键进入本界面时，若按键按下时间较长（触发系统 key repeat）
	// 或设备按键去抖差产生重复 DOWN 事件，残留的确认键事件会被本界面误解析为
	// ACTION_SELECT -> onSelect() -> 立即开始录制第一个动作（上）。这里在进入后的一小段时间内
	// 忽略 SELECT（消费但不录制），防止"进入即录制"。
	private static final long ENTRY_DEBOUNCE_MS = 800L;
	private long enteredAt;

	private LinearLayout bindListContainer;
	private LinearLayout recordStatusBar;
	private TextView recordStatusText;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
							 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_nokia_key_bind, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		host.scaleMidContent(view, true);

		// 记录进入时间，用于防抖：进入界面的残留确认键（key repeat）会被忽略，不触发录制
		enteredAt = SystemClock.uptimeMillis();

		// 动态调整根视图高度 = panelH / scale，使内容视觉高度恰好填满中间容器：
		// - 视觉高 < panelH：高度拉高，ScrollView(weight=1) 吸收多余空间（避免底部留白）；
		// - 视觉高 > panelH：若保持布局固定 262dp，scaleMidContent 会触发"缩小分支"
		//   （finalScale = panelH / contentH），宽度随比例同步缩小、右侧出现缝隙
		//   （如 ldpi 的 4a24ecf：宽度缩到 232px，容器 240px，右缝 8px）。
		//   改为目标高度后 visualH == panelH，不触发缩小，宽度保持铺满。
		view.post(() -> {
			View panel = (View) view.getParent();
			if (panel == null || panel.getHeight() <= 0 || view.getHeight() <= 0) {
				return;
			}
			DisplayMetrics dm = getResources().getDisplayMetrics();
			float density = dm.density;
			float widthDp = dm.widthPixels / density;
			float heightDp = dm.heightPixels / density;
			// 与 NokiaBaseActivity.scaleMidContent 完全相同的缩放算法
			float scale = widthDp / 240f;
			if (320f * scale > heightDp) {
				scale = heightDp / 320f;
			}
			if (Math.abs(scale - Math.round(scale)) < 0.04f) {
				scale = Math.round(scale);
			}
			int panelH = panel.getHeight();
			int targetH = Math.round(panelH / scale);
			ViewGroup.LayoutParams lp = view.getLayoutParams();
			if (lp.height != targetH) {
				lp.height = targetH;
				view.setLayoutParams(lp);
				NokiaLog.i("KeyBind", "调整内容高度=" + targetH + "px 使视觉高=panelH=" + panelH
						+ "px scale=" + scale);
				// 高度变化触发重新布局后，重新执行等比缩放（此时 visualH == panelH，
				// 不触发缩小分支，宽度保持铺满）
				view.post(() -> host.scaleMidContent(view, true));
			}
		});

		keyBinding = new NokiaKeyBinding(requireContext());

		bindListContainer = view.findViewById(R.id.bindListContainer);
		recordStatusBar = view.findViewById(R.id.recordStatusBar);
		recordStatusText = view.findViewById(R.id.recordStatusText);

		// 录制状态栏触摸点击 = 取消当前录制（仅触摸触发跳过，返回键在录制态被忽略）
		recordStatusBar.setClickable(true);
		recordStatusBar.setOnClickListener(v -> {
			NokiaLog.i("KeyBind", "触摸点击录制状态栏 -> 取消录制");
			onSkipCurrent();
		});

		// 壁纸设为桌面深蓝渐变，与诺基亚桌面画风一致
		View wall = host.findViewById(R.id.wallpaper);
		if (wall != null) {
			wall.setBackgroundResource(R.drawable.bg_nokia_menu);
		}

		// 底部菜单栏由 NokiaPage 声明 + host.refreshPageBar() 自动装配
		host.refreshPageBar();

		buildList();

		setFocusIndex(0);
	}

	// ---- 构建列表 ----

	private void buildList() {
		bindListContainer.removeAllViews();

		for (int i = 0; i < NokiaKeyBinding.ACTION_COUNT; i++) {
			View row = createRow(i);
			// 分隔线（每个 row 下方）
			View divider = new View(requireContext());
			divider.setBackgroundColor(0xFF2a4a7a);
			LinearLayout.LayoutParams lpDiv = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					NokiaDimens.dp(getResources(), 1));
			lpDiv.leftMargin = NokiaDimens.dp(getResources(), 12);
			lpDiv.rightMargin = NokiaDimens.dp(getResources(), 12);

			bindListContainer.addView(row);
			bindListContainer.addView(divider, lpDiv);
			itemViews[i] = row;
		}
	}

	private View createRow(int action) {
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setClickable(true);

		row.setPadding(NokiaDimens.dp(getResources(), 12), NokiaDimens.dp(getResources(), 8),
				NokiaDimens.dp(getResources(), 12), NokiaDimens.dp(getResources(), 8));

		// 左侧：动作名
		TextView tvAction = new TextView(requireContext());
		tvAction.setText(NokiaKeyBinding.getActionName(action));
		tvAction.setTextColor(0xFFFFFFFF);
		tvAction.setTextSize(11);
		LinearLayout.LayoutParams lpAction = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
		row.addView(tvAction, lpAction);

		// 右侧：按键名
		TextView tvKey = new TextView(requireContext());
		int kc = keyBinding.getKeyCode(action);
		if (NokiaKeyBinding.isBound(kc)) {
			tvKey.setText(keyCodeToString(kc));
			tvKey.setTextColor(0xFF9fb3d1);
		} else {
			tvKey.setText("未绑定");
			tvKey.setTextColor(0xFFFF8A80);
		}
		tvKey.setTextSize(10);
		row.addView(tvKey);

		// 录制提示箭头
		TextView tvHint = new TextView(requireContext());
		tvHint.setText(" >");
		tvHint.setTextColor(0xFF9fb3d1);
		tvHint.setTextSize(11);
		row.addView(tvHint);

		// 点击 → 进入录制
		row.setTag(action);
		row.setOnClickListener(v -> startRecording(action));

		return row;
	}

	/** 兼容 API < 29 的 keyCode 转字符串方法。 */
	private static String keyCodeToString(int keycode) {
		if (android.os.Build.VERSION.SDK_INT >= 29) {
			return KeyEvent.keyCodeToString(keycode);
		}
		// 简单的 fallback：返回常见按键的名称
		switch (keycode) {
			case KeyEvent.KEYCODE_DPAD_UP: return "KEYCODE_DPAD_UP";
			case KeyEvent.KEYCODE_DPAD_DOWN: return "KEYCODE_DPAD_DOWN";
			case KeyEvent.KEYCODE_DPAD_LEFT: return "KEYCODE_DPAD_LEFT";
			case KeyEvent.KEYCODE_DPAD_RIGHT: return "KEYCODE_DPAD_RIGHT";
			case KeyEvent.KEYCODE_DPAD_CENTER: return "KEYCODE_DPAD_CENTER";
			case KeyEvent.KEYCODE_ENTER: return "KEYCODE_ENTER";
			case KeyEvent.KEYCODE_VOLUME_UP: return "KEYCODE_VOLUME_UP";
			case KeyEvent.KEYCODE_VOLUME_DOWN: return "KEYCODE_VOLUME_DOWN";
			case KeyEvent.KEYCODE_BACK: return "KEYCODE_BACK";
			case KeyEvent.KEYCODE_BUTTON_L1: return "KEYCODE_BUTTON_L1";
			case KeyEvent.KEYCODE_BUTTON_R1: return "KEYCODE_BUTTON_R1";
			case KeyEvent.KEYCODE_SOFT_LEFT: return "KEYCODE_SOFT_LEFT";
			case KeyEvent.KEYCODE_SOFT_RIGHT: return "KEYCODE_SOFT_RIGHT";
			case KeyEvent.KEYCODE_BUTTON_A: return "KEYCODE_BUTTON_A";
			case KeyEvent.KEYCODE_BUTTON_B: return "KEYCODE_BUTTON_B";
			case KeyEvent.KEYCODE_BUTTON_X: return "KEYCODE_BUTTON_X";
			case KeyEvent.KEYCODE_BUTTON_Y: return "KEYCODE_BUTTON_Y";
			case KeyEvent.KEYCODE_F1: return "KEYCODE_F1";
			case KeyEvent.KEYCODE_F2: return "KEYCODE_F2";
			default: return "KEYCODE_" + keycode;
		}
	}

	// ---- 录制模式 ----

	void startRecording(int action) {
		recording = true;
		recordingAction = action;
		recordStatusBar.setVisibility(View.VISIBLE);
		recordStatusText.setText("正在录制: " + NokiaKeyBinding.getActionName(action) + " — 请按目标键（点此处取消）");
		NokiaLog.i("KeyBind", "开始录制 action=" + NokiaKeyBinding.getActionName(action)
				+ "，等待物理按键...");
	}

	/** 由 Activity.dispatchKeyEvent 在录制模式下调用。 */
	public void onKeyRecorded(int keycode) {
		if (!recording) return;
		int action = recordingAction;
		recording = false;
		recordStatusBar.setVisibility(View.GONE);
		recordingAction = -1;

		NokiaLog.i("KeyBind", "录制完成 action=" + NokiaKeyBinding.getActionName(action)
				+ " 捕获 " + NokiaLog.keyName(keycode));

		int occupied = keyBinding.getActionForKeyCode(keycode);
		if (occupied >= 0 && occupied != action) {
			// 该键已被其它动作占用，进入确认模式（复用方向键/确认/返回导航）
			NokiaLog.w("KeyBind", "录制冲突：" + NokiaLog.keyName(keycode)
					+ " 已被 " + NokiaKeyBinding.getActionName(occupied) + " 占用");
			enterConfirm(action, occupied, keycode);
			return;
		}
		applyBinding(action, keycode);
	}

	/** 录制态下按返回键：跳过当前动作的绑定（保留默认值），退出录制模式。 */
	@Override
	public void onSkipCurrent() {
		if (!recording) return;
		int action = recordingAction;
		recording = false;
		recordingAction = -1;
		recordStatusBar.setVisibility(View.GONE);
		NokiaLog.i("KeyBind", "跳过录制 action=" + NokiaKeyBinding.getActionName(action)
				+ "（保留默认 " + NokiaLog.keyName(keyBinding.getKeyCode(action)) + "）");
	}

	/** 应用绑定并刷新列表。 */
	private void applyBinding(int action, int keycode) {
		keyBinding.setKeyCode(action, keycode);
		// 桌面按键绑定变化后，同步到全局 JAR 设置的按键映射
		NokiaGlobalProfile.syncKeyBindings(requireContext());
		buildList();
		setFocusIndex(focusIndex);
	}

	/** 进入冲突确认模式：复用 Fragment 自身的方向键/确认/返回导航选择覆盖或取消。 */
	private void enterConfirm(int action, int occupied, int keycode) {
		confirming = true;
		confirmAction = action;
		confirmOccupied = occupied;
		confirmKeycode = keycode;
		confirmChoice = 0; // 默认选中“取消”，避免误覆盖

		// 底部菜单栏由 NokiaPage 声明动态装配（中间固定显示界面名"按键绑定"，覆盖/取消由左右软键触发）
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		host.refreshPageBar();

		recordStatusBar.setVisibility(View.VISIBLE);
		updateConfirmText();
		NokiaLog.i("KeyBind", "进入冲突确认模式，等待选择（←→切换，确认选定，返回=取消）");
	}

	private void updateConfirmText() {
		String cancel = (confirmChoice == 0) ? "[取消]" : " 取消 ";
		String over = (confirmChoice == 1) ? "[覆盖]" : " 覆盖 ";
		recordStatusText.setText("冲突：" + NokiaLog.keyName(confirmKeycode)
				+ " 已被「" + NokiaKeyBinding.getActionName(confirmOccupied) + "」占用  "
				+ cancel + over + "（←→切换，确认选定）");
	}

	/** 执行用户的选择并退出确认模式。 */
	private void doConfirm() {
		if (confirmChoice == 1) {
			NokiaLog.i("KeyBind", "用户选择覆盖：解除 "
					+ NokiaKeyBinding.getActionName(confirmOccupied)
					+ "，绑定到 " + NokiaKeyBinding.getActionName(confirmAction));
			applyBinding(confirmAction, confirmKeycode);
		} else {
			NokiaLog.i("KeyBind", "用户取消覆盖，保持 "
					+ NokiaKeyBinding.getActionName(confirmOccupied) + " 不变");
			Toast.makeText(requireContext(), "已取消，绑定未更改", Toast.LENGTH_SHORT).show();
		}
		confirming = false;
		confirmAction = confirmKeycode = confirmOccupied = -1;

		// 底部菜单栏由 NokiaPage 声明动态装配（恢复列表页左右软键文案）
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		host.refreshPageBar();

		recordStatusBar.setVisibility(View.GONE);
		buildList();
		setFocusIndex(focusIndex);
	}

	/** 供 Activity 查询当前是否在录制模式。 */
	public boolean isRecording() {
		return recording;
	}

	// ---- NokiaFocusHost 接口 ----

	@Override
	public boolean onDirection(int direction) {
		if (confirming) {
			// 左右切换选择；上下忽略
			if (direction == NokiaKeyBinding.ACTION_LEFT
					|| direction == NokiaKeyBinding.ACTION_RIGHT) {
				confirmChoice = (confirmChoice == 0) ? 1 : 0;
				updateConfirmText();
				NokiaLog.d("KeyBind", "冲突确认切换选择 -> "
						+ (confirmChoice == 0 ? "取消" : "覆盖"));
			}
			return true;
		}
		NokiaLog.d("KeyBind", "onDirection " + NokiaKeyBinding.getActionName(direction)
				+ " focus=" + focusIndex);
		if (focusIndex < 0) {
			setFocusIndex(0);
			return true;
		}
		switch (direction) {
			case NokiaKeyBinding.ACTION_UP:
				if (focusIndex > 0) setFocusIndex(focusIndex - 1);
				return true;
			case NokiaKeyBinding.ACTION_DOWN:
				if (focusIndex < NokiaKeyBinding.ACTION_COUNT - 1) setFocusIndex(focusIndex + 1);
				return true;
			default:
				return true; // 左右无效果但消费事件
		}
	}

	@Override
	public boolean onSelect() {
		if (confirming) {
			doConfirm();
			return true;
		}
		// 进入防抖：刚进入界面时的残留确认键（触发进入的那个按键的 repeat DOWN）
		// 会被误解析为"选择当前焦点项"，导致立即开始录制第一个动作（上）。
		// 窗口期内消费掉 SELECT 但不进入录制；触摸点击列表行不受影响（不走本方法）。
		if (SystemClock.uptimeMillis() - enteredAt < ENTRY_DEBOUNCE_MS) {
			NokiaLog.i("KeyBind", "进入防抖窗口（"
					+ (SystemClock.uptimeMillis() - enteredAt) + "ms），忽略 SELECT，不触发录制");
			return true;
		}
		NokiaLog.d("KeyBind", "onSelect focus=" + focusIndex);
		if (focusIndex >= 0 && focusIndex < NokiaKeyBinding.ACTION_COUNT) {
			startRecording(focusIndex);
		}
		return true;
	}

	@Override
	public boolean onSoftLeft() {
		if (confirming) {
			confirmChoice = 0;
			doConfirm();
			return true;
		}
		NokiaLog.d("KeyBind", "onSoftLeft -> 等同选择");
		return onSelect(); // 左软键 = 选择 = 进入录制
	}

	@Override
	public boolean onSoftRight() {
		if (confirming) {
			confirmChoice = 1;
			doConfirm();
			return true;
		}
		NokiaLog.d("KeyBind", "onSoftRight -> 返回");
		// 右软键 = 返回
		((NokiaDesktopActivity) requireActivity()).exitCurrent();
		return true;
	}

	@Override
	public boolean onBack() {
		if (confirming) {
			// 返回 = 取消
			confirmChoice = 0;
			doConfirm();
			return true;
		}
		NokiaLog.d("KeyBind", "onBack -> 返回");
		((NokiaDesktopActivity) requireActivity()).exitCurrent();
		return true;
	}

	// ---- NokiaPage 接口（底部菜单栏声明，由 host.refreshPageBar() 装配） ----

	@Override
	public String getPageTitle() {
		// 覆盖模式中间固定显示界面名"按键绑定"（不再当按钮）
		return "按键绑定";
	}

	@Override
	public String getSoftLeftText() {
		// 覆盖模式：左软键"取消"；列表模式：左软键"选择"
		return confirming ? "取消" : "选择";
	}

	@Override
	public String getSoftRightText() {
		// 覆盖模式：右软键"覆盖"；列表模式：右软键"返回"
		return confirming ? "覆盖" : "返回";
	}

	// ---- 焦点管理 ----

	private void setFocusIndex(int index) {
		if (index < 0 || index >= itemViews.length) return;
		// 取消旧焦点
		if (focusIndex >= 0 && focusIndex < itemViews.length && itemViews[focusIndex] != null) {
			itemViews[focusIndex].setBackgroundColor(0);
		}
		focusIndex = index;
		// 设置新焦点
		if (itemViews[focusIndex] != null) {
			itemViews[focusIndex].setBackgroundResource(R.drawable.bg_nokia_selected_dark);
			scrollToItem(focusIndex);
		}
	}

	private void scrollToItem(int index) {
		// 确保选中项在 ScrollView 中可见
		if (bindListContainer == null) return;
		View parent = (View) bindListContainer.getParent();
		if (parent instanceof ScrollView && itemViews[index] != null) {
			ScrollView sv = (ScrollView) parent;
			View item = itemViews[index];
			sv.post(() -> {
				int scrollY = sv.getScrollY();
				int itemTop = item.getTop();
				int itemBottom = item.getBottom();
				int svHeight = sv.getHeight();
				if (itemTop < scrollY) {
					sv.smoothScrollTo(0, itemTop);
				} else if (itemBottom > scrollY + svHeight) {
					sv.smoothScrollTo(0, itemBottom - svHeight);
				}
			});
		}
	}
}
