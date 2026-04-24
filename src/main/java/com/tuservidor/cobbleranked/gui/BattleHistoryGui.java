package com.tuservidor.cobbleranked.gui;

import com.tuservidor.cobbleranked.data.StatsStorage;
import com.tuservidor.cobbleranked.model.PlayerStats;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class BattleHistoryGui extends BaseGui {

    public BattleHistoryGui(ServerPlayerEntity player) {
        super(player, 6, "&b&lHistorial de Batallas");
    }

    @Override
    protected void build() {
        fillBorder(GuiItem.filler());

        PlayerStats stats = StatsStorage.get(player.getUuid(), player.getName().getString());
        List<PlayerStats.BattleRecord> history = stats.getHistory();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm");

        // Inner slots: 10-16, 19-25, 28-34 — up to 10 entries
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};

        if (history.isEmpty()) {
            setItem(22, GuiItem.of(Items.BARRIER,
                "&cSin historial",
                "&7Aún no has jugado ninguna batalla ranked."
            ));
        } else {
            for (int i = 0; i < Math.min(history.size(), slots.length); i++) {
                PlayerStats.BattleRecord r = history.get(i);
                boolean win  = r.getResult().equals("WIN");
                boolean draw = r.getResult().equals("DRAW");

                String resultColor = win ? "&a" : draw ? "&7" : "&c";
                String resultLabel = win ? "&a§lVICTORIA" : draw ? "&7EMPATE" : "&c§lDERROTA";
                String deltaStr    = r.getEloDelta() >= 0
                    ? "&a(+" + r.getEloDelta() + " ELO)"
                    : "&c(" + r.getEloDelta() + " ELO)";

                setItem(slots[i], GuiItem.of(
                    win ? Items.LIME_DYE : draw ? Items.GRAY_DYE : Items.RED_DYE,
                    resultLabel + " &7vs &f" + r.getOpponentName(),
                    "&7ELO después: &e" + r.getEloAfter() + " " + deltaStr,
                    "&8" + sdf.format(new Date(r.getTimestamp()))
                ));
            }
        }

        // Back
        setItem(49, GuiItem.of(GuiItem.BACK, "&7← Volver",
            "&7Regresa al menú principal"
        ), () -> new PlayerMainGui(player).open());
    }
}
