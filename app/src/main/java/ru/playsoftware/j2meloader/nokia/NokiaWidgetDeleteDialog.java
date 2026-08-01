package ru.playsoftware.j2meloader.nokia;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import ru.playsoftware.j2meloader.R;

/**
 * 删除模式子菜单弹窗（S3 左软键弹出）。
 * <p>
 * 视觉与选项菜单弹窗一致：底部锚定、标题栏 + 选项列表 + 自带底部软键栏。
 * 提供「全选/取消全选」与「删除已选（X 项）」两个选项，
 * 全选切换后通过 {@link Listener} 查询最新勾选状态并刷新自身文案。
 * <p>
 * 按键行为：上/下移动、确认键/左软键（选择）执行、右软键（返回）/返回键关闭。
 * 已接入 {@link NokiaKeyBinding}，禁止写死 keyCode。
 */
public class NokiaWidgetDeleteDialog extends DialogFragment {
	private static final String TAG = "WidgetDelete";
	private static final int INDEX_SELECT_ALL = 0;
	private static final int INDEX_DELETE = 1;

	public interface Listener {
		/** 执行「全选/取消全选」切换（由宿主更新勾选状态）。 */
		void onSelectAllToggle();

		/** 执行「删除已选」（由宿主删除并回到 S1）。 */
		void onDeleteSelected();

		boolean isAllChecked();

		int getCheckedCount();
	}

	private Listener listener;
	private LinearLayout rowSelectAll;
	private LinearLayout rowDelete;
	private TextView tvSelectAll;
	private TextView tvDelete;
	private boolean deleteEnabled = false;
	private int focusIndex = 0;

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		NokiaLog.i(TAG, "onCreateDialog: 创建删除子菜单");
		Dialog dialog = new Dialog(requireActivity());
		dialog.setContentView(R.layout.dialog_nokia_widget_options);
		dialog.setCancelable(false);
		dialog.setCanceledOnTouchOutside(false);
		if (dialog.getWindow() != null) {
			dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT);
			dialog.getWindow().setGravity(Gravity.BOTTOM);
			dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
		}

		TextView titleView = dialog.findViewById(R.id.widgetOptionsTitle);
		if (titleView != null) {
			titleView.setText("删除组件");
		}

		LinearLayout list = dialog.findViewById(R.id.widgetOptionsList);

		// 全选 / 取消全选
		rowSelectAll = new LinearLayout(requireContext());
		rowSelectAll.setOrientation(LinearLayout.HORIZONTAL);
		rowSelectAll.setGravity(Gravity.CENTER_VERTICAL);
		rowSelectAll.setLayoutParams(new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
		rowSelectAll.setPadding(dp(14), 0, dp(14), 0);
		rowSelectAll.setClickable(true);
		ImageView ivAll = new ImageView(requireContext());
		ivAll.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
		try {
			ivAll.setImageResource(android.R.drawable.ic_menu_agenda);
		} catch (Exception ignored) {}
		rowSelectAll.addView(ivAll);
		tvSelectAll = new TextView(requireContext());
		tvSelectAll.setPadding(dp(10), 0, 0, 0);
		tvSelectAll.setTextSize(14);
		tvSelectAll.setSingleLine(true);
		tvSelectAll.setTextColor(0xFFFFFFFF);
		rowSelectAll.addView(tvSelectAll);
		rowSelectAll.setOnClickListener(v -> {
			setFocus(INDEX_SELECT_ALL);
			trigger(INDEX_SELECT_ALL);
		});
		list.addView(rowSelectAll);

		// 删除已选（X 项）
		rowDelete = new LinearLayout(requireContext());
		rowDelete.setOrientation(LinearLayout.HORIZONTAL);
		rowDelete.setGravity(Gravity.CENTER_VERTICAL);
		rowDelete.setLayoutParams(new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
		rowDelete.setPadding(dp(14), 0, dp(14), 0);
		ImageView ivDel = new ImageView(requireContext());
		ivDel.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
		try {
			ivDel.setImageResource(android.R.drawable.ic_menu_delete);
		} catch (Exception ignored) {}
		rowDelete.addView(ivDel);
		tvDelete = new TextView(requireContext());
		tvDelete.setPadding(dp(10), 0, 0, 0);
		tvDelete.setTextSize(14);
		tvDelete.setSingleLine(true);
		rowDelete.addView(tvDelete);
		rowDelete.setOnClickListener(v -> {
			setFocus(INDEX_DELETE);
			trigger(INDEX_DELETE);
		});
		list.addView(rowDelete);

		refreshLabels();

		// 接入用户自定义按键映射（禁止写死 keyCode）
		final NokiaKeyBinding keyBinding =
				((NokiaDesktopActivity) requireActivity()).getKeyBinding();
		dialog.setOnKeyListener((d, keyCode, event) -> {
			if (event.getAction() != KeyEvent.ACTION_DOWN) {
				return true; // 消费抬起事件
			}
			if (keyCode == KeyEvent.KEYCODE_BACK) {
				NokiaLog.i(TAG, "返回键：关闭删除子菜单");
				dismiss();
				return true;
			}
			int action = keyBinding.resolveAction(event);
			switch (action) {
				case NokiaKeyBinding.ACTION_UP:
					moveFocus(-1);
					return true;
				case NokiaKeyBinding.ACTION_DOWN:
					moveFocus(1);
					return true;
				case NokiaKeyBinding.ACTION_SELECT:
				case NokiaKeyBinding.ACTION_SOFT_LEFT:
					// 左软键（选择）等同确认键：执行当前选项
					trigger(focusIndex);
					return true;
				case NokiaKeyBinding.ACTION_SOFT_RIGHT:
					NokiaLog.i(TAG, "右软键：关闭删除子菜单");
					dismiss();
					return true;
				case NokiaKeyBinding.ACTION_LEFT:
				case NokiaKeyBinding.ACTION_RIGHT:
					return true; // 菜单为纵向列表，左右无效果
				default:
					return false;
			}
		});

		setFocus(INDEX_SELECT_ALL);

		// Android 12+：Dialog 窗口首个导航键会被触摸模式吞掉，show 后强制退出该状态
		dialog.setOnShowListener(d -> NokiaDialogFocus.forceNonTouchMode(dialog));

		return dialog;
	}

	private void refreshLabels() {
		if (listener == null) return;
		boolean all = listener.isAllChecked();
		int count = listener.getCheckedCount();
		deleteEnabled = count > 0;
		if (tvSelectAll != null) {
			tvSelectAll.setText(all ? "取消全选" : "全选");
		}
		if (tvDelete != null) {
			tvDelete.setText("删除已选（" + count + " 项）");
			tvDelete.setTextColor(deleteEnabled ? 0xFFFFFFFF : 0xFF666666);
		}
		if (rowDelete != null) {
			rowDelete.setClickable(deleteEnabled);
			rowDelete.setEnabled(deleteEnabled);
		}
		if (focusIndex == INDEX_DELETE && !deleteEnabled) {
			setFocus(INDEX_SELECT_ALL);
		}
		NokiaLog.d(TAG, "刷新删除子菜单: allChecked=" + all + " checkedCount=" + count);
	}

	private void moveFocus(int step) {
		if (step > 0) {
			if (focusIndex == INDEX_SELECT_ALL && deleteEnabled) {
				setFocus(INDEX_DELETE);
			}
		} else {
			if (focusIndex == INDEX_DELETE) {
				setFocus(INDEX_SELECT_ALL);
			}
		}
	}

	private void setFocus(int index) {
		focusIndex = index;
		if (rowSelectAll != null) {
			rowSelectAll.setBackgroundResource(
					index == INDEX_SELECT_ALL ? R.drawable.bg_nokia_selected_dark : 0);
		}
		if (rowDelete != null) {
			rowDelete.setBackgroundResource(
					index == INDEX_DELETE && deleteEnabled ? R.drawable.bg_nokia_selected_dark : 0);
		}
	}

	private void trigger(int index) {
		if (index == INDEX_DELETE && !deleteEnabled) return;
		if (index == INDEX_SELECT_ALL) {
			NokiaLog.i(TAG, "执行：全选/取消全选");
			if (listener != null) {
				listener.onSelectAllToggle();
			}
			refreshLabels();
		} else {
			NokiaLog.i(TAG, "执行：删除已选");
			if (listener != null) {
				listener.onDeleteSelected();
			}
			dismiss();
		}
	}

	private int dp(int v) {
		return (int) (v * getResources().getDisplayMetrics().density);
	}
}
