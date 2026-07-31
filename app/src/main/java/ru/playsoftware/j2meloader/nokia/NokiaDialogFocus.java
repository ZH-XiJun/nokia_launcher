package ru.playsoftware.j2meloader.nokia;

import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;

/**
 * Dialog 弹窗的触摸模式处理助手。
 *
 * <p>在 Android 12+ 上，新建的 Dialog 独立窗口常处于触摸模式（touch mode），
 * 导致第一个导航键（方向键/确认键等）被 {@code ViewRootImpl} 消费用于退出触摸模式，
 * 弹窗对首个按键无响应、第二次按下才生效。本助手在弹窗 show 后给 DecorView 组合焦点，
 * 使 {@code ViewRootImpl.leaveTouchMode()} 返回 false，从而放行第一个按键。</p>
 */
public final class NokiaDialogFocus {

	private NokiaDialogFocus() {
	}

	/**
	 * 在弹窗显示后强制其窗口退出"会吞第一个导航键"的状态。
	 * 应在弹窗 {@code onCreateDialog} 中通过 {@code dialog.setOnShowListener(...)} 调用。
	 *
	 * @param dialog 目标弹窗
	 */
	public static void forceNonTouchMode(Dialog dialog) {
		if (dialog == null || dialog.getWindow() == null) {
			return;
		}
		final View decor = dialog.getWindow().getDecorView();
		if (decor == null) {
			return;
		}
		decor.setFocusable(true);
		decor.setFocusableInTouchMode(true);
		// FOCUS_BLOCK_DESCENDANTS：让 leaveTouchMode() 认为"已聚焦的 ViewGroup 不倾向让后代
		// 接管焦点"，从而返回 false，第一个导航键不再被吞。
		if (decor instanceof ViewGroup) {
			((ViewGroup) decor).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
		}
		decor.post(() -> decor.requestFocus());
	}
}
