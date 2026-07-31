package ru.playsoftware.j2meloader.nokia;

import android.app.Dialog;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import ru.playsoftware.j2meloader.R;

/**
 * 诺基亚风格卸载确认弹窗。
 * 提供"取消"与"卸载"两个选项，可用方向键（左右/软键）切换高亮，
 * 按确认键（DPAD_CENTER / ENTER）或对应软键触发当前高亮项；返回键等效"取消"。
 * 实际删除逻辑由宿主通过 {@link ConfirmListener} 回调执行（避免传递非 Parcelable 的 AppItem）。
 */
public class NokiaUninstallDialog extends DialogFragment {
	private static final String TAG = "UninstallDialog";
	private static final String ARG_NAME = "app_name";

	/** 0 = 卸载（左，确认），1 = 取消（右） */
	private int focusIndex = 0;
	private TextView softLeft;
	private TextView softRight;
	private ConfirmListener confirmListener;

	public interface ConfirmListener {
		void onConfirm();
	}

	public static NokiaUninstallDialog newInstance(String appName) {
		NokiaUninstallDialog dialog = new NokiaUninstallDialog();
		Bundle args = new Bundle();
		args.putString(ARG_NAME, appName);
		dialog.setArguments(args);
		return dialog;
	}

	public void setConfirmListener(ConfirmListener listener) {
		this.confirmListener = listener;
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		NokiaLog.i(TAG, "onCreateDialog: 创建卸载确认弹窗");
		Dialog dialog = new Dialog(requireActivity());
		dialog.setContentView(R.layout.dialog_nokia_uninstall);
		dialog.setCancelable(false);
		dialog.setCanceledOnTouchOutside(false);
		if (dialog.getWindow() != null) {
			dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT);
			dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
		}

		String appName = requireArguments() != null ? requireArguments().getString(ARG_NAME, "") : "";
		TextView content = dialog.findViewById(R.id.uninstall_content);
		softLeft = dialog.findViewById(R.id.softLeft);
		softRight = dialog.findViewById(R.id.softRight);
		if (content != null) {
			content.setText("是否卸载「" + appName + "」？");
		}

		// 默认高亮"取消"（右），避免误触卸载
		setFocus(1);

		// 触摸支持：点击软键 = 直接触发该项
		if (softLeft != null) {
			softLeft.setOnClickListener(v -> trigger(0));
		}
		if (softRight != null) {
			softRight.setOnClickListener(v -> trigger(1));
		}

		dialog.setOnKeyListener((d, keyCode, event) -> {
			if (event.getAction() != KeyEvent.ACTION_DOWN) {
				// 消费抬起事件，避免重复触发
				return true;
			}
			NokiaLog.d(TAG, "onKey keyCode=" + keyCode
					+ " focusIndex=" + focusIndex);
			switch (keyCode) {
				case KeyEvent.KEYCODE_DPAD_LEFT:
					setFocus(0);
					return true;
				case KeyEvent.KEYCODE_DPAD_RIGHT:
					setFocus(1);
					return true;
				case KeyEvent.KEYCODE_SOFT_LEFT:
				case KeyEvent.KEYCODE_MENU:
					NokiaLog.i(TAG, "左软键/菜单键：确认卸载");
					trigger(0);
					return true;
				case KeyEvent.KEYCODE_SOFT_RIGHT:
					NokiaLog.i(TAG, "右软键：取消卸载");
					trigger(1);
					return true;
				case KeyEvent.KEYCODE_DPAD_CENTER:
				case KeyEvent.KEYCODE_ENTER:
					trigger(focusIndex);
					return true;
				case KeyEvent.KEYCODE_BACK:
					NokiaLog.i(TAG, "返回键：取消卸载");
					trigger(1);
					return true;
				default:
					return false;
			}
		});

		return dialog;
	}

	private void setFocus(int index) {
		focusIndex = index;
		applyFocus();
	}

	private void applyFocus() {
		if (softLeft == null || softRight == null) return;
		if (focusIndex == 0) {
			softLeft.setBackgroundResource(R.drawable.bg_nokia_selected);
			softRight.setBackgroundResource(0);
		} else {
			softRight.setBackgroundResource(R.drawable.bg_nokia_selected);
			softLeft.setBackgroundResource(0);
		}
	}

	private void trigger(int index) {
		if (index == 0) {
			NokiaLog.i(TAG, "确认卸载");
			if (confirmListener != null) {
				confirmListener.onConfirm();
			}
		} else {
			NokiaLog.i(TAG, "取消卸载");
		}
		dismiss();
	}
}
