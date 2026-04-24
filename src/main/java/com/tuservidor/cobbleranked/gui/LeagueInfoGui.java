package com.tuservidor.cobbleranked.gui;

import com.tuservidor.cobbleranked.league.data.LeagueStorage;
import com.tuservidor.cobbleranked.league.model.LeagueMember;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public class LeagueInfoGui extends BaseGui {

    public LeagueInfoGui(ServerPlayerEntity player) {
        super(player, 6, "&d&lLiga Pokémon");
    }

    @Override
    protected void build() {
        fillBorder(GuiItem.filler());

        // ── Gym Leaders (slots 10-17) ──────────────────────────────────────────
        setItem(10, GuiItem.of(Items.IRON_SWORD, "&a§lLíderes de Gimnasio &8(1-8)"));

        List<LeagueMember> gyms = LeagueStorage.getMembersByRole(LeagueMember.Role.GYM_LEADER);
        for (int slot = 1; slot <= 8; slot++) {
            final int s = slot;
            int guiSlot = 9 + slot;
            LeagueMember found = gyms.stream()
                .filter(m -> m.getSlot() == s).findFirst().orElse(null);
            if (found != null) {
                setItem(guiSlot, GuiItem.of(GuiItem.GYM_ICON,
                    "&a#" + s + " &f" + found.getName(),
                    "&7Tipo: &e" + found.getType(),
                    "&7Victorias: &a" + found.getWins()
                        + " &7| Derrotas: &c" + found.getLosses()
                ));
            } else {
                setItem(guiSlot, GuiItem.of(Items.GRAY_DYE,
                    "&8#" + s + " Vacante",
                    "&7Ningún líder registrado"
                ));
            }
        }

        // ── Elite Four (slots 19-22) ───────────────────────────────────────────
        setItem(19, GuiItem.of(Items.DIAMOND_SWORD, "&d§lAlto Mando &8(1-4)"));

        List<LeagueMember> e4 = LeagueStorage.getMembersByRole(LeagueMember.Role.ELITE_FOUR);
        for (int slot = 1; slot <= 4; slot++) {
            final int s = slot;
            int guiSlot = 18 + slot;
            LeagueMember found = e4.stream()
                .filter(m -> m.getSlot() == s).findFirst().orElse(null);
            if (found != null) {
                setItem(guiSlot, GuiItem.of(GuiItem.ELITE_ICON,
                    "&d#" + s + " &f" + found.getName(),
                    "&7Tipo: &e" + found.getType(),
                    "&7Victorias: &a" + found.getWins()
                        + " &7| Derrotas: &c" + found.getLosses()
                ));
            } else {
                setItem(guiSlot, GuiItem.of(Items.GRAY_DYE,
                    "&8#" + s + " Vacante",
                    "&7Ningún miembro registrado"
                ));
            }
        }

        // ── Champion (slot 25) ────────────────────────────────────────────────
        List<LeagueMember> champs = LeagueStorage.getMembersByRole(LeagueMember.Role.CHAMPION);
        if (!champs.isEmpty()) {
            LeagueMember champ = champs.get(0);
            setItem(25, GuiItem.of(GuiItem.CHAMPION_ICON,
                "&6&l👑 Campeón",
                "&f" + champ.getName(),
                "&7Victorias: &a" + champ.getWins()
                    + " &7| Derrotas: &c" + champ.getLosses()
            ));
        } else {
            setItem(25, GuiItem.of(Items.NETHER_STAR,
                "&6&l👑 Campeón",
                "&8Vacante — ¡Nadie ha completado la liga!"
            ));
        }

        // ── Back ──────────────────────────────────────────────────────────────
        setItem(49, GuiItem.of(GuiItem.BACK, "&7← Volver",
            "&7Regresa al menú principal"
        ), () -> new PlayerMainGui(player).open());
    }
}
