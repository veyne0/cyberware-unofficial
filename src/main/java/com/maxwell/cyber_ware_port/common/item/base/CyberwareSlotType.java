package com.maxwell.cyber_ware_port.common.item.base;

import com.maxwell.cyber_ware_port.common.block.robosurgeon.RobosurgeonBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import static com.maxwell.cyber_ware_port.common.block.robosurgeon.RobosurgeonBlockEntity.*;

public enum CyberwareSlotType {
    EYES("cyberware_slot.cyberware_unofficial.eyes"),
    BRAIN("cyberware_slot.cyberware_unofficial.brain"),
    HEART("cyberware_slot.cyberware_unofficial.heart"),
    LUNGS("cyberware_slot.cyberware_unofficial.lungs"),
    STOMACH("cyberware_slot.cyberware_unofficial.stomach"),
    SKIN("cyberware_slot.cyberware_unofficial.skin"),
    MUSCLE("cyberware_slot.cyberware_unofficial.muscle"),
    BONES("cyberware_slot.cyberware_unofficial.bones"),
    ARMS("cyberware_slot.cyberware_unofficial.arms"),
    HANDS("cyberware_slot.cyberware_unofficial.hands"),
    LEGS("cyberware_slot.cyberware_unofficial.legs"),
    BOOTS("cyberware_slot.cyberware_unofficial.boots"),
    UNKNOWN("cyberware_slot.cyberware_unofficial.unknown");
    private final String translationKey;

    CyberwareSlotType(String translationKey) {
        this.translationKey = translationKey;
    }

    public static CyberwareSlotType fromId(int id) {
        if (id < 0) return UNKNOWN;
        if (isInRange(id, SLOT_EYES)) return EYES;
        if (isInRange(id, SLOT_BRAIN)) return BRAIN;
        if (isInRange(id, SLOT_HEART)) return HEART;
        if (isInRange(id, SLOT_LUNGS)) return LUNGS;
        if (isInRange(id, SLOT_STOMACH)) return STOMACH;
        if (isInRange(id, SLOT_SKIN)) return SKIN;
        if (isInRange(id, SLOT_MUSCLE)) return MUSCLE;
        if (isInRange(id, SLOT_BONES)) return BONES;
        if (isInRange(id, SLOT_ARMS)) return ARMS;
        if (isInRange(id, SLOT_HANDS)) return HANDS;
        if (isInRange(id, SLOT_LEGS)) return LEGS;
        if (isInRange(id, SLOT_BOOTS)) return BOOTS;
        return UNKNOWN;

    }

    private static boolean isInRange(int id, int startId) {
        return id >= startId && id < startId + RobosurgeonBlockEntity.SLOTS_PER_PART;

    }

    public MutableComponent getDisplayName() {
        return Component.translatable(this.translationKey);
    }
}