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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
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
        // 先把所有已知部件隐藏(递归查找,监守者等模型部件嵌套在 bone/body 下),再显示目标部位(只显示第一个部件做物品图标)
        for (String name : MobPartRenderRegistry.ALL_CANDIDATE_NAMES) {
            ModelPart hidden = MobPartRenderRegistry.findPart(root, name);
            if (hidden != null) {
                hidden.visible = false;
            }
        }
        if (show.isEmpty()) return;
        ModelPart partModel = MobPartRenderRegistry.findPart(root, show.get(0));
        partModel.resetPose();
        partModel.xRot = 0;
        partModel.yRot = 0;
        partModel.zRot = 0;
        // 自动居中:非人形模型的部件枢轴差异很大(末影人头 y=-15,蜘蛛头枢轴远离原点),
        // 按部件 cube 包围盒把部件摆到人类部件标准取景位置,统一沿用下方取景参数
        float[] mobCenter = cubeBoundsCenter(partModel);
        float[] target = humanoidCenter(part);
        partModel.x = target[0] - mobCenter[0];
        partModel.y = target[1] - mobCenter[1];
        partModel.z = target[2] - mobCenter[2];
        partModel.visible = true;

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
        partModel.render(poseStack, consumer, packedLight, packedOverlay);
        poseStack.popPose();
    }

    /**
     * 1.20.1 中 ModelPart.cubes 为 private,反射获取。
     * 注意:必须用 ObfuscationReflectionHelper + SRG 名(f_104212_),生产环境运行时是 SRG 名,
     * 直接 getDeclaredField("cubes") 会 NoSuchFieldException(1.21.1 NeoForge 运行时是 Mojmap 才能直接写)。
     * Cube 的 min/max 字段是 public final,直接访问。
     */
    private static final java.lang.reflect.Field CUBES_FIELD;

    static {
        CUBES_FIELD = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(ModelPart.class, "f_104212_");
        CUBES_FIELD.setAccessible(true);
    }

    private static float[] cubeBoundsCenter(ModelPart part) {
        @SuppressWarnings("unchecked")
        List<ModelPart.Cube> cubes;
        try {
            cubes = (List<ModelPart.Cube>) CUBES_FIELD.get(part);
        } catch (IllegalAccessException e) {
            return new float[]{0, 0, 0};
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (ModelPart.Cube cube : cubes) {
            minX = Math.min(minX, cube.minX);
            minY = Math.min(minY, cube.minY);
            minZ = Math.min(minZ, cube.minZ);
            maxX = Math.max(maxX, cube.maxX);
            maxY = Math.max(maxY, cube.maxY);
            maxZ = Math.max(maxZ, cube.maxZ);
        }
        if (minX == Float.MAX_VALUE) return new float[]{0, 0, 0};
        return new float[]{(minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2};
    }

    /** 人类部件在模型空间中的 cube 包围盒中心(枢轴 + cube 中心,取景参数按此调校) */
    private static float[] humanoidCenter(BodyPartType part) {
        return switch (part) {
            case HEAD -> new float[]{0, -4, 0};
            case TORSO -> new float[]{0, 6, 0};
            case ARM_LEFT -> new float[]{-4, 6, 0};
            case ARM_RIGHT -> new float[]{4, 6, 0};
            case LEG_LEFT -> new float[]{-2, 18, 0};
            case LEG_RIGHT, HAND_LEFT, HAND_RIGHT, FOOT_LEFT, FOOT_RIGHT -> new float[]{2, 18, 0};
            default -> new float[]{0, 0, 0};
        };
    }
}
