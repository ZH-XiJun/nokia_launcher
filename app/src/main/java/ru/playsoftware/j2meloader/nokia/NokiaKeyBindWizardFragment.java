package ru.playsoftware.j2meloader.nokia;

import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import ru.playsoftware.j2meloader.R;

/**
 * 首次启动按键绑定向导。
 * <p>
 * 状态机：INTRO（弹窗询问是否绑定）→ RECORDING（按 上/下/左/右/确认/左软键/右软键/返回
 * 顺序逐个提示并捕获一次物理键）→ DONE（标记完成并返回桌面）。
 * <p>
 * 仅首次启动弹出（由 NokiaKeyBinding.isWizardDone 控制，清数据后重置）。
 * 整个流程完全由物理按键驱动，复用 NokiaKeyRecorder 的录制捕获机制。
 */
public class NokiaKeyBindWizardFragment extends Fragment implements NokiaFocusHost, NokiaKeyRecorder {

	private static final int STATE_INTRO = 0;
	private static final int STATE_RECORDING = 1;
	private static final int STATE_DONE = 2;

	private static final int HIGHLIGHT = 0x550055FF;

	private NokiaKeyBinding keyBinding;
	private int state = STATE_INTRO;
	private int introChoice = 0;      // 0=绑定, 1=跳过
	private int recordingStep = -1;   // -1=非录制态；0..7=正在录制第 N 个动作

	private View introCard;
	private View recordingLayout;
	private View doneLayout;
	private View introBind;
	private View introSkip;
	private TextView recordPrompt;
	private TextView recordProgress;
	private TextView stepBadge;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
							 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_nokia_key_bind_wizard, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		host.scaleMidContent(view, true);

		keyBinding = new NokiaKeyBinding(requireContext());

		// 壁纸设为浅灰
		View wall = host.findViewById(R.id.wallpaper);
		if (wall != null) {
			wall.setBackgroundColor(0xFFF0F0F0);
		}

		// 顶部标题
		TextView title = host.findViewById(R.id.topTitle);
		if (title != null) {
			title.setText("按键绑定向导");
		}

		introCard = view.findViewById(R.id.introCard);
		recordingLayout = view.findViewById(R.id.recordingLayout);
		doneLayout = view.findViewById(R.id.doneLayout);
		introBind = view.findViewById(R.id.introBind);
		introSkip = view.findViewById(R.id.introSkip);
		recordPrompt = view.findViewById(R.id.recordPrompt);
		recordProgress = view.findViewById(R.id.recordProgress);
		stepBadge = view.findViewById(R.id.stepBadge);

		// 触摸支持（不影响按键路径）
		introBind.setOnClickListener(v -> {
			introChoice = 0;
			updateIntroHighlight();
			startRecording();
		});
		introSkip.setOnClickListener(v -> finishWizard(false));

		showIntro();
	}

	// ---- 状态切换 ----

	private void showIntro() {
		state = STATE_INTRO;
		recordingStep = -1;
		introCard.setVisibility(View.VISIBLE);
		recordingLayout.setVisibility(View.GONE);
		doneLayout.setVisibility(View.GONE);
		introChoice = 0;
		updateIntroHighlight();
		if (stepBadge != null) stepBadge.setText("");
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		host.setBottomBar("绑定", null, "跳过");
		NokiaLog.i("KeyWizard", "进入 INTRO 弹窗（绑定/跳过）");
	}

	private void updateIntroHighlight() {
		introBind.setBackgroundColor(introChoice == 0 ? HIGHLIGHT : 0);
		introSkip.setBackgroundColor(introChoice == 1 ? HIGHLIGHT : 0);
	}

	private void startRecording() {
		state = STATE_RECORDING;
		recordingStep = 0;
		introCard.setVisibility(View.GONE);
		recordingLayout.setVisibility(View.VISIBLE);
		doneLayout.setVisibility(View.GONE);
		// 录制态下任意键都会被捕获为当前动作的绑定键，底部栏隐藏
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		host.setBottomBar(null, null, null);
		updateRecordingPrompt();
		NokiaLog.i("KeyWizard", "开始录制，第 1 项="
				+ NokiaKeyBinding.getWizardPromptName(0));
	}

	private void updateRecordingPrompt() {
		recordPrompt.setText("请按下『" + NokiaKeyBinding.getWizardPromptName(recordingStep) + "』键");
		recordProgress.setText("第 " + (recordingStep + 1) + " / " + NokiaKeyBinding.ACTION_COUNT + " 项");
		if (stepBadge != null) {
			stepBadge.setText((recordingStep + 1) + "/" + NokiaKeyBinding.ACTION_COUNT);
		}
	}

	// ---- NokiaKeyRecorder（录制态捕获物理键）----

	@Override
	public boolean isRecording() {
		return state == STATE_RECORDING;
	}

	@Override
	public void onKeyRecorded(int keycode) {
		if (state != STATE_RECORDING) return;
		int action = recordingStep;

		keyBinding.setKeyCode(action, keycode);
		// 同步到全局 JAR 设置
		NokiaGlobalProfile.syncKeyBindings(requireContext());
		NokiaLog.i("KeyWizard", "第 " + (action + 1) + " 项 绑定成功 "
				+ NokiaLog.keyName(keycode));

		int next = action + 1;
		if (next >= NokiaKeyBinding.ACTION_COUNT) {
			finishWizard(true);
		} else {
			recordingStep = next;
			updateRecordingPrompt();
			NokiaLog.i("KeyWizard", "进入第 " + (next + 1) + " 项="
					+ NokiaKeyBinding.getWizardPromptName(next));
		}
	}

	private void finishWizard(boolean bound) {
		state = STATE_DONE;
		recordingStep = -1;
		introCard.setVisibility(View.GONE);
		recordingLayout.setVisibility(View.GONE);
		doneLayout.setVisibility(View.VISIBLE);
		NokiaDesktopActivity host = (NokiaDesktopActivity) requireActivity();
		host.setBottomBar(null, null, null);
		if (stepBadge != null) stepBadge.setText("");
		// 标记向导已完成，下次启动不再弹出
		keyBinding.markWizardDone();
		// 让 Activity 的内存绑定立即生效
		host.reloadKeyBindings();
		NokiaLog.i("KeyWizard", "向导结束 bound=" + bound + "，标记完成并准备返回桌面");

		// 短暂展示完成页后返回桌面待机屏
		new Handler().postDelayed(() -> {
			FragmentManager fm = requireActivity().getSupportFragmentManager();
			fm.beginTransaction()
					.replace(R.id.midPanel, new NokiaDesktopFragment())
					.commit();
			NokiaLog.i("KeyWizard", "返回桌面待机屏");
		}, 1200);
	}

	// ---- NokiaFocusHost（仅 INTRO 状态使用）----

	@Override
	public boolean onDirection(int direction) {
		if (state != STATE_INTRO) return true;
		introChoice = (introChoice == 0) ? 1 : 0;
		updateIntroHighlight();
		NokiaLog.d("KeyWizard", "INTRO 切换选择 -> " + (introChoice == 0 ? "绑定" : "跳过"));
		return true;
	}

	@Override
	public boolean onSelect() {
		if (state != STATE_INTRO) return true;
		if (introChoice == 0) {
			NokiaLog.i("KeyWizard", "INTRO 选择 绑定");
			startRecording();
		} else {
			NokiaLog.i("KeyWizard", "INTRO 选择 跳过");
			finishWizard(false);
		}
		return true;
	}

	@Override
	public boolean onSoftLeft() {
		if (state != STATE_INTRO) return true;
		NokiaLog.i("KeyWizard", "左软键 -> 绑定");
		startRecording();
		return true;
	}

	@Override
	public boolean onSoftRight() {
		if (state != STATE_INTRO) return true;
		NokiaLog.i("KeyWizard", "右软键 -> 跳过");
		finishWizard(false);
		return true;
	}

	@Override
	public boolean onBack() {
		// 录制态的 BACK 由 onKeyRecorded 处理（跳过当前项）；此处仅 INTRO 生效
		if (state != STATE_INTRO) return true;
		NokiaLog.i("KeyWizard", "返回键 -> 跳过");
		finishWizard(false);
		return true;
	}
}
