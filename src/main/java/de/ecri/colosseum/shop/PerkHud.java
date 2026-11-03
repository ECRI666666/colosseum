package de.ecri.colosseum.shop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * Zeigt die eigenen Perks in der Seitenleiste rechts. Die Anzeige geht nur an diesen einen Spieler und
 * berührt die Scoreboards des Servers nicht.
 */
public final class PerkHud {
    private static final String NAME = "colosseum_perks";

    private static Objective objective(Component title) {
        return new Scoreboard().addObjective(NAME, ObjectiveCriteria.DUMMY, title, ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
    }

    public static List<Component> lines(Collection<Perk> perks) {
        List<Component> out = new ArrayList<>();
        for (Perk perk : Perk.values()) if (perks.contains(perk)) out.add(de.ecri.colosseum.Lang.t("hud.line", perk.title, perk.line2));
        if (out.isEmpty()) out.add(de.ecri.colosseum.Lang.t("hud.none"));
        return out;
    }

    /** Baut die Anzeige neu auf. wasShown sagt, ob der Client gerade eine alte Anzeige hat. */
    public static void show(ServerPlayer player, Collection<Perk> perks, boolean wasShown) {
        Objective objective = objective(de.ecri.colosseum.Lang.t("hud.title", perks.size(), Perk.MAX_ACTIVE).withStyle(ChatFormatting.GOLD));
        if (wasShown) player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_REMOVE));
        player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD));
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
        List<Component> lines = lines(perks);
        for (int i = 0; i < lines.size(); i++) {
            Component text = lines.get(i).copy().withStyle(perks.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.AQUA);
            player.connection.send(new ClientboundSetScorePacket("perk" + i, NAME, lines.size() - i, Optional.of(text), Optional.empty()));
        }
    }

    public static void hide(ServerPlayer player) {
        player.connection.send(new ClientboundSetObjectivePacket(objective(Component.empty()), ClientboundSetObjectivePacket.METHOD_REMOVE));
    }

    private PerkHud() {}
}
