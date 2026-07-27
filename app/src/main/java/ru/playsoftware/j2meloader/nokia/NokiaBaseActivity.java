package ru.playsoftware.j2meloader.nokia;

import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
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
	private static final float TOP_H = 18f;
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
		// 把应用顶栏整体下移系统状态栏高度，避免被系统状态栏盖住内容；
		// 最顶部的壁纸仍然可见，形成沉浸式背景。
		if (topPanel != null) {
			ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) topPanel.getLayoutParams();
			lp.topMargin = getStatusBarHeight();
			topPanel.setLayoutParams(lp);
		}

		// 顶/底栏：match_parent 宽度已保证左右铺满、贴顶贴底；
		// 仅把内层 240dp 内容等比放大，并按比例设置栏高。
		scalePanelContent(topPanel, scale, TOP_H, density, false, true);
		// 中间：match_parent 宽度铺满左右、weight 填充顶底之间；
		// 内容顶部对齐，所以从左上角等比放大，不额外设置面板高度。
		scalePanelContent(findViewById(R.id.midPanel), scale, MID_H, density, false, false);
		scalePanelContent(findViewById(R.id.bottomPanel), scale, BOT_H, density, false, true);
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
			lp.height = (int) (baseH * density * scale);
			panel.setLayoutParams(lp);
		}
		panel.setVisibility(View.VISIBLE);
	}
}
