package com.github.squi2rel.nbs.nms;

import com.github.squi2rel.nbs.NBSJukebox;
import com.github.squi2rel.nbs.nbs.NBSPlayer;
import com.github.squi2rel.nbs.nbs.NBSSong;
import com.github.squi2rel.nbs.util.NMSUtil;
import com.github.squi2rel.nbs.util.ReflectUtil;
import com.github.squi2rel.nbs.util.VField;
import com.github.squi2rel.nbs.util.VMethod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Collectors;

public class NBSJukeboxSongPlayer extends JukeboxSongPlayer {
    private static final VField<JukeboxSongPlayer, Long> STARTED;
    private static final VMethod<Void> SPAWN;

    private NBSPlayer player;
    private final OnSongChanged onSongChanged;
    private final BlockPos blockPos;

    static {
        STARTED = ReflectUtil.getHandle(JukeboxSongPlayer.class, long.class);
        SPAWN = ReflectUtil.getHandleStatic(JukeboxSongPlayer.class, void.class, LevelAccessor.class, BlockPos.class);
    }

    public NBSJukeboxSongPlayer(OnSongChanged onSongChanged, BlockPos blockPos) {
        super(onSongChanged, blockPos);
        this.onSongChanged = onSongChanged;
        this.blockPos = blockPos;
    }

    @Override
    public boolean isPlaying() {
        return player != null ? player.isPlaying() : super.isPlaying();
    }

    public boolean isPlayingNBS() {
        return player != null && player.isPlaying();
    }

    @Override
    public long getTicksSinceSongStarted() {
        return player != null ? player.getProgress() / 50 : super.getTicksSinceSongStarted();
    }

    @Override
    public void setSongWithoutPlaying(@NotNull Holder<JukeboxSong> song, long ticksSinceSongStarted) {
        super.setSongWithoutPlaying(song, ticksSinceSongStarted);
    }

    public void playNBS(LevelAccessor level, String name) {
        NBSSong nbs = NBSJukebox.registry.get(name);
        if (player != null) player.stop();
        player = new NBSPlayer(nbs);
        player.setLocation(new Location(null, blockPos.getX(), blockPos.getY(), blockPos.getZ()));
        updatePlayers(level);
        player.start();
        onSongChanged.notifyChange();
    }

    public void setProgress(long millis) {
        player.setProgress(millis);
    }

    public long getProgress() {
        return player.getProgress();
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public void stop(@NotNull LevelAccessor level, @Nullable BlockState state) {
        stopNBS();
        song = new Holder.Direct<>(null);
        super.stop(level, state);
    }

    public void stopNBS() {
        if (player != null) {
            player.stop();
            player = null;
        }
    }

    @Override
    public void tick(@NotNull LevelAccessor level, @Nullable BlockState state) {
        if (player != null) {
            if (!player.isPlaying()) {
                this.stop(level, state);
            } else {
                updatePlayers(level);

                if (STARTED.get(this) % 20L == 0L) {
                    level.gameEvent(GameEvent.JUKEBOX_PLAY, blockPos, GameEvent.Context.of(state));
                    SPAWN.invoke(level, blockPos);
                }

                STARTED.varHandle().getAndAdd(this, 1);
            }
            return;
        }
        super.tick(level, state);
    }

    private void updatePlayers(LevelAccessor level) {
        player.setPlayers(NMSUtil.getNearbyPlayers(level, blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, 64).stream()
                .map(ServerPlayer::getBukkitEntity)
                .collect(Collectors.toUnmodifiableList()));
    }
}
