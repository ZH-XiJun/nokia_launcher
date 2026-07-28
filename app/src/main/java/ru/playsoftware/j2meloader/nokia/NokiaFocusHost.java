package ru.playsoftware.j2meloader.nokia;

/**
 * Fragment 实现此接口即可接收来自 Activity 的按键导航事件。
 * NokiaDesktopActivity.dispatchKeyEvent() 会将按键解析后的动作分发给当前 Fragment。
 */
public interface NokiaFocusHost {

	/**
	 * 方向键导航。
	 * @param direction 为 NokiaKeyBinding.ACTION_UP/DOWN/LEFT/RIGHT 之一
	 * @return true 表示已处理该事件
	 */
	boolean onDirection(int direction);

	/** 确认键被按下。 */
	boolean onSelect();

	/** 左软键被按下。 */
	boolean onSoftLeft();

	/** 右软键被按下。 */
	boolean onSoftRight();

	/** 返回键被按下。 */
	boolean onBack();
}
