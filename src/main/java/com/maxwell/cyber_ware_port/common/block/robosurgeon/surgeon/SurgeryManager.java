package com.maxwell.cyber_ware_port.common.block.robosurgeon.surgeon;

import com.maxwell.cyber_ware_port.api.json.CyberwareAPI;
import com.maxwell.cyber_ware_port.common.capability.CyberwareUserData;
import com.maxwell.cyber_ware_port.common.item.base.BodyPartType;
import com.maxwell.cyber_ware_port.common.item.base.ICyberware;
import com.maxwell.cyber_ware_port.common.item.cyberware.MobPartItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

public class SurgeryManager {
    public static void execute(ServerPlayer player, IItemHandlerModifiable table, CyberwareUserData userData) {
        ItemStackHandler body = userData.getInstalledCyberware();
        List<ItemStack> ejectList = new ArrayList<>();
        for (int i = 0; i < table.getSlots(); i++) {
            ItemStack tableStack = table.getStackInSlot(i);
            if (isGhost(tableStack))
                continue;
            ItemStack oldPart = body.getStackInSlot(i);
            if (!oldPart.isEmpty()) {
                ejectList.add(oldPart.copy());
            }
            ItemStack insertedDeducted = table.extractItem(i, 64, false);
            // 臂/腿区域按槽位规范化左右侧(+0=左、+1=右),防止部位装到另一侧槽位导致渲染相反
            if (!insertedDeducted.isEmpty() && insertedDeducted.getItem() instanceof MobPartItem) {
                BodyPartType sided = MobPartItem.sidedPartForSlot(i, MobPartItem.getPart(insertedDeducted));
                if (sided != MobPartItem.getPart(insertedDeducted)) {
                    insertedDeducted.getOrCreateTag().putString(MobPartItem.NBT_PART_TYPE, sided.getSerializedName());
                }
            }
            body.setStackInSlot(i, insertedDeducted);
            // 手术覆盖过的槽位原生肉体已切除(装新部位或移除旧部位都算):
            // 槽位为空时原版肢体不再渲染,腿部缺失还会降低移动速度
            userData.markFleshRemoved(i);
        }
        resolveConflicts(body, ejectList);
        for (ItemStack stack : ejectList) {
            if (stack.isEmpty())
                continue;
            if (!player.getInventory().add(stack)) {
                net.minecraft.world.entity.item.ItemEntity itemEntity = player.drop(stack, false);
                if (itemEntity != null) {
                    itemEntity.setNoPickUpDelay();
                    itemEntity.setUnlimitedLifetime();
                }
            }
        }
    }

    private static void resolveConflicts(ItemStackHandler body, List<ItemStack> ejectList) {
        for (int i = 0; i < body.getSlots(); i++) {
            ItemStack s1 = body.getStackInSlot(i);
            ICyberware cw1 = CyberwareAPI.getCyberware(s1);
            if (cw1 == null)
                continue;
            for (int j = i + 1; j < body.getSlots(); j++) {
                ItemStack s2 = body.getStackInSlot(j);
                ICyberware cw2 = CyberwareAPI.getCyberware(s2);
                if (cw2 == null)
                    continue;
                boolean conflict = false;
                if (cw1.getBodyPartType(s1) != BodyPartType.NONE
                        && cw1.getBodyPartType(s1) == cw2.getBodyPartType(s2)) {
                    conflict = true;
                }
                if (!conflict) {
                    if (cw1.isIncompatible(s1, s2) || cw2.isIncompatible(s2, s1)) {
                        conflict = true;
                    }
                }
                if (conflict) {
                    int q1 = cw1.getQuality(s1);
                    int q2 = cw2.getQuality(s2);
                    int loserIndex;
                    if (q1 > q2) {
                        loserIndex = j;
                    } else if (q2 > q1) {
                        loserIndex = i;
                    } else {
                        loserIndex = j;
                    }
                    ItemStack loserStack = body.getStackInSlot(loserIndex);
                    ejectList.add(loserStack.copy());
                    body.setStackInSlot(loserIndex, ItemStack.EMPTY);
                    if (loserIndex == i) {
                        break;
                    }

                }
            }
        }
    }

    public static boolean isGhost(ItemStack s) {
        return !s.isEmpty() && s.hasTag() && s.getTag().getBoolean("cyberware_ghost");
    }
}