package de.ecri.colosseum.shop;

import de.ecri.colosseum.Colosseum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Boni für den Durchlauf. Sie brauchen Strom und gehen beim Fallen oder Verlassen verloren. */
public enum Perk {
    TOUGH("perk.toughness", "perk.toughness_info", 2500, new BlockPos(9, 66, -5), Direction.NORTH, Attributes.MAX_HEALTH, 10.0, AttributeModifier.Operation.ADD_VALUE),
    SWIFT("perk.swiftness", "perk.swiftness_info", 2000, new BlockPos(-27, 66, -7), Direction.SOUTH, Attributes.MOVEMENT_SPEED, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
    MIGHT("perk.might", "perk.might_info", 3000, new BlockPos(25, 66, -19), Direction.SOUTH, Attributes.ATTACK_DAMAGE, 3.0, AttributeModifier.Operation.ADD_VALUE),
    MARKSMAN("perk.marksman", "perk.marksman_info", 2500, new BlockPos(-31, 66, 19), Direction.NORTH, null, 0, null),
    FAST_REVIVE("perk.fast_revive", "perk.fast_revive_info", 1500, new BlockPos(18, 66, 5), Direction.SOUTH, null, 0, null),
    DOUBLE_TAP("perk.double_strike", "perk.double_strike_info", 3000, new BlockPos(-16, 66, -5), Direction.NORTH, Attributes.ATTACK_SPEED, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
    VAMPIRE("perk.vampire", "perk.vampire_info", 3500, new BlockPos(28, 66, -19), Direction.SOUTH, null, 0, null),
    SECOND_CHANCE("perk.second_chance", "perk.second_chance_info", 4000, new BlockPos(-28, 66, 19), Direction.NORTH, null, 0, null);

    /** So viele Perks kann man gleichzeitig haben. */
    public static final int MAX_ACTIVE = 4;

    public final net.minecraft.network.chat.Component title, line2;
    public final int price;
    public final BlockPos button;
    public final Direction facing;
    private final Holder<Attribute> attribute;
    private final double amount;
    private final AttributeModifier.Operation operation;
    private final Identifier id;

    Perk(String title, String line2, int price, BlockPos button, Direction facing, Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
        this.title = de.ecri.colosseum.Lang.t(title);
        this.line2 = de.ecri.colosseum.Lang.t(line2);
        this.price = price;
        this.button = button;
        this.facing = facing;
        this.attribute = attribute;
        this.amount = amount;
        this.operation = operation;
        this.id = Colosseum.id("perk_" + name().toLowerCase(java.util.Locale.ROOT));
    }

    public void apply(ServerPlayer p) {
        if (attribute == null) return;
        AttributeInstance instance = p.getAttribute(attribute);
        if (instance != null) instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        if (this == TOUGH) p.heal((float) amount);
    }

    public void remove(ServerPlayer p) {
        if (attribute == null) return;
        AttributeInstance instance = p.getAttribute(attribute);
        if (instance != null) instance.removeModifier(id);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    public static Perk at(BlockPos pos) {
        for (Perk perk : values()) if (perk.button.equals(pos)) return perk;
        return null;
    }
}
