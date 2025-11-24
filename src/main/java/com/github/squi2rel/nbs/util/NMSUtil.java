package com.github.squi2rel.nbs.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;

public class NMSUtil {
    private static final Random RANDOM = new Random();

    @SuppressWarnings("unchecked")
    public static final Holder<SoundEvent>[] INSTRUMENTS = new Holder[]{
            SoundEvents.NOTE_BLOCK_HARP,
            SoundEvents.NOTE_BLOCK_BASS,
            SoundEvents.NOTE_BLOCK_BASEDRUM,
            SoundEvents.NOTE_BLOCK_SNARE,
            SoundEvents.NOTE_BLOCK_HAT,
            SoundEvents.NOTE_BLOCK_GUITAR,
            SoundEvents.NOTE_BLOCK_FLUTE,
            SoundEvents.NOTE_BLOCK_BELL,
            SoundEvents.NOTE_BLOCK_CHIME,
            SoundEvents.NOTE_BLOCK_XYLOPHONE,
            SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE,
            SoundEvents.NOTE_BLOCK_COW_BELL,
            SoundEvents.NOTE_BLOCK_DIDGERIDOO,
            SoundEvents.NOTE_BLOCK_BIT,
            SoundEvents.NOTE_BLOCK_BANJO,
            SoundEvents.NOTE_BLOCK_PLING
    };

    public static List<ServerPlayer> getNearbyPlayers(LevelAccessor level, double x, double y, double z, double r) {
        AABB box = new AABB(x - r, y - r, z - r, x + r, y + r, z + r);
        return level.getEntitiesOfClass(ServerPlayer.class, box);
    }

    public static void sendNowPlayingPacket(LevelAccessor level, BlockPos pos, String name) {
        List<ServerPlayer> players = NMSUtil.getNearbyPlayers(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 64);
        ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(
                Component.translatable("record.nowPlaying", Component.literal(name)).withStyle(ChatFormatting.LIGHT_PURPLE)
        );
        for (ServerPlayer player : players) player.connection.send(packet);
    }

    public static void sendNotePacket(List<Player> players, int id, Location pos, float volume, float pitch) {
        if (players == null || players.isEmpty() || id >= INSTRUMENTS.length) return;
        ClientboundSoundPacket packet = new ClientboundSoundPacket(
                INSTRUMENTS[id], SoundSource.RECORDS, pos.getX(), pos.getY(), pos.getZ(), volume, pitch, RANDOM.nextLong()
        );
        for (Player player : players) ((CraftPlayer) player).getHandle().connection.send(packet);
    }
}
