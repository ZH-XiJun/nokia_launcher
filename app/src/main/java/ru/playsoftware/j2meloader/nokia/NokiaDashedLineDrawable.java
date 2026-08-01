package ru.playsoftware.j2meloader.nokia;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/**
 * 横向点线（虚线）分割线 Drawable。
 *
 * 使用 drawRect 循环画实心方块点阵，替代 DashPathEffect，彻底规避以下已知问题：
 * 1. DashPathEffect + ANTI_ALIAS 在 1px 线宽下 dash 边缘被羽化糊成实线（240×320 mdpi）；
 * 2. 硬件加速 Canvas 下 DashPathEffect 在 Android 4.4 等旧设备上可能画成实线或不渲染；
 * 3. Resources.getSystem() 绕过了 attachBaseContext 的 density 修正。
 *
 * 点宽和间隔在 dp 换算后保证 ≥1px，确保低密度屏幕下点阵始终可见。
 */
public class NokiaDashedLineDrawable extends Drawable {

	private final Paint paint;
	private final float dotPx;    // 单个点宽度（px）
	private final float gapPx;    // 间隔宽度（px）

	/**
	 * @param res      传入调用方的 Resources（接受 attachBaseContext 的 density 修正），禁止用 Resources.getSystem()
	 * @param color    点阵颜色（含 alpha）
	 * @param dotDp    点宽（dp），如 3
	 * @param gapDp    间隔（dp），如 3
	 */
	public NokiaDashedLineDrawable(Resources res, int color, float dotDp, float gapDp) {
		float d = res.getDisplayMetrics().density;
		this.dotPx = Math.max(1f, NokiaDimens.dpF(res, dotDp));
		this.gapPx = Math.max(1f, NokiaDimens.dpF(res, gapDp));

		paint = new Paint();
		paint.setColor(color);
		paint.setStyle(Paint.Style.FILL);       // FILL 无抗锯齿羽化，drawRect 全版本硬件加速正常
		paint.setAntiAlias(false);              // 点阵无需抗锯齿
	}

	@Override
	public void draw(Canvas canvas) {
		int left = getBounds().left;
		int right = getBounds().right;
		int h = getBounds().height();
		float y = h / 2f - 0.5f;               // 纵向居中（dotPx 通常 1~3px，对齐半像素可更清晰）
		float step = dotPx + gapPx;
		float x = left;

		while (x < right) {
			float drawEnd = Math.min(x + dotPx, right);
			canvas.drawRect(x, y, drawEnd, y + dotPx, paint);
			x += step;
		}
	}

	@Override
	public void setAlpha(int alpha) {
		paint.setAlpha(alpha);
	}

	@Override
	public void setColorFilter(ColorFilter colorFilter) {
		paint.setColorFilter(colorFilter);
	}

	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}
}
