package ru.playsoftware.j2meloader.nokia;

import android.os.Bundle;
import android.view.View;

import ru.playsoftware.j2meloader.R;

public class NokiaBoxActivity extends NokiaBaseActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_nokia_box);
		setupNokiaUi();
	}

	public void onExit(View view) {
		finish();
	}
}
