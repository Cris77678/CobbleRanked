package com.tuservidor.cobbleranked.placeholder;

import com.tuservidor.cobbleranked.CobbleRanked;
import com.tuservidor.cobbleranked.data.StatsStorage;
import com.tuservidor.cobbleranked.model.PlayerStats;
import java.lang.reflect.Method;

public class PlaceholderRegistry {

    public static void register() {
        try {
            Class<?> placeholdersClass = Class.forName("eu.pb4.placeholders.api.Placeholders");
            Class<?> identifierClass   = Class.forName("net.minecraft.util.Identifier");
            Class<?> resultClass       = Class.forName("eu.pb4.placeholders.api.PlaceholderResult");
            Class<?> handlerClass      = Class.forName("eu.pb4.placeholders.api.PlaceholderHandler");

            Method registerMethod = placeholdersClass.getMethod("register", identifierClass, handlerClass);
            Method ofMethod = identifierClass.getMethod("of", String.class, String.class);
            Method valueMethod = resultClass.getMethod("value", Class.forName("net.minecraft.text.Text"));
            Class<?> textClass = Class.forName("net.minecraft.text.Text");
            Method literalMethod = textClass.getMethod("literal", String.class);

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
                        
                        // CORRECCIÓN: Usa el sistema optimizado que ya no inunda el disco
                        PlayerStats stats = StatsStorage.get(uuid, name);
                        String val = String.valueOf(stats.getClass().getMethod(getter).invoke(stats));
                        Object text = literalMethod.invoke(null, val);
                        return valueMethod.invoke(null, text);
                    }
                );
                registerMethod.invoke(null, id, handler);
            }
            CobbleRanked.LOGGER.info("PlaceholderAPI placeholders registered successfully.");
        } catch (Exception e) {
            CobbleRanked.LOGGER.info("PlaceholderAPI no encontrado - desactivado.");
        }
    }
}