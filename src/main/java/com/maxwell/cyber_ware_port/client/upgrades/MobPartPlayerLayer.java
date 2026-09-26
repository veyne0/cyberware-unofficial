package com.maxwell.cyber_ware_port.client.upgrades;

import com.maxwell.cyber_ware_port.CyberWare;
import com.maxwell.cyber_ware_port.client.model.MobPartRenderRegistry;
import com.maxwell.cyber_ware_port.client.screen.roboSurgeon.RobosurgeonScreen;
import com.maxwell.cyber_ware_port.common.capability.CyberwareCapabilityProvider;
import com.maxwell.cyber_ware_port.common.item.base.BodyPartType;
import com.maxwell.cyber_ware_port.common.item.cyberware.MobPartItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.items.ItemStackHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * 玩家生物部位外观渲染层:把玩家身上安装的生物部位(mob_part)
 * 用原版生物模型+贴图渲染出来,姿态通过 copyFrom 对齐到玩家的
 * 头/躯干/手臂/腿部件上,替代被隐藏的原版部件。
 *
 * 手术台 GUI 打开时改用投射状态(桌面待装 + 虚影保留)实时渲染预览,
 * 替换部位无需手术后即可在预览中看到新样子。
 */
@OnlyIn(Dist.CLIENT)
public class MobPartPlayerLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** 四足动物(躯干需要恢复原版 90° 横置旋转) */
    private static final java.util.Set<String> QUADRUPEDS = java.util.Set.of("pig", "cow", "sheep", "goat", "wolf");
    private final EntityModelSet modelSet;
    private final Map<ResourceLocation, ModelPart> bakedCache = new HashMap<>();

    /** 调试:已打过日志的 mobId:part:name 组合 */
    private static final java.util.Set<String> DEBUG_LOGGED = new java.util.HashSet<>();

    public MobPartPlayerLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer, EntityModelSet modelSet) {
        super(renderer);
        this.modelSet = modelSet;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        PlayerModel<AbstractClientPlayer> parent = this.getParentModel();
        // 手术台 GUI 打开时:用投射状态(桌面待装 + 虚影保留)实时渲染预览,
        // 替换部位无需手术后即可在预览中看到新样子
        if (player == Minecraft.getInstance().player
                && Minecraft.getInstance().screen instanceof RobosurgeonScreen rs) {
            for (var entry : rs.projectedMobParts().entrySet()) {
                BodyPartType part = entry.getKey();
                ModelPart poseSource = switch (part) {
                    case HEAD -> parent.head;
                    case TORSO -> parent.body;
                    case ARM_LEFT -> parent.leftArm;
                    case ARM_RIGHT -> parent.rightArm;
                    case LEG_LEFT -> parent.leftLeg;
                    case LEG_RIGHT -> parent.rightLeg;
                    default -> null;
                };
                if (poseSource == null) continue;
                if (!isSkinLayerShown(player, part)) continue;
                renderMobPart(poseStack, buffer, packedLight, entry.getValue(), part, poseSource);
            }
            return;
        }
        player.getCapability(CyberwareCapabilityProvider.CYBERWARE_CAPABILITY).ifPresent(data -> {
            ItemStackHandler handler = data.getInstalledCyberware();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!(stack.getItem() instanceof MobPartItem)) continue;
                // 臂/腿区域按槽位决定左右侧(+0=左、+1=右),防止物品类型与槽位不符时渲染相反
                BodyPartType part = MobPartItem.sidedPartForSlot(i, MobPartItem.getPart(stack));
                ModelPart poseSource = switch (part) {
                    case HEAD -> parent.head;
                    case TORSO -> parent.body;
                    case ARM_LEFT -> parent.leftArm;
                    case ARM_RIGHT -> parent.rightArm;
                    case LEG_LEFT -> parent.leftLeg;
                    case LEG_RIGHT -> parent.rightLeg;
                    default -> null;
                };
                if (poseSource == null) continue;
                if (!isSkinLayerShown(player, part)) continue;
                renderMobPart(poseStack, buffer, packedLight, MobPartItem.getMobId(stack), part, poseSource);
            }
        });
    }

    private static boolean isSkinLayerShown(AbstractClientPlayer player, BodyPartType part) {
        return switch (part) {
            case HEAD -> player.isModelPartShown(PlayerModelPart.HAT);
            case TORSO -> player.isModelPartShown(PlayerModelPart.JACKET);
            case ARM_LEFT -> player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
            case ARM_RIGHT -> player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
            case LEG_LEFT -> player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
            case LEG_RIGHT -> player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
            default -> true;
        };
    }

    private void renderMobPart(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                               ResourceLocation mobId, BodyPartType part, ModelPart poseSource) {
        MobPartRenderRegistry.MobPartRenderData data = MobPartRenderRegistry.get(mobId);
        if (data == null) return;
        // 模型实例按 mobId 共享缓存,每次渲染前重置可见性,只显示目标部位
        ModelPart root = this.bakedCache.computeIfAbsent(mobId, id -> this.modelSet.bakeLayer(data.layer()));
        for (String name : MobPartRenderRegistry.ALL_CANDIDATE_NAMES) {
            ModelPart hidden = MobPartRenderRegistry.findPart(root, name);
            if (hidden != null) {
                hidden.visible = false;
            }
        }
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(data.texture()));
        // 主部件 + 附加子部件(如 1.21.1 史莱姆头的眼睛/嘴),附加件与主部件使用同一变换
        java.util.List<String> names = new java.util.ArrayList<>(MobPartRenderRegistry.partNames(part, root));
        for (String extra : MobPartRenderRegistry.extraParts(mobId)) {
            if (MobPartRenderRegistry.findPart(root, extra) != null) {
                names.add(extra);
            }
        }
        for (String name : names) {
            ModelPart p = MobPartRenderRegistry.findPart(root, name);
            // copyFrom 不拷 scale,先清残留再对齐,避免上一次的缩放累积
            p.xScale = p.yScale = p.zScale = 1.0f;
            p.copyFrom(poseSource);
            applyMobScaling(p, mobId, part);
            p.visible = true;
            if (DEBUG_LOGGED.add(mobId + ":" + part + ":" + name)) {
                CyberWare.LOGGER.info("[MobPartLayer] mob={} part={} name={} pivot=({},{},{}) rot=({},{},{}) scale=({},{},{})",
                        mobId, part, name, p.x, p.y, p.z, p.xRot, p.yRot, p.zRot, p.xScale, p.yScale, p.zScale);
            }
            p.render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY);
        }
    }

    /** 不同生物的模型尺寸和玩家差异很大,装到玩家身上时按比例修正以匹配原版部件大小 */
    public static void applyMobScaling(ModelPart p, ResourceLocation mobId, BodyPartType part) {
        String id = mobId.getPath();
        switch (part) {
            case HEAD -> {
                // 苦力怕/骷髅/僵尸等头 cube 都是 8x8x8,与玩家头一致,无需缩放
                if ("chicken".equals(id)) {
                    // 鸡头只有 4x6x4,放大到接近玩家头
                    p.xScale = 1.75f;
                    p.yScale = 1.75f;
                    p.zScale = 1.75f;
                } else if ("warden".equals(id)) {
                    // 监守者头 cube 16x16x10(玩家头 8x8x8),缩到玩家比例
                    p.xScale = 0.5f;
                    p.yScale = 0.5f;
                    p.zScale = 0.8f;
                } else if ("guardian".equals(id) || "elder_guardian".equals(id)) {
                    // 守卫者头(原版渲染缩放 0.5):cube 相对枢轴 y=8..22,缩 0.5 后 4..11,
                    // 居中对齐玩家头(-8..0)需上移 11.5
                    p.xScale = 0.5f;
                    p.yScale = 0.5f;
                    p.zScale = 0.5f;
                    p.y -= 11.5f;
                } else if ("slime".equals(id)) {
                    // 史莱姆头:内核 cube 6x6x6(y=17..23,枢轴 0),放大 4/3 成玩家头大小,
                    // 缩放后 y=22.67..30.67,上移 30.67 对齐玩家头(-8..0);
                    // 1.21.1 里眼睛/嘴是根级子部件会随缩放一起对齐,1.20.1 脸画在内核贴图上无需处理
                    p.xScale = 1.3333f;
                    p.yScale = 1.3333f;
                    p.zScale = 1.3333f;
                    p.y -= 30.6667f;
                } else if ("wither".equals(id)) {
                    // 凋零中头 cube 8x8x8(枢轴 0,相对 y=-4..4),下移 4 对齐玩家头(-8..0)
                    p.y -= 4.0f;
                }
            }
            case TORSO -> {
                // 苦力怕/闪电苦力怕躯干 cube 8x12x4(枢轴 y=0..12),与玩家身体同尺寸同朝向,无需调整;
                // 鸡躯干不在掉落列表,无需处理
                if ("snow_golem".equals(id)) {
                    // 雪傀儡 upper_body 10x10x10(压缩 0.5),压扁对齐玩家躯干(8x12x4);
                    // 其 cube 相对枢轴 y=-10..0(挂枢轴上方),yScale 1.2 后 -12..0,
                    // 玩家身体在枢轴下方 0..12,需下移 12 对齐,否则躯干挂到头顶
                    p.xScale = 0.8f;
                    p.yScale = 1.2f;
                    p.zScale = 0.4f;
                    p.y += 12.0f;
                } else if ("warden".equals(id)) {
                    // 监守者躯干 18x21x11,缩到玩家躯干比例(8x12x4);
                    // 其 cube 相对枢轴 y=-13..8(中心-2.5),玩家身体 cube 在枢轴下方
                    // y=0..12(中心+6,枢轴在脖子),缩放后中心 -1.375,需下移 7.4 对齐,
                    // 否则躯干挂到头顶导致胸口空;z 方向 cube 中心 0.525,前移 0.5 对齐
                    p.xScale = 0.45f;
                    p.yScale = 0.55f;
                    p.zScale = 0.35f;
                    p.y += 7.4f;
                    p.z -= 0.5f;
                } else if ("wither".equals(id)) {
                    // 凋零躯干=肋骨架:脊柱 3x10x3 + 三根横骨,挂在枢轴下方 0..10,
                    // 放大 1.2 到玩家躯干高度(12),保留自带的 0.204 前倾
                    p.xScale = 1.2f;
                    p.yScale = 1.2f;
                    p.zScale = 1.2f;
                } else if (QUADRUPEDS.contains(id)) {
                    // 四足动物躯干:原版模型自带 xRot=90°(横置),copyFrom 被覆盖需补回;
                    // 整体缩到玩家躯干比例并下移对齐胸口
                    p.xRot = (float) (Math.PI / 2);
                    p.y += 5;
                    switch (id) {
                        case "pig" -> {
                            p.xScale = 0.5f;
                            p.yScale = 0.6f;
                            p.zScale = 0.75f;
                        }
                        case "goat" -> {
                            p.xScale = 0.6f;
                            p.yScale = 0.6f;
                            p.zScale = 0.8f;
                        }
                        default -> {
                            // 牛/羊/狼
                            p.xScale = 0.66f;
                            p.yScale = 0.66f;
                        }
                    }
                }
            }
            case LEG_LEFT, LEG_RIGHT -> {
                boolean creeperLike = "creeper".equals(id) || "lightning_creeper".equals(id);
                if (creeperLike) {
                    // 苦力怕腿只有 6 单位长(玩家腿 12),纵向拉长一倍
                    p.yScale = 2.0f;
                } else if ("pig".equals(id)) {
                    // 猪腿也只有 6 单位长
                    p.yScale = 2.0f;
                } else if ("chicken".equals(id)) {
                    // 鸡腿 5 单位长
                    p.yScale = 2.4f;
                } else if ("spider".equals(id)) {
                    // 蜘蛛腿方块沿 X 轴水平延伸(16 单位细条),转 90° 立起来并缩放到玩家腿长
                    p.zRot = -(float) (Math.PI / 2);
                    p.xScale = 0.75f;
                    p.yScale = 0.75f;
                    p.zScale = 0.75f;
                } else if ("enderman".equals(id)) {
                    // 末影人腿长 30 单位(玩家 12),缩到玩家比例
                    p.xScale = 0.4f;
                    p.yScale = 0.4f;
                    p.zScale = 0.4f;
                } else if ("iron_golem".equals(id)) {
                    // 铁傀儡腿 cube 6x16x5(玩家腿 4x12x4),缩 0.75 对齐
                    p.xScale = 0.75f;
                    p.yScale = 0.75f;
                    p.zScale = 0.75f;
                } else if ("warden".equals(id)) {
                    // 监守者腿 cube 6x13x6(中心 y=6.5),缩 0.85 对齐玩家腿长;
                    // 缩放后 cube 中心 5.5,玩家腿中心 6,下移 0.5 对齐
                    p.xScale = 0.85f;
                    p.yScale = 0.85f;
                    p.zScale = 0.85f;
                    p.y += 0.5f;
                } else if ("strider".equals(id)) {
                    // 炽足兽腿 4x16x4(枢轴在腿顶,cube 0..16),缩 0.75 对齐玩家腿长(12)
                    p.xScale = 0.75f;
                    p.yScale = 0.75f;
                    p.zScale = 0.75f;
                }
            }
            case ARM_LEFT, ARM_RIGHT -> {
                // 苦力怕没有手臂部件,跳过;其他 humanoid 模型通常尺寸差不多
                if ("spider".equals(id)) {
                    p.xScale = 0.75f;
                    p.yScale = 0.75f;
                    p.zScale = 0.75f;
                } else if ("enderman".equals(id)) {
                    // 末影人手臂同样偏长(30 单位),缩到玩家比例
                    p.xScale = 0.4f;
                    p.yScale = 0.4f;
                    p.zScale = 0.4f;
                } else if ("warden".equals(id)) {
                    // 监守者臂 cube 8x28x8(cube 中心 y=14),缩 0.45 后中心 6.3,
                    // 玩家臂 cube 中心 y=4,上移 2.3 对齐肩部
                    p.xScale = 0.45f;
                    p.yScale = 0.45f;
                    p.zScale = 0.45f;
                    p.y -= 2.3f;
                } else if ("iron_golem".equals(id)) {
                    // 铁傀儡臂 cube 相对枢轴偏移巨大(右 -13..-9 / 左 9..13),
                    // copyFrom 玩家肩部枢轴后 cube 仍挂在原偏移处导致悬空;
                    // 缩 0.45 并平移枢轴补偿,让臂贴回玩家肩部
                    p.xScale = 0.45f;
                    p.yScale = 0.45f;
                    p.zScale = 0.45f;
                    if (part == BodyPartType.ARM_RIGHT) {
                        p.x = -1.05f;
                    } else {
                        p.x = 1.05f;
                    }
                    p.y = 0.4f;
                    p.z = 0.9f;
                }
            }
            default -> { /* 其他部位目前不渲染 */ }
        }
    }
}
