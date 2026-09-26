package com.maxwell.cyber_ware_port.common.network;

import com.maxwell.cyber_ware_port.common.effect.MobPartEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 -> 服务端:释放当前选中的生物部位技能。
 * index 对应 {@link MobPartEffects#getAbilities} 双端一致扫描出的技能顺序。
 */
public record UseMobPartAbilityPacket(int index) {

    public UseMobPartAbilityPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.index);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                MobPartEffects.executeAbility(player, this.index);
            }
        });
        context.setPacketHandled(true);
    }
}
