package com.tuservidor.cobbleranked.league.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tuservidor.cobbleranked.CobbleRanked;
import com.tuservidor.cobbleranked.league.battle.LeagueBattleTracker;
import com.tuservidor.cobbleranked.league.data.LeagueStorage;
import com.tuservidor.cobbleranked.league.model.LeagueBattle;
import com.tuservidor.cobbleranked.league.model.LeagueMember;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * /league — Pokémon League management and battle system.
 *
 * ── Admin commands (op level 2 or cobbleranked.admin) ──────────────────────
 *   /league add gym    <player> <slot 1-8> <type>   – Register gym leader
 *   /league add elite  <player> <slot 1-4> <type>   – Register Elite Four member
 *   /league add champ  <player>             <type>   – Register Champion
 *   /league remove     <player>                      – Remove from league (announces)
 *
 * ── Battle commands ─────────────────────────────────────────────────────────
 *   /league battle <member> <win|loss>               – Manually record a battle result
 *       (self = challenger, member = who was challenged)
 *
 * ── Info commands ───────────────────────────────────────────────────────────
 *   /league list                                     – Show full league roster
 *   /league info <player>                            – Show member stats
 *   /league history [player]                         – Show recent battles
 */
public class LeagueCommand {

    /** Los 18 tipos de Pokémon con sus nombres correctos en español */
    private static final List<String> POKEMON_TYPES = List.of(
        "Acero", "Agua", "Bicho", "Dragón", "Eléctrico", "Fantasma",
        "Fuego", "Hada", "Hielo", "Lucha", "Normal", "Planta",
        "Psíquico", "Roca", "Siniestro", "Tierra", "Veneno", "Volador"
    );

    private static final SuggestionProvider<ServerCommandSource> TYPE_SUGGESTIONS =
        (ctx, builder) -> {
            String input = builder.getRemaining().toLowerCase();
            POKEMON_TYPES.stream()
                .filter(t -> t.toLowerCase().startsWith(input))
                .forEach(builder::suggest);
            return builder.buildFuture();
        };

    private static final SuggestionProvider<ServerCommandSource> MEMBER_SUGGESTIONS =
        (ctx, builder) -> {
            String input = builder.getRemaining().toLowerCase();
            LeagueStorage.getAllMembers().stream()
                .map(LeagueMember::getName)
                .filter(n -> n.toLowerCase().startsWith(input))
                .forEach(builder::suggest);
            return builder.buildFuture();
        };

    private static final SuggestionProvider<ServerCommandSource> RESULT_SUGGESTIONS =
        (ctx, builder) -> {
            builder.suggest("win");
            builder.suggest("loss");
            return builder.buildFuture();
        };

    /** Sugiere jugadores que están conectados en ese momento */
    private static final SuggestionProvider<ServerCommandSource> ONLINE_PLAYER_SUGGESTIONS =
        (ctx, builder) -> {
            String input = builder.getRemaining().toLowerCase();
            CobbleRanked.server.getPlayerManager().getPlayerList().stream()
                .map(p -> p.getName().getString())
                .filter(n -> n.toLowerCase().startsWith(input))
                .forEach(builder::suggest);
            return builder.buildFuture();
        };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        var base = CommandManager.literal("league");

        // ── /league add ───────────────────────────────────────────────────────
        var add = CommandManager.literal("add")
            .requires(LeagueCommand::isAdmin);

        // /league add gym <player> <slot> <type>
        add.then(CommandManager.literal("gym")
            .then(CommandManager.argument("player", StringArgumentType.word())
                .suggests(ONLINE_PLAYER_SUGGESTIONS)
                .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 8))
                    .then(CommandManager.argument("type", StringArgumentType.word())
                        .suggests(TYPE_SUGGESTIONS)
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "player");
                            int    slot = IntegerArgumentType.getInteger(ctx, "slot");
                            String type = StringArgumentType.getString(ctx, "type");
                            return addMember(ctx.getSource(), name, LeagueMember.Role.GYM_LEADER, slot, type);
                        })
                    )
                )
            )
        );

        // /league add elite <player> <slot> <type>
        add.then(CommandManager.literal("elite")
            .then(CommandManager.argument("player", StringArgumentType.word())
                .suggests(ONLINE_PLAYER_SUGGESTIONS)
                .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 4))
                    .then(CommandManager.argument("type", StringArgumentType.word())
                        .suggests(TYPE_SUGGESTIONS)
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "player");
                            int    slot = IntegerArgumentType.getInteger(ctx, "slot");
                            String type = StringArgumentType.getString(ctx, "type");
                            return addMember(ctx.getSource(), name, LeagueMember.Role.ELITE_FOUR, slot, type);
                        })
                    )
                )
            )
        );

        // /league add champ <player>  — el Campeón no tiene tipo
        add.then(CommandManager.literal("champ")
            .then(CommandManager.argument("player", StringArgumentType.word())
                .suggests(ONLINE_PLAYER_SUGGESTIONS)
                .executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "player");
                    return addMember(ctx.getSource(), name, LeagueMember.Role.CHAMPION, 1, null);
                })
            )
        );

        base.then(add);

        // ── /league remove <player> ───────────────────────────────────────────
        base.then(CommandManager.literal("remove")
            .requires(LeagueCommand::isAdmin)
            .then(CommandManager.argument("player", StringArgumentType.word())
                .suggests(MEMBER_SUGGESTIONS)
                .executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "player");
                    return removeMember(ctx.getSource(), name);
                })
            )
        );

        // ── /league battle <member> <win|loss> ────────────────────────────────
        base.then(CommandManager.literal("battle")
            .requires(src -> src.isExecutedByPlayer())
            .then(CommandManager.argument("member", StringArgumentType.word())
                .suggests(MEMBER_SUGGESTIONS)
                .then(CommandManager.argument("result", StringArgumentType.word())
                    .suggests(RESULT_SUGGESTIONS)
                    .executes(ctx -> {
                        String memberName = StringArgumentType.getString(ctx, "member");
                        String resultStr  = StringArgumentType.getString(ctx, "result");
                        return recordBattle(ctx.getSource(), memberName, resultStr);
                    })
                )
            )
        );

        // ── /league list ──────────────────────────────────────────────────────
        base.then(CommandManager.literal("list")
            .executes(ctx -> {
                showList(ctx.getSource());
                return 1;
            })
        );

        // ── /league info <player> ─────────────────────────────────────────────
        base.then(CommandManager.literal("info")
            .then(CommandManager.argument("player", StringArgumentType.word())
                .executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "player");
                    return showInfo(ctx.getSource(), name);
                })
            )
        );

        // ── /league history [player] ──────────────────────────────────────────
        base.then(CommandManager.literal("history")
            .executes(ctx -> {
                showHistory(ctx.getSource(), null);
                return 1;
            })
            .then(CommandManager.argument("player", StringArgumentType.word())
                .executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "player");
                    showHistory(ctx.getSource(), name);
                    return 1;
                })
            )
        );

        // ── /league help ──────────────────────────────────────────────────────
        base.executes(ctx -> {
            sendMsg(ctx.getSource(),
                "§6§l=== Liga Pokémon ===\n" +
                "§e/league list §7- Ver la liga completa\n" +
                "§e/league info <jugador> §7- Ver info de un miembro\n" +
                "§e/league history [jugador] §7- Historial de batallas\n" +
                "§e/league battle <miembro> <win|loss> §7- Registrar resultado\n" +
                "§7§o(Admins)\n" +
                "§e/league add gym <jugador> <slot 1-8> <tipo> §7- Añadir líder\n" +
                "§e/league add elite <jugador> <slot 1-4> <tipo> §7- Añadir Alto Mando\n" +
                "§e/league add champ <jugador> <tipo> §7- Añadir Campeón\n" +
                "§e/league remove <jugador> §7- Quitar de la liga");
            return 1;
        });

        dispatcher.register(base);
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private static int addMember(ServerCommandSource src, String playerName,
                                  LeagueMember.Role role, int slot, String type) {
        ServerPlayerEntity target = CobbleRanked.server.getPlayerManager().getPlayer(playerName);
        if (target == null) {
            sendMsg(src, CobbleRanked.config.getPrefix()
                + "§cEl jugador §e" + playerName + " §cno está en línea.");
            return 0;
        }

        UUID uuid = target.getUuid();

        // If already a member, update them
        boolean isUpdate = LeagueStorage.isMember(uuid);
        LeagueMember member = new LeagueMember(uuid, playerName, role, slot, capitalize(type));
        LeagueStorage.addMember(member);

        // Notify requester
        sendMsg(src, CobbleRanked.config.getPrefix()
            + "§a" + (isUpdate ? "Actualizado" : "Registrado") + ": "
            + member.formatted());

        // Server-wide announcement
        String announcement = buildAddAnnouncement(member, isUpdate);
        broadcast(announcement);

        return 1;
    }

    private static int removeMember(ServerCommandSource src, String playerName) {
        Optional<LeagueMember> opt = LeagueStorage.getMemberByName(playerName);
        if (opt.isEmpty()) {
            sendMsg(src, CobbleRanked.config.getPrefix()
                + "§c" + playerName + " §cno es miembro de la liga.");
            return 0;
        }

        LeagueMember member = opt.get();
        LeagueStorage.removeMember(member.getUuid());

        sendMsg(src, CobbleRanked.config.getPrefix()
            + "§7" + playerName + " fue removido de la liga.");

        // Server-wide announcement
        String announcement = buildRemoveAnnouncement(member);
        broadcast(announcement);

        return 1;
    }

    private static int recordBattle(ServerCommandSource src, String memberName, String resultStr) {
        if (!src.isExecutedByPlayer()) {
            sendMsg(src, CobbleRanked.config.getPrefix() + "§cSolo jugadores pueden registrar batallas.");
            return 0;
        }

        boolean challengerWon;
        if (resultStr.equalsIgnoreCase("win") || resultStr.equalsIgnoreCase("victoria")) {
            challengerWon = true;
        } else if (resultStr.equalsIgnoreCase("loss") || resultStr.equalsIgnoreCase("derrota")) {
            challengerWon = false;
        } else {
            sendMsg(src, CobbleRanked.config.getPrefix()
                + "§cResultado inválido. Usa: §ewin §co §eloss");
            return 0;
        }

        Optional<LeagueMember> opt = LeagueStorage.getMemberByName(memberName);
        if (opt.isEmpty()) {
            sendMsg(src, CobbleRanked.config.getPrefix()
                + "§c" + memberName + " §cno es miembro de la liga. Revisa §e/league list");
            return 0;
        }

        ServerPlayerEntity challenger;
        try { challenger = src.getPlayer(); }
        catch (Exception e) { return 0; }

        if (challenger == null) return 0;

        // Prevent recording against yourself
        if (challenger.getUuid().equals(opt.get().getUuid())) {
            sendMsg(src, CobbleRanked.config.getPrefix() + "§cNo puedes registrar una batalla contra ti mismo.");
            return 0;
        }

        LeagueBattleTracker.registerManual(
            challenger.getUuid(), challenger.getName().getString(),
            opt.get(), challengerWon);

        sendMsg(src, CobbleRanked.config.getPrefix() + "§aBatalla registrada.");
        return 1;
    }

    private static void showList(ServerCommandSource src) {
        StringBuilder sb = new StringBuilder("§6§l=== Liga Pokémon ===\n");

        // Gym Leaders
        List<LeagueMember> gyms = LeagueStorage.getMembersByRole(LeagueMember.Role.GYM_LEADER);
        sb.append("§a§lLíderes de Gimnasio §7(").append(gyms.size()).append("/8)\n");
        if (gyms.isEmpty()) {
            sb.append("  §8Ninguno registrado.\n");
        } else {
            gyms.forEach(m -> sb.append("  §7#").append(m.getSlot())
                .append(" §f").append(m.getName())
                .append(" §8- §7Tipo: §e").append(m.getType())
                .append(" §8| §7V:§a").append(m.getWins())
                .append(" §7D:§c").append(m.getLosses()).append("\n"));
        }

        // Elite Four
        List<LeagueMember> e4 = LeagueStorage.getMembersByRole(LeagueMember.Role.ELITE_FOUR);
        sb.append("§d§lAlto Mando §7(").append(e4.size()).append("/4)\n");
        if (e4.isEmpty()) {
            sb.append("  §8Ninguno registrado.\n");
        } else {
            e4.forEach(m -> sb.append("  §7#").append(m.getSlot())
                .append(" §f").append(m.getName())
                .append(" §8- §7Tipo: §e").append(m.getType())
                .append(" §8| §7V:§a").append(m.getWins())
                .append(" §7D:§c").append(m.getLosses()).append("\n"));
        }

        // Champion
        List<LeagueMember> champs = LeagueStorage.getMembersByRole(LeagueMember.Role.CHAMPION);
        sb.append("§6§lCampeón\n");
        if (champs.isEmpty()) {
            sb.append("  §8Ninguno registrado.\n");
        } else {
            champs.forEach(m -> sb.append("  §f").append(m.getName())
                .append(" §8- §7Tipo: §e").append(m.getType())
                .append(" §8| §7V:§a").append(m.getWins())
                .append(" §7D:§c").append(m.getLosses()).append("\n"));
        }

        sendMsg(src, sb.toString().trim());
    }

    private static int showInfo(ServerCommandSource src, String playerName) {
        Optional<LeagueMember> opt = LeagueStorage.getMemberByName(playerName);
        if (opt.isEmpty()) {
            sendMsg(src, CobbleRanked.config.getPrefix()
                + "§c" + playerName + " §cno es miembro de la liga.");
            return 0;
        }

        LeagueMember m = opt.get();
        List<LeagueBattle> history = LeagueStorage.getBattlesForMember(m.getUuid(), 5);

        StringBuilder sb = new StringBuilder(
            "§6=== " + m.getName() + " ===\n"
            + "§7Rol: " + m.getRoleLabel() + "\n"
            + "§7Tipo: §e" + m.getType() + "\n"
            + "§7Victorias: §a" + m.getWins() + " §7| Derrotas: §c" + m.getLosses()
            + " §7| Total: §f" + m.getTotalBattles() + "\n"
        );

        if (!history.isEmpty()) {
            sb.append("§7Últimos retadores:\n");
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm");
            for (LeagueBattle b : history) {
                String color = b.challengerWon() ? "§c" : "§a";
                String result = b.challengerWon() ? "DERROTA" : "VICTORIA";
                sb.append("  ").append(color).append(result)
                  .append(" §7vs §f").append(b.getChallengerName())
                  .append(" §8[").append(sdf.format(new Date(b.getTimestamp()))).append("]\n");
            }
        }

        sendMsg(src, sb.toString().trim());
        return 1;
    }

    private static void showHistory(ServerCommandSource src, String filterName) {
        List<LeagueBattle> list;

        if (filterName != null) {
            Optional<LeagueMember> opt = LeagueStorage.getMemberByName(filterName);
            if (opt.isEmpty()) {
                sendMsg(src, CobbleRanked.config.getPrefix()
                    + "§c" + filterName + " §cno es miembro de la liga.");
                return;
            }
            list = LeagueStorage.getBattlesForMember(opt.get().getUuid(), 10);
        } else {
            list = LeagueStorage.getRecentBattles(10);
        }

        if (list.isEmpty()) {
            sendMsg(src, CobbleRanked.config.getPrefix() + "§7Sin historial de batallas de liga.");
            return;
        }

        StringBuilder sb = new StringBuilder("§6=== Historial de Liga ===\n");
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm");

        for (LeagueBattle b : list) {
            String resultColor = b.challengerWon() ? "§a" : "§c";
            String resultText  = b.challengerWon()
                ? "§e" + b.getChallengerName() + resultColor + " venció a " + b.getMemberName()
                : "§f" + b.getMemberName() + resultColor + " defendió vs " + b.getChallengerName();
            sb.append(resultColor).append("● ")
              .append(resultText)
              .append(" §8[").append(sdf.format(new Date(b.getTimestamp()))).append("]\n");
        }

        sendMsg(src, sb.toString().trim());
    }

    // ── Announcements ─────────────────────────────────────────────────────────

    private static String buildAddAnnouncement(LeagueMember member, boolean isUpdate) {
        String icon = switch (member.getRole()) {
            case GYM_LEADER -> "🏅";
            case ELITE_FOUR -> "💜";
            case CHAMPION   -> "👑";
        };
        String action = isUpdate ? "actualizado" : "registrado";
        return "\n"
            + "§6§l" + icon + " LIGA POKÉMON " + icon + "\n"
            + member.getRole().getColor() + "§l" + member.getName()
            + " §r§7ha sido " + action + " como "
            + member.getRoleLabel() + "§7!\n"
            + (member.getRole() == LeagueMember.Role.GYM_LEADER
                ? "§7Tipo de especialidad: §e" + member.getType() + "\n"
                : "§7Tipo de especialidad: §e" + member.getType() + "\n");
    }

    private static String buildRemoveAnnouncement(LeagueMember member) {
        String icon = switch (member.getRole()) {
            case GYM_LEADER -> "🏅";
            case ELITE_FOUR -> "💜";
            case CHAMPION   -> "👑";
        };
        return "\n"
            + "§6§l" + icon + " CAMBIO EN LA LIGA " + icon + "\n"
            + member.getRole().getColor() + "§l" + member.getName()
            + " §r§7ha dejado su puesto de "
            + member.getRoleLabel() + "§7.\n"
            + "§7¡El puesto de " + member.getRole().getColor()
            + member.getRole().getDisplayName() + " §7está vacante!\n";
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

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

    private static void sendMsg(ServerCommandSource src, String msg) {
        src.sendMessage(Text.literal(colorize(msg)));
    }

    private static void broadcast(String msg) {
        CobbleRanked.server.execute(() ->
            CobbleRanked.server.getPlayerManager().broadcast(
                Text.literal(colorize(msg)), false));
    }

    private static String colorize(String s) {
        return s.replace("&0","§0").replace("&1","§1").replace("&2","§2")
                .replace("&3","§3").replace("&4","§4").replace("&5","§5")
                .replace("&6","§6").replace("&7","§7").replace("&8","§8")
                .replace("&9","§9").replace("&a","§a").replace("&b","§b")
                .replace("&c","§c").replace("&d","§d").replace("&e","§e")
                .replace("&f","§f").replace("&l","§l").replace("&r","§r");
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
