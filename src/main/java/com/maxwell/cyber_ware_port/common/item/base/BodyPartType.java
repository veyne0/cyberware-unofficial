package com.maxwell.cyber_ware_port.common.item.base;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum BodyPartType implements StringRepresentable {
    NONE,
    EYES,
    BRAIN,
    HEART,
    LUNGS,
    STOMACH,
    SKIN,
    MUSCLE,
    BONES,
    ARM_LEFT,
    ARM_RIGHT,
    HAND_LEFT,
    HAND_RIGHT,
    LEG_LEFT,
    LEG_RIGHT,
    FOOT_LEFT,
    FOOT_RIGHT,
    HEAD,
    TORSO;

    public static final Codec<BodyPartType> CODEC = StringRepresentable.fromEnum(BodyPartType::values);

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase();
    }
}