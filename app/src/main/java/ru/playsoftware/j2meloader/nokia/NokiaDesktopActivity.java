package ru.playsoftware.j2meloader.nokia;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import ru.playsoftware.j2meloader.R;

public class NokiaDesktopActivity extends NokiaBaseActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_nokia_desktop);
		setupNokiaUi();
	}

	public void onOpenMenu(View view) {
		startActivity(new Intent(this, NokiaMenuActivity.class));
	}
}
