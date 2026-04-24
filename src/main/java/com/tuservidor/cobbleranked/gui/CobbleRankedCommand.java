package com.tuservidor.cobbleranked.gui;

import com.mojang.brigadier.CommandDispatcher;
import com.tuservidor.cobbleranked.CobbleRanked;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public class CobbleRankedCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("cobbleranked")
                .requires(ServerCommandSource::isExecutedByPlayer)
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    if (isAdmin(ctx.getSource())) new AdminMainGui(player).open();
                    else new PlayerMainGui(player).open();
                    return 1;
                })
        );
        dispatcher.register(
            CommandManager.literal("cr").requires(ServerCommandSource::isExecutedByPlayer).executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    if (isAdmin(ctx.getSource())) new AdminMainGui(player).open();
                    else new PlayerMainGui(player).open();
                    return 1;
                })
        );
    }

    // CORRECCIÓN: Chequeo de LuckPerms seguro mediante FabricLoader (Evita el Crash si no está instalado)
    public static boolean isAdmin(ServerCommandSource src) {
        if (!src.isExecutedByPlayer()) return true;
        if (src.hasPermissionLevel(2)) return true;
        
        if (FabricLoader.getInstance().isModLoaded("luckperms")) {
            return checkLuckPerms(src);
        }
        return false;
    }

    private static boolean checkLuckPerms(ServerCommandSource src) {
        try {
            var player = src.getPlayer();
            if (player == null) return false;
            var lp = net.luckperms.api.LuckPermsProvider.get().getUserManager().getUser(player.getUuid());
            return lp != null && lp.getCachedData().getPermissionData().checkPermission("cobbleranked.admin").asBoolean();
        } catch (Throwable t) { return false; }
    }
}