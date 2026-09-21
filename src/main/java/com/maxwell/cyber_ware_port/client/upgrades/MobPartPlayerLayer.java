package com.maxwell.cyber_ware_port.client.upgrades;

import com.maxwell.cyber_ware_port.CyberWare;
import com.maxwell.cyber_ware_port.client.model.MobPartRenderRegistry;
import com.maxwell.cyber_ware_port.common.capability.CyberwareCapabilityProvider;
import com.maxwell.cyber_ware_port.common.capability.CyberwareUserData;
import com.maxwell.cyber_ware_port.common.item.base.BodyPartType;
import com.maxwell.cyber_ware_port.common.item.cyberware.MobPartItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * 玩家生物部位外观渲染层:把玩家身上安装的生物部位(mob_part)
 * 用原版生物模型+贴图渲染出来,姿态通过 copyFrom 对齐到玩家的
 * 头/躯干/手臂/腿部件上,替代被隐藏的原版部件。
 */
public class MobPartPlayerLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final EntityModelSet modelSet;
    private final Map<ResourceLocation, ModelPart> bakedCache = new HashMap<>();

    public MobPartPlayerLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer, EntityModelSet modelSet) {
        super(renderer);
        this.modelSet = modelSet;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        CyberwareUserData data = player.getData(CyberwareCapabilityProvider.CYBERWARE_DATA.get());
        ItemStackHandler handler = data.getInstalledCyberware();
        PlayerModel<AbstractClientPlayer> parent = this.getParentModel();
        // 临时调试日志:检查 Pre 事件里设置的 visible 是否在渲染层被重置
        if (player.tickCount % 200 == 0) {
            CyberWare.LOGGER.info("[CW-DBG] Layer: headVis={} bodyVis={} lArmVis={} lLegVis={}",
                    parent.head.visible, parent.body.visible, parent.leftArm.visible, parent.leftLeg.visible);
        }

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!(stack.getItem() instanceof MobPartItem)) continue;
            BodyPartType part = MobPartItem.getPart(stack);
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
            if (root.hasChild(name)) {
                root.getChild(name).visible = false;
            }
        }
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(data.texture()));
        for (String name : MobPartRenderRegistry.partNames(part, root)) {
            ModelPart p = root.getChild(name);
            p.copyFrom(poseSource);
            applyMobScaling(p, mobId, part);
            p.visible = true;
            p.render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY);
        }
    }

    /** 不同生物的模型尺寸和玩家差异很大,装到玩家身上时按比例修正以匹配原版部件大小 */
    private static void applyMobScaling(ModelPart p, ResourceLocation mobId, BodyPartType part) {
        String id = mobId.getPath();
        switch (part) {
            case HEAD -> {
                // 多数 humanoid 头 cube 8x8x8,大多数生物头 cube 是 4x4 或更小(苦力怕 4、僵尸 8)
                if ("creeper".equals(id) || "skeleton".equals(id)) {
                    p.yScale = 2.0f;
                }
            }
            case TORSO -> {
                // 苦力怕躯干高度只有 8 单位(玩家躯干 12),纵向拉高
                if ("creeper".equals(id)) {
                    p.yScale = 1.5f;
                }
            }
            case LEG_LEFT, LEG_RIGHT -> {
                if ("creeper".equals(id)) {
                    // 苦力怕腿只有 6 单位长(玩家腿 12),纵向拉长一倍
                    p.yScale = 2.0f;
                } else if ("spider".equals(id)) {
                    // 蜘蛛腿横向 16 单位,整体缩到玩家腿比例
                    p.xScale = 0.5f;
                    p.yScale = 0.5f;
                    p.zScale = 0.5f;
                }
            }
            case ARM_LEFT, ARM_RIGHT -> {
                // 苦力怕没有手臂部件,跳过;其他 humanoid 模型通常尺寸差不多
                if ("spider".equals(id)) {
                    p.xScale = 0.5f;
                    p.yScale = 0.5f;
                    p.zScale = 0.5f;
                }
            }
            default -> { /* 其他部位目前不渲染 */ }
        }
    }
}
