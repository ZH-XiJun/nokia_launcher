package ru.playsoftware.j2meloader.nokia;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import ru.playsoftware.j2meloader.R;

/**
 * 按键绑定设置界面。
 * 列出所有 8 个动作及对应按键，支持方向键导航选中 + 确认键进入录制模式，
 * 按下任意物理键即完成绑定。
 */
public class NokiaKeyBindFragment extends Fragment implements NokiaFocusHost {

	private NokiaKeyBinding keyBinding;
	private View[] itemViews = new View[NokiaKeyBinding.ACTION_COUNT];
	private int focusIndex = 0;
	private boolean recording = false;
	private int recordingAction = -1;

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

		keyBinding = new NokiaKeyBinding(requireContext());

		bindListContainer = view.findViewById(R.id.bindListContainer);
		recordStatusBar = view.findViewById(R.id.recordStatusBar);
		recordStatusText = view.findViewById(R.id.recordStatusText);

		// 壁纸设为浅灰
		View wall = host.findViewById(R.id.wallpaper);
		if (wall != null) {
			wall.setBackgroundColor(0xFFF0F0F0);
		}

		// 标题
		TextView title = host.findViewById(R.id.topTitle);
		if (title != null) {
			title.setText("按键绑定");
		}

		// 底部软键
		TextView bl = host.findViewById(R.id.bottomLeft);
		if (bl != null) {
			bl.setText("选择");
		}
		TextView bc = host.findViewById(R.id.bottomCenter);
		if (bc != null) {
			bc.setText("");
		}
		TextView br = host.findViewById(R.id.bottomRight);
		if (br != null) {
			br.setText("返回");
			br.setOnClickListener(v -> host.exitCurrent());
		}

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
			divider.setBackgroundColor(0xFFDDDDDD);
			LinearLayout.LayoutParams lpDiv = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, 1);
			lpDiv.leftMargin = 12;
			lpDiv.rightMargin = 12;

			bindListContainer.addView(row);
			bindListContainer.addView(divider, lpDiv);
			itemViews[i] = row;
		}
	}

	private View createRow(int action) {
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setFocusable(true);
		row.setClickable(true);
		row.setPadding(12, 8, 12, 8);

		// 左侧：动作名
		TextView tvAction = new TextView(requireContext());
		tvAction.setText(NokiaKeyBinding.getActionName(action));
		tvAction.setTextColor(0xFF333333);
		tvAction.setTextSize(11);
		LinearLayout.LayoutParams lpAction = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
		row.addView(tvAction, lpAction);

		// 右侧：按键名
		TextView tvKey = new TextView(requireContext());
		int kc = keyBinding.getKeyCode(action);
		if (NokiaKeyBinding.isBound(kc)) {
			tvKey.setText(keyCodeToString(kc));
			tvKey.setTextColor(0xFF666666);
		} else {
			tvKey.setText("未绑定");
			tvKey.setTextColor(0xFFCC0000);
		}
		tvKey.setTextSize(10);
		row.addView(tvKey);

		// 录制提示箭头
		TextView tvHint = new TextView(requireContext());
		tvHint.setText(" >");
		tvHint.setTextColor(0xFF999999);
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
		recordStatusText.setText("正在录制: " + NokiaKeyBinding.getActionName(action) + " — 请按下目标按键...");
		NokiaLog.i("KeyBind", "开始录制 action=" + NokiaKeyBinding.getActionName(action)
				+ "，等待物理按键...");
	}

	/** 由 Activity.dispatchKeyEvent 在录制模式下调用。 */
	public void onKeyRecorded(int keycode) {
		if (!recording) return;
		int action = recordingAction;
		recording = false;
		recordStatusBar.setVisibility(View.GONE);

		NokiaLog.i("KeyBind", "录制完成 action=" + NokiaKeyBinding.getActionName(action)
				+ " 捕获 " + NokiaLog.keyName(keycode));
		keyBinding.setKeyCode(action, keycode);
		recordingAction = -1;

		// 刷新列表
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
		NokiaLog.d("KeyBind", "onSelect focus=" + focusIndex);
		if (focusIndex >= 0 && focusIndex < NokiaKeyBinding.ACTION_COUNT) {
			startRecording(focusIndex);
		}
		return true;
	}

	@Override
	public boolean onSoftLeft() {
		NokiaLog.d("KeyBind", "onSoftLeft -> 等同选择");
		return onSelect(); // 左软键 = 选择 = 进入录制
	}

	@Override
	public boolean onSoftRight() {
		NokiaLog.d("KeyBind", "onSoftRight -> 返回");
		// 右软键 = 返回
		((NokiaDesktopActivity) requireActivity()).exitCurrent();
		return true;
	}

	@Override
	public boolean onBack() {
		NokiaLog.d("KeyBind", "onBack -> 返回");
		((NokiaDesktopActivity) requireActivity()).exitCurrent();
		return true;
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
			itemViews[focusIndex].setBackgroundColor(0x550055FF);
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
