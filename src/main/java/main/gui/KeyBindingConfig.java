package main.gui;

import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按键绑定配置。
 * 管理游戏动作到键盘按键的映射，支持动态修改。
 */
public class KeyBindingConfig {

    private static final Map<String, Integer> bindings = new LinkedHashMap<>();
    private static final Map<Integer, String> actionFields = new LinkedHashMap<>();

    static {
        bindings.put("向前移动", KeyEvent.VK_W);
        bindings.put("向左移动", KeyEvent.VK_A);
        bindings.put("向后移动", KeyEvent.VK_S);
        bindings.put("向右移动", KeyEvent.VK_D);
        bindings.put("跳跃", KeyEvent.VK_SPACE);
        bindings.put("调试界面", KeyEvent.VK_F3);
        bindings.put("背包", KeyEvent.VK_E);
        bindings.put("暂停菜单", KeyEvent.VK_ESCAPE);
        bindings.put("冲刺", KeyEvent.VK_ALT);

        actionFields.put(KeyEvent.VK_W, "w");
        actionFields.put(KeyEvent.VK_A, "a");
        actionFields.put(KeyEvent.VK_S, "s");
        actionFields.put(KeyEvent.VK_D, "d");
        actionFields.put(KeyEvent.VK_SPACE, "space");
        actionFields.put(KeyEvent.VK_F3, "f3");
        actionFields.put(KeyEvent.VK_E, "eKey");
        actionFields.put(KeyEvent.VK_ESCAPE, "esc");
        actionFields.put(KeyEvent.VK_ALT, "alt");
    }

    public static String[] getActionNames() {
        return bindings.keySet().toArray(new String[0]);
    }

    public static int getKeyCode(String actionName) {
        return bindings.getOrDefault(actionName, -1);
    }

    public static String getFieldName(String actionName) {
        int code = getKeyCode(actionName);
        return actionFields.getOrDefault(code, "");
    }

    public static boolean setKeyCode(String actionName, int newKeyCode) {
        if (!bindings.containsKey(actionName)) return false;
        int oldCode = bindings.get(actionName);
        bindings.put(actionName, newKeyCode);
        actionFields.remove(oldCode);
        actionFields.put(newKeyCode, actionFields.getOrDefault(oldCode, ""));
        return true;
    }

    public static String getKeyText(String actionName) {
        int code = getKeyCode(actionName);
        return KeyEvent.getKeyText(code);
    }
}