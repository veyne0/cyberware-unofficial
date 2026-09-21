package com.maxwell.cyber_ware_port.common.block.surgerychamber;

import com.maxwell.cyber_ware_port.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SurgeryChamberBlockEntity extends BlockEntity {
    /** 拆解产物输出栏(最多同时装下蜘蛛的 9 个部位) */
    public static final int OUTPUT_SLOTS = 9;
    public float animationProgress = 0;
    public float prevAnimationProgress = 0;
    private final ItemStackHandler outputHandler = new ItemStackHandler(OUTPUT_SLOTS);

    public SurgeryChamberBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.SURGERY_CHAMBER.get(), pPos, pBlockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SurgeryChamberBlockEntity entity) {
        entity.prevAnimationProgress = entity.animationProgress;
        boolean isOpen = state.getValue(SurgeryChamberBlock.OPEN);
        float target = isOpen ? 1.0F : 0.0F;
        if (entity.animationProgress < target) {
            entity.animationProgress = Math.min(entity.animationProgress + 0.1F, target);
        } else if (entity.animationProgress > target) {
            entity.animationProgress = Math.max(entity.animationProgress - 0.1F, target);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null) {
            boolean isOpen = this.getBlockState().getValue(SurgeryChamberBlock.OPEN);
            this.animationProgress = isOpen ? 1.0F : 0.0F;
            this.prevAnimationProgress = this.animationProgress;
        }
    }

    public boolean isOpen() {
        return this.level != null && this.getBlockState().getValue(SurgeryChamberBlock.OPEN);
    }

    public ItemStackHandler getOutputHandler() {
        return this.outputHandler;
    }

    public void setDoorState(boolean open) {
        setDoorState(open, null);
    }

    public void setDoorState(boolean open, @Nullable Player player) {
        if (this.level == null || this.level.isClientSide) return;
        BlockState currentState = this.getBlockState();
        if (currentState.getValue(SurgeryChamberBlock.OPEN) != open) {
            this.level.setBlock(this.worldPosition, currentState.setValue(SurgeryChamberBlock.OPEN, open), 3);
            this.level.playSound(null, this.worldPosition, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.5F, 1.2F);
            this.level.playSound(null, this.worldPosition, open ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5F, 1.2F);
            BlockPos abovePos = this.worldPosition.above();
            BlockState aboveState = this.level.getBlockState(abovePos);
            if (aboveState.is(currentState.getBlock())) {
                this.level.setBlock(abovePos, aboveState.setValue(SurgeryChamberBlock.OPEN, open), 3);
            }
            // 开门时把拆解产物直接交给开门的玩家,拿不下的掉在舱边
            if (open) {
                distributeOutput(player);
            }
            setChanged();
        }
    }

    public void toggleDoor(@Nullable Player player) {
        setDoorState(!isOpen(), player);
    }

    /** 分发输出栏内容:优先塞进玩家背包,剩余掉落在舱门附近 */
    private void distributeOutput(@Nullable Player player) {
        boolean any = false;
        for (int i = 0; i < this.outputHandler.getSlots(); i++) {
            ItemStack stack = this.outputHandler.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            any = true;
            if (player != null && player.getInventory().add(stack.copy())) {
                this.outputHandler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        if (!any) return;
        for (int i = 0; i < this.outputHandler.getSlots(); i++) {
            ItemStack stack = this.outputHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(this.level, this.worldPosition.getX() + 0.5,
                        this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5, stack);
                this.outputHandler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /** 手术舱被破坏时掉落输出栏内容 */
    public void drops() {
        if (this.level == null) return;
        for (int i = 0; i < this.outputHandler.getSlots(); i++) {
            ItemStack stack = this.outputHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(this.level, this.worldPosition.getX() + 0.5,
                        this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5, stack);
                this.outputHandler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.saveAdditional(pTag, pRegistries);
        pTag.putFloat("AnimationProgress", this.animationProgress);
        pTag.put("Output", this.outputHandler.serializeNBT(pRegistries));
    }

    @Override
    protected void loadAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.loadAdditional(pTag, pRegistries);
        if (pTag.contains("AnimationProgress")) {
            this.animationProgress = pTag.getFloat("AnimationProgress");
            this.prevAnimationProgress = this.animationProgress;
        }
        if (pTag.contains("Output")) {
            this.outputHandler.deserializeNBT(pRegistries, pTag.getCompound("Output"));
        }
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider pRegistries) {
        return saveWithoutMetadata(pRegistries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
