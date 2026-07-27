package ru.playsoftware.j2meloader.nokia;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import ru.playsoftware.j2meloader.R;

public class NokiaMenuActivity extends NokiaBaseActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_nokia_menu);
		setupNokiaUi();
	}

	public void onOpenBox(View view) {
		startActivity(new Intent(this, NokiaBoxActivity.class));
	}

	public void onExit(View view) {
		finish();
	}
}
