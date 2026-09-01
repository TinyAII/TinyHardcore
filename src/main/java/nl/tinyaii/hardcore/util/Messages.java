package nl.tinyaii.hardcore.util;

import org.bukkit.ChatColor;

/**
 * 消息工具：统一颜色转义 + 前缀。
 */
public final class Messages {

    private Messages() {}

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    public static String prefix() {
        return color("&7[&c极限&7] &r");
    }
}
