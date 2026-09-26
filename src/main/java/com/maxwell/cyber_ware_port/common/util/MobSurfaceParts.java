package com.maxwell.cyber_ware_port.common.util;

import com.maxwell.cyber_ware_port.common.item.cyberware.MobPartItem;
import com.maxwell.cyber_ware_port.common.item.base.BodyPartType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
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
            EntityType.WITHER_SKELETON,
            EntityType.ZOMBIFIED_PIGLIN,
            EntityType.PIGLIN,
            EntityType.PIGLIN_BRUTE,
            EntityType.WITCH,
            EntityType.ENDERMAN
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
        // 四足动物:只掉头和腿(躯干渲染问题多,不再掉落)
        for (EntityType<?> type : List.of(EntityType.PIG, EntityType.COW, EntityType.SHEEP,
                EntityType.GOAT, EntityType.WOLF)) {
            register(type, (mob, list) -> {
                list.add(BodyPartType.HEAD);
                list.add(BodyPartType.LEG_LEFT);
                list.add(BodyPartType.LEG_LEFT);
                list.add(BodyPartType.LEG_RIGHT);
                list.add(BodyPartType.LEG_RIGHT);
            });
        }
        // 鸡:只掉头和腿
        register(EntityType.CHICKEN, (mob, list) -> {
            list.add(BodyPartType.HEAD);
            list.add(BodyPartType.LEG_LEFT);
            list.add(BodyPartType.LEG_RIGHT);
        });
        // 铁傀儡:头 + 双臂 + 双腿(躯干体积巨大不参与拆解)
        register(EntityType.IRON_GOLEM, (mob, list) -> {
            list.add(BodyPartType.HEAD);
            list.add(BodyPartType.ARM_LEFT);
            list.add(BodyPartType.ARM_RIGHT);
            list.add(BodyPartType.LEG_LEFT);
            list.add(BodyPartType.LEG_RIGHT);
        });
        // 雪傀儡:头 + 躯干(手臂是细棍不参与拆解)
        register(EntityType.SNOW_GOLEM, (mob, list) -> {
            list.add(BodyPartType.HEAD);
            list.add(BodyPartType.TORSO);
        });
        // 监守者:头 + 躯干 + 双臂
        register(EntityType.WARDEN, (mob, list) -> {
            list.add(BodyPartType.HEAD);
            list.add(BodyPartType.TORSO);
            list.add(BodyPartType.ARM_LEFT);
            list.add(BodyPartType.ARM_RIGHT);
        });
        // 唤魔者:只掉头和躯干(手臂是交叠的一体模型,不对应玩家双臂)
        register(EntityType.EVOKER, (mob, list) -> {
            list.add(BodyPartType.HEAD);
            list.add(BodyPartType.TORSO);
        });
        // 守卫者/远古守卫者:只掉头
        register(EntityType.GUARDIAN, (mob, list) -> list.add(BodyPartType.HEAD));
        register(EntityType.ELDER_GUARDIAN, (mob, list) -> list.add(BodyPartType.HEAD));
        // 史莱姆:只掉头
        register(EntityType.SLIME, (mob, list) -> list.add(BodyPartType.HEAD));
        // 炽足兽:只掉腿(2 左 2 右)
        register(EntityType.STRIDER, (mob, list) -> {
            list.add(BodyPartType.LEG_LEFT);
            list.add(BodyPartType.LEG_LEFT);
            list.add(BodyPartType.LEG_RIGHT);
            list.add(BodyPartType.LEG_RIGHT);
        });
        // 凋零:头 + 躯干(死亡后必定随机掉一个,双掉 2.5%,见 EntitiesItemDropEvents)
        register(EntityType.WITHER, (mob, list) -> {
            list.add(BodyPartType.HEAD);
            list.add(BodyPartType.TORSO);
        });
    }

    /** 部位掉落概率:史莱姆 100%(实际由分裂到底的最小体型掉落,见 EntitiesItemDropEvents),其余 10% */
    private static final Map<EntityType<?>, Float> DROP_CHANCE = Map.of(EntityType.SLIME, 1.0f);
    private static final float DEFAULT_DROP_CHANCE = 0.10f;

    /** 指定生物的部位掉落概率(不含抢夺/武士刀加成) */
    public static float dropChance(EntityType<?> type) {
        return DROP_CHANCE.getOrDefault(type, DEFAULT_DROP_CHANCE);
    }

    private static void register(EntityType<?> type, BiConsumer<EntityType<?>, List<BodyPartType>> filler) {
        List<BodyPartType> list = new ArrayList<>();
        filler.accept(type, list);
        PARTS.put(type, List.copyOf(list));
    }

    public static boolean canDropParts(EntityType<?> type) {
        return PARTS.containsKey(type);
    }

    /** 所有支持拆解的生物类型(查询界面用,按注册顺序) */
    public static List<EntityType<?>> supportedTypes() {
        return List.copyOf(PARTS.keySet());
    }

    /** 击杀掉落:随机掉一个该生物的表面部位(按部位列表等概率) */
    public static ItemStack randomDrop(EntityType<?> type, RandomSource random) {
        return randomDrop(type, random, null);
    }

    /** 击杀掉落:随机掉一个表面部位;mobIdOverride 非空时用该 id 生成(如充能苦力怕 -> 闪电苦力怕) */
    public static ItemStack randomDrop(EntityType<?> type, RandomSource random, ResourceLocation mobIdOverride) {
        List<BodyPartType> parts = PARTS.get(type);
        if (parts == null || parts.isEmpty()) return ItemStack.EMPTY;
        BodyPartType part = parts.get(random.nextInt(parts.size()));
        return mobIdOverride != null ? MobPartItem.create(mobIdOverride, part) : MobPartItem.create(type, part);
    }

    public static boolean isSupported(ResourceLocation mobId) {
        EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(mobId);
        return PARTS.containsKey(type);
    }

    /** 指定生物可改造的外部部位类型(去重保序,每种类型一个改造槽位) */
    public static List<BodyPartType> uniqueParts(EntityType<?> type) {
        List<BodyPartType> parts = PARTS.get(type);
        if (parts == null || parts.isEmpty()) return List.of();
        java.util.LinkedHashSet<BodyPartType> set = new java.util.LinkedHashSet<>(parts);
        return List.copyOf(set);
    }
}
