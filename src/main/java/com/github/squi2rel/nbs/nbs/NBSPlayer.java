package com.github.squi2rel.nbs.nbs;

import com.github.squi2rel.nbs.NBSJukebox;
import com.github.squi2rel.nbs.util.NMSUtil;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public class NBSPlayer implements Runnable {
    private final NBSNote[] notes;
    private final NBSLayer[] layers;
    private final Instrument[] instruments;
    private final double ticksPerSecond;
    private final int ticks;
    private volatile boolean playing = false;
    private volatile boolean paused = false;
    private volatile long pauseStart;
    private final AtomicBoolean skipping = new AtomicBoolean(false);
    private final AtomicLong startTime = new AtomicLong();
    private Thread thread;

    private List<Player> players;
    private Location location;

    public NBSPlayer(NBSSong song) {
        ticksPerSecond = song.tempo();

        ArrayList<NBSNote> list = new ArrayList<>(song.notes());
        list.sort(Comparator.comparingInt(a -> a.tick));
        notes = list.toArray(new NBSNote[0]);
        layers = song.layers().toArray(new NBSLayer[0]);
        instruments = song.instruments().stream().map(i -> {
            Sound sound = NBSJukebox.soundsMap.get(i.soundFile);
            if (sound == null) return null;
            return new Instrument(sound, i.soundKey);
        }).toArray(Instrument[]::new);

        int maxTick = 0;
        for (NBSNote raw : notes) {
            int tick = raw.tick;
            maxTick = Math.max(maxTick, tick);
        }
        ticks = maxTick + 20;
    }

    @Override
    public void run() {
        startTime.set(System.nanoTime());

        long tickDurationNanos = (long) (1_000_000_000.0 / ticksPerSecond);

        int tick = 0;
        int idx = 0;
        int size = notes.length;

        while (playing) {
            while (paused) {
                LockSupport.park();
                if (!playing) return;
            }

            if (skipping.compareAndSet(true, false)) {
                tick = (int) ((System.nanoTime() - startTime.get()) / tickDurationNanos);
                int low = 0;
                int high = size;

                while (low < high) {
                    int mid = (low + high) >>> 1;
                    if (notes[mid].tick < tick) {
                        low = mid + 1;
                    } else {
                        high = mid;
                    }
                }
                idx = low;
            }

            long expectedTime = startTime.get() + tick * tickDurationNanos;
            long now = System.nanoTime();
            long sleepNanos = expectedTime - now;

            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }

            while (idx < size && notes[idx].tick == tick) {
                NBSNote n = notes[idx++];
                NBSLayer l = layers[n.layer];
                int length = NMSUtil.INSTRUMENTS.length;
                if (n.instrument >= length) {
                    Instrument i = instruments[n.instrument - length];
                    if (i == null) continue;
                    float key = n.key + i.key - 45 + (n.pitch / 100f);
                    while (key < 33) key += 12;
                    while (key > 57) key -= 12;
                    NMSUtil.sendNotePacket(
                            players,
                            i.sound,
                            location,
                            (n.velocity * l.volume) / 2500f,
                            (float) Math.pow(2, (key - 45) / 12f)
                    );
                } else {
                    float key = n.key + n.pitch / 100f;
                    while (key < 33) key += 12;
                    while (key > 57) key -= 12;
                    NMSUtil.sendNotePacket(
                            players,
                            n.instrument,
                            location,
                            (n.velocity * l.volume) / 2500f,
                            (float) Math.pow(2, (key - 45) / 12f)
                    );
                }
            }

            if (++tick > ticks) break;
        }
        playing = false;
    }

    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public void setProgress(long millis) {
        if (paused) {
            startTime.set(pauseStart - millis * 1000000);
        } else {
            startTime.set(System.nanoTime() - millis * 1000000);
        }
        skipping.set(true);
    }

    public void start() {
        if (playing) return;
        playing = true;
        thread = new Thread(this, "NBS-Player-Thread");
        thread.start();
    }

    public void stop() {
        playing = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    public boolean isPlaying() {
        return playing;
    }

    public void pause() {
        pauseStart = System.nanoTime();
        paused = true;
    }

    public void resume() {
        startTime.getAndAdd(System.nanoTime() - pauseStart);
        paused = false;
        if (thread != null) LockSupport.unpark(thread);
    }

    public boolean isPaused() {
        return paused;
    }

    public long getProgress() {
        if (!playing) return 0;
        if (paused) return pauseStart - startTime.get();
        return (System.nanoTime() - startTime.get()) / 1000000;
    }

    private static class Instrument {
        public Sound sound;
        public byte key;

        public Instrument(Sound sound, byte key) {
            this.sound = sound;
            this.key = key;
        }
    }
}
