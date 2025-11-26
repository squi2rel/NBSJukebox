package com.github.squi2rel.nbs.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.CraftSound;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.LockSupport;

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
        new Thread(() -> {
            long start = System.currentTimeMillis();
            long sleep = 33333332L;
            long next = System.nanoTime() + sleep;
            while (true) {
                long now = System.currentTimeMillis();
                if (now - start > 3000) break;
                float f = 60 - (now - start) / 50f;
                int i = (int) (f * 255 / 20f);
                if (i > 255) i = 255;
                int color = Mth.hsvToArgb(f / 50f, 0.7f, 0.6f, Math.max(0, i));
                ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(
                        Component.translatable("record.nowPlaying", Component.literal(name)).withColor(color)
                );
                for (ServerPlayer player : players) player.connection.send(packet);
                long currentTimeNs = System.nanoTime();
                long waitNs = next - currentTimeNs;
                if (waitNs > 0) {
                    LockSupport.parkNanos(waitNs);
                }
                next += sleep;
            }
        }).start();
    }

    public static void sendNotePacket(List<Player> players, int id, Location pos, float volume, float pitch) {
        if (players == null || players.isEmpty() || id >= INSTRUMENTS.length) return;
        ClientboundSoundPacket packet = new ClientboundSoundPacket(
                INSTRUMENTS[id], SoundSource.RECORDS, pos.getX(), pos.getY(), pos.getZ(), volume, pitch, RANDOM.nextLong()
        );
        for (Player player : players) ((CraftPlayer) player).getHandle().connection.send(packet);
    }

    public static void sendNotePacket(List<Player> players, Sound sound, Location pos, float volume, float pitch) {
        if (players == null || players.isEmpty()) return;
        ClientboundSoundPacket packet = new ClientboundSoundPacket(
                CraftSound.bukkitToMinecraftHolder(sound), SoundSource.RECORDS, pos.getX(), pos.getY(), pos.getZ(), volume, pitch, RANDOM.nextLong()
        );
        for (Player player : players) ((CraftPlayer) player).getHandle().connection.send(packet);
    }
}
