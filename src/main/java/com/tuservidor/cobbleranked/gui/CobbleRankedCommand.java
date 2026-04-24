package com.tuservidor.cobbleranked.gui;

import com.mojang.brigadier.CommandDispatcher;
import com.tuservidor.cobbleranked.CobbleRanked;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * /cobbleranked — Opens the main GUI.
 *   - Admins (op 2 or cobbleranked.admin) → AdminMainGui
 *   - Everyone else → PlayerMainGui
 */
public class CobbleRankedCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("cobbleranked")
                .requires(ServerCommandSource::isExecutedByPlayer)
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    if (isAdmin(ctx.getSource())) {
                        new AdminMainGui(player).open();
                    } else {
                        new PlayerMainGui(player).open();
                    }
                    return 1;
                })
        );

        // Alias /cr
        dispatcher.register(
            CommandManager.literal("cr")
                .requires(ServerCommandSource::isExecutedByPlayer)
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    if (isAdmin(ctx.getSource())) {
                        new AdminMainGui(player).open();
                    } else {
                        new PlayerMainGui(player).open();
                    }
                    return 1;
                })
        );
    }

    private static boolean isAdmin(ServerCommandSource src) {
        if (!src.isExecutedByPlayer()) return true;
        if (src.hasPermissionLevel(2)) return true;
        try {
            var player = src.getPlayer();
            if (player == null) return false;
            var lp = net.luckperms.api.LuckPermsProvider.get()
                .getUserManager().getUser(player.getUuid());
            return lp != null && lp.getCachedData().getPermissionData()
                .checkPermission("cobbleranked.admin").asBoolean();
        } catch (Exception e) { return false; }
    }
}
