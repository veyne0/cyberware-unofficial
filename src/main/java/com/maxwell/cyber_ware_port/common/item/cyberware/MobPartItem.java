package com.maxwell.cyber_ware_port.common.item.cyberware;

import com.maxwell.cyber_ware_port.common.block.robosurgeon.RobosurgeonBlockEntity;
import com.maxwell.cyber_ware_port.common.item.base.BodyPartType;
import com.maxwell.cyber_ware_port.common.item.base.CyberwareItem;
import com.maxwell.cyber_ware_port.init.ModDataComponents;
import com.maxwell.cyber_ware_port.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

/**
 * 生物身体部位物品:单一物品 id(mob_part),具体是哪个生物的哪个部位
 * 由 MOB_ID / PART_TYPE 两个数据组件决定。部位可安装到玩家对应区域。
 */
public class MobPartItem extends CyberwareItem {
    public MobPartItem() {
        super(new Builder(0, 0).quality(0));
    }

    public static ResourceLocation getMobId(ItemStack stack) {
        ResourceLocation id = stack.get(ModDataComponents.MOB_ID.get());
        return id != null ? id : ResourceLocation.withDefaultNamespace("zombie");
    }

    public static BodyPartType getPart(ItemStack stack) {
        BodyPartType part = stack.get(ModDataComponents.PART_TYPE.get());
        return part != null ? part : BodyPartType.NONE;
    }

    public static ItemStack create(EntityType<?> mobType, BodyPartType part) {
        ItemStack stack = new ItemStack(ModItems.MOB_PART.get());
        stack.set(ModDataComponents.MOB_ID.get(), BuiltInRegistries.ENTITY_TYPE.getKey(mobType));
        stack.set(ModDataComponents.PART_TYPE.get(), part);
        return stack;
    }

    /** 部位 -> 玩家槽位区起始槽 */
    public static int slotForPart(BodyPartType part) {
        return switch (part) {
            case HEAD -> RobosurgeonBlockEntity.SLOT_HEAD;
            case TORSO -> RobosurgeonBlockEntity.SLOT_TORSO;
            case ARM_LEFT, ARM_RIGHT -> RobosurgeonBlockEntity.SLOT_ARMS;
            case HAND_LEFT, HAND_RIGHT -> RobosurgeonBlockEntity.SLOT_HANDS;
            case LEG_LEFT, LEG_RIGHT -> RobosurgeonBlockEntity.SLOT_LEGS;
            case FOOT_LEFT, FOOT_RIGHT -> RobosurgeonBlockEntity.SLOT_BOOTS;
            default -> 0;
        };
    }

    @Override
    public BodyPartType getBodyPartType(ItemStack stack) {
        return getPart(stack);
    }

    @Override
    public int getSlot(ItemStack stack) {
        return slotForPart(getPart(stack));
    }

    @Override
    public int getEssenceCost(ItemStack stack) {
        return isPristine(stack) ? 1 : 2;
    }

    @Override
    public int getMaxInstallAmount(ItemStack stack) {
        return switch (getPart(stack)) {
            case ARM_LEFT, ARM_RIGHT, LEG_LEFT, LEG_RIGHT, HAND_LEFT, HAND_RIGHT, FOOT_LEFT, FOOT_RIGHT -> 2;
            default -> 1;
        };
    }

    @Override
    public boolean isIncompatible(ItemStack self, ItemStack other) {
        // 所有生物部位共用同一个物品 id(mob_part),父类实现会把任意两个部位判成互斥;
        // 这里改为只有"同生物同部位"才算重复,不同部位之间互不冲突
        if (other.getItem() instanceof MobPartItem) {
            return ItemStack.isSameItemSameComponents(self, other) && this.getMaxInstallAmount(self) <= 1;
        }
        return super.isIncompatible(self, other);
    }

    @Override
    public Component getName(ItemStack stack) {
        ChatFormatting style = isPristine(stack) ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY;
        BodyPartType part = getPart(stack);
        Component mobName = Component.translatable(
                EntityType.byString(getMobId(stack).toString())
                        .map(EntityType::getDescriptionId)
                        .orElse("entity.minecraft.pig"));
        Component partName = Component.translatable("item.cyber_ware_port.mob_part.part." + part.getSerializedName());
        return Component.translatable("item.cyber_ware_port.mob_part.format", mobName, partName).withStyle(style);
    }
}
