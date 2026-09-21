package com.maxwell.cyber_ware_port.common.util;

import com.maxwell.cyber_ware_port.common.item.cyberware.MobPartItem;
import com.maxwell.cyber_ware_port.common.item.base.BodyPartType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 生物表面部位注册表:定义每种生物可以被拆解出哪些部位。
 * 只包含表面可见部位(头/躯干/四肢),内脏器官不参与拆解。
 */
public class MobSurfaceParts {
    /** 与人类骨架一致的人形生物(模型部件完整对应) */
    private static final List<EntityType<?>> HUMANOID = List.of(
            EntityType.ZOMBIE,
            EntityType.HUSK,
            EntityType.DROWNED,
            EntityType.SKELETON,
            EntityType.STRAY,
            EntityType.WITHER_SKELETON
    );

    private static final Map<EntityType<?>, List<BodyPartType>> PARTS = new HashMap<>();

    static {
        for (EntityType<?> type : HUMANOID) {
            register(type, (mob, list) -> {
                list.add(BodyPartType.HEAD);
                list.add(BodyPartType.TORSO);
                list.add(BodyPartType.ARM_LEFT);
                list.add(BodyPartType.ARM_RIGHT);
                list.add(BodyPartType.LEG_LEFT);
                list.add(BodyPartType.LEG_RIGHT);
            });
        }
        // 苦力怕:头 + 躯干 + 4 条腿(2 左 2 右,对应玩家左右腿槽)
        register(EntityType.CREEPER, (mob, list) -> {
            list.add(BodyPartType.HEAD);
            list.add(BodyPartType.TORSO);
            list.add(BodyPartType.LEG_LEFT);
            list.add(BodyPartType.LEG_LEFT);
            list.add(BodyPartType.LEG_RIGHT);
            list.add(BodyPartType.LEG_RIGHT);
        });
        // 蜘蛛:头 + 8 条腿(只有头和腿,没有人类意义的躯干)
        register(EntityType.SPIDER, (mob, list) -> {
            list.add(BodyPartType.HEAD);
            list.add(BodyPartType.LEG_LEFT);
            list.add(BodyPartType.LEG_LEFT);
            list.add(BodyPartType.LEG_LEFT);
            list.add(BodyPartType.LEG_LEFT);
            list.add(BodyPartType.LEG_RIGHT);
            list.add(BodyPartType.LEG_RIGHT);
            list.add(BodyPartType.LEG_RIGHT);
            list.add(BodyPartType.LEG_RIGHT);
        });
    }

    private static void register(EntityType<?> type, BiConsumer<EntityType<?>, List<BodyPartType>> filler) {
        List<BodyPartType> list = new ArrayList<>();
        filler.accept(type, list);
        PARTS.put(type, List.copyOf(list));
    }

    public static boolean canDismember(EntityType<?> type) {
        return PARTS.containsKey(type);
    }

    /** 拆解产物:每个表面部位一个物品 */
    public static List<ItemStack> createDrops(EntityType<?> type) {
        List<BodyPartType> parts = PARTS.get(type);
        if (parts == null) return List.of();
        List<ItemStack> drops = new ArrayList<>(parts.size());
        for (BodyPartType part : parts) {
            drops.add(MobPartItem.create(type, part));
        }
        return drops;
    }

    public static boolean isSupported(ResourceLocation mobId) {
        EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(mobId);
        return PARTS.containsKey(type);
    }
}
