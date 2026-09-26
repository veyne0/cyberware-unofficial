package com.maxwell.cyber_ware_port;

import com.maxwell.cyber_ware_port.client.GuardianLaserClientRenderer;
import com.maxwell.cyber_ware_port.client.screen.roboSurgeon.SurgeryOverlay;
import com.maxwell.cyber_ware_port.common.entity.EntitiesAttributeEvents;
import com.maxwell.cyber_ware_port.common.network.A_PacketHandler;
import com.maxwell.cyber_ware_port.config.CyberwareConfig;
import com.maxwell.cyber_ware_port.init.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@SuppressWarnings("removal")
@Mod(CyberWare.MODID)
public class CyberWare {
    public static final String MODID = "cyberware_unofficial";
    public static final Logger LOGGER = LogUtils.getLogger();
    public CyberWare(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModEntities.register(modEventBus);
        // 显式注册实体属性事件:debug.log 显示 @EventBusSubscriber 自动扫描未把
        // EntitiesAttributeEvents 注入 MOD 总线(16/19 类),导致 cyber_skeleton
        // 构造时 DefaultAttributes 无 supplier 直接 NPE;显式 addListener 不依赖扫描
        modEventBus.addListener(EntitiesAttributeEvents::entityAttributeEvent);
        ModLootModifiers.register(modEventBus);
        A_PacketHandler.register();
        // 同理显式注册两个被自动扫描漏掉的 client 侧 FORGE 总线监听
        // (仅客户端分支内引用 client 类,专用服务器不会加载它们)
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.addListener(GuardianLaserClientRenderer::onRenderLevel);
            MinecraftForge.EVENT_BUS.addListener(SurgeryOverlay::onRenderOverlay);
        }
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CyberwareConfig.COMMON_CONFIG, "cyberware-common.toml");

    }

}
