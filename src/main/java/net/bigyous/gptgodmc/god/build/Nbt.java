package net.bigyous.gptgodmc.god.build;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Minimal NBT reader (enough for schematics). Compounds become Maps, lists become Lists. */
public final class Nbt {
    private static final int MAX_DEPTH = 64;

    private Nbt() {
    }

    /** Reads a (possibly gzipped) NBT file and returns its root compound. */
    public static Map<String, Object> read(InputStream raw) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(raw);
        buffered.mark(2);
        int b1 = buffered.read();
        int b2 = buffered.read();
        buffered.reset();
        InputStream in = (b1 == 0x1f && b2 == 0x8b) ? new GZIPInputStream(buffered) : buffered;
        DataInputStream data = new DataInputStream(in);
        int type = data.readByte();
        if (type != 10) {
            throw new IOException("Not an NBT compound (tag type " + type + ")");
        }
        data.readUTF();
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) payload(data, 10, 0);
        return root;
    }

    private static Object payload(DataInputStream in, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nested too deeply");
        }
        switch (type) {
            case 1:
                return in.readByte();
            case 2:
                return in.readShort();
            case 3:
                return in.readInt();
            case 4:
                return in.readLong();
            case 5:
                return in.readFloat();
            case 6:
                return in.readDouble();
            case 7: {
                int len = checkedLength(in.readInt());
                byte[] b = new byte[len];
                in.readFully(b);
                return b;
            }
            case 8:
                return in.readUTF();
            case 9: {
                int elementType = in.readByte();
                int len = checkedLength(in.readInt());
                List<Object> list = new ArrayList<>(Math.min(len, 4096));
                for (int i = 0; i < len; i++) {
                    list.add(payload(in, elementType, depth + 1));
                }
                return list;
            }
            case 10: {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int t = in.readByte();
                    if (t == 0) {
                        return map;
                    }
                    String name = in.readUTF();
                    map.put(name, payload(in, t, depth + 1));
                }
            }
            case 11: {
                int len = checkedLength(in.readInt());
                int[] a = new int[len];
                for (int i = 0; i < len; i++) a[i] = in.readInt();
                return a;
            }
            case 12: {
                int len = checkedLength(in.readInt());
                long[] a = new long[len];
                for (int i = 0; i < len; i++) a[i] = in.readLong();
                return a;
            }
            default:
                throw new IOException("Unknown NBT tag type " + type);
        }
    }

    private static int checkedLength(int len) throws IOException {
        if (len < 0 || len > 64_000_000) {
            throw new IOException("Implausible NBT array length " + len);
        }
        return len;
    }

    public static int number(Object o) {
        if (o instanceof Short s) return s & 0xFFFF;
        if (o instanceof Number n) return n.intValue();
        throw new IllegalArgumentException("Expected a number, got " + o);
    }
}
