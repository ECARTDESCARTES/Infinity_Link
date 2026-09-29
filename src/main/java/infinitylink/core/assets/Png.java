package infinitylink.core.assets;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** Encodeur PNG minimal en Java pur : RGBA 8 bits, non entrelacé, filtre 0 par ligne, IDAT zlib unique. */
public final class Png {
    private Png() {}

    static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** argb : w*h pixels 0xAARRGGBB, ligne par ligne depuis le haut. */
    public static byte[] encode(int w, int h, int[] argb) {
        if (w < 1 || h < 1 || w > 4096 || h > 4096 || argb.length != w * h) throw new IllegalArgumentException("dimensions " + w + "x" + h);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(SIGNATURE);
        byte[] ihdr = new byte[13];
        be32(ihdr, 0, w); be32(ihdr, 4, h);
        ihdr[8] = 8; ihdr[9] = 6; // 8 bits, RGBA ; compression, filtre, entrelacement = 0
        chunk(out, "IHDR", ihdr);
        byte[] raw = new byte[h * (1 + 4 * w)];
        int k = 0;
        for (int y = 0; y < h; y++) {
            raw[k++] = 0;
            for (int x = 0; x < w; x++) {
                int p = argb[y * w + x];
                raw[k++] = (byte) (p >>> 16); raw[k++] = (byte) (p >>> 8); raw[k++] = (byte) p; raw[k++] = (byte) (p >>> 24);
            }
        }
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!d.finished()) z.write(buf, 0, d.deflate(buf));
        d.end();
        chunk(out, "IDAT", z.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void be32(byte[] b, int at, int v) {
        b[at] = (byte) (v >>> 24); b[at + 1] = (byte) (v >>> 16); b[at + 2] = (byte) (v >>> 8); b[at + 3] = (byte) v;
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] len = new byte[4];
        be32(len, 0, data.length);
        out.writeBytes(len);
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(t);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        byte[] c = new byte[4];
        be32(c, 0, (int) crc.getValue());
        out.writeBytes(c);
    }
}
