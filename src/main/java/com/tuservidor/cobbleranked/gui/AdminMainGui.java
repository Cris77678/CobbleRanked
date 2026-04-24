package com.tuservidor.cobbleranked.gui;

import com.tuservidor.cobbleranked.CobbleRanked;
import com.tuservidor.cobbleranked.league.data.LeagueStorage;
import com.tuservidor.cobbleranked.league.model.LeagueMember;
import com.tuservidor.cobbleranked.queue.MatchmakingQueue;
import com.tuservidor.cobbleranked.queue.QueueType;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public class AdminMainGui extends BaseGui {

    public AdminMainGui(ServerPlayerEntity player) {
        super(player, 6, "&c&l⚙ Panel de Administración");
    }

    @Override
    protected void build() {
        fillBorder(GuiItem.darkFiller());

        // ── Liga — Registrar líderes ───────────────────────────────────────────
        setItem(10, GuiItem.of(GuiItem.GYM_ICON,
            "&a&lGestionar Líderes de Gimnasio",
            "&7Añadir o quitar líderes de los",
            "&78 gimnasios de la liga",
            "&7Registrados: &e" + LeagueStorage.getMembersByRole(LeagueMember.Role.GYM_LEADER).size() + "/8",
            "",
            "&aClick para gestionar"
        ), () -> new AdminGymGui(player).open());

        // ── Elite Four ────────────────────────────────────────────────────────
        setItem(12, GuiItem.of(GuiItem.ELITE_ICON,
            "&d&lGestionar Alto Mando",
            "&7Añadir o quitar los 4 miembros",
            "&7del Alto Mando",
            "&7Registrados: &e" + LeagueStorage.getMembersByRole(LeagueMember.Role.ELITE_FOUR).size() + "/4",
            "",
            "&dClick para gestionar"
        ), () -> new AdminEliteGui(player).open());

        // ── Champion ──────────────────────────────────────────────────────────
        List<LeagueMember> champs = LeagueStorage.getMembersByRole(LeagueMember.Role.CHAMPION);
        setItem(14, GuiItem.of(GuiItem.CHAMPION_ICON,
            "&6&lGestionar Campeón",
            "&7Registrar o quitar al Campeón",
            "&7Actual: &e" + (champs.isEmpty() ? "Vacante" : champs.get(0).getName()),
            "",
            "&6Click para gestionar"
        ), () -> new AdminChampGui(player).open());

        // ── Ver miembros ──────────────────────────────────────────────────────
        setItem(16, GuiItem.of(Items.BOOK,
            "&b&lVer Todos los Miembros",
            "&7Lista completa de la liga",
            "&7Total: &e" + LeagueStorage.getAllMembers().size(),
            "",
            "&bClick para ver"
        ), () -> new AdminMembersGui(player).open());

        // ── Temporada ─────────────────────────────────────────────────────────
        setItem(28, GuiItem.of(
            CobbleRanked.seasonActive ? Items.LIME_CONCRETE : Items.RED_CONCRETE,
            CobbleRanked.seasonActive ? "&a&lTemporada Activa" : "&c&lTemporada Inactiva",
            "&7Nombre: &e" + CobbleRanked.config.getSeasonName(),
            "&7Número: &e" + CobbleRanked.config.getCurrentSeason(),
            CobbleRanked.seasonActive
                ? "&cClick para &l§n§cterminAR§r &cla temporada"
                : "&aClick para &lINICIAR &anueva temporada",
            "",
            "&8§oLa nueva temporada reseteará todo el ELO"
        ), () -> new AdminSeasonGui(player).open());

        // ── Cola ──────────────────────────────────────────────────────────────
        setItem(30, GuiItem.of(GuiItem.QUEUE_VIEW,
            "&e&lCola de Emparejamiento",
            "&7En Ranked: &e" + MatchmakingQueue.queueSize(QueueType.RANKED) + " jugadores",
            "&7En Liga: &e" + MatchmakingQueue.queueSize(QueueType.LEAGUE) + " jugadores",
            "",
            "&eClick para ver"
        ), () -> new AdminQueueGui(player).open());

        // ── Configuración ─────────────────────────────────────────────────────
        setItem(32, GuiItem.of(GuiItem.CONFIG_ITEM,
            "&6&lConfiguración",
            "&7ELO inicial: &e" + CobbleRanked.config.getStartingElo(),
            "&7K-Factor: &e" + CobbleRanked.config.getKFactor(),
            "&7Cooldown: &e" + CobbleRanked.config.getBattleCooldownSeconds() + "s",
            "&7Leaderboard: &eTop " + CobbleRanked.config.getLeaderboardSize(),
            "",
            "&6Click para editar"
        ), () -> new AdminConfigGui(player).open());

        // ── Recargar config ───────────────────────────────────────────────────
        setItem(34, GuiItem.of(Items.WRITABLE_BOOK,
            "&7&lRecargar Config",
            "&7Recarga el archivo config.json",
            "&7del servidor",
            "",
            "&7Click para recargar"
        ), () -> {
            CobbleRanked.config.init();
            player.sendMessage(net.minecraft.text.Text.literal(
                CobbleRanked.config.format(CobbleRanked.config.getMsgReload())));
            new AdminMainGui(player).open(); // refresh
        });

        // ── Cerrar ────────────────────────────────────────────────────────────
        setItem(49, GuiItem.of(GuiItem.CLOSE, "&c&lCerrar",
            "&7Cierra el panel de administración"
        ), player::closeHandledScreen);
    }
}
