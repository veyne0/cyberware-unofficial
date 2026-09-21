package com.maxwell.cyber_ware_port.common.block.robosurgeon;

import com.maxwell.cyber_ware_port.api.event.CyberwareSurgeryEvent;
import com.maxwell.cyber_ware_port.api.json.CyberwareAPI;
import com.maxwell.cyber_ware_port.common.block.robosurgeon.surgeon.MobDismemberManager;
import com.maxwell.cyber_ware_port.common.block.robosurgeon.surgeon.SurgeryManager;
import com.maxwell.cyber_ware_port.common.block.robosurgeon.surgeon.SurgerySyncHelper;
import com.maxwell.cyber_ware_port.common.block.surgerychamber.SurgeryChamberBlock;
import com.maxwell.cyber_ware_port.common.block.surgerychamber.SurgeryChamberBlockEntity;
import com.maxwell.cyber_ware_port.common.capability.CyberwareCapabilityProvider;
import com.maxwell.cyber_ware_port.common.capability.CyberwareUserData;
import com.maxwell.cyber_ware_port.common.container.RobosurgeonMenu;
import com.maxwell.cyber_ware_port.common.item.base.CyberwareSlotType;
import com.maxwell.cyber_ware_port.common.item.base.ICyberware;
import com.maxwell.cyber_ware_port.common.network.SyncSurgeryProgressPacket;
import com.maxwell.cyber_ware_port.common.util.MobSurfaceParts;
import com.maxwell.cyber_ware_port.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RobosurgeonBlockEntity extends BlockEntity implements MenuProvider {
    public static final int TOTAL_SLOTS = BodyRegionEnum.getTotalSlots();
    public static final int SLOTS_PER_PART = BodyRegionEnum.SLOTS_PER_PART;
    public static final int SLOT_EYES = BodyRegionEnum.EYES.getStartSlot();
    public static final int SLOT_BRAIN = BodyRegionEnum.BRAIN.getStartSlot();
    public static final int SLOT_HEART = BodyRegionEnum.HEART.getStartSlot();
    public static final int SLOT_LUNGS = BodyRegionEnum.LUNGS.getStartSlot();
    public static final int SLOT_STOMACH = BodyRegionEnum.STOMACH.getStartSlot();
    public static final int SLOT_SKIN = BodyRegionEnum.SKIN.getStartSlot();
    public static final int SLOT_MUSCLE = BodyRegionEnum.MUSCLE.getStartSlot();
    public static final int SLOT_BONES = BodyRegionEnum.BONES.getStartSlot();
    public static final int SLOT_ARMS = BodyRegionEnum.ARMS.getStartSlot();
    public static final int SLOT_HANDS = BodyRegionEnum.HANDS.getStartSlot();
    public static final int SLOT_LEGS = BodyRegionEnum.LEGS.getStartSlot();
    public static final int SLOT_BOOTS = BodyRegionEnum.BOOTS.getStartSlot();
    public static final int SLOT_HEAD = BodyRegionEnum.HEAD.getStartSlot();
    public static final int SLOT_TORSO = BodyRegionEnum.TORSO.getStartSlot();
    private final ItemStackHandler itemHandler = createItemHandler();
    private final ContainerData data;
    private int progress = 0;
    private int maxProgress = 100;

    public RobosurgeonBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.ROBO_SURGEON.get(), pPos, pBlockState);
        this.data = createContainerData();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RobosurgeonBlockEntity entity) {
        if (level.isClientSide) return;
        BlockPos chamberPos = entity.findChamberPos();
        if (chamberPos == null) {
            entity.resetProgress();
            return;
        }
        BlockEntity be = level.getBlockEntity(chamberPos);
        if (!(be instanceof SurgeryChamberBlockEntity chamber)) {
            entity.resetProgress();
            return;
        }
        LivingEntity patient = entity.findPatient(chamberPos);
        if (patient instanceof ServerPlayer serverPlayer) {
            if (chamber.isOpen()) {
                if (entity.progress > 0) {
                    entity.resetProgress();
                    syncProgress(entity, serverPlayer);
                }
                return;
            }
            if (entity.progress == 0) {
                entity.maxProgress = 100;
            }
            if (entity.needsSurgery(serverPlayer) && entity.checkRequirements(serverPlayer)) {
                entity.progress++;
                setChanged(level, pos, state);
                syncProgress(entity, serverPlayer);
                if (entity.progress % 20 == 0) {
                    serverPlayer.hurt(level.damageSources().magic(), 1.0f);
                    level.playSound(null, chamberPos, SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.5f, 1.0f);
                    if (entity.progress % 40 == 0) {
                        level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.3F, 1.5F);
                        if (entity.progress % 80 == 0) {
                            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 0.2F, 0.8F);
                        }
                    }
                }
                if (entity.progress >= entity.maxProgress) {
                    entity.performSurgery(serverPlayer);
                    level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 2.0F);
                    level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.5F, 1.0F);
                    entity.resetProgress();
                    syncProgress(entity, serverPlayer);
                    chamber.setDoorState(true);
                }
            } else if (entity.progress > 0) {
                entity.resetProgress();
                syncProgress(entity, serverPlayer);
                chamber.setDoorState(true);
            }
            return;
        }

        // 生物拆解分支:活体生物进舱,拆完变成部位物品存入舱输出栏
        if (chamber.isOpen()) {
            if (entity.progress > 0) {
                entity.resetProgress();
                entity.releaseMob(chamberPos);
            }
            return;
        }
        if (patient != null && MobSurfaceParts.canDismember(patient.getType())) {
            entity.tickMobDismemberment(level, pos, state, chamber, patient, chamberPos);
        } else if (entity.progress > 0) {
            entity.resetProgress();
            entity.releaseMob(chamberPos);
        }
    }

    /** 拆解一个生物比给玩家装义体慢一倍 */
    private static final int DISMEMBER_MAX_PROGRESS = 200;

    private void tickMobDismemberment(Level level, BlockPos pos, BlockState state,
                                      SurgeryChamberBlockEntity chamber, LivingEntity mob, BlockPos chamberPos) {
        List<ItemStack> drops = MobSurfaceParts.createDrops(mob.getType());
        if (!MobDismemberManager.canFit(chamber.getOutputHandler(), drops)) {
            // 输出栏装不下,中止拆解并把生物放出来
            if (this.progress > 0) {
                this.resetProgress();
                this.releaseMob(chamberPos);
            }
            return;
        }
        // 冻结并保护生物,防止拆解过程中乱动或被打死
        mob.setInvulnerable(true);
        if (mob instanceof Mob m) {
            m.setNoAi(true);
            m.getNavigation().stop();
        }
        mob.setDeltaMovement(Vec3.ZERO);
        if (this.progress == 0) {
            this.maxProgress = DISMEMBER_MAX_PROGRESS;
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.4F, 1.2F);
        }
        this.progress++;
        setChanged(level, pos, state);
        if (this.progress % 40 == 0) {
            level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.3F, 1.2F);
        }
        if (this.progress >= this.maxProgress) {
            MobDismemberManager.execute(chamber, mob);
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 2.0F);
            this.resetProgress();
            chamber.setDoorState(true);
        }
    }

    /** 释放舱内被冻结的生物(拆解中止时调用) */
    private void releaseMob(BlockPos chamberPos) {
        if (level == null) return;
        AABB box = new AABB(chamberPos).deflate(0.3, 0.1, 0.3).inflate(0, 0.9, 0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e instanceof ServerPlayer) continue;
            e.setInvulnerable(false);
            if (e instanceof Mob m) {
                m.setNoAi(false);
            }
        }
    }

    private static void syncProgress(RobosurgeonBlockEntity entity, @Nullable ServerPlayer player) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new SyncSurgeryProgressPacket(entity.progress, entity.maxProgress));
        }
    }

    public void performSurgery(ServerPlayer player) {
        if (!checkRequirements(player)) return;
        CyberwareSurgeryEvent.Pre preEvent = new CyberwareSurgeryEvent.Pre(player, this);
        NeoForge.EVENT_BUS.post(preEvent);
        CyberwareUserData userData = player.getData(CyberwareCapabilityProvider.CYBERWARE_DATA.get());
        SurgeryManager.execute(player, this.itemHandler, userData.getInstalledCyberware());
        userData.recalculateCapacity(player);
        userData.syncToClient(player);
        this.populateGhostItems(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.IRON_GOLEM_HURT, SoundSource.PLAYERS, 1.0f, 1.0f);
        NeoForge.EVENT_BUS.post(new CyberwareSurgeryEvent.Post(player, this));
    }

    public void populateGhostItems(ServerPlayer player) {
        CyberwareUserData userData = player.getData(CyberwareCapabilityProvider.CYBERWARE_DATA.get());
        if (SurgerySyncHelper.updateGhosts(userData.getInstalledCyberware(), this.itemHandler)) {
            this.setChanged();
            if (this.level != null) {
                this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    private boolean checkRequirements(ServerPlayer player) {
        CyberwareUserData data = player.getData(CyberwareCapabilityProvider.CYBERWARE_DATA.get());
        ItemStackHandler playerBody = data.getInstalledCyberware();
        List<ItemStack> futureBody = new ArrayList<>();
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            ItemStack table = itemHandler.getStackInSlot(i);
            ItemStack finalStack = SurgeryManager.isGhost(table) ? playerBody.getStackInSlot(i) : table;
            if (!finalStack.isEmpty()) {
                futureBody.add(finalStack);
            }
        }
        for (ItemStack stack : futureBody) {
            ICyberware cw = getCyber(stack);
            if (cw == null) continue;
            // 所有生物部位共用同一个物品 id(mob_part),必须按"同物品同组件"计数,
            // 否则任意两个不同部位都会被当成同一种义体超限,手术永远无法开始
            int sameCount = 0;
            for (ItemStack s : futureBody) {
                if (ItemStack.isSameItemSameComponents(s, stack)) sameCount += s.getCount();
            }
            if (sameCount > cw.getMaxInstallAmount(stack)) return false;
            for (net.minecraft.world.item.Item req : cw.getPrerequisites(stack)) {
                if (futureBody.stream().noneMatch(s -> s.is(req))) return false;
            }
        }
        return true;
    }

    private boolean isGhost(ItemStack stack) {
        return SurgeryManager.isGhost(stack);
    }

    private ICyberware getCyber(ItemStack stack) {
        return CyberwareAPI.getCyberware(stack);
    }

    private ItemStackHandler createItemHandler() {
        return new ItemStackHandler(TOTAL_SLOTS) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                ICyberware cw = CyberwareAPI.getCyberware(stack);
                if (cw == null) return false;
                return CyberwareSlotType.fromId(cw.getSlot(stack)) == CyberwareSlotType.fromId(slot);
            }
        };
    }

    private boolean needsSurgery(ServerPlayer player) {
        CyberwareUserData data = player.getData(CyberwareCapabilityProvider.CYBERWARE_DATA.get());
        ItemStackHandler playerBody = data.getInstalledCyberware();
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            ItemStack table = itemHandler.getStackInSlot(i);
            if (SurgeryManager.isGhost(table)) continue;
            if (!ItemStack.matches(table, playerBody.getStackInSlot(i))) return true;
        }
        return false;
    }

    private BlockPos findChamberPos() {
        if (level == null) return null;
        BlockPos below = worldPosition.below();
        BlockState state = level.getBlockState(below);
        if (state.getBlock() instanceof SurgeryChamberBlock) {
            return state.getValue(SurgeryChamberBlock.HALF) == DoubleBlockHalf.UPPER ? below.below() : below;
        }
        return null;
    }

    private LivingEntity findPatient(BlockPos chamberPos) {
        if (level == null) return null;
        AABB box = new AABB(chamberPos).deflate(0.3, 0.1, 0.3).inflate(0, 0.9, 0);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, box);
        // 玩家优先(玩家手术语义不变),否则取离舱中心最近的生物
        Vec3 center = Vec3.atCenterOf(chamberPos);
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (LivingEntity e : entities) {
            if (e instanceof ServerPlayer) return e;
            double d = e.distanceToSqr(center);
            if (d < nearestDist) {
                nearestDist = d;
                nearest = e;
            }
        }
        return nearest;
    }

    private void resetProgress() {
        this.progress = 0;
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("container.cyber_ware_port.robosurgeon");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player p) {
        if (p instanceof ServerPlayer sp) populateGhostItems(sp);
        return new RobosurgeonMenu(id, inv, this, data);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put("inventory", itemHandler.serializeNBT(provider));
        tag.putInt("progress", progress);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        itemHandler.deserializeNBT(provider, tag.getCompound("inventory"));
        progress = tag.getInt("progress");
    }

    public void drops() {
        if (level == null) return;
        SimpleContainer inv = new SimpleContainer(TOTAL_SLOTS);
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty() && !isGhost(stack)) inv.setItem(i, stack);
        }
        Containers.dropContents(level, worldPosition, inv);
        // 机器被拆时释放舱内可能被冻结的生物
        BlockPos chamberPos = findChamberPos();
        if (chamberPos != null) releaseMob(chamberPos);
    }

    private ContainerData createContainerData() {
        return new ContainerData() {
            @Override
            public int get(int i) {
                return i == 0 ? progress : maxProgress;
            }

            @Override
            public void set(int i, int v) {
                if (i == 0) progress = v;
                else maxProgress = v;
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }
}