package com.maxwell.cyber_ware_port.init;

import com.maxwell.cyber_ware_port.CyberWare;
import com.maxwell.cyber_ware_port.common.item.base.BodyPartType;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

public class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(CyberWare.MODID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> GHOST_COMPONENT =
            register("ghost", builder -> builder.networkSynchronized(ByteBufCodecs.BOOL).persistent(com.mojang.serialization.Codec.BOOL));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> PRISTINE =
            register("pristine", builder -> builder.networkSynchronized(ByteBufCodecs.BOOL).persistent(com.mojang.serialization.Codec.BOOL));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ACTIVE =
            register("active", builder -> builder.networkSynchronized(ByteBufCodecs.BOOL).persistent(com.mojang.serialization.Codec.BOOL));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> MOB_ID =
            register("mob_id", builder -> builder.networkSynchronized(ResourceLocation.STREAM_CODEC).persistent(ResourceLocation.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BodyPartType>> PART_TYPE =
            register("part_type", builder -> builder.networkSynchronized(ByteBufCodecs.idMapper(i -> BodyPartType.values()[i], BodyPartType::ordinal)).persistent(BodyPartType.CODEC));

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
        return COMPONENTS.registerComponentType(name, builder);
    }

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }
}