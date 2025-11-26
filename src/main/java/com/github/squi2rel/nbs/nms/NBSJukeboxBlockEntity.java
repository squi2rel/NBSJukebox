package com.github.squi2rel.nbs.nms;

import com.github.squi2rel.nbs.NBSJukebox;
import com.github.squi2rel.nbs.nbs.NBSSong;
import com.github.squi2rel.nbs.util.NMSUtil;
import com.github.squi2rel.nbs.util.ReflectUtil;
import com.github.squi2rel.nbs.util.VField;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.gameevent.GameEvent;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.github.squi2rel.nbs.NBSJukebox.ID;
import static org.bukkit.craftbukkit.inventory.CraftItemStack.PDC_CUSTOM_DATA_KEY;

public class NBSJukeboxBlockEntity extends JukeboxBlockEntity {
    private static final VField<JukeboxBlockEntity, ItemStack> ITEM;

    static {
        ITEM = ReflectUtil.getHandle(JukeboxBlockEntity.class, ItemStack.class);
    }

    private final JukeboxBlockEntity original;
    private final NBSJukeboxSongPlayer player;

    public NBSJukeboxBlockEntity(JukeboxBlockEntity original) {
        super(original.getBlockPos(), original.getBlockState());
        this.original = original;
        ReflectUtil.set(this, JukeboxSongPlayer.class, player = new NBSJukeboxSongPlayer(this::onSongChanged, getBlockPos()));
        ITEM.set(this, ITEM.get(original));
        persistentDataContainer = original.persistentDataContainer;
    }

    public JukeboxBlockEntity getOriginal() {
        return original;
    }

    @Override
    public int getComparatorOutput() {
        if (player != null && player.isPlaying()) return 15;
        return super.getComparatorOutput();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return (stack.has(DataComponents.JUKEBOX_PLAYABLE)
                || stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(PDC_CUSTOM_DATA_KEY).contains(ID))
                && this.getItem(slot).isEmpty();
    }

    @Override
    public void setTheItem(@NotNull ItemStack stack) {
        String songName = getNBSSongName(stack);
        if (songName == null) {
            super.setTheItem(stack);
            return;
        }
        ITEM.set(this, stack);
        NBSSong song = NBSJukebox.registry.get(songName);
        boolean flag = !ITEM.get(this).isEmpty();
        ITEM.set(this, stack);
        notifyItemChangedInJukebox(flag);
        if (flag) {
            String name = song.fileName();
            NMSUtil.sendNowPlayingPacket(level, getBlockPos(), name.substring(0, name.length() - 4));
            playNBS(song);
        } else {
            player.stop(Objects.requireNonNull(level), this.getBlockState());
        }
    }

    private void playNBS(NBSSong song) {
        String name = song.fileName();
        player.playNBS(level, name.substring(0, name.length() - 4));
    }

    private static String getNBSSongName(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag().getCompound(PDC_CUSTOM_DATA_KEY);
        return tag.contains(ID) ? tag.getString(ID) : null;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (player.isPlayingNBS()) {
            tag.putLong(TICKS_SINCE_SONG_STARTED_TAG_ID, player.getTicksSinceSongStarted());
            persistentDataContainer.put(NBSJukebox.PLAYTIME, LongTag.valueOf(player.getProgress()));
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        onLoad();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        player.stopNBS();
    }

    public void onLoad() {
        NBSSong song = NBSJukebox.registry.get(getNBSSongName(ITEM.get(this)));
        if (song != null) {
            playNBS(song);
            Long time = persistentDataContainer.get(NBSJukebox.PLAYTIME_KEY, PersistentDataType.LONG);
            if (time != null) player.setProgress(time);
        }
    }

    private void notifyItemChangedInJukebox(boolean hasRecord) {
        if (level != null && level.getBlockState(getBlockPos()) == getBlockState()) {
            level.setBlock(getBlockPos(), getBlockState().setValue(JukeboxBlock.HAS_RECORD, hasRecord), 2);
            level.gameEvent(GameEvent.BLOCK_CHANGE, getBlockPos(), GameEvent.Context.of(this.getBlockState()));
        }
    }
}
