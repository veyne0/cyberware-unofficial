package com.maxwell.cyber_ware_port.common.block.robosurgeon.surgeon;

import com.maxwell.cyber_ware_port.common.block.surgerychamber.SurgeryChamberBlockEntity;
import com.maxwell.cyber_ware_port.common.util.MobSurfaceParts;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/**
 * 生物拆解执行器:手术完成后把生物的表面部位物品写入手术舱输出栏,
 * 并移除原生物。玩家打开舱门时由 SurgeryChamberBlockEntity 分发输出栏内容。
 */
public class MobDismemberManager {

    /** 判断输出栏是否装得下全部拆解产物 */
    public static boolean canFit(ItemStackHandler output, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            boolean fits = false;
            for (int i = 0; i < output.getSlots(); i++) {
                if (output.insertItem(i, drop.copy(), true).isEmpty()) {
                    fits = true;
                    break;
                }
            }
            if (!fits) return false;
        }
        return true;
    }

    /** 执行拆解:产物入舱、生物移除 */
    public static void execute(SurgeryChamberBlockEntity chamber, LivingEntity mob) {
        ItemStackHandler output = chamber.getOutputHandler();
        for (ItemStack drop : MobSurfaceParts.createDrops(mob.getType())) {
            for (int i = 0; i < output.getSlots() && !drop.isEmpty(); i++) {
                drop = output.insertItem(i, drop, false);
            }
            // canFit 已校验过空间,正常不会溢出;万一有剩余就掉落在舱边
            if (!drop.isEmpty() && chamber.getLevel() != null) {
                net.minecraft.world.Containers.dropItemStack(chamber.getLevel(),
                        mob.getX(), mob.getY() + 0.5, mob.getZ(), drop);
            }
        }
        chamber.setChanged();

        // 拆解完成的视觉/音效反馈
        if (chamber.getLevel() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD,
                    mob.getX(), mob.getY() + 0.8, mob.getZ(), 20, 0.3, 0.5, 0.3, 0.02);
            level.playSound(null, mob.blockPosition(),
                    SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.6F, 0.6F);
        }
        mob.discard();
    }

    private MobDismemberManager() {
    }
}
