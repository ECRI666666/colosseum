package de.ecri.colosseum.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Wo der Spieler stand, bevor er die Arena betrat. */
public record ReturnPoint(ResourceKey<Level> dimension, Vec3 pos, float yRot, float xRot) {
    public static final MapCodec<ReturnPoint> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(ReturnPoint::dimension),
        Vec3.CODEC.fieldOf("pos").forGetter(ReturnPoint::pos),
        Codec.FLOAT.fieldOf("y_rot").forGetter(ReturnPoint::yRot),
        Codec.FLOAT.fieldOf("x_rot").forGetter(ReturnPoint::xRot)
    ).apply(i, ReturnPoint::new));
}
