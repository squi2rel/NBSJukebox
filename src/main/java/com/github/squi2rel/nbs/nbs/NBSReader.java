package com.github.squi2rel.nbs.nbs;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NBSReader {
    private static Header readHeader(LEDataInput in) throws IOException {
        Header h = new Header();
        short firstTwoBytes = in.readShort();
        if (firstTwoBytes == 0) {
            h.version = in.readUnsignedByte();
            h.vanillaInstrumentCount = in.readUnsignedByte();
        }
        if (h.version >= 3) h.songLength = in.readShort();
        h.layerCount = in.readShort();
        h.name = in.readString();
        h.author = in.readString();
        h.originalAuthor = in.readString();
        h.description = in.readString();
        h.tempo = in.readShort();
        h.autoSaving = in.readUnsignedByte();
        h.autoSavingDuration = in.readUnsignedByte();
        h.timeSignature = in.readUnsignedByte();
        h.minutesSpent = in.readInt();
        h.leftClicks = in.readInt();
        h.rightClicks = in.readInt();
        h.noteBlocksAdded = in.readInt();
        h.noteBlocksRemoved = in.readInt();
        h.midiSchematic = in.readString();
        if (h.version >= 4) {
            h.loopOn = in.readUnsignedByte();
            h.maxLoopCount = in.readUnsignedByte();
            h.loopStartTick = in.readShort();
        }
        return h;
    }

    private static List<NBSNote> readNotes(LEDataInput in, Header h) throws IOException {
        List<NBSNote> notes = new ArrayList<>();
        int tick = -1;
        boolean newer = h.version >= 4;
        while (true) {
            short jumpTick = in.readShort();
            if (jumpTick == 0) break;
            tick += jumpTick;
            int layer = -1;
            while (true) {
                short jumpLayer = in.readShort();
                if (jumpLayer == 0) break;
                layer += jumpLayer;
                NBSNote n = readNote(in, tick, layer);
                if (newer) {
                    n.velocity = in.readByte();
                    n.panning = in.readByte();
                    n.pitch = in.readShort();
                }
                notes.add(n);
            }
        }
        return notes;
    }

    private static NBSNote readNote(LEDataInput in, int tick, int layer) throws IOException {
        NBSNote n = new NBSNote();
        n.tick = tick;
        n.layer = layer;
        n.instrument = in.readByte();
        n.key = in.readByte();
        return n;
    }

    private static List<NBSLayer> readLayers(LEDataInput in, Header h) throws IOException {
        int size = h.layerCount;
        int version = h.version;
        List<NBSLayer> layers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            layers.add(readLayer(in, version));
        }
        return layers;
    }

    private static NBSLayer readLayer(LEDataInput in, int version) throws IOException {
        NBSLayer l = new NBSLayer();
        l.name = in.readString();
        if (version >= 4) l.locked = in.readByte() != 0;
        l.volume = in.readByte();
        if (version >= 2) l.stereo = in.readUnsignedByte();
        return l;
    }

    private static List<NBSInstrument> readInstruments(LEDataInput in) throws IOException {
        int size = in.readUnsignedByte();
        List<NBSInstrument> instruments = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            instruments.add(readInstrument(in));
        }
        return instruments;
    }

    private static NBSInstrument readInstrument(LEDataInput in) throws IOException {
        NBSInstrument i = new NBSInstrument();
        i.instrumentName = in.readString();
        i.soundFile = in.readString();
        i.soundKey = in.readByte();
        i.pressPianoKey = in.readByte();
        return i;
    }

    public static NBSSong parse(File path) throws IOException {
        try (LEDataInput in = new LEDataInput(new FileInputStream(path))) {
            Header h = readHeader(in);
            List<NBSNote> notes = readNotes(in, h);
            List<NBSLayer> layers = readLayers(in, h);
            List<NBSInstrument> instruments = readInstruments(in);
            double tempo = h.tempo / 100.0;
            int lastTick = notes.stream().mapToInt(n -> n.tick).max().orElse(0);
            long duration = Math.round((lastTick + 1) / tempo * 1000);
            return new NBSSong(path.getName(), tempo, notes, layers, instruments, duration);
        }
    }

    public static class LEDataInput implements AutoCloseable {
        private final DataInputStream in;

        public LEDataInput(InputStream is) {
            this.in = new DataInputStream(new BufferedInputStream(is));
        }

        public byte readByte() throws IOException {
            return in.readByte();
        }

        public int readUnsignedByte() throws IOException {
            return in.readUnsignedByte();
        }

        public short readShort() throws IOException {
            int low = in.readUnsignedByte();
            int high = in.readUnsignedByte();
            return (short) ((high << 8) | low);
        }

        public int readInt() throws IOException {
            int b0 = in.readUnsignedByte();
            int b1 = in.readUnsignedByte();
            int b2 = in.readUnsignedByte();
            int b3 = in.readUnsignedByte();
            return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
        }

        public String readString() throws IOException {
            int len = readInt();
            if (len <= 0) return "";
            byte[] buf = new byte[len];
            in.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        }

        public void close() throws IOException {
            in.close();
        }
    }

    public static class Header {
        public int version;
        public int vanillaInstrumentCount;
        public short songLength;
        public short layerCount;
        public String name, author, originalAuthor, description;
        public short tempo;
        public int autoSaving;
        public int autoSavingDuration;
        public int timeSignature;
        public int minutesSpent, leftClicks, rightClicks, noteBlocksAdded, noteBlocksRemoved;
        public String midiSchematic;
        public int loopOn;
        public int maxLoopCount;
        public short loopStartTick;
    }
}
