package ru.playsoftware.j2meloader.nokia;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ru.playsoftware.j2meloader.R;

/**
 * 诺基亚风格界面的公共基类。
 * 设计基准为 240x320（参考截图）。每个布局由三个 240dp 宽的面板组成：
 *   id=topPanel   （高 18dp，贴顶、宽度铺满）
 *   id=midPanel   （高 280dp，贴左右、在顶/底之间垂直居中）
 *   id=bottomPanel（高 22dp，贴底、宽度铺满）
 * 外层 FrameLayout 铺满全屏并承载壁纸背景。基类按"宽度优先"计算缩放比
 *   scale = 屏幕宽度(dp) / 240
 * （若按此比例整体高度会超出屏幕，则退化为 contain 以避免裁切），再用
 * setScaleX/Y 等比放大面板内容、用 setX/setY 把顶栏贴顶、底栏贴底、中间居中。
 * 这样无论何种分辨率，顶栏/底栏/左右都铺满屏幕，多出的空位由壁纸背景自然填充，
 * 呈现怀旧的全屏效果。
 */
public abstract class NokiaBaseActivity extends AppCompatActivity {
	/**
	 * 部分低分辨率设备（如 320x480 且系统 density 非标准，例如 136 DPI → density=0.85）
	 * 会让所有 dp 尺寸落在亚像素位置，被抗锯齿虚化成灰边，导致图标发虚。
	 * 这里把 density 吸附到标准的 1.0（mdpi），物理布局完全不变（240dp 设计仍铺满屏幕），
	 * 但所有尺寸对齐到整数像素，彻底消除亚像素模糊。高 DPI 设备（density 已是整数倍）不受影响。
	 */
	@Override
	protected void attachBaseContext(Context newBase) {
		Configuration config = newBase.getResources().getConfiguration();
		int dpi = config.densityDpi;
		int fixed = dpi;
		boolean standard = dpi == 120 || dpi == 160 || dpi == 213
				|| dpi == 240 || dpi == 320 || dpi == 480 || dpi == 640;
		if (!standard) {
			fixed = 160;
		}
		if (fixed != dpi) {
			Configuration newConfig = new Configuration(config);
			newConfig.densityDpi = fixed;
			super.attachBaseContext(newBase.createConfigurationContext(newConfig));
		} else {
			super.attachBaseContext(newBase);
		}
	}

	private TextView tvTime;
	private final Handler clockHandler = new Handler();
	private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
	private final Runnable clockTick = new Runnable() {
		@Override
		public void run() {
			if (tvTime != null) {
				tvTime.setText(fmt.format(new Date()));
			}
			clockHandler.postDelayed(this, 1000);
		}
	};

	/** 设计基准尺寸（单位 dp）。 */
	private static final float BASE_W = 240f;
	private static final float TOP_H = 22f;
	private static final float BOT_H = 22f;
	private static final float MID_H = 280f; // 320 - 18 - 22

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
	}

	/** 在 setContentView() 之后调用：初始化时钟并应用分辨率缩放。 */
	protected void setupNokiaUi() {
		tvTime = findViewById(R.id.tvTime);
		applyScale();
	}

	@Override
	protected void onResume() {
		super.onResume();
		clockHandler.post(clockTick);
	}

	@Override
	protected void onPause() {
		super.onPause();
		clockHandler.removeCallbacks(clockTick);
	}

	private void applyScale() {
		DisplayMetrics dm = getResources().getDisplayMetrics();
		float density = dm.density;
		float widthDp = dm.widthPixels / density;
		float heightDp = dm.heightPixels / density;

		// 宽度优先缩放；若整体高度会超出屏幕则退化为 contain，避免裁切。
		float scale = widthDp / BASE_W;
		if (BASE_W > 0 && 320f * scale > heightDp) {
			scale = heightDp / 320f;
		}

		View topPanel = findViewById(R.id.topPanel);
		// 顶栏紧贴屏幕最顶部；FLAG_FULLSCREEN 已隐藏状态栏，不需要再下移。
		if (topPanel != null) {
			ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) topPanel.getLayoutParams();
			lp.topMargin = 0;
			topPanel.setLayoutParams(lp);
		}

		// 底栏：match_parent 宽度已保证左右铺满、贴顶贴底；
		// 仅把内层 240dp 内容等比放大，并按比例设置栏高。
		scalePanelContent(findViewById(R.id.bottomPanel), scale, BOT_H, density, false, true);

		// 顶栏：以"原生分辨率"渲染（不做 setScaleX/Y 缩放），避免矢量图标被栅格化后拉伸发虚；
		// 仅按比例设置栏高，内层宽度铺满屏幕、内容垂直居中。
		if (topPanel != null) {
			if (topPanel instanceof ViewGroup && ((ViewGroup) topPanel).getChildCount() > 0) {
				View content = ((ViewGroup) topPanel).getChildAt(0);
				content.setPivotX(0);
				content.setPivotY(0);
				content.setScaleX(1);
				content.setScaleY(1);
			}
			ViewGroup.LayoutParams lp = topPanel.getLayoutParams();
			lp.height = Math.round(TOP_H * density * scale);
			topPanel.setLayoutParams(lp);
			topPanel.setVisibility(View.VISIBLE);
		}
	}

	/**
	 * 缩放某个碎片的根视图（240dp × 280dp 的设计内容）并锚定到中间容器。
	 * 内容统一从左上角等比放大铺满整宽（240dp × scale = 屏幕宽）。
	 * 垂直位置（顶部对齐 / 居中）必须在布局完成后，用容器真实高度减去缩放后的
	 * 内容高度来定，否则重力会基于"未缩放的小盒子"居中，导致内容下移、顶部留空、
	 * 底部被裁切——因为 setScaleX/Y 不改变布局边界，不能依赖 gravity。
	 *
	 * @param content  碎片根视图（其父必须是中间容器 midPanel）
	 * @param topAlign true=贴容器顶部（桌面待机屏）；false=垂直居中（菜单/百宝箱）
	 */
	protected void scaleMidContent(View content, boolean topAlign) {
		DisplayMetrics dm = getResources().getDisplayMetrics();
		float density = dm.density;
		float widthDp = dm.widthPixels / density;
		float heightDp = dm.heightPixels / density;
		float scale = widthDp / BASE_W;
		if (BASE_W > 0 && 320f * scale > heightDp) {
			scale = heightDp / 320f;
		}

		content.setPivotX(0);
		content.setPivotY(0);
		content.setScaleX(scale);
		content.setScaleY(scale);

		final float fScale = scale;
		final float fDensity = density;
		content.post(new Runnable() {
			@Override
			public void run() {
				ViewParent parent = content.getParent();
				if (!(parent instanceof View)) {
					return;
				}
				View panel = (View) parent;
				panel.setVisibility(View.VISIBLE);
				int panelH = panel.getHeight();
				int visualH = (int) (MID_H * fDensity * fScale);
				int offset = topAlign ? 0 : Math.max(0, (panelH - visualH) / 2);
				content.setY(offset);
			}
		});
	}

	/** 获取系统状态栏高度；如无法取得则返回 0。 */
	private int getStatusBarHeight() {
		int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
		return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : 0;
	}

	/**
	 * 缩放面板内第一个子视图（即 240dp 宽的设计内容）。
	 * centerPivot=true 时以内容中心为支点（用于需垂直居中的中间区域）；
	 * 否则以左上角为支点。setHeight=true 时还会把面板高度设为按比例放大的像素值（用于顶/底栏）。
	 */
	private void scalePanelContent(View panel, float scale, float baseH, float density,
								   boolean centerPivot, boolean setHeight) {
		if (panel == null) {
			return;
		}
		if (panel instanceof ViewGroup && ((ViewGroup) panel).getChildCount() > 0) {
			View content = ((ViewGroup) panel).getChildAt(0);
			if (centerPivot) {
				// 内容固定为 240dp x baseH，支点取其中心。
				content.setPivotX(120f * density);
				content.setPivotY(baseH * density / 2f);
			} else {
				content.setPivotX(0);
				content.setPivotY(0);
			}
			content.setScaleX(scale);
			content.setScaleY(scale);
		}
		if (setHeight) {
			// 顶/底栏高度按 scale 放大，宽度由 match_parent 铺满。
			ViewGroup.LayoutParams lp = panel.getLayoutParams();
			lp.height = Math.round(baseH * density * scale);
			panel.setLayoutParams(lp);
		}
		panel.setVisibility(View.VISIBLE);
	}
}
