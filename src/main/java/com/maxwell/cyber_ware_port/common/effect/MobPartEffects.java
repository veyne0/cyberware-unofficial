package com.maxwell.cyber_ware_port.common.effect;

import com.maxwell.cyber_ware_port.CyberWare;
import com.maxwell.cyber_ware_port.common.block.robosurgeon.RobosurgeonBlockEntity;
import com.maxwell.cyber_ware_port.common.capability.CyberwareCapabilityProvider;
import com.maxwell.cyber_ware_port.common.capability.CyberwareUserData;
import com.maxwell.cyber_ware_port.common.item.base.BodyPartType;
import com.maxwell.cyber_ware_port.common.item.cyberware.MobPartItem;
import com.maxwell.cyber_ware_port.common.network.A_PacketHandler;
import com.maxwell.cyber_ware_port.common.network.GuardianLaserPacket;
import com.maxwell.cyber_ware_port.common.network.MobAbilityCooldownPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 生物部位效果:特性(被动,永久生效) + 技能(主动释放)。
 * 技能由客户端 V 键发送 {@link com.maxwell.cyber_ware_port.common.network.UseMobPartAbilityPacket} 触发。
 */
@Mod.EventBusSubscriber(modid = CyberWare.MODID)
public final class MobPartEffects {
    /** 拥有夜视特性的生物(所有可拆解生物的头部通用)。1.20.1 无 bogged(1.21 生物),已移除 */
    private static final Set<String> NIGHT_VISION_HEADS = Set.of(
            "zombie", "husk", "drowned", "skeleton", "stray", "wither_skeleton", "creeper", "spider",
            "zombified_piglin", "piglin", "piglin_brute", "witch", "enderman", "lightning_creeper");
    /** 吃腐肉免疫饥饿的躯干(僵尸系) */
    private static final Set<String> ZOMBIE_TORSOS = Set.of("zombie", "husk", "drowned", "zombified_piglin");
    /** 右键骨头回血的躯干(骷髅系)。1.20.1 无 bogged,已移除 */
    private static final Set<String> BONE_TORSOS = Set.of("skeleton", "stray");

    private static final String NBT_BONE_HEAL_CD = "cyberware_bone_heal_cd";
    private static final String NBT_ROTTEN_EAT_AT = "cyberware_rotten_eat_at";
    /** 史莱姆头部分裂计数(0..2,满血时重置) */
    private static final String NBT_SLIME_SPLITS = "cyberware_slime_splits";
    /**
     * 缺腿移动减速的属性修正(1.20.1 用固定 UUID + 名称;操作 MULTIPLY_TOTAL 等价于 1.21 的 ADD_MULTIPLIED_TOTAL)
     */
    private static final UUID MISSING_LEGS_UUID = UUID.fromString("6c1f2b4a-8d3e-4f59-b7c2-0a9e5d831f47");
    private static final String MISSING_LEGS_NAME = "cyberware_unofficial_missing_legs";

    // ==================== 技能注册 ====================

    /** 主动技能定义:id 用于翻译 key 与执行分发,cooldownTicks 为冷却时长(0=无冷却) */
    public record MobAbility(String id, int cooldownTicks) {
        public String nameKey() {
            return "ability.cyberware_unofficial." + id;
        }
    }

    /** 玩家 persistentData 里的技能冷却前缀,值为冷却结束时的 gameTime */
    private static final String NBT_ABILITY_CD_PREFIX = "cyberware_ability_cd_";

    /** key = "生物id:部位名" -> 技能列表 */
    private static final Map<String, List<MobAbility>> ABILITIES = new HashMap<>();

    static {
        ABILITIES.put("creeper:torso", List.of(new MobAbility("self_destruct", 0)));
        ABILITIES.put("witch:torso", List.of(new MobAbility("witch_potion", 200)));
        ABILITIES.put("piglin:torso", List.of(new MobAbility("piglin_barter", 600)));
        ABILITIES.put("enderman:torso", List.of(new MobAbility("enderman_teleport", 100)));
        // 牛/山羊/羊头部:吃草(冷却 5 秒)
        ABILITIES.put("cow:head", List.of(new MobAbility("graze", 100)));
        ABILITIES.put("goat:head", List.of(new MobAbility("graze", 100)));
        ABILITIES.put("sheep:head", List.of(new MobAbility("graze", 100)));
        // 鸡头部:吃小麦种子(冷却 5 秒)
        ABILITIES.put("chicken:head", List.of(new MobAbility("peck_seeds", 100)));
        // 狼头部:狂暴(冷却 2 分钟)
        ABILITIES.put("wolf:head", List.of(new MobAbility("wolf_rage", 2400)));
        // 铁傀儡头部:右键铁锭修补(冷却 5 秒,也可 V 键消耗铁锭触发)
        ABILITIES.put("iron_golem:head", List.of(new MobAbility("iron_repair", 100)));
        // 唤魔者躯干:濒死自动触发不死图腾效果(冷却 15 分钟)
        ABILITIES.put("evoker:torso", List.of(new MobAbility("die_totem", 18000)));
        // 唤魔者头部:召唤唤魔尖牙(冷却 15 秒)
        ABILITIES.put("evoker:head", List.of(new MobAbility("evoker_fangs", 300)));
        // 雪傀儡躯干:发射雪球(1 点伤害,冷却 1 秒)
        ABILITIES.put("snow_golem:torso", List.of(new MobAbility("snowball_shoot", 20)));
        // 闪电苦力怕躯干:自爆(苦力怕两倍伤害,冷却 0)
        ABILITIES.put("lightning_creeper:torso", List.of(new MobAbility("self_destruct", 0)));
        // 监守者头部:音波攻击(10 点伤害,冷却 10 秒)
        ABILITIES.put("warden:head", List.of(new MobAbility("sonic_boom", 200)));
        // 守卫者头部:激光(3 点伤害,冷却 10 秒)
        ABILITIES.put("guardian:head", List.of(new MobAbility("guardian_laser", 200)));
        // 远古守卫者头部:激光(5 点伤害,冷却 6 秒)
        ABILITIES.put("elder_guardian:head", List.of(new MobAbility("elder_guardian_laser", 120)));
        // 凋零头部:发射凋零之首(冷却 3 秒)
        ABILITIES.put("wither:head", List.of(new MobAbility("wither_skull", 60)));
    }

    // ==================== 部位查询辅助 ====================

    /** 取玩家已安装 cyberware 容器(可能不存在能力) */
    private static ItemStackHandler getInstalledHandler(Player player) {
        return player.getCapability(CyberwareCapabilityProvider.CYBERWARE_CAPABILITY)
                .resolve().map(CyberwareUserData::getInstalledCyberware).orElse(null);
    }

    /** 玩家对应区域是否安装了指定生物的指定部位 */
    public static boolean hasMobPart(Player player, String mobId, BodyPartType part) {
        ItemStackHandler handler = getInstalledHandler(player);
        if (handler == null) return false;
        int start = MobPartItem.slotForPart(part);
        for (int i = start; i < start + RobosurgeonBlockEntity.SLOTS_PER_PART && i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!(stack.getItem() instanceof MobPartItem)) continue;
            if (MobPartItem.getPart(stack) == part && MobPartItem.getMobId(stack).getPath().equals(mobId)) {
                return true;
            }
        }
        return false;
    }

    /** 统计安装的指定生物部位数量(用于双臂概率叠加) */
    public static int countMobParts(Player player, String mobId, BodyPartType[] parts) {
        int count = 0;
        for (BodyPartType part : parts) {
            if (hasMobPart(player, mobId, part)) count++;
        }
        return count;
    }

    /**
     * 获取指定部位物品的效果翻译 key 列表(用于 tooltip)。
     * 特性为 trait.* ,技能为 ability.*.desc 。
     */
    public static List<String> getEffectKeys(ResourceLocation mobId, BodyPartType part) {
        List<String> keys = new ArrayList<>();
        String mob = mobId.getPath();
        if (part == BodyPartType.HEAD) {
            if (NIGHT_VISION_HEADS.contains(mob)) keys.add("trait.cyberware_unofficial.night_vision");
            if (mob.equals("chicken")) keys.add("trait.cyberware_unofficial.slow_falling");
            // 水下呼吸:溺尸/守卫者/远古守卫者头部
            if (mob.equals("drowned") || mob.equals("guardian") || mob.equals("elder_guardian")) {
                keys.add("trait.cyberware_unofficial.water_breathing");
            }
            // 远古守卫者头部:免疫挖掘疲劳
            if (mob.equals("elder_guardian")) {
                keys.add("trait.cyberware_unofficial.mining_fatigue_immunity");
            }
            // 史莱姆头部:分裂
            if (mob.equals("slime")) {
                keys.add("trait.cyberware_unofficial.slime_split");
            }
        }
        if (part == BodyPartType.LEG_LEFT || part == BodyPartType.LEG_RIGHT) {
            // 炽足兽腿部:岩浆表面行走
            if (mob.equals("strider")) {
                keys.add("trait.cyberware_unofficial.lava_walking");
            }
        }
        if (part == BodyPartType.TORSO && mob.equals("wither")) {
            // 凋零躯干:免疫凋零 + 血量过半时免疫远程攻击
            keys.add("trait.cyberware_unofficial.wither_immunity");
            keys.add("trait.cyberware_unofficial.ranged_immunity");
        }
        if (part == BodyPartType.TORSO) {
            if (ZOMBIE_TORSOS.contains(mob)) keys.add("trait.cyberware_unofficial.rotten_flesh_immunity");
            if (BONE_TORSOS.contains(mob)) keys.add("trait.cyberware_unofficial.bone_heal");
            if (mob.equals("wither_skeleton")) keys.add("trait.cyberware_unofficial.wither_immunity");
        }
        if ((part == BodyPartType.ARM_LEFT || part == BodyPartType.ARM_RIGHT) && mob.equals("wither_skeleton")) {
            keys.add("trait.cyberware_unofficial.wither_attack");
        }
        if ((part == BodyPartType.ARM_LEFT || part == BodyPartType.ARM_RIGHT) && mob.equals("iron_golem")) {
            keys.add("trait.cyberware_unofficial.iron_throw");
        }
        if ((part == BodyPartType.LEG_LEFT || part == BodyPartType.LEG_RIGHT) && mob.equals("iron_golem")) {
            keys.add("trait.cyberware_unofficial.golem_fall_immunity");
        }
        if (part == BodyPartType.TORSO && mob.equals("snow_golem")) {
            keys.add("trait.cyberware_unofficial.golem_fall_immunity");
            keys.add("trait.cyberware_unofficial.snow_water_weakness");
        }
        if (part == BodyPartType.HEAD && mob.equals("warden")) {
            keys.add("trait.cyberware_unofficial.darkness_immunity");
        }
        if (part == BodyPartType.TORSO && mob.equals("warden")) {
            keys.add("trait.cyberware_unofficial.knockback_resistance");
            keys.add("trait.cyberware_unofficial.fire_immunity");
        }
        if ((part == BodyPartType.ARM_LEFT || part == BodyPartType.ARM_RIGHT) && mob.equals("warden")) {
            keys.add("trait.cyberware_unofficial.warden_strength");
        }
        List<MobAbility> abilities = ABILITIES.get(mob + ":" + part.getSerializedName());
        if (abilities != null) {
            for (MobAbility ability : abilities) {
                keys.add(ability.nameKey() + ".desc");
            }
        }
        return keys;
    }

    /** 获取玩家当前可用的技能列表(客户端/服务端各自扫描已安装部位,顺序一致) */
    public static List<MobAbility> getAbilities(Player player) {
        ItemStackHandler handler = getInstalledHandler(player);
        List<MobAbility> result = new ArrayList<>();
        if (handler == null) return result;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!(stack.getItem() instanceof MobPartItem)) continue;
            String key = MobPartItem.getMobId(stack).getPath() + ":" + MobPartItem.getPart(stack).getSerializedName();
            List<MobAbility> list = ABILITIES.get(key);
            if (list != null) {
                for (MobAbility ability : list) {
                    if (seen.add(ability.id())) result.add(ability);
                }
            }
        }
        return result;
    }

    /** 服务端执行技能;失败(条件不满足/冷却中)不进入冷却 */
    public static void executeAbility(ServerPlayer player, int index) {
        List<MobAbility> abilities = getAbilities(player);
        if (index < 0 || index >= abilities.size()) return;
        MobAbility ability = abilities.get(index);
        if (ability.cooldownTicks() > 0 && player.level().getGameTime() < abilityCooldownEnd(player, ability.id())) {
            return; // 冷却中
        }
        boolean success = switch (ability.id()) {
            case "self_destruct" -> {
                selfDestruct(player);
                yield true;
            }
            case "witch_potion" -> throwWitchPotion(player);
            case "piglin_barter" -> piglinBarter(player);
            case "enderman_teleport" -> endermanTeleport(player);
            case "graze" -> graze(player);
            case "peck_seeds" -> peckSeeds(player);
            case "wolf_rage" -> wolfRage(player);
            case "iron_repair" -> ironRepair(player);
            case "die_totem" -> {
                totemBurst(player);
                yield true;
            }
            case "evoker_fangs" -> evokerFangs(player);
            case "snowball_shoot" -> snowballShot(player);
            case "sonic_boom" -> sonicBoom(player);
            case "guardian_laser" -> guardianLaser(player, false);
            case "elder_guardian_laser" -> guardianLaser(player, true);
            case "wither_skull" -> witherSkull(player);
            default -> false;
        };
        if (success && ability.cooldownTicks() > 0) {
            long end = player.level().getGameTime() + ability.cooldownTicks();
            player.getPersistentData().putLong(NBT_ABILITY_CD_PREFIX + ability.id(), end);
            A_PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new MobAbilityCooldownPacket(ability.id(), end));
        }
    }

    /** 技能冷却结束时刻(gameTime),未在冷却返回 0 */
    public static long abilityCooldownEnd(Player player, String abilityId) {
        return player.getPersistentData().getLong(NBT_ABILITY_CD_PREFIX + abilityId);
    }

    // ==================== 技能:女巫躯干投掷药水 ====================

    /** 随机扔出一瓶女巫同款有害药水(中毒/缓慢/瞬间伤害/虚弱),冷却 10 秒 */
    private static boolean throwWitchPotion(ServerPlayer player) {
        Potion potion = switch (player.getRandom().nextInt(4)) {
            case 0 -> Potions.POISON;
            case 1 -> Potions.SLOWNESS;
            case 2 -> Potions.HARMING;
            default -> Potions.WEAKNESS;
        };
        ThrownPotion thrown = new ThrownPotion(player.level(), player);
        thrown.setItem(PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), potion));
        thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0F, 0.5F, 1.0F);
        player.level().addFreshEntity(thrown);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    // ==================== 技能:猪灵躯干以物易物 ====================

    /** 手持金锭消耗 1 个,换取原版猪灵以物易物战利品表随机结果,冷却 30 秒 */
    private static boolean piglinBarter(ServerPlayer player) {
        InteractionHand hand = null;
        if (player.getMainHandItem().is(Items.GOLD_INGOT)) {
            hand = InteractionHand.MAIN_HAND;
        } else if (player.getOffhandItem().is(Items.GOLD_INGOT)) {
            hand = InteractionHand.OFF_HAND;
        }
        if (hand == null) {
            player.displayClientMessage(Component.translatable("ability.cyberware_unofficial.piglin_barter.need_gold"), true);
            return false;
        }
        player.getItemInHand(hand).shrink(1);
        ServerLevel level = player.serverLevel();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.PIGLIN_BARTER);
        for (ItemStack loot : level.getServer().getLootData()
                .getLootTable(BuiltInLootTables.PIGLIN_BARTERING).getRandomItems(params)) {
            if (!player.getInventory().add(loot)) {
                player.drop(loot, false);
            }
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PIGLIN_ADMIRING_ITEM, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    // ==================== 技能:末影人躯干传送 ====================

    /** 传送到准星指向的位置(最远 32 格),冷却 5 秒 */
    private static boolean endermanTeleport(ServerPlayer player) {
        HitResult hit = player.pick(32.0D, 1.0F, false);
        Vec3 target;
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            target = Vec3.atBottomCenterOf(blockHit.getBlockPos().relative(blockHit.getDirection()));
        } else {
            target = hit.getLocation();
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        // randomTeleport 会做落点安全检查(同紫颂果)
        if (!player.randomTeleport(target.x, target.y, target.z, true)) {
            return false;
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    // ==================== 技能:牛/山羊/羊头部吃草 ====================

    /** 啃食脚下的草方块(变泥土),回 2 格血 + 1 格饱食度,冷却 5 秒 */
    private static boolean graze(ServerPlayer player) {
        BlockPos below = player.blockPosition().below();
        if (!player.level().getBlockState(below).is(Blocks.GRASS_BLOCK)) {
            player.displayClientMessage(Component.translatable("ability.cyberware_unofficial.graze.need_grass"), true);
            return false;
        }
        player.level().setBlock(below, Blocks.DIRT.defaultBlockState(), 3);
        player.heal(4.0F);
        player.getFoodData().eat(1, 0.1F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    // ==================== 技能:鸡头部啄食 ====================

    /** 消耗背包 1 个小麦种子,回 2 格血 + 1 格饱食度,冷却 5 秒 */
    private static boolean peckSeeds(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.WHEAT_SEEDS)) {
                stack.shrink(1);
                player.heal(4.0F);
                player.getFoodData().eat(1, 0.1F);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1.0F, 1.0F);
                return true;
            }
        }
        player.displayClientMessage(Component.translatable("ability.cyberware_unofficial.peck_seeds.need_seeds"), true);
        return false;
    }

    // ==================== 技能:狼躯干狂暴 ====================

    /** 获得 30 秒力量 Ⅰ + 速度 Ⅱ,冷却 2 分钟 */
    private static boolean wolfRage(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 0));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 1.0F, 0.8F);
        return true;
    }

    // ==================== 技能:铁傀儡头部铁锭修补 ====================

    /** 消耗手持 1 个铁锭回复 1 格血(2 HP),冷却 5 秒;无铁锭/满血不触发 */
    private static boolean ironRepair(ServerPlayer player) {
        InteractionHand hand = null;
        if (player.getMainHandItem().is(Items.IRON_INGOT)) {
            hand = InteractionHand.MAIN_HAND;
        } else if (player.getOffhandItem().is(Items.IRON_INGOT)) {
            hand = InteractionHand.OFF_HAND;
        }
        if (hand == null) {
            player.displayClientMessage(Component.translatable("ability.cyberware_unofficial.iron_repair.need_iron"), true);
            return false;
        }
        if (player.getHealth() >= player.getMaxHealth()) {
            player.displayClientMessage(Component.translatable("ability.cyberware_unofficial.iron_repair.need_health"), true);
            return false;
        }
        if (!player.getAbilities().instabuild) {
            player.getItemInHand(hand).shrink(1);
        }
        player.heal(2.0F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.swing(hand, true);
        return true;
    }

    // ==================== 技能:唤魔者躯干不死图腾 ====================

    /** 濒死时恢复到 1 颗心 + 再生/伤害吸收/抗性/防火(不死图腾同款),冷却 15 分钟 */
    private static void totemBurst(ServerPlayer player) {
        player.setHealth(1.0F);
        player.clearFire();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 800, 0));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        // 原版图腾实体事件 35:客户端自动播放完整图腾动画(第一人称模型 + 粒子 + 音效)
        player.level().broadcastEntityEvent(player, (byte) 35);
    }

    /** 致死伤害时消耗唤魔者躯干技能自动触发图腾效果(独立 15 分钟冷却) */
    private static void tryAutoTotem(ServerPlayer player) {
        if (player.level().getGameTime() < abilityCooldownEnd(player, "die_totem")) return;
        if (!hasMobPart(player, "evoker", BodyPartType.TORSO)) return;
        totemBurst(player);
        long end = player.level().getGameTime() + 18000;
        player.getPersistentData().putLong(NBT_ABILITY_CD_PREFIX + "die_totem", end);
        A_PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new MobAbilityCooldownPacket("die_totem", end));
    }

    // ==================== 技能:唤魔者头部唤魔尖牙 ====================

    /** 沿视线方向依次召唤 6 颗唤魔尖牙(啃咬敌人),冷却 15 秒 */
    private static boolean evokerFangs(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.horizontalDistanceSqr() < 1.0E-4) flat = player.getForward();
        flat = flat.normalize();
        Vec3 start = player.position().add(flat.x * 2.0, 0.1, flat.z * 2.0);
        float yRot = player.getYRot();
        for (int i = 0; i < 6; i++) {
            double fx = start.x + flat.x * i * 1.1;
            double fz = start.z + flat.z * i * 1.1;
            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(fx, player.getY(), fz).getX(),
                    BlockPos.containing(fx, player.getY(), fz).getZ());
            EvokerFangs fangs = new EvokerFangs(level, fx, groundY, fz, yRot, 5 + i * 3, player);
            level.addFreshEntity(fangs);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    // ==================== 技能:苦力怕躯干自爆 ====================

    private static void selfDestruct(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        // 闪电苦力怕躯干:两倍伤害
        boolean lightning = hasMobPart(player, "lightning_creeper", BodyPartType.TORSO);
        float damage = player.getMaxHealth() * (lightning ? 2.0F : 1.0F);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(lightning ? 7.0 : 5.0),
                t -> t != player && t.isAlive())) {
            target.hurt(player.damageSources().explosion(null), damage);
            Vec3 away = target.position().subtract(center);
            double horiz = Math.max(away.horizontalDistance(), 0.01);
            target.push(away.x / horiz * 1.5, 0.6, away.z / horiz * 1.5);
            target.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 0.5, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.5, center.z, 8, 1.0, 0.5, 1.0, 0);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0F, 1.0F);
        // 自己也会死
        player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
    }

    // ==================== 技能:雪傀儡躯干雪球 ====================

    private static final String NBT_SNOWBALL_FLAG = "cyberware_snowball";

    /** 发射一颗雪球,命中造成 1 点伤害(普通雪球为 0),冷却 1 秒 */
    private static boolean snowballShot(ServerPlayer player) {
        Snowball ball = new Snowball(player.level(), player);
        ball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
        ball.getPersistentData().putBoolean(NBT_SNOWBALL_FLAG, true);
        player.level().addFreshEntity(ball);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    /** 雪傀儡雪球命中:额外造成 1 点伤害 */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof Snowball ball)) return;
        if (!ball.getPersistentData().getBoolean(NBT_SNOWBALL_FLAG)) return;
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof LivingEntity target)) return;
        if (!(ball.getOwner() instanceof Player shooter)) return;
        target.hurt(shooter.damageSources().thrown(ball, shooter), 1.0F);
    }

    // ==================== 技能:监守者头部音波 ====================

    /** 沿视线发射音波,命中造成 10 点伤害(可穿墙,最远 20 格),冷却 10 秒 */
    private static boolean sonicBoom(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // 蓄力阶段(原版行为):施法瞬间先播放监守者蓄力音效
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 3.0F, 1.0F);
        // 原版蓄力 34 tick 后才爆发:延迟结算,还原蓝色音波圈沿视线飞出的特效
        level.getServer().tell(new TickTask(level.getServer().getTickCount() + 34, () -> {
            if (player.isRemoved() || !player.isAlive()) return;
            ServerLevel sl = player.serverLevel();
            // 原版音波起点:脚下 1.6 格(胸口高度),沿视线每 1 格一个 SONIC_BOOM 圈,
            // 延伸到射程外 7 格,还原原版蓝圈一路飞过的效果
            Vec3 origin = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            double range = 20.0;
            int rings = (int) range + 7;
            for (int i = 1; i <= rings; i++) {
                Vec3 pos = origin.add(look.scale(i));
                sl.sendParticles(ParticleTypes.SONIC_BOOM, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            }
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 1.0F);
            // 射线命中判定(眼睛沿视线 20 格)
            Vec3 end = origin.add(look.scale(range));
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                    sl, player, origin, end,
                    player.getBoundingBox().expandTowards(look.scale(range)).inflate(2.0),
                    t -> t instanceof LivingEntity living && living.isAlive() && t != player && !t.isSpectator());
            if (hit != null && hit.getEntity() instanceof LivingEntity target) {
                target.hurt(player.damageSources().sonicBoom(player), 10.0F);
                // 原版击退:水平 2.5 / 垂直 0.5,按目标击退抗性衰减
                double kb = 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                target.push(look.x * 2.5 * kb, look.y * 0.5 * kb, look.z * 2.5 * kb);
            }
        }));
        player.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    // ==================== 技能:守卫者/远古守卫者头部激光 ====================

    private static final String NBT_LASER_TARGET = "cyberware_laser_target";
    private static final String NBT_LASER_END = "cyberware_laser_end";

    /**
     * 原版同款激光:蓄力(守卫者 80 tick/远古守卫者 60 tick)期间客户端渲染光束,
     * 蓄力结束后沿视线结算伤害(无视护甲,同原版 indirectMagic)。
     * 目标 id 与结束时刻通过 {@link com.maxwell.cyber_ware_port.common.network.GuardianLaserPacket}
     * 广播给客户端画光束。
     */
    private static boolean guardianLaser(ServerPlayer player, boolean elder) {
        ServerLevel level = player.serverLevel();
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double range = 20.0;
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                level, player, origin, origin.add(look.scale(range)),
                player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0),
                t -> t instanceof LivingEntity living && living.isAlive() && t != player
                        && !t.isSpectator() && player.hasLineOfSight(living));
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
            player.displayClientMessage(Component.translatable("ability.cyberware_unofficial.guardian_laser.need_target"), true);
            return false;
        }
        long start = level.getGameTime();
        int duration = elder ? 60 : 80;
        long end = start + duration;
        float damage = elder ? 5.0F : 3.0F;
        player.getPersistentData().putInt(NBT_LASER_TARGET, target.getId());
        player.getPersistentData().putLong(NBT_LASER_END, end);
        A_PacketHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new GuardianLaserPacket(player.getId(), target.getId(), start, end));
        // 原版蓄力开始瞬间的守卫者攻击音效
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 1.0F, 1.0F);
        int targetId = target.getId();
        level.getServer().tell(new TickTask(level.getServer().getTickCount() + duration, () -> {
            player.getPersistentData().remove(NBT_LASER_TARGET);
            player.getPersistentData().remove(NBT_LASER_END);
            if (player.isRemoved() || !player.isAlive()) return;
            if (player.level().getEntity(targetId) instanceof LivingEntity living && living.isAlive()
                    && player.hasLineOfSight(living)) {
                // 原版激光伤害源:indirectMagic,无视护甲
                living.hurt(player.damageSources().indirectMagic(player, player), damage);
            }
        }));
        player.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    // ==================== 技能:凋零头部凋零之首 ====================

    /** 发射一颗原版凋零之首(黑颅:5 点伤害+凋零Ⅱ,小爆炸),瞄准准星,冷却 3 秒 */
    private static boolean witherSkull(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // 严格按准星(视线向量)发射:1.20.1 构造函数把颅骨生成在 owner 处(只继承朝向),
        // 这里从眼睛位置沿视线前方 0.5 格生成,飞行方向 = 视线方向,弹道与准星完全重合
        Vec3 look = player.getViewVector(1.0F);
        WitherSkull skull = new WitherSkull(level, player, look.x, look.y, look.z);
        Vec3 spawn = player.getEyePosition().add(look.scale(0.5));
        skull.setPos(spawn.x, spawn.y, spawn.z);
        level.addFreshEntity(skull);
        // 原版凋零射击音效
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    // ==================== 特性(被动) ====================

    /** 头部特性:夜视/水下呼吸(每 2 秒刷新);鸡头部:缓降;雪傀儡躯干:碰水受伤;炽足兽腿部:岩浆表面行走 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player ticked = event.player;
        // 炽足兽腿部:客户端也要每 tick 把玩家抬到岩浆表面,
        // 否则客户端物理始终按"岩浆中"计算(拖拽极大),表现为移动速度不正常
        if (ticked.level().isClientSide) {
            if ((hasMobPart(ticked, "strider", BodyPartType.LEG_LEFT) || hasMobPart(ticked, "strider", BodyPartType.LEG_RIGHT))
                    && ticked.isInLava()) {
                snapOntoLavaSurface(ticked);
            }
            return;
        }
        if (!(ticked instanceof ServerPlayer player)) return;
        // 缺肢移动惩罚:被手术切掉的腿(槽空且肉体已切除)越多越慢,单腿 -30%,双腿 -70%
        CyberwareUserData cwData = player.getCapability(CyberwareCapabilityProvider.CYBERWARE_CAPABILITY).orElse(null);
        if (cwData != null) {
            ItemStackHandler cwBody = cwData.getInstalledCyberware();
            int missingLegs = 0;
            for (int k = 0; k < 2; k++) {
                int slot = RobosurgeonBlockEntity.SLOT_LEGS + k;
                if (cwBody.getStackInSlot(slot).isEmpty() && cwData.isFleshRemoved(slot)) missingLegs++;
            }
            AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.removeModifier(MISSING_LEGS_UUID);
                if (missingLegs > 0) {
                    speedAttr.addTransientModifier(new AttributeModifier(MISSING_LEGS_UUID, MISSING_LEGS_NAME,
                            missingLegs >= 2 ? -0.7 : -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL));
                }
            }
        }
        // 炽足兽腿部:踩进岩浆时浮到岩浆表面行走(每 tick 检查,响应要快)
        if (hasMobPart(player, "strider", BodyPartType.LEG_LEFT) || hasMobPart(player, "strider", BodyPartType.LEG_RIGHT)) {
            if (player.isInLava()) {
                snapOntoLavaSurface(player);
            } else if (player.isOnFire()) {
                // 离开岩浆立即灭火,配合火焰免疫事件,出岩浆后不再燃烧
                player.clearFire();
            }
        }
        if (player.tickCount % 40 != 0) return;
        if (hasMobPart(player, "chicken", BodyPartType.HEAD)) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
        }
        // 溺尸/守卫者/远古守卫者头部:水下呼吸
        if (hasMobPart(player, "drowned", BodyPartType.HEAD) || hasMobPart(player, "guardian", BodyPartType.HEAD)
                || hasMobPart(player, "elder_guardian", BodyPartType.HEAD)) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 400, 0, false, false));
        }
        // 史莱姆头部:满血时重置分裂计数(每次满血恢复 2 次分裂机会)
        if (player.getHealth() >= player.getMaxHealth()) {
            player.getPersistentData().putInt(NBT_SLIME_SPLITS, 0);
        }
        // 雪傀儡躯干:碰到水每 2 秒受 1 颗心伤害(同雪傀儡遇水融化)
        if (player.isInWater() && hasMobPart(player, "snow_golem", BodyPartType.TORSO)) {
            player.hurt(player.damageSources().drown(), 2.0F);
        }
        for (String mobId : NIGHT_VISION_HEADS) {
            if (hasMobPart(player, mobId, BodyPartType.HEAD)) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false));
                return;
            }
        }
    }

    /** 炽足兽腿部:把玩家抬到岩浆表面(脚下最高岩浆块的上一格),清零纵向速度,可正常行走跳跃 */
    private static void snapOntoLavaSurface(Player player) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        int top = -1;
        while (player.level().getFluidState(pos).is(FluidTags.LAVA)) {
            top = pos.getY();
            pos.move(0, 1, 0);
        }
        if (top >= 0) {
            player.setPos(player.getX(), top + 1.0, player.getZ());
            player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
            player.setOnGround(true);
            player.resetFallDistance();
            player.hurtMarked = true;
        }
    }

    /** 炽足兽腿部特性:免疫火焰/岩浆伤害(着火时离开岩浆还会被立即灭火,不会余燃) */
    @SubscribeEvent
    public static void onStriderFireDamage(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getSource().is(DamageTypeTags.IS_FIRE)
                && !event.getSource().is(DamageTypes.LAVA)) {
            return;
        }
        if (hasMobPart(player, "strider", BodyPartType.LEG_LEFT) || hasMobPart(player, "strider", BodyPartType.LEG_RIGHT)) {
            event.setCanceled(true);
        }
    }

    /** 僵尸系躯干特性:吃腐肉免疫饥饿效果(记录进食时刻,饥饿效果施加时校验) */
    @SubscribeEvent
    public static void onEatFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getItem().is(Items.ROTTEN_FLESH)) {
            for (String mobId : ZOMBIE_TORSOS) {
                if (hasMobPart(player, mobId, BodyPartType.TORSO)) {
                    player.getPersistentData().putLong(NBT_ROTTEN_EAT_AT, player.level().getGameTime());
                    return;
                }
            }
        }
    }

    /** 凋灵骷髅/凋零躯干特性:免疫凋零效果;远古守卫者头部:免疫挖掘疲劳;监守者头部:免疫黑暗;僵尸系躯干:吃腐肉后短时间内免疫饥饿 */
    @SubscribeEvent
    public static void onPotionApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getEffectInstance() == null) return;
        if (event.getEffectInstance().getEffect() == MobEffects.WITHER
                && (hasMobPart(player, "wither_skeleton", BodyPartType.TORSO) || hasMobPart(player, "wither", BodyPartType.TORSO))) {
            event.setResult(MobEffectEvent.Applicable.Result.DENY);
            return;
        }
        if (event.getEffectInstance().getEffect() == MobEffects.DIG_SLOWDOWN && hasMobPart(player, "elder_guardian", BodyPartType.HEAD)) {
            event.setResult(MobEffectEvent.Applicable.Result.DENY);
            return;
        }
        if (event.getEffectInstance().getEffect() == MobEffects.DARKNESS && hasMobPart(player, "warden", BodyPartType.HEAD)) {
            event.setResult(MobEffectEvent.Applicable.Result.DENY);
            return;
        }
        if (event.getEffectInstance().getEffect() == MobEffects.HUNGER) {
            for (String mobId : ZOMBIE_TORSOS) {
                if (hasMobPart(player, mobId, BodyPartType.TORSO)) {
                    long last = player.getPersistentData().getLong(NBT_ROTTEN_EAT_AT);
                    if (player.level().getGameTime() - last <= 10) {
                        event.setResult(MobEffectEvent.Applicable.Result.DENY);
                    }
                    return;
                }
            }
        }
    }

    /** 骷髅/流髅躯干特性:手持骨头右键回 2.5 格血(5 HP,2 秒冷却) */
    @SubscribeEvent
    public static void onRightClickBone(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.BONE)) return;
        boolean hasTorso = false;
        for (String mobId : BONE_TORSOS) {
            if (hasMobPart(player, mobId, BodyPartType.TORSO)) {
                hasTorso = true;
                break;
            }
        }
        if (!hasTorso) return;
        long now = player.level().getGameTime();
        if (now < player.getPersistentData().getLong(NBT_BONE_HEAL_CD)) return;
        if (player.getHealth() >= player.getMaxHealth()) return;
        player.getPersistentData().putLong(NBT_BONE_HEAL_CD, now + 40);
        player.heal(5.0F);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.4F);
        player.swing(InteractionHand.MAIN_HAND, true);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** 凋灵骷髅手臂特性:攻击时施加凋零Ⅰ(单臂 50%,双臂 100%),持续 5 秒;铁傀儡手臂:不打村民 */
    @SubscribeEvent
    public static void onPlayerAttack(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        LivingEntity target = event.getEntity();
        if (target == player) return;
        int arms = countMobParts(player, "wither_skeleton",
                new BodyPartType[]{BodyPartType.ARM_LEFT, BodyPartType.ARM_RIGHT});
        if (arms > 0) {
            float chance = arms >= 2 ? 1.0F : 0.5F;
            if (player.getRandom().nextFloat() < chance) {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
            }
        }
        int golemArms = countMobParts(player, "iron_golem",
                new BodyPartType[]{BodyPartType.ARM_LEFT, BodyPartType.ARM_RIGHT});
        if (golemArms > 0) {
            // 铁傀儡手臂不打村民(伤害取消)
            if (target instanceof AbstractVillager) {
                event.setCanceled(true);
            }
        }
    }

    /** 铁傀儡手臂特性:伤害结算后将目标抛飞(单臂 2 格,双臂 4 格) */
    @SubscribeEvent
    public static void onDamageLanded(LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        LivingEntity target = event.getEntity();
        if (target == player) return;
        int golemArms = countMobParts(player, "iron_golem",
                new BodyPartType[]{BodyPartType.ARM_LEFT, BodyPartType.ARM_RIGHT});
        if (golemArms <= 0) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        // 原版 hurt() 在伤害结算后还会执行 knockback() 覆盖速度
        // (铁傀儡自带 100% 击退抗性才躲过覆盖),延迟到下一 tick 再抛飞;
        // 直接设定竖直速度(不叠加原版击退自带的 0.4 上抛,否则高度失控):
        // 按 MC 重力 0.08/tick + 0.98 空气阻力仿真,0.65≈2 格(单臂),0.93≈4 格(双臂)
        float up = golemArms >= 2 ? 0.93F : 0.65F;
        serverLevel.getServer().tell(new TickTask(serverLevel.getServer().getTickCount() + 1, () -> {
            if (target.isRemoved() || target.isDeadOrDying()) return;
            var motion = target.getDeltaMovement();
            target.setDeltaMovement(motion.x, up, motion.z);
            target.hurtMarked = true;
        }));
        player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    /** 铁傀儡腿部/雪傀儡躯干特性:免疫摔落伤害;监守者躯干特性:免疫火焰伤害 */
    @SubscribeEvent
    public static void onFallDamage(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getSource().is(DamageTypeTags.IS_FALL)
                && (hasMobPart(player, "iron_golem", BodyPartType.LEG_LEFT)
                || hasMobPart(player, "iron_golem", BodyPartType.LEG_RIGHT)
                || hasMobPart(player, "snow_golem", BodyPartType.TORSO))) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().is(DamageTypeTags.IS_FIRE)
                && hasMobPart(player, "warden", BodyPartType.TORSO)) {
            event.setCanceled(true);
            return;
        }
        // 炽足兽腿部:在岩浆中/岩浆表面时免疫火焰伤害(同原版炽足兽)
        if (event.getSource().is(DamageTypeTags.IS_FIRE)
                && (hasMobPart(player, "strider", BodyPartType.LEG_LEFT)
                || hasMobPart(player, "strider", BodyPartType.LEG_RIGHT))
                && (player.isInLava()
                || player.level().getFluidState(player.blockPosition().below()).is(FluidTags.LAVA))) {
            event.setCanceled(true);
        }
    }

    /** 监守者手臂特性:近战伤害 +5/臂(伤害计算阶段叠加) */
    @SubscribeEvent
    public static void onWardenArmDamage(LivingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof Player player)) return;
        int arms = countMobParts(player, "warden",
                new BodyPartType[]{BodyPartType.ARM_LEFT, BodyPartType.ARM_RIGHT});
        if (arms > 0) {
            event.setAmount(event.getAmount() + 5.0F * arms);
        }
    }

    /** 监守者躯干特性:免疫击退 */
    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (hasMobPart(player, "warden", BodyPartType.TORSO)) {
            event.setCanceled(true);
        }
    }

    /**
     * 玩家受击处理:
     * - 凋零躯干:血量不高于一半时免疫远程攻击;
     * - 史莱姆头部:前两次致死伤害转化为最大生命值的一半/四分之一,分裂两次后才会真死;
     * - 唤魔者躯干技能:受到致死伤害时自动触发不死图腾效果(15 分钟冷却)。
     */
    @SubscribeEvent
    public static void onFatalDamage(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // 凋零躯干:血量过半时免疫远程攻击(原版箭矢/三叉戟/雪球等投射物)
        if (hasMobPart(player, "wither", BodyPartType.TORSO)
                && event.getSource().is(DamageTypeTags.IS_PROJECTILE)
                && player.getHealth() <= player.getMaxHealth() * 0.5F) {
            event.setCanceled(true);
            return;
        }
        if (event.getAmount() < player.getHealth()) return;
        // 调试:致死伤害节点(触发分裂/图腾的入口),便于排查特性未触发问题
        CyberWare.LOGGER.info("[MobPartEffects] 致死伤害: health={}, amount={}, slimeHead={}, splits={}",
                player.getHealth(), event.getAmount(),
                hasMobPart(player, "slime", BodyPartType.HEAD),
                player.getPersistentData().getInt(NBT_SLIME_SPLITS));
        // 史莱姆头部:分裂(最多两次,满血时重置)
        if (hasMobPart(player, "slime", BodyPartType.HEAD)) {
            int splits = player.getPersistentData().getInt(NBT_SLIME_SPLITS);
            if (splits < 2) {
                event.setCanceled(true);
                player.getPersistentData().putInt(NBT_SLIME_SPLITS, splits + 1);
                player.setHealth(player.getMaxHealth() / (float) Math.pow(2, splits + 1));
                player.removeAllEffects();
                ServerLevel level = player.serverLevel();
                // 原版史莱姆分裂音效 + 粘液粒子
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 1.0F);
                level.sendParticles(ParticleTypes.ITEM_SLIME,
                        player.getX(), player.getY(0.5), player.getZ(), 8, 0.4, 0.4, 0.4, 0.0);
                return;
            }
        }
        if (player.level().getGameTime() < abilityCooldownEnd(player, "die_totem")) return;
        if (!hasMobPart(player, "evoker", BodyPartType.TORSO)) return;
        event.setCanceled(true);
        tryAutoTotem(player);
    }

    /** 铁傀儡头部:手持铁锭右键回复 1 格血(2 HP,5 秒冷却) */
    @SubscribeEvent
    public static void onRightClickIron(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getItemStack().is(Items.IRON_INGOT)) return;
        if (!hasMobPart(player, "iron_golem", BodyPartType.HEAD)) return;
        long now = player.level().getGameTime();
        if (now < abilityCooldownEnd(player, "iron_repair")) return;
        if (ironRepair(player)) {
            long end = now + 100;
            player.getPersistentData().putLong(NBT_ABILITY_CD_PREFIX + "iron_repair", end);
            A_PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new MobAbilityCooldownPacket("iron_repair", end));
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private MobPartEffects() {}
}
