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

/** 功能表（应用网格）中间内容碎片。 */
public class NokiaMenuFragment extends Fragment {
	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
							 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_nokia_menu, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		// 顶对齐，使"功能表"标题紧贴信号栏下方，避免垂直居中产生的多余间距。
		host.scaleMidContent(view, true);

		View wall = host.findViewById(R.id.wallpaper);
		if (wall != null) {
			wall.setBackgroundResource(R.drawable.bg_nokia_menu);
		}
		// 功能表界面顶部不加标题。
		TextView title = host.findViewById(R.id.topTitle);
		if (title != null) {
			title.setText("");
		}
		// 底部中间原是进入功能表的入口，进入后清空，避免重复显示"功能表"。
		TextView bc = host.findViewById(R.id.bottomCenter);
		if (bc != null) {
			bc.setText("");
		}
		TextView bl = host.findViewById(R.id.bottomLeft);
		if (bl != null) {
			bl.setText("选择");
		}
		TextView br = host.findViewById(R.id.bottomRight);
		if (br != null) {
			br.setText("退出");
			br.setOnClickListener(v -> host.exitCurrent());
		}

		View cellBox = view.findViewById(R.id.cellBox);
		if (cellBox != null) {
			cellBox.setOnClickListener(v -> host.openBox());
		}
	}
}
