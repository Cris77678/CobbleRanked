package com.tuservidor.cobbleranked.gui;

import com.tuservidor.cobbleranked.league.data.LeagueStorage;
import com.tuservidor.cobbleranked.league.model.LeagueMember;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Shows 8 gym leader slots. Click occupied slot → remove. Click empty → prompt.
 */
public class AdminGymGui extends BaseGui {

    public AdminGymGui(ServerPlayerEntity player) {
        super(player, 4, "&a&lGimnasios — Gestionar Líderes");
    }

    @Override
    protected void build() {
        fillBorder(GuiItem.filler());

        List<LeagueMember> gyms = LeagueStorage.getMembersByRole(LeagueMember.Role.GYM_LEADER);

        // Slots 10-17 for gym 1-8
        for (int slot = 1; slot <= 8; slot++) {
            int guiSlot = 9 + slot;
            final int gymSlot = slot;
            LeagueMember found = gyms.stream()
                .filter(m -> m.getSlot() == gymSlot).findFirst().orElse(null);

            if (found != null) {
                final LeagueMember member = found;
                setItem(guiSlot, GuiItem.of(GuiItem.GYM_ICON,
                    "&a#" + slot + " &f" + found.getName(),
                    "&7Tipo: &e" + found.getType(),
                    "&7V: &a" + found.getWins() + " &7D: &c" + found.getLosses(),
                    "",
                    "&cClick para QUITAR de la liga"
                ), () -> {
                    LeagueStorage.removeMember(member.getUuid());
                    broadcastRemove(member);
                    new AdminGymGui(player).open(); // refresh
                });
            } else {
                setItem(guiSlot, GuiItem.of(Items.GRAY_DYE,
                    "&8#" + slot + " Vacante",
                    "&7No hay líder en este gimnasio",
                    "",
                    "&7Para añadir un líder usa:",
                    "&e/league add gym <jugador> " + slot + " <tipo>"
                ));
            }
        }

        // Back
        setItem(31, GuiItem.of(GuiItem.BACK, "&7← Volver al Panel",
            "&7Regresa al panel de administración"
        ), () -> new AdminMainGui(player).open());
    }

    private void broadcastRemove(LeagueMember member) {
        CobbleRanked.server.execute(() ->
            CobbleRanked.server.getPlayerManager().broadcast(
                Text.literal("\n§6§l🏅 CAMBIO EN LA LIGA 🏅\n"
                    + member.getRole().getColor() + "§l" + member.getName()
                    + " §r§7ha dejado su puesto de §aLíder #" + member.getSlot()
                    + " §7(" + member.getType() + ")§7.\n"
                    + "§7¡El Gimnasio #" + member.getSlot() + " está vacante!\n"),
                false));
    }

    // Reference CobbleRanked for server access
    private static com.tuservidor.cobbleranked.CobbleRanked CobbleRanked;
    static { try { CobbleRanked = null; } catch (Exception ignored) {} }

    // Use the static field directly
    private static net.minecraft.server.MinecraftServer getServer() {
        return com.tuservidor.cobbleranked.CobbleRanked.server;
    }
}
