package com.maxwell.cyber_ware_port.client.render;

import com.maxwell.cyber_ware_port.client.model.MobPartRenderRegistry;
import com.maxwell.cyber_ware_port.common.item.base.BodyPartType;
import com.maxwell.cyber_ware_port.common.item.cyberware.MobPartItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生物部位物品渲染器:按 MOB_ID 烘焙原版生物模型,按 PART_TYPE 只显示对应部件。
 * 仿 CyberSkullItemRenderer 的 BEWLR 模式。
 */
public class MobPartItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final EntityModelSet modelSet;
    private final Map<ResourceLocation, ModelPart> bakedCache = new HashMap<>();

    public MobPartItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
        this.modelSet = modelSet;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        this.bakedCache.clear();
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ResourceLocation mobId = MobPartItem.getMobId(stack);
        BodyPartType part = MobPartItem.getPart(stack);
        MobPartRenderRegistry.MobPartRenderData data = MobPartRenderRegistry.get(mobId);
        if (data == null) return;

        ModelPart root = this.bakedCache.computeIfAbsent(mobId, id -> this.modelSet.bakeLayer(data.layer()));
        List<String> show = MobPartRenderRegistry.partNames(part, root);
        // children 是私有的无法枚举,改用候选名清单:先把所有已知部件隐藏,再显示目标部位
        for (String name : MobPartRenderRegistry.ALL_CANDIDATE_NAMES) {
            if (root.hasChild(name)) {
                root.getChild(name).visible = show.contains(name);
            }
        }

        // 每类部件单独定标:让目标部件大致充满 0..1 的物品展示空间并居中
        float scale = 0.4f;
        float cx = 0.5f;
        float cy = 0.1f;
        switch (part) {
            case HEAD -> {
                scale = 0.8f;
                cy = -0.9f;
            }
            case TORSO -> {
                scale = 0.6f;
                cy = -0.16f;
            }
            case ARM_LEFT -> {
                scale = 0.6f;
                cy = -0.175f;
                cx = 0.31f;
            }
            case ARM_RIGHT -> {
                scale = 0.6f;
                cy = -0.175f;
                cx = 0.69f;
            }
            case LEG_LEFT, LEG_RIGHT, HAND_LEFT, HAND_RIGHT, FOOT_LEFT, FOOT_RIGHT -> {
                scale = 0.6f;
                cy = 0.275f;
            }
            default -> {
            }
        }

        poseStack.pushPose();
        poseStack.translate(cx, cy, 0.5f);
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0f, -1.501f, 0.0f);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(data.texture()));
        root.render(poseStack, consumer, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
