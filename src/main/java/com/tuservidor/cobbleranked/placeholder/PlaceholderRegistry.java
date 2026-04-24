package com.tuservidor.cobbleranked.placeholder;

import com.tuservidor.cobbleranked.CobbleRanked;
import com.tuservidor.cobbleranked.data.StatsStorage;
import com.tuservidor.cobbleranked.model.PlayerStats;

import java.lang.reflect.Method;

/**
 * Registers placeholders via reflection so PlaceholderAPI is fully optional.
 * If PlaceholderAPI is not installed, this class does nothing.
 *
 * Supported placeholders:
 *   %cobbleranked:elo%         → current ELO
 *   %cobbleranked:rank%        → rank name (Bronce/Plata/Oro/Diamante)
 *   %cobbleranked:rank_color%  → colored rank (§6Oro ⚔⚔⚔)
 *   %cobbleranked:wins%        → total wins
 *   %cobbleranked:losses%      → total losses
 *   %cobbleranked:winrate%     → win rate as percentage
 *   %cobbleranked:streak%      → current win streak
 *   %cobbleranked:season%      → current season name
 */
public class PlaceholderRegistry {

    public static void register() {
        try {
            // Check if PlaceholderAPI is present via reflection
            Class<?> placeholdersClass = Class.forName("eu.pb4.placeholders.api.Placeholders");
            Class<?> identifierClass   = Class.forName("net.minecraft.util.Identifier");
            Class<?> resultClass       = Class.forName("eu.pb4.placeholders.api.PlaceholderResult");
            Class<?> handlerClass      = Class.forName("eu.pb4.placeholders.api.PlaceholderHandler");

            Method registerMethod = placeholdersClass.getMethod("register",
                identifierClass, handlerClass);
            Method ofMethod = identifierClass.getMethod("of", String.class, String.class);
            Method valueMethod = resultClass.getMethod("value",
                Class.forName("net.minecraft.text.Text"));
            Class<?> textClass = Class.forName("net.minecraft.text.Text");
            Method literalMethod = textClass.getMethod("literal", String.class);

            // Register each placeholder
            String[][] placeholders = {
                {"elo",        "getElo"},
                {"wins",       "getWins"},
                {"losses",     "getLosses"},
                {"streak",     "getWinStreak"}
            };

            for (String[] entry : placeholders) {
                final String key = entry[0];
                final String getter = entry[1];
                Object id = ofMethod.invoke(null, "cobbleranked", key);
                Object handler = java.lang.reflect.Proxy.newProxyInstance(
                    handlerClass.getClassLoader(),
                    new Class[]{ handlerClass },
                    (proxy, method, args) -> {
                        if (!method.getName().equals("onPlaceholderRequest")) return null;
                        Object ctx = args[0];
                        Method hasPlayer = ctx.getClass().getMethod("hasPlayer");
                        if (!(Boolean) hasPlayer.invoke(ctx)) return resultClass.getMethod("invalid", String.class).invoke(null, "no player");
                        Method getPlayer = ctx.getClass().getMethod("player");
                        Object player = getPlayer.invoke(ctx);
                        Method getUuid = player.getClass().getMethod("getUuid");
                        Method getName = player.getClass().getMethod("getName");
                        java.util.UUID uuid = (java.util.UUID) getUuid.invoke(player);
                        String name = ((net.minecraft.text.Text) getName.invoke(player)).getString();
                        PlayerStats stats = StatsStorage.get(uuid, name);
                        String val = String.valueOf(stats.getClass().getMethod(getter).invoke(stats));
                        Object text = literalMethod.invoke(null, val);
                        return valueMethod.invoke(null, text);
                    }
                );
                registerMethod.invoke(null, id, handler);
            }

            CobbleRanked.LOGGER.info("PlaceholderAPI placeholders registered successfully.");
        } catch (ClassNotFoundException e) {
            CobbleRanked.LOGGER.info("PlaceholderAPI not found - placeholders disabled.");
        } catch (Exception e) {
            CobbleRanked.LOGGER.warn("Failed to register placeholders: {}", e.getMessage());
        }
    }
}
