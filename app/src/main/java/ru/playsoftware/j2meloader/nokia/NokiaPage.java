package ru.playsoftware.j2meloader.nokia;

/**
 * 诺基亚页面契约：声明底部菜单栏差异。
 * <p>
 * 页面实现该接口后，由 {@link NokiaDesktopActivity} 在页面切到前台 / 页面主动请求时
 * 自动装配底部菜单栏（{@code setBottomBar}），页面自身不再直接操作
 * bottomLeft / bottomCenter / bottomRight 三个 TextView。
 * <p>
 * 三个 getter 允许<b>动态取值</b>：页面内部状态（焦点、mode、覆盖模式、向导步骤）变化后，
 * 调用 {@code host.refreshPageBar()} 即可重新装配。
 */
public interface NokiaPage extends NokiaFocusHost {

	/** 底部菜单栏中间显示的界面名；返回 null 则隐藏（如桌面）。 */
	String getPageTitle();

	/** 左软键文字；返回 null 则隐藏。 */
	String getSoftLeftText();

	/** 右软键文字；返回 null 则隐藏。 */
	String getSoftRightText();
}
