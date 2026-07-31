package ru.playsoftware.j2meloader.nokia;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import ru.playsoftware.j2meloader.R;

/**
 * 诺基亚风格应用选项菜单弹窗。
 *
 * 显示"启动"/"设置"/"卸载"三个选项，方向键上下导航，确认键触发，
 * 左/右软键或返回键关闭弹窗。
 *
 * 按键规范：
 * - 接入 NokiaKeyBinding，禁止写死 keyCode
 * - 软键栏无高亮/焦点逻辑（只显示文字标签）
 * - 内容区选项使用 bg_nokia_selected_dark 高亮
 */
public class NokiaAppOptionsDialog extends DialogFragment {
    private static final String TAG = "AppOptions";
    private static final String ARG_NAME = "app_name";

    private static final int OPTION_LAUNCH = 0;
    private static final int OPTION_SETTINGS = 1;
    private static final int OPTION_UNINSTALL = 2;
    private static final int OPTION_COUNT = 3;

    private final LinearLayout[] optionRows = new LinearLayout[OPTION_COUNT];
    private int focusIndex = 0;
    private OptionsListener listener;

    public interface OptionsListener {
        void onLaunch();
        void onSettings();
        void onUninstall();
    }

    public static NokiaAppOptionsDialog newInstance(String appName) {
        NokiaAppOptionsDialog dialog = new NokiaAppOptionsDialog();
        Bundle args = new Bundle();
        args.putString(ARG_NAME, appName);
        dialog.setArguments(args);
        return dialog;
    }

    public void setOptionsListener(OptionsListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        NokiaLog.i(TAG, "onCreateDialog: 创建选项菜单");
        Dialog dialog = new Dialog(requireActivity());
        dialog.setContentView(R.layout.dialog_nokia_app_options);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(Gravity.BOTTOM);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        String appName = requireArguments().getString(ARG_NAME, "");
        TextView title = dialog.findViewById(R.id.options_title);
        if (title != null) title.setText(appName);

        // 绑定选项行
        optionRows[OPTION_LAUNCH] = dialog.findViewById(R.id.option_launch);
        optionRows[OPTION_SETTINGS] = dialog.findViewById(R.id.option_settings);
        optionRows[OPTION_UNINSTALL] = dialog.findViewById(R.id.option_uninstall);

        // 触摸支持：点击即触发
        for (int i = 0; i < OPTION_COUNT; i++) {
            final int idx = i;
            if (optionRows[i] != null) {
                optionRows[i].setOnClickListener(v -> {
                    setFocus(idx);
                    trigger(idx);
                });
            }
        }

        // 接入 NokiaKeyBinding（禁止写死 keyCode）
        final NokiaKeyBinding keyBinding =
                ((NokiaDesktopActivity) requireActivity()).getKeyBinding();

        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return true; // 消费抬起事件
            }

            // 返回键单独处理（NokiaKeyBinding 不管 BACK）
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                NokiaLog.i(TAG, "返回键：关闭选项菜单");
                dismiss();
                return true;
            }

            int action = keyBinding.resolveAction(event);
            switch (action) {
                case NokiaKeyBinding.ACTION_UP:
                    if (focusIndex > 0) {
                        setFocus(focusIndex - 1);
                    }
                    return true;
                case NokiaKeyBinding.ACTION_DOWN:
                    if (focusIndex < OPTION_COUNT - 1) {
                        setFocus(focusIndex + 1);
                    }
                    return true;
                case NokiaKeyBinding.ACTION_SELECT:
                    trigger(focusIndex);
                    return true;
                case NokiaKeyBinding.ACTION_SOFT_LEFT:
                case NokiaKeyBinding.ACTION_SOFT_RIGHT:
                    // 软键关闭弹窗（等效返回）
                    NokiaLog.i(TAG, "软键：关闭选项菜单");
                    dismiss();
                    return true;
                case NokiaKeyBinding.ACTION_LEFT:
                case NokiaKeyBinding.ACTION_RIGHT:
                    // 方向键左右无功能，消费掉避免穿透
                    return true;
                default:
                    return false;
            }
        });

        // 默认焦点在"启动"
        setFocus(0);

        // Android 12+：Dialog 窗口首个导航键会被触摸模式吞掉，show 后强制退出该状态
        dialog.setOnShowListener(d -> NokiaDialogFocus.forceNonTouchMode(dialog));

        return dialog;
    }

    private void setFocus(int index) {
        focusIndex = index;
        for (int i = 0; i < OPTION_COUNT; i++) {
            if (optionRows[i] != null) {
                if (i == index) {
                    optionRows[i].setBackgroundResource(R.drawable.bg_nokia_selected_dark);
                } else {
                    optionRows[i].setBackgroundResource(0);
                }
            }
        }
    }

    private void trigger(int index) {
        NokiaLog.i(TAG, "触发选项: " + index);
        if (listener == null) {
            dismiss();
            return;
        }
        switch (index) {
            case OPTION_LAUNCH:
                listener.onLaunch();
                break;
            case OPTION_SETTINGS:
                listener.onSettings();
                break;
            case OPTION_UNINSTALL:
                listener.onUninstall();
                break;
        }
        dismiss();
    }
}
