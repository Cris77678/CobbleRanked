package com.tuservidor.cobbleranked;

import com.tuservidor.cobbleranked.commands.RankedCommand;
import com.tuservidor.cobbleranked.config.RankedConfig;
import com.tuservidor.cobbleranked.data.BattleTracker;
import com.tuservidor.cobbleranked.data.StatsStorage;
import com.tuservidor.cobbleranked.elo.EloCalculator;
import com.tuservidor.cobbleranked.placeholder.PlaceholderRegistry;
import com.tuservidor.cobbleranked.league.commands.LeagueCommand;
import com.tuservidor.cobbleranked.league.data.LeagueStorage;
import com.tuservidor.cobbleranked.league.battle.LeagueBattleTracker;
import com.tuservidor.cobbleranked.queue.MatchmakingQueue;
import com.tuservidor.cobbleranked.queue.QueueCommand;
import com.tuservidor.cobbleranked.gui.BaseGui;
import com.tuservidor.cobbleranked.gui.CobbleRankedCommand;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CobbleRanked implements ModInitializer {

    public static final String MOD_ID = "cobbleranked";
    public static final String CONFIG_PATH = "config/cobbleranked/config.json";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static MinecraftServer server;
    public static RankedConfig config = new RankedConfig();
    public static boolean seasonActive = false;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setDaemon(true).setNameFormat("CobbleRanked-%d").build());

    public static void runAsync(Runnable task) {
        if (EXECUTOR.isShutdown()) { task.run(); return; }
        CompletableFuture.runAsync(task, EXECUTOR)
            .orTimeout(15, TimeUnit.SECONDS)
            .exceptionally(e -> { LOGGER.error("Async error", e); return null; });
    }

    @Override
    public void onInitialize() {
        LOGGER.info("CobbleRanked loading...");

        MatchmakingQueue.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            RankedCommand.register(dispatcher);
            LeagueCommand.register(dispatcher);
            QueueCommand.register(dispatcher);
            CobbleRankedCommand.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
            server = srv;
            config.init();
            
            // CORRECCIÓN: Carga la temporada correctamente desde disco
            seasonActive = config.isSeasonActive();
            
            EloCalculator.setKFactor(config.getKFactor());
            BattleTracker.register();

            LeagueStorage.load();
            LeagueBattleTracker.register();

            try {
                PlaceholderRegistry.register();
            } catch (NoClassDefFoundError ignored) {}

            LOGGER.info("CobbleRanked ready! Season: {} | Active: {} | League members: {}",
                config.getSeasonName(), seasonActive, LeagueStorage.getAllMembers().size());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> {
            StatsStorage.flush(handler.player.getUuid());
            MatchmakingQueue.leaveAll(handler.player.getUuid());
            MatchmakingQueue.clearConfirmed(handler.player.getUuid());
            BaseGui.CLICK_MAPS.remove(handler.player.getUuid());
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(srv -> EXECUTOR.shutdown());
    }
}