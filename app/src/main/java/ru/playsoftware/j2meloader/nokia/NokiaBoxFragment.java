package ru.playsoftware.j2meloader.nokia;

import android.os.Bundle;

import ru.playsoftware.j2meloader.R;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/** 百宝箱中间内容碎片。 */
public class NokiaBoxFragment extends Fragment implements NokiaFocusHost {
	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
							 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_nokia_box, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		host.scaleMidContent(view, false);

		View wall = host.findViewById(R.id.wallpaper);
		if (wall != null) {
			wall.setBackgroundResource(R.drawable.bg_nokia_box);
		}
		TextView title = host.findViewById(R.id.topTitle);
		if (title != null) {
			title.setText("百宝箱");
		}
		TextView bl = host.findViewById(R.id.bottomLeft);
		if (bl != null) bl.setText("");
		TextView bc = host.findViewById(R.id.bottomCenter);
		if (bc != null) bc.setText("");
		TextView br = host.findViewById(R.id.bottomRight);
		if (br != null) {
			br.setText("退出");
			br.setOnClickListener(v -> host.exitCurrent());
		}
		// 百宝箱：左/中按钮均空 → 自动隐藏避免蓝色块，仅保留右"退出"
		host.setBottomBar(null, null, "退出");
	}

	// ---- NokiaFocusHost ----

	@Override
	public boolean onDirection(int direction) { return false; }

	@Override
	public boolean onSelect() { return false; }

	@Override
	public boolean onSoftLeft() { return false; }

	@Override
	public boolean onSoftRight() {
		((NokiaDesktopActivity) requireActivity()).exitCurrent();
		return true;
	}

	@Override
	public boolean onBack() {
		((NokiaDesktopActivity) requireActivity()).exitCurrent();
		return true;
	}
}
