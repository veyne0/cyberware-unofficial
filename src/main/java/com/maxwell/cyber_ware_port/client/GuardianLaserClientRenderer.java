package com.maxwell.cyber_ware_port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 守卫者激光客户端渲染:玩家蓄力期间在玩家眼睛与目标之间
 * 画原版同款光束(几何/UV 滚动/颜色渐变均照搬 GuardianRenderer)。
 * 不要恢复 @Mod.EventBusSubscriber:自动扫描会漏掉本类,
 * 现由主类构造器里 MinecraftForge.EVENT_BUS.addListener 显式注册。
 */
public final class GuardianLaserClientRenderer {
    private static final ResourceLocation BEAM_TEXTURE =
            new ResourceLocation("minecraft", "textures/entity/guardian_beam.png");
    private static final RenderType BEAM_RENDER_TYPE = RenderType.entityCutoutNoCull(BEAM_TEXTURE);

    /** key = 施法玩家实体 id */
    private record Laser(int targetId, long startTick, long endTick) {}
    private static final Map<Integer, Laser> ACTIVE = new ConcurrentHashMap<>();

    public static void onLaserSync(int playerId, int targetId, long startTick, long endTick) {
        ACTIVE.put(playerId, new Laser(targetId, startTick, endTick));
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (ACTIVE.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            ACTIVE.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        long gameTime = level.getGameTime();
        float partialTick = event.getPartialTick();

        Iterator<Map.Entry<Integer, Laser>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Laser> entry = it.next();
            Laser laser = entry.getValue();
            Entity shooter = level.getEntity(entry.getKey());
            Entity target = level.getEntity(laser.targetId());
            // 蓄力结束或任一方实体消失:移除
            if (gameTime >= laser.endTick() || !(shooter instanceof LivingEntity shooterLiving)
                    || !(target instanceof LivingEntity targetLiving) || shooter.isRemoved()) {
                it.remove();
                continue;
            }
            // 原版几何:进度 f ∈ (0,1],UV 沿光束滚动
            float duration = laser.endTick() - laser.startTick();
            float f = (gameTime - laser.startTick() + partialTick) / duration;
            float f1 = gameTime - laser.startTick() + partialTick;
            float f2 = f1 * 0.5F % 1.0F;
            Vec3 from = shooterLiving.getEyePosition(partialTick);
            Vec3 to = new Vec3(
                    Mth.lerp((double) partialTick, targetLiving.xOld, targetLiving.getX()),
                    Mth.lerp((double) partialTick, targetLiving.yOld, targetLiving.getY()) + targetLiving.getBbHeight() * 0.5D,
                    Mth.lerp((double) partialTick, targetLiving.zOld, targetLiving.getZ()));
            renderBeam(poseStack, buffer, cam, from, to, gameTime - laser.startTick() + partialTick, f, f2);
        }
        buffer.endBatch(BEAM_RENDER_TYPE);
    }

    /** 光束绘制逻辑照搬原版 GuardianRenderer.render(玩家视角:直接在关卡坐标系画) */
    private static void renderBeam(PoseStack poseStack, MultiBufferSource buffer, Vec3 cam,
                                   Vec3 from, Vec3 to, float age, float progress, float uvScroll) {
        poseStack.pushPose();
        poseStack.translate(from.x - cam.x, from.y - cam.y, from.z - cam.z);
        Vec3 dir = to.subtract(from);
        float length = (float) dir.length() + 1.0F;
        dir = dir.normalize();
        float pitch = (float) Math.acos(dir.y);
        float yaw = (float) Math.atan2(dir.z, dir.x);
        poseStack.mulPose(Axis.YP.rotationDegrees(((float) (Math.PI / 2F) - yaw) * (180F / (float) Math.PI)));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch * (180F / (float) Math.PI)));
        float spin = age * 0.05F * -1.5F;
        float f8 = progress * progress;
        int j = 64 + (int) (f8 * 191.0F);
        int k = 32 + (int) (f8 * 191.0F);
        int l = 128 - (int) (f8 * 64.0F);
        float f11 = Mth.cos(spin + 2.3561945F) * 0.282F;
        float f12 = Mth.sin(spin + 2.3561945F) * 0.282F;
        float f13 = Mth.cos(spin + ((float) Math.PI / 4F)) * 0.282F;
        float f14 = Mth.sin(spin + ((float) Math.PI / 4F)) * 0.282F;
        float f15 = Mth.cos(spin + 3.926991F) * 0.282F;
        float f16 = Mth.sin(spin + 3.926991F) * 0.282F;
        float f17 = Mth.cos(spin + 5.4977875F) * 0.282F;
        float f18 = Mth.sin(spin + 5.4977875F) * 0.282F;
        float f19 = Mth.cos(spin + (float) Math.PI) * 0.2F;
        float f20 = Mth.sin(spin + (float) Math.PI) * 0.2F;
        float f21 = Mth.cos(spin + 0.0F) * 0.2F;
        float f22 = Mth.sin(spin + 0.0F) * 0.2F;
        float f23 = Mth.cos(spin + ((float) Math.PI / 2F)) * 0.2F;
        float f24 = Mth.sin(spin + ((float) Math.PI / 2F)) * 0.2F;
        float f25 = Mth.cos(spin + ((float) Math.PI * 1.5F)) * 0.2F;
        float f26 = Mth.sin(spin + ((float) Math.PI * 1.5F)) * 0.2F;
        float f29 = -1.0F + uvScroll;
        float f30 = length * 2.5F + f29;
        VertexConsumer vc = buffer.getBuffer(BEAM_RENDER_TYPE);
        PoseStack.Pose pose = poseStack.last();
        vertex(vc, pose, f19, length, f20, j, k, l, 0.4999F, f30);
        vertex(vc, pose, f19, 0.0F, f20, j, k, l, 0.4999F, f29);
        vertex(vc, pose, f21, 0.0F, f22, j, k, l, 0.0F, f29);
        vertex(vc, pose, f21, length, f22, j, k, l, 0.0F, f30);
        vertex(vc, pose, f23, length, f24, j, k, l, 0.4999F, f30);
        vertex(vc, pose, f23, 0.0F, f24, j, k, l, 0.4999F, f29);
        vertex(vc, pose, f25, 0.0F, f26, j, k, l, 0.0F, f30);
        vertex(vc, pose, f25, length, f26, j, k, l, 0.0F, f29);
        float f31 = 0.0F;
        // 原版:双数 tick 交错 UV,让内圈闪动
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getGameTime() % 2 == 0) {
            f31 = 0.5F;
        }
        vertex(vc, pose, f11, length, f12, j, k, l, 0.5F, f31 + 0.5F);
        vertex(vc, pose, f13, length, f14, j, k, l, 1.0F, f31 + 0.5F);
        vertex(vc, pose, f17, length, f18, j, k, l, 1.0F, f31);
        vertex(vc, pose, f15, length, f16, j, k, l, 0.5F, f31);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose,
                               float x, float y, float z, int r, int g, int b, float u, float v) {
        vertex(vc, pose.pose(), pose.normal(), x, y, z, r, g, b, u, v);
    }

    private static void vertex(VertexConsumer vc, Matrix4f matrix, Matrix3f normal,
                               float x, float y, float z, int r, int g, int b, float u, float v) {
        vc.vertex(matrix, x, y, z)
                .color(r, g, b, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(240, 240)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    private GuardianLaserClientRenderer() {}
}
