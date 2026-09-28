package sage.link.core.assets;

import java.util.Arrays;

/** Modèle de l'éditeur 16×16 (doc 16 GUI1, doc 30 §5.3) : palette de 16 couleurs, pinceau, gomme (transparent). */
public final class PixelCanvas {
    public static final int SIZE = 16;
    /** Palette par défaut (ARGB opaque), dans l'ordre des teintes vanilla. */
    public static final int[] PALETTE = {
        0xFFFFFFFF, 0xFFF9801D, 0xFFC74EBD, 0xFF3AB3DA, 0xFFFED83D, 0xFF80C71F, 0xFFF38BAA, 0xFF474F52,
        0xFF9D9D97, 0xFF169C9C, 0xFF8932B8, 0xFF3C44AA, 0xFF835432, 0xFF5E7C16, 0xFFB02E26, 0xFF1D1D21};

    public enum Tool { BRUSH, ERASER }

    private final int[] px = new int[SIZE * SIZE];
    private int color = PALETTE[0];
    private Tool tool = Tool.BRUSH;
    private boolean dirty;

    public int color() { return color; }
    public Tool tool() { return tool; }
    public boolean dirty() { return dirty; }
    public void tool(Tool t) { tool = t; }

    /** Choisit la couleur i de la palette et repasse au pinceau. */
    public void pick(int i) {
        if (i < 0 || i >= PALETTE.length) throw new IllegalArgumentException("couleur " + i);
        color = PALETTE[i];
        tool = Tool.BRUSH;
    }

    /** Applique l'outil courant en (x, y) ; hors grille : ignoré (false). */
    public boolean apply(int x, int y) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return false;
        int v = tool == Tool.ERASER ? 0 : color;
        if (px[y * SIZE + x] != v) { px[y * SIZE + x] = v; dirty = true; }
        return true;
    }

    public int get(int x, int y) { return px[y * SIZE + x]; }
    public void clear() { Arrays.fill(px, 0); dirty = true; }

    public byte[] toPng() {
        dirty = false;
        return Png.encode(SIZE, SIZE, px.clone());
    }
}
