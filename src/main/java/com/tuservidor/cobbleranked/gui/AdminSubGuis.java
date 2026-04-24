package com.tuservidor.cobbleranked.gui;

import com.tuservidor.cobbleranked.CobbleRanked;
import com.tuservidor.cobbleranked.data.StatsStorage;
import com.tuservidor.cobbleranked.league.data.LeagueStorage;
import com.tuservidor.cobbleranked.league.model.LeagueMember;
import com.tuservidor.cobbleranked.queue.MatchmakingQueue;
import com.tuservidor.cobbleranked.queue.QueueEntry;
import com.tuservidor.cobbleranked.queue.QueueType;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

// ══════════════════════════════════════════════════════════════════════════════
// AdminEliteGui — Manage Elite Four slots 1-4
// ══════════════════════════════════════════════════════════════════════════════
class AdminEliteGui extends BaseGui {

    AdminEliteGui(ServerPlayerEntity player) {
        super(player, 4, "&d&lAlto Mando — Gestionar Miembros");
    }

    @Override
    protected void build() {
        fillBorder(GuiItem.darkFiller());

        List<LeagueMember> e4 = LeagueStorage.getMembersByRole(LeagueMember.Role.ELITE_FOUR);

        // Slots 11-14 for E4 positions 1-4
        int[] guiSlots = {11, 12, 13, 14};
        for (int i = 0; i < 4; i++) {
            int pos = i + 1;
            int guiSlot = guiSlots[i];
            LeagueMember found = e4.stream()
                .filter(m -> m.getSlot() == pos).findFirst().orElse(null);

            if (found != null) {
                final LeagueMember member = found;
                setItem(guiSlot, GuiItem.of(GuiItem.ELITE_ICON,
                    "&d#" + pos + " &f" + found.getName(),
                    "&7Tipo: &e" + found.getType(),
                    "&7V: &a" + found.getWins() + " &7D: &c" + found.getLosses(),
                    "",
                    "&cClick para QUITAR del Alto Mando"
                ), () -> {
                    LeagueStorage.removeMember(member.getUuid());
                    broadcastRemove(member);
                    new AdminEliteGui(player).open();
                });
            } else {
                setItem(guiSlot, GuiItem.of(Items.GRAY_DYE,
                    "&8#" + pos + " Vacante",
                    "&7Para añadir usa:",
                    "&e/league add elite <jugador> " + pos + " <tipo>"
                ));
            }
        }

        setItem(31, GuiItem.of(GuiItem.BACK, "&7← Volver al Panel"),
            () -> new AdminMainGui(player).open());
    }

    private void broadcastRemove(LeagueMember m) {
        CobbleRanked.server.execute(() ->
            CobbleRanked.server.getPlayerManager().broadcast(
                Text.literal("§d§l💜 CAMBIO EN LA LIGA 💜\n"
                    + "§d§l" + m.getName() + " §r§7dejó el Alto Mando #" + m.getSlot() + ".\n"), false));
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// AdminChampGui — Manage Champion
// ══════════════════════════════════════════════════════════════════════════════
class AdminChampGui extends BaseGui {

    AdminChampGui(ServerPlayerEntity player) {
        super(player, 3, "&6&lCampeón — Gestionar");
    }

    @Override
    protected void build() {
        fillBorder(GuiItem.darkFiller());

        List<LeagueMember> champs = LeagueStorage.getMembersByRole(LeagueMember.Role.CHAMPION);

        if (!champs.isEmpty()) {
            LeagueMember champ = champs.get(0);
            setItem(13, GuiItem.of(GuiItem.CHAMPION_ICON,
                "&6&l👑 " + champ.getName(),
                "&7Victorias: &a" + champ.getWins()
                    + " &7| Derrotas: &c" + champ.getLosses(),
                "",
                "&cClick para QUITAR como Campeón"
            ), () -> {
                LeagueStorage.removeMember(champ.getUuid());
                CobbleRanked.server.execute(() ->
                    CobbleRanked.server.getPlayerManager().broadcast(
                        Text.literal("§6§l👑 EL TRONO ESTÁ VACANTE 👑\n"
                            + "§6§l" + champ.getName() + " §r§7dejó el puesto de Campeón.\n"), false));
                new AdminChampGui(player).open();
            });
        } else {
            setItem(13, GuiItem.of(Items.NETHER_STAR,
                "&8Trono Vacante",
                "&7No hay Campeón registrado.",
                "",
                "&7Para añadir usa:",
                "&e/league add champ <jugador>"
            ));
        }

        setItem(22, GuiItem.of(GuiItem.BACK, "&7← Volver al Panel"),
            () -> new AdminMainGui(player).open());
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// AdminMembersGui — Full roster view with remove buttons
// ══════════════════════════════════════════════════════════════════════════════
class AdminMembersGui extends BaseGui {

    AdminMembersGui(ServerPlayerEntity player) {
        super(player, 6, "&b&lLiga — Todos los Miembros");
    }

    @Override
    protected void build() {
        fillBorder(GuiItem.filler());

        List<LeagueMember> all = LeagueStorage.getAllMembers();
        all.sort((a, b) -> {
            int roleComp = a.getRole().ordinal() - b.getRole().ordinal();
            return roleComp != 0 ? roleComp : a.getSlot() - b.getSlot();
        });

        // Inner slots starting at 10
        int[] slots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
        for (int i = 0; i < Math.min(all.size(), slots.length); i++) {
            LeagueMember m = all.get(i);
            setItem(slots[i], GuiItem.of(
                m.getRole() == LeagueMember.Role.GYM_LEADER  ? GuiItem.GYM_ICON :
                m.getRole() == LeagueMember.Role.ELITE_FOUR  ? GuiItem.ELITE_ICON :
                                                               GuiItem.CHAMPION_ICON,
                m.getRole().getColor() + m.getName(),
                "&7Rol: " + m.getRoleLabel(),
                m.getType() != null ? "&7Tipo: &e" + m.getType() : "",
                "&7V: &a" + m.getWins() + " &7D: &c" + m.getLosses(),
                "",
                "&cClick para QUITAR de la liga"
            ), () -> {
                LeagueStorage.removeMember(m.getUuid());
                new AdminMembersGui(player).open();
            });
        }

        if (all.isEmpty()) {
            setItem(22, GuiItem.of(Items.BARRIER, "&cSin miembros",
                "&7Nadie registrado en la liga aún."));
        }

        setItem(49, GuiItem.of(GuiItem.BACK, "&7← Volver al Panel"),
            () -> new AdminMainGui(player).open());
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// AdminSeasonGui — Start / End season
// ══════════════════════════════════════════════════════════════════════════════
class AdminSeasonGui extends BaseGui {

    AdminSeasonGui(ServerPlayerEntity player) {
        super(player, 3, "&6&lGestionar Temporada");
    }

    @Override
    protected void build() {
        fillBorder(GuiItem.darkFiller());

        // Season info
        setItem(13, GuiItem.of(Items.BOOK,
            "&6Temporada Actual",
            "&7Nombre: &e" + CobbleRanked.config.getSeasonName(),
            "&7Número: &e" + CobbleRanked.config.getCurrentSeason(),
            "&7Estado: " + (CobbleRanked.seasonActive ? "&aActiva" : "&cInactiva")
        ));

        if (!CobbleRanked.seasonActive) {
            // Start season
            setItem(11, GuiItem.of(Items.LIME_CONCRETE,
                "&a&lIniciar Nueva Temporada",
                "&7Iniciará la temporada #" + (CobbleRanked.config.getCurrentSeason() + 1),
                "&8§o¡ADVERTENCIA! Reseteará todo el ELO",
                "",
                "&aClick para iniciar"
            ), () -> {
                int season = CobbleRanked.config.getCurrentSeason() + 1;
                String name = "Temporada " + season;
                CobbleRanked.config.setCurrentSeason(season);
                CobbleRanked.config.setSeasonName(name);
                CobbleRanked.config.init();
                CobbleRanked.seasonActive = true;
                StatsStorage.resetAll(season);
                CobbleRanked.server.execute(() ->
                    CobbleRanked.server.getPlayerManager().broadcast(
                        Text.literal(CobbleRanked.config.format(
                            CobbleRanked.config.getMsgSeasonStart(), "%name%", name)), false));
                new AdminSeasonGui(player).open();
            });
        } else {
            // End season
            setItem(15, GuiItem.of(Items.RED_CONCRETE,
                "&c&lTerminar Temporada",
                "&7Terminará: &e" + CobbleRanked.config.getSeasonName(),
                "",
                "&cClick para terminar"
            ), () -> {
                CobbleRanked.seasonActive = false;
                CobbleRanked.server.execute(() ->
                    CobbleRanked.server.getPlayerManager().broadcast(
                        Text.literal(CobbleRanked.config.format(
                            CobbleRanked.config.getMsgSeasonEnd(),
                            "%name%", CobbleRanked.config.getSeasonName())), false));
                new AdminSeasonGui(player).open();
            });
        }

        setItem(22, GuiItem.of(GuiItem.BACK, "&7← Volver al Panel"),
            () -> new AdminMainGui(player).open());
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// AdminQueueGui — View current queue contents
// ══════════════════════════════════════════════════════════════════════════════
class AdminQueueGui extends BaseGui {

    AdminQueueGui(ServerPlayerEntity player) {
        super(player, 6, "&e&lCola de Emparejamiento");
    }

    @Override
    protected void build() {
        fillBorder(GuiItem.filler());

        // Ranked queue header
        setItem(10, GuiItem.of(Items.GOLDEN_SWORD,
            "&6&lCola Ranked",
            "&7Jugadores: &e" + MatchmakingQueue.queueSize(QueueType.RANKED),
            "&7Distancia máx: &e50 bloques"
        ));

        // League queue header
        setItem(14, GuiItem.of(Items.SHIELD,
            "&d&lCola Liga",
            "&7Jugadores: &e" + MatchmakingQueue.queueSize(QueueType.LEAGUE),
            "&7Distancia máx: &e50 bloques"
        ));

        // Info
        setItem(22, GuiItem.of(Items.COMPASS,
            "&7El matchmaker corre cada &e5 segundos",
            "&7automáticamente en el servidor.",
            "",
            "&8Los jugadores deben estar en el",
            "&8mismo mundo a ≤50 bloques."
        ));

        setItem(49, GuiItem.of(GuiItem.BACK, "&7← Volver al Panel"),
            () -> new AdminMainGui(player).open());
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// AdminConfigGui — Edit all config values in-GUI
// ══════════════════════════════════════════════════════════════════════════════
class AdminConfigGui extends BaseGui {

    AdminConfigGui(ServerPlayerEntity player) {
        super(player, 6, "&6&lConfiguración — CobbleRanked");
    }

    @Override
    protected void build() {
        fillBorder(GuiItem.darkFiller());

        // ELO Inicial
        setItem(10, GuiItem.of(Items.GOLD_INGOT,
            "&6ELO Inicial",
            "&7Valor actual: &e" + CobbleRanked.config.getStartingElo(),
            "",
            "&aClick izq: &7+100   &cClick der: &7-100"
        ), () -> {
            // Left click = increase, handled via separate items below
        });
        setItem(19, GuiItem.of(Items.LIME_DYE,  "&a+100 ELO Inicial"), () -> {
            CobbleRanked.config.setStartingElo(CobbleRanked.config.getStartingElo() + 100);
            CobbleRanked.config.init(); new AdminConfigGui(player).open();
        });
        setItem(28, GuiItem.of(Items.RED_DYE,   "&c-100 ELO Inicial"), () -> {
            int v = Math.max(0, CobbleRanked.config.getStartingElo() - 100);
            CobbleRanked.config.setStartingElo(v);
            CobbleRanked.config.init(); new AdminConfigGui(player).open();
        });

        // K-Factor
        setItem(12, GuiItem.of(Items.EXPERIENCE_BOTTLE,
            "&bK-Factor (ELO por batalla)",
            "&7Valor actual: &e" + CobbleRanked.config.getKFactor(),
            "&8Más alto = más cambio de ELO",
            "",
            "&a+8 / &c-8"
        ));
        setItem(21, GuiItem.of(Items.LIME_DYE,  "&a+8 K-Factor"), () -> {
            CobbleRanked.config.setKFactor(CobbleRanked.config.getKFactor() + 8);
            CobbleRanked.config.init(); new AdminConfigGui(player).open();
        });
        setItem(30, GuiItem.of(Items.RED_DYE,   "&c-8 K-Factor"), () -> {
            int v = Math.max(8, CobbleRanked.config.getKFactor() - 8);
            CobbleRanked.config.setKFactor(v);
            CobbleRanked.config.init(); new AdminConfigGui(player).open();
        });

        // Cooldown
        setItem(14, GuiItem.of(Items.CLOCK,
            "&eCooldown entre batallas",
            "&7Valor actual: &e" + CobbleRanked.config.getBattleCooldownSeconds() + " segundos",
            "",
            "&a+30s / &c-30s"
        ));
        setItem(23, GuiItem.of(Items.LIME_DYE,  "&a+30s Cooldown"), () -> {
            CobbleRanked.config.setBattleCooldownSeconds(
                CobbleRanked.config.getBattleCooldownSeconds() + 30);
            CobbleRanked.config.init(); new AdminConfigGui(player).open();
        });
        setItem(32, GuiItem.of(Items.RED_DYE,   "&c-30s Cooldown"), () -> {
            int v = Math.max(0, CobbleRanked.config.getBattleCooldownSeconds() - 30);
            CobbleRanked.config.setBattleCooldownSeconds(v);
            CobbleRanked.config.init(); new AdminConfigGui(player).open();
        });

        // Leaderboard size
        setItem(16, GuiItem.of(Items.GOLDEN_SWORD,
            "&6Tamaño del Leaderboard",
            "&7Valor actual: &eTop " + CobbleRanked.config.getLeaderboardSize(),
            "",
            "&a+5 / &c-5"
        ));
        setItem(25, GuiItem.of(Items.LIME_DYE,  "&a+5 Leaderboard"), () -> {
            CobbleRanked.config.setLeaderboardSize(
                Math.min(20, CobbleRanked.config.getLeaderboardSize() + 5));
            CobbleRanked.config.init(); new AdminConfigGui(player).open();
        });
        setItem(34, GuiItem.of(Items.RED_DYE,   "&c-5 Leaderboard"), () -> {
            int v = Math.max(5, CobbleRanked.config.getLeaderboardSize() - 5);
            CobbleRanked.config.setLeaderboardSize(v);
            CobbleRanked.config.init(); new AdminConfigGui(player).open();
        });

        // ELO thresholds for ranks
        setItem(37, GuiItem.of(Items.IRON_INGOT,
            "&7Umbral Plata: &e" + CobbleRanked.config.getEloSilver(),
            "&8(ELO mínimo para Plata)",
            "&a+50 / &c-50"
        ));
        setItem(38, GuiItem.of(Items.LIME_DYE, "&a+50 Umbral Plata"), () -> {
            CobbleRanked.config.setEloSilver(CobbleRanked.config.getEloSilver() + 50);
            CobbleRanked.config.init(); new AdminConfigGui(player).open();
        });
        setItem(39, GuiItem.of(Items.RED_DYE, "&c-50 Umbral Plata"), () -> {
            int v = Math.max(0, CobbleRanked.config.getEloSilver() - 50);
            CobbleRanked.config.setEloSilver(v);
            CobbleRanked.config.init(); new AdminConfigGui(player).open();
        });

        setItem(41, GuiItem.of(Items.GOLD_INGOT,
            "&6Umbral Oro: &e" + CobbleRanked.config.getEloGold(),
            "&8(ELO mínimo para Oro)",
            "&a+50 / &c-50"
        ));
        setItem(42, GuiItem.of(Items.LIME_DYE, "&a+50 Umbral Oro"), () -> {
            CobbleRanked.config.setEloGold(CobbleRanked.config.getEloGold() + 50);
            CobbleRanked.config.init(); new AdminConfigGui(player).open();
        });
        setItem(43, GuiItem.of(Items.RED_DYE, "&c-50 Umbral Oro"), () -> {
            int v = Math.max(0, CobbleRanked.config.getEloGold() - 50);
            CobbleRanked.config.setEloGold(v);
            CobbleRanked.config.init(); new AdminConfigGui(player).open();
        });

        setItem(49, GuiItem.of(GuiItem.BACK, "&7← Volver al Panel"),
            () -> new AdminMainGui(player).open());
    }
}
