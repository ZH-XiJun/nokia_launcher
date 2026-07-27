package ru.playsoftware.j2meloader.nokia;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ru.playsoftware.j2meloader.R;

public class NokiaDesktopActivity extends AppCompatActivity {
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
		getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
				android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_nokia_desktop);
		tvTime = findViewById(R.id.tvTime);
	}

	public void onOpenMenu(android.view.View view) {
		startActivity(new Intent(this, NokiaMenuActivity.class));
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
}
