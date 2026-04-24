package com.tuservidor.cobbleranked.gui;

import com.tuservidor.cobbleranked.league.data.LeagueStorage;
import com.tuservidor.cobbleranked.league.model.LeagueMember;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public class AdminGymGui extends BaseGui {

    public AdminGymGui(ServerPlayerEntity player) {
        super(player, 6, "&a&lGimnasios — Gestionar");
    }

    @Override
    protected void build() {
        fillBorder(GuiItem.darkFiller());

        List<LeagueMember> gyms = LeagueStorage.getMembersByRole(LeagueMember.Role.GYM_LEADER);
        
        // FIX 10: Array extendido para dar lugar físico a 18 gimnasios en la interfaz
        int[] slots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31}; 

        for (int slot = 1; slot <= 18; slot++) {
            final int gymSlot = slot;
            int guiSlot = slots[slot - 1];
            LeagueMember found = gyms.stream().filter(m -> m.getSlot() == gymSlot).findFirst().orElse(null);

            if (found != null) {
                final LeagueMember member = found;
                setItem(guiSlot, GuiItem.of(GuiItem.GYM_ICON,
                    "&a#" + slot + " &f" + found.getName(),
                    "&7Tipo: &e" + found.getType(),
                    "&7V: &a" + found.getWins() + " &7D: &c" + found.getLosses(),
                    "", "&cClick para QUITAR"
                ), () -> {
                    LeagueStorage.removeMember(member.getUuid());
                    com.tuservidor.cobbleranked.queue.MatchmakingQueue.forceEjectLeagueMember(member.getUuid());
                    new AdminGymGui(player).open();
                });
            } else {
                setItem(guiSlot, GuiItem.of(Items.GRAY_DYE,
                    "&8#" + slot + " Vacante",
                    "&7Añadir: /league add gym <j> " + slot + " <tipo>"
                ));
            }
        }

        setItem(49, GuiItem.of(GuiItem.BACK, "&7← Volver al Panel"), () -> new AdminMainGui(player).open());
    }
}