package com.maxwell.cyber_ware_port.common.item.cyberware;

import com.maxwell.cyber_ware_port.common.block.robosurgeon.RobosurgeonBlockEntity;
import com.maxwell.cyber_ware_port.common.effect.MobPartEffects;
import com.maxwell.cyber_ware_port.common.item.base.BodyPartType;
import com.maxwell.cyber_ware_port.common.item.base.CyberwareItem;
import com.maxwell.cyber_ware_port.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 生物身体部位物品:单一物品 id(mob_part),具体是哪个生物的哪个部位
 * 由 NBT(MobId / PartType)决定。部位可安装到玩家对应区域。
 */
public class MobPartItem extends CyberwareItem {
    public static final String NBT_MOB_ID = "MobId";
    public static final String NBT_PART_TYPE = "PartType";

    public MobPartItem() {
        super(new Builder(0, 0).quality(0));
    }

    public static ResourceLocation getMobId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        String id = tag != null ? tag.getString(NBT_MOB_ID) : "";
        return id.isEmpty() ? new ResourceLocation("minecraft", "zombie") : new ResourceLocation(id);
    }

    public static BodyPartType getPart(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        String part = tag != null ? tag.getString(NBT_PART_TYPE) : "";
        try {
            return part.isEmpty() ? BodyPartType.NONE : BodyPartType.valueOf(part.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BodyPartType.NONE;
        }
    }

    public static ItemStack create(EntityType<?> mobType, BodyPartType part) {
        return create(EntityType.getKey(mobType), part);
    }

    /** 按任意 mob id 创建部位物品(如闪电苦力怕 lightning_creeper 这种非实体 id) */
    public static ItemStack create(ResourceLocation mobId, BodyPartType part) {
        ItemStack stack = new ItemStack(ModItems.MOB_PART.get());
        stack.getOrCreateTag().putString(NBT_MOB_ID, mobId.toString());
        stack.getOrCreateTag().putString(NBT_PART_TYPE, part.getSerializedName());
        return stack;
    }

    /** 部位 -> 玩家槽位区起始槽 */
    public static int slotForPart(BodyPartType part) {
        switch (part) {
            case HEAD: return RobosurgeonBlockEntity.SLOT_HEAD;
            case TORSO: return RobosurgeonBlockEntity.SLOT_TORSO;
            case ARM_LEFT: case ARM_RIGHT: return RobosurgeonBlockEntity.SLOT_ARMS;
            case HAND_LEFT: case HAND_RIGHT: return RobosurgeonBlockEntity.SLOT_HANDS;
            case LEG_LEFT: case LEG_RIGHT: return RobosurgeonBlockEntity.SLOT_LEGS;
            case FOOT_LEFT: case FOOT_RIGHT: return RobosurgeonBlockEntity.SLOT_BOOTS;
            default: return 0;
        }
    }

    /**
     * 臂/腿区域按槽位决定左右侧:+0=左、+1=右(与人类部位默认摆放一致),
     * 其余区域沿用物品自身类型。渲染与安装规范化都以槽位为准,防止左右相反。
     */
    public static BodyPartType sidedPartForSlot(int slot, BodyPartType itemPart) {
        if (slot == RobosurgeonBlockEntity.SLOT_ARMS) return BodyPartType.ARM_LEFT;
        if (slot == RobosurgeonBlockEntity.SLOT_ARMS + 1) return BodyPartType.ARM_RIGHT;
        if (slot == RobosurgeonBlockEntity.SLOT_LEGS) return BodyPartType.LEG_LEFT;
        if (slot == RobosurgeonBlockEntity.SLOT_LEGS + 1) return BodyPartType.LEG_RIGHT;
        return itemPart;
    }

    @Override
    public BodyPartType getBodyPartType(ItemStack stack) {
        return getPart(stack);
    }

    /** 部位 -> tooltip 插槽文案 key 后缀(脏器部位归入头/躯干区域) */
    private static String slotKeyForPart(BodyPartType part) {
        switch (part) {
            case HEAD: case EYES: case BRAIN: return "head";
            case TORSO: case HEART: case LUNGS: case STOMACH:
            case SKIN: case MUSCLE: case BONES: return "torso";
            case ARM_LEFT: return "arm_left";
            case ARM_RIGHT: return "arm_right";
            case HAND_LEFT: return "hand_left";
            case HAND_RIGHT: return "hand_right";
            case LEG_LEFT: return "leg_left";
            case LEG_RIGHT: return "leg_right";
            case FOOT_LEFT: return "foot_left";
            case FOOT_RIGHT: return "foot_right";
            default: return "torso";
        }
    }

    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel,
                                List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);
        BodyPartType part = getPart(pStack);
        if (part != BodyPartType.NONE) {
            pTooltipComponents.add(Component.translatable(
                    "gui.cyberware_unofficial.mob_part.slot." + slotKeyForPart(part))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public int getSlot(ItemStack stack) {
        return slotForPart(getPart(stack));
    }

    @Override
    public int getEssenceCost(ItemStack stack) {
        // 史莱姆头:最小史莱姆必掉,改用 20 点高排斥值限制可安装数量
        if ("slime".equals(getMobId(stack).getPath()) && getPart(stack) == BodyPartType.HEAD) {
            return 20;
        }
        // 其余按部位效果强度计排斥:有技能 5 点,只有特性 4 点,都没有 2 点
        List<String> effects = MobPartEffects.getEffectKeys(getMobId(stack), getPart(stack));
        boolean hasAbility = false;
        boolean hasTrait = false;
        for (String k : effects) {
            if (k.startsWith("ability.")) hasAbility = true;
            if (k.startsWith("trait.")) hasTrait = true;
        }
        if (hasAbility) return 5;
        if (hasTrait) return 4;
        return 2;
    }

    @Override
    public int getMaxInstallAmount(ItemStack stack) {
        switch (getPart(stack)) {
            case ARM_LEFT: case ARM_RIGHT: case LEG_LEFT: case LEG_RIGHT:
            case HAND_LEFT: case HAND_RIGHT: case FOOT_LEFT: case FOOT_RIGHT:
                return 2;
            default:
                return 1;
        }
    }

    @Override
    public boolean isIncompatible(ItemStack self, ItemStack other) {
        // 所有生物部位共用同一个物品 id(mob_part),父类实现会把任意两个部位判成互斥;
        // 这里改为只有"同生物同部位"才算重复,不同部位之间互不冲突
        if (other.getItem() instanceof MobPartItem) {
            return ItemStack.isSameItemSameTags(self, other) && this.getMaxInstallAmount(self) <= 1;
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
                        .orElse("entity.cyberware_unofficial." + getMobId(stack).getPath()));
        Component partName = Component.translatable("item.cyberware_unofficial.mob_part.part." + part.getSerializedName());
        return Component.translatable("item.cyberware_unofficial.mob_part.format", mobName, partName).withStyle(style);
    }
}
