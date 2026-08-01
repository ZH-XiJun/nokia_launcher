package ru.playsoftware.j2meloader.nokia;

import android.content.res.Resources;

/**
 * 统一尺寸换算工具类。
 * 所有 nokia 界面与 Drawable 的 dp → px 换算均收口于此，
 * 替代各 Fragment / Dialog 中各自复制的私有 dp() 方法。
 *
 * 行为：dp = value × density，与原 8 处私有实现逐值一致。
 */
public final class NokiaDimens {

    private NokiaDimens() {
        // 工具类，禁止实例化
    }

    /** dp 转 px（整数，直接截断小数） */
    public static int dp(Resources res, float value) {
        return (int) (value * res.getDisplayMetrics().density);
    }

    /** dp 转 px（保留小数） */
    public static float dpF(Resources res, float value) {
        return value * res.getDisplayMetrics().density;
    }
}
