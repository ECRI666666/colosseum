package de.ecri.colosseum;

import de.ecri.colosseum.dimension.ArenaBuilder;
import de.ecri.colosseum.dimension.ArenaProtection;
import de.ecri.colosseum.lobby.LobbyController;
import de.ecri.colosseum.player.ArenaTransfer;
import de.ecri.colosseum.portal.PortalIgnition;
import de.ecri.colosseum.registry.ModAttachments;
import de.ecri.colosseum.registry.ModBlocks;
import de.ecri.colosseum.run.ArenaRun;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Einstieg der Mod: registriert Inhalte und meldet die Spielregeln beim Event-Bus an. */
@Mod(Colosseum.MOD_ID)
public final class Colosseum {
    public static final String MOD_ID = "colosseum";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public Colosseum(IEventBus modBus) {
        ModBlocks.BLOCKS.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        PortalIgnition.register();
        ArenaTransfer.register();
        ArenaProtection.register();
        ArenaBuilder.register();
        ArenaRun.register();
        LobbyController.register();
    }
}
