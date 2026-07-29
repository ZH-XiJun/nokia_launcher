package ru.playsoftware.j2meloader.nokia;

/**
 * 录制态统一接口。
 * 实现该接口的 Fragment 表示当前正在"录制物理按键"——
 * Activity 的 dispatchKeyEvent 会在 resolveAction 之前，把任意物理按键
 * 直接喂给 {@link #onKeyRecorded(int)}，从而捕获用户按下的键用于绑定。
 * <p>
 * 按键绑定设置界面（NokiaKeyBindFragment）与首次启动向导
 * （NokiaKeyBindWizardFragment）都实现本接口，复用同一套按键捕获机制。
 */
public interface NokiaKeyRecorder {

	/** 当前是否处于录制态（捕获任意物理键）。 */
	boolean isRecording();

	/** 录制态下捕获到一次物理按键时回调。 */
	void onKeyRecorded(int keycode);
}
