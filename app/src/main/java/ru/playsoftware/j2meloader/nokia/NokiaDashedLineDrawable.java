package ru.playsoftware.j2meloader.nokia;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/**
 * 横向点线（虚线）分割线 Drawable。
 * 用 DashPathEffect 绘制，避免 shape="line" 在某些 ROM/API 上不渲染虚线的问题。
 */
public class NokiaDashedLineDrawable extends Drawable {

	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

	public NokiaDashedLineDrawable(int color, float dashDp, float gapDp) {
		float d = Resources.getSystem().getDisplayMetrics().density;
		paint.setColor(color);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(d); // 线宽 1dp
		float dash = dashDp * d;
		float gap = gapDp * d;
		paint.setPathEffect(new DashPathEffect(new float[] { dash, gap }, 0));
	}

	@Override
	public void draw(Canvas canvas) {
		int h = getBounds().height();
		float y = h / 2f;
		canvas.drawLine(getBounds().left, y, getBounds().right, y, paint);
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
