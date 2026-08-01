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
 * 诺基亚风格选项菜单弹窗（桌面组件设置 S2 选项菜单）。
 * <p>
 * 视觉与「功能表→应用程序→选中JAR的选项菜单」一致：底部锚定，
 * 标题栏 + 选项列表 + 自带底部软键栏。选项行用 {@code bg_nokia_selected_dark} 高亮。
 * <p>
 * 按键行为（按设计文档）：
 * <ul>
 *   <li>上/下：在可用选项间移动（自动跳过灰掉的项）</li>
 *   <li>确认键 / 左软键（选择）：执行当前选项</li>
 *   <li>右软键（返回）/ 返回键：关闭弹窗</li>
 *   <li>左/右：无效果</li>
 * </ul>
 * 已接入 {@link NokiaKeyBinding}，禁止写死 keyCode。
 */
public class NokiaWidgetOptionsDialog extends DialogFragment {
	private static final String TAG = "WidgetOptions";
	private static final String ARG_TITLE = "title";
	private static final String ARG_LABELS = "labels";
	private static final String ARG_ENABLED = "enabled";
	private static final String ARG_ICONS = "icons";

	public interface OptionsListener {
		void onOptionSelected(int index);
	}

	private String title;
	private String[] labels;
	private boolean[] enabled;
	private int[] icons;
	private LinearLayout[] optionRows;
	private int focusIndex = -1;
	private OptionsListener listener;

	public static NokiaWidgetOptionsDialog newInstance(String title, String[] labels,
													   boolean[] enabled, int[] icons) {
		NokiaWidgetOptionsDialog dialog = new NokiaWidgetOptionsDialog();
		Bundle args = new Bundle();
		args.putString(ARG_TITLE, title);
		args.putStringArray(ARG_LABELS, labels);
		args.putBooleanArray(ARG_ENABLED, enabled);
		args.putIntArray(ARG_ICONS, icons);
		dialog.setArguments(args);
		return dialog;
	}

	public void setOptionsListener(OptionsListener listener) {
		this.listener = listener;
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		Bundle args = requireArguments();
		title = args.getString(ARG_TITLE, "");
		labels = args.getStringArray(ARG_LABELS);
		enabled = args.getBooleanArray(ARG_ENABLED);
		icons = args.getIntArray(ARG_ICONS);
		if (labels == null) labels = new String[0];
		if (enabled == null) enabled = new boolean[labels.length];

		NokiaLog.i(TAG, "onCreateDialog: 创建选项菜单，title=" + title + " options=" + labels.length);

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
			titleView.setText(title);
		}

		LinearLayout list = dialog.findViewById(R.id.widgetOptionsList);
		optionRows = new LinearLayout[labels.length];
		for (int i = 0; i < labels.length; i++) {
			LinearLayout row = new LinearLayout(requireContext());
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setLayoutParams(new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
			row.setPadding(dp(14), 0, dp(14), 0);

			if (icons != null && i < icons.length) {
				ImageView iv = new ImageView(requireContext());
				iv.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
				try {
					iv.setImageResource(icons[i]);
				} catch (Exception ignored) {}
				if (!enabled[i]) {
					iv.setAlpha(0.5f);
				}
				row.addView(iv);
			}

			TextView tv = new TextView(requireContext());
			tv.setLayoutParams(new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			if (icons != null && i < icons.length) {
				tv.setPadding(dp(10), 0, 0, 0);
			}
			tv.setText(labels[i]);
			tv.setTextSize(14);
			tv.setSingleLine(true);
			tv.setTextColor(enabled[i] ? 0xFFFFFFFF : 0xFF666666);
			row.addView(tv);

			if (enabled[i]) {
				final int idx = i;
				row.setClickable(true);
				row.setOnClickListener(v -> {
					setFocus(idx);
					trigger(idx);
				});
			}
			list.addView(row);
			optionRows[i] = row;
		}

		// 接入用户自定义按键映射（禁止写死 keyCode）
		final NokiaKeyBinding keyBinding =
				((NokiaDesktopActivity) requireActivity()).getKeyBinding();
		dialog.setOnKeyListener((d, keyCode, event) -> {
			if (event.getAction() != KeyEvent.ACTION_DOWN) {
				return true; // 消费抬起事件
			}
			// 返回键由弹窗自己处理（NokiaKeyBinding 不管 BACK）
			if (keyCode == KeyEvent.KEYCODE_BACK) {
				NokiaLog.i(TAG, "返回键：关闭选项菜单");
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
					NokiaLog.i(TAG, "右软键：关闭选项菜单");
					dismiss();
					return true;
				case NokiaKeyBinding.ACTION_LEFT:
				case NokiaKeyBinding.ACTION_RIGHT:
					return true; // 菜单为纵向列表，左右无效果
				default:
					return false;
			}
		});

		// 默认焦点在第一个可用选项
		int first = firstEnabledIndex();
		setFocus(first >= 0 ? first : 0);

		// Android 12+：Dialog 窗口首个导航键会被触摸模式吞掉，show 后强制退出该状态
		dialog.setOnShowListener(d -> NokiaDialogFocus.forceNonTouchMode(dialog));

		return dialog;
	}

	private void moveFocus(int step) {
		int next = focusIndex + step;
		while (next >= 0 && next < enabled.length && !enabled[next]) {
			next += step;
		}
		if (next >= 0 && next < enabled.length) {
			setFocus(next);
		}
	}

	private int firstEnabledIndex() {
		for (int i = 0; i < enabled.length; i++) {
			if (enabled[i]) return i;
		}
		return 0;
	}

	private void setFocus(int index) {
		if (optionRows == null || index < 0 || index >= optionRows.length) return;
		focusIndex = index;
		for (int i = 0; i < optionRows.length; i++) {
			if (optionRows[i] == null) continue;
			if (i == index && enabled[i]) {
				optionRows[i].setBackgroundResource(R.drawable.bg_nokia_selected_dark);
			} else {
				optionRows[i].setBackgroundResource(0);
			}
		}
	}

	private void trigger(int index) {
		if (index < 0 || index >= enabled.length || !enabled[index]) return;
		NokiaLog.i(TAG, "执行选项: " + index + " (" + labels[index] + ")");
		if (listener != null) {
			listener.onOptionSelected(index);
		}
		dismiss();
	}

	private int dp(int v) {
		return (int) (v * getResources().getDisplayMetrics().density);
	}
}
