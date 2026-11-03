package de.ecri.colosseum;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Alle Texte im Spiel kommen aus den Sprachdateien der Mod. Der Server schickt nur die Kennung,
 * jeder Client zeigt sie in seiner eigenen Sprache. Fehlt eine Sprache, gilt Englisch.
 */
public final class Lang {
    public static MutableComponent t(String key, Object... args) {
        return key.isEmpty() ? Component.empty() : Component.translatable("colosseum." + key, args);
    }

    private Lang() {}
}
