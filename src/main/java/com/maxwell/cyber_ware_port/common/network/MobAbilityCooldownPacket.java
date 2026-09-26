package com.maxwell.cyber_ware_port.common.network;

import com.maxwell.cyber_ware_port.client.ForgeClientEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 -> 客户端:同步技能冷却结束时刻(gameTime)。
 * 客户端 HUD 用它在左下角技能名后显示剩余冷却。
 */
public record MobAbilityCooldownPacket(String abilityId, long cooldownEnd) {

    public static MobAbilityCooldownPacket fromBytes(FriendlyByteBuf buf) {
        return new MobAbilityCooldownPacket(buf.readUtf(), buf.readLong());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.abilityId);
        buf.writeLong(this.cooldownEnd);
    }

    public static void handle(MobAbilityCooldownPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ForgeClientEvents.onMobAbilityCooldown(msg.abilityId, msg.cooldownEnd)));
        ctx.get().setPacketHandled(true);
    }
}
