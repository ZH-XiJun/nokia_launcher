package ru.playsoftware.j2meloader.nokia;

import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ru.playsoftware.j2meloader.R;

/**
 * 诺基亚风格界面的公共基类。
 * 设计基准为 240x320（参考截图）。布局里应放一个 id=design_root、尺寸固定为
 * 240dp x 320dp 的内层容器，外层铺满全屏。基类会按当前屏幕分辨率对整个内层
 * 容器做等比缩放（contain 模式，取宽/高缩放比的较小值，保证不被裁切），
 * 从而一套布局自适应任意分辨率。
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
		View design = findViewById(R.id.design_root);
		if (design == null) {
			return;
		}
		DisplayMetrics dm = getResources().getDisplayMetrics();
		float scale = Math.min(dm.widthPixels / 240f, dm.heightPixels / 320f);
		design.setScaleX(scale);
		design.setScaleY(scale);
	}
}
