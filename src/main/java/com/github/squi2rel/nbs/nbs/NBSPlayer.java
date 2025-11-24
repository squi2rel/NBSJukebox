package com.github.squi2rel.nbs.nbs;

import com.github.squi2rel.nbs.util.NMSUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public class NBSPlayer implements Runnable {

    private final List<NBSNote> notes;
    private final double ticksPerSecond;
    private final int ticks;
    private final AtomicBoolean playing = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean skipping = new AtomicBoolean(false);
    private final AtomicLong startTime = new AtomicLong();
    private final AtomicLong pauseStart = new AtomicLong();
    private Thread thread;

    private List<Player> players;
    private Location location;

    public NBSPlayer(NBSSong song) {
        ticksPerSecond = song.tempo();

        notes = song.notes();
        int maxTick = 0;

        for (NBSNote raw : notes) {
            if (raw.instrument >= NMSUtil.INSTRUMENTS.length) continue;
            int tick = raw.tick;
            maxTick = Math.max(maxTick, tick);
        }

        notes.sort(Comparator.comparingInt(a -> a.tick));
        ticks = maxTick + 20;
    }

    @Override
    public void run() {
        long tickDurationNanos = (long) (1_000_000_000.0 / ticksPerSecond);

        int tick = 0;
        int idx = 0;
        int size = notes.size();

        while (playing.get()) {
            while (paused.get()) {
                LockSupport.park();
                if (!playing.get()) return;
                startTime.getAndAdd(System.nanoTime() - pauseStart.get());
            }

            if (skipping.get()) {
                tick = (int) ((System.nanoTime() - startTime.get()) / tickDurationNanos);
                int low = 0;
                int high = size;

                while (low < high) {
                    int mid = (low + high) >>> 1;
                    if (notes.get(mid).tick < tick) {
                        low = mid + 1;
                    } else {
                        high = mid;
                    }
                }
                idx = low;
                skipping.set(false);
            }

            long expectedTime = startTime.get() + tick * tickDurationNanos;
            long now = System.nanoTime();
            long sleepNanos = expectedTime - now;

            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }

            while (idx < size && notes.get(idx).tick == tick) {
                NBSNote n = notes.get(idx++);
                byte key = n.key;
                while (key < 33) key += 12;
                while (key > 57) key -= 12;
                NMSUtil.sendNotePacket(players, n.instrument, location, n.velocity / 25f, (float) Math.pow(2, (key - 45) / 12f));
            }

            if (++tick > ticks) break;
        }
        playing.set(false);
    }

    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public void setProgress(long millis) {
        pauseStart.set(0);
        startTime.set(System.nanoTime() - millis * 1000000);
        skipping.set(true);
    }

    public void start() {
        if (playing.get()) return;
        startTime.set(System.nanoTime());
        playing.set(true);
        thread = new Thread(this, "NBS-Player-Thread");
        thread.start();
    }

    public void stop() {
        playing.set(false);
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    public boolean isPlaying() {
        return playing.get();
    }

    public void pause() {
        pauseStart.set(System.nanoTime());
        paused.set(true);
    }

    public void resume() {
        paused.set(false);
        if (thread != null) LockSupport.unpark(thread);
    }

    public boolean isPaused() {
        return paused.get();
    }

    public long getProgress() {
        if (!playing.get()) return 0;
        if (paused.get()) return pauseStart.get() - startTime.get();
        return (System.nanoTime() - startTime.get()) / 1000000;
    }
}
