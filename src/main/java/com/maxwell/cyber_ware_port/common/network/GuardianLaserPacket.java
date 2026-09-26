package com.maxwell.cyber_ware_port.common.network;

import com.maxwell.cyber_ware_port.client.GuardianLaserClientRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 -> 客户端:同步守卫者激光蓄力状态。
 * 客户端在蓄力期间( gameTime ∈ [startTick, endTick) )渲染原版同款光束。
 */
public record GuardianLaserPacket(int playerId, int targetId, long startTick, long endTick) {

    public static GuardianLaserPacket fromBytes(FriendlyByteBuf buf) {
        return new GuardianLaserPacket(buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readLong());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.playerId);
        buf.writeVarInt(this.targetId);
        buf.writeLong(this.startTick);
        buf.writeLong(this.endTick);
    }

    public static void handle(GuardianLaserPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        GuardianLaserClientRenderer.onLaserSync(msg.playerId, msg.targetId, msg.startTick, msg.endTick)));
        ctx.get().setPacketHandled(true);
    }
}
