package com.maxwell.cyber_ware_port.common.entity;

import com.maxwell.cyber_ware_port.common.entity.monster.cyberwither.CyberWitherBoss;
import com.maxwell.cyber_ware_port.init.ModEntities;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;

/**
 * 实体属性注册。不要恢复 @Mod.EventBusSubscriber:自动扫描在部分构建产物中
 * 会漏掉本类(debug.log 实证 16/19),现由主类构造器 addListener 显式注册。
 */
public class EntitiesAttributeEvents {
    public static void entityAttributeEvent(EntityAttributeCreationEvent event) {
        event.put(ModEntities.CYBER_ZOMBIE.get(), Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE)
                .build());
        event.put(ModEntities.CYBER_SKELETON.get(), AbstractSkeleton.createAttributes()
                .add(Attributes.MAX_HEALTH, 25.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .build());
        event.put(ModEntities.CYBER_CREEPER.get(), Creeper.createAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .build());
        event.put(ModEntities.CYBER_WITHER.get(), CyberWitherBoss.createAttributes().build());
        event.put(ModEntities.CYBER_WITHER_SKELETON.get(), AbstractSkeleton.createAttributes()
                .add(Attributes.MAX_HEALTH, 50.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .build());

    }

}
