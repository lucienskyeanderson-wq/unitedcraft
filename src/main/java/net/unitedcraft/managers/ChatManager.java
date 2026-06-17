package net.unitedcraft.managers;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.unitedcraft.UnitedCraft;
import net.unitedcraft.models.Nation;
import net.unitedcraft.models.NationMember;

import java.sql.*;
import java.util.Optional;

public class ChatManager {

    private final NationManager nationManager = new NationManager();

    /** Toggle nation chat mode for a player. Returns true if now ON, false if OFF. */
    public boolean toggleNationChat(java.util.UUID playerUuid) {
        boolean isOn = isNationChatOn(playerUuid);
        try {
            if (isOn) {
                try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                        "DELETE FROM nation_chat_toggle WHERE player_uuid = ?")) {
                    ps.setString(1, playerUuid.toString());
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                        "INSERT OR IGNORE INTO nation_chat_toggle (player_uuid) VALUES (?)")) {
                    ps.setString(1, playerUuid.toString());
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error toggling nation chat", e);
        }
        return !isOn;
    }

    public boolean isNationChatOn(java.util.UUID playerUuid) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "SELECT player_uuid FROM nation_chat_toggle WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            return ps.executeQuery().next();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error checking nation chat", e);
        }
        return false;
    }

    /**
     * Sends a nation chat message to all online members of the player's nation.
     * Format: [NationName] [Role] PlayerName: message
     */
    public void sendNationMessage(ServerPlayerEntity sender, String message, MinecraftServer server) {
        Optional<NationMember> memberOpt = nationManager.getMember(sender.getUuid());
        if (memberOpt.isEmpty() || memberOpt.get().getNationId() == null) {
            sender.sendMessage(Text.literal("§cYou are not in a nation."));
            return;
        }

        NationMember member = memberOpt.get();
        Optional<Nation> nationOpt = nationManager.getNationById(member.getNationId());
        if (nationOpt.isEmpty()) return;

        Nation nation = nationOpt.get();
        String formatted = "§6[" + nation.getName() + "] " + member.getRoleDisplay() + " §f" +
            sender.getName().getString() + "§7: §f" + message;

        // Send to all online nation members
        nationManager.getMembers(nation.getId()).forEach(m -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(m.getPlayerUuid());
            if (p != null) p.sendMessage(Text.literal(formatted));
        });
    }

    /**
     * Sends an ally chat message — visible to all online members of allied nations too.
     */
    public void sendAllyMessage(ServerPlayerEntity sender, String message,
                                 MinecraftServer server, AllianceManager allianceManager) {
        Optional<NationMember> memberOpt = nationManager.getMember(sender.getUuid());
        if (memberOpt.isEmpty() || memberOpt.get().getNationId() == null) {
            sender.sendMessage(Text.literal("§cYou are not in a nation."));
            return;
        }

        NationMember member = memberOpt.get();
        Optional<Nation> nationOpt = nationManager.getNationById(member.getNationId());
        if (nationOpt.isEmpty()) return;

        Nation nation = nationOpt.get();
        String formatted = "§d[ALLY] §6[" + nation.getName() + "] " + member.getRoleDisplay() +
            " §f" + sender.getName().getString() + "§7: §f" + message;

        // Own nation
        sendToNation(server, nation.getId(), formatted);

        // Allied nations
        for (String allyId : allianceManager.getAlliedNationIds(nation.getId())) {
            sendToNation(server, allyId, formatted);
        }
    }

    private void sendToNation(MinecraftServer server, String nationId, String message) {
        nationManager.getMembers(nationId).forEach(m -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(m.getPlayerUuid());
            if (p != null) p.sendMessage(Text.literal(message));
        });
    }
}
