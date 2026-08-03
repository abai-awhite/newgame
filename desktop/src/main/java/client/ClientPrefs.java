package client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/**
 * 客户端本地持久化（对应 localStorage）：
 * 玩家名 / 稳定身份 / 按键绑定 / 设置开关 / 上次世界与服务器地址。
 */
public final class ClientPrefs {

    private static Preferences prefs() {
        return Gdx.app.getPreferences("game-client");
    }

    private ClientPrefs() { }

    public static String getString(String key, String def) {
        return prefs().getString(key, def);
    }

    public static void putString(String key, String value) {
        prefs().putString(key, value).flush();
    }

    public static boolean getBoolean(String key, boolean def) {
        return prefs().getBoolean(key, def);
    }

    public static void putBoolean(String key, boolean value) {
        prefs().putBoolean(key, value).flush();
    }

    public static int getInt(String key, int def) {
        return prefs().getInteger(key, def);
    }

    public static void putInt(String key, int value) {
        prefs().putInteger(key, value).flush();
    }

    public static String getPlayerName() {
        return getString("playerName", "Player");
    }

    /** 稳定玩家身份：首次生成后持久化，重连共用（服务器按此存档）。 */
    public static String getStablePlayerId() {
        String id = getString("playerId", "");
        if (id.isEmpty()) {
            id = "p_" + Long.toString(System.currentTimeMillis(), 36)
                    + "_" + Integer.toHexString((int) (Math.random() * 0xFFFFFF));
            putString("playerId", id);
        }
        return id;
    }
}
