package infinitylink.mc.assets;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import infinitylink.core.assets.AssetsClient;
import infinitylink.core.assets.PixelCanvas;
import infinitylink.mc.Bridge;

import java.nio.file.Files;
import java.nio.file.Path;

/** Éditeur minimal 16×16 (doc 16 GUI1) : grille, palette de 16 couleurs, pinceau, gomme, enregistrer (PNG dans le
 *  dossier de dépôt), envoyer (même chemin que /lk envoyer). Clic gauche : outil courant ; clic droit : gomme. */
public final class EditorScreen extends Screen {
    private final String name;
    private final PixelCanvas canvas = new PixelCanvas();
    private int cell, gx, gy, px, py;
    private String status = "";

    public EditorScreen(String name) {
        super(Component.literal("InfinityLink : editeur 16x16"));
        this.name = name;
    }

    @Override protected void init() {
        cell = Math.max(4, Math.min(12, (height - 70) / PixelCanvas.SIZE));
        int grid = cell * PixelCanvas.SIZE;
        gx = (width - grid) / 2 - 40;
        gy = 24;
        px = gx + grid + 12;
        py = gy;
        int bx = px, by = py + 8 * 14 + 8;
        addRenderableWidget(Button.builder(Component.literal("Pinceau"), b -> canvas.tool(PixelCanvas.Tool.BRUSH)).bounds(bx, by, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Gomme"), b -> canvas.tool(PixelCanvas.Tool.ERASER)).bounds(bx, by + 20, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Enregistrer"), b -> save()).bounds(bx, by + 40, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Envoyer"), b -> { if (save()) send(); }).bounds(bx, by + 60, 80, 18).build());
    }

    private boolean save() {
        try {
            Path depot = AssetsLink.depot();
            Files.createDirectories(depot);
            Path f = AssetsClient.depotFile(depot, name);
            Files.write(f, canvas.toPng());
            status = "enregistre : laconia/depot/" + name;
            return true;
        } catch (Throwable t) {
            status = "echec : " + t.getMessage();
            Bridge.STATE.error("editeur (enregistrer)", t);
            return false;
        }
    }

    private void send() {
        try {
            AssetsLink.upload(name);
            status = "envoye ; verdict dans le chat";
        } catch (Throwable t) {
            status = "echec d'envoi : " + t.getMessage();
        }
    }

    private boolean paint(double mx, double my, boolean erase) {
        int x = (int) Math.floor((mx - gx) / cell), y = (int) Math.floor((my - gy) / cell);
        if (x < 0 || y < 0 || x >= PixelCanvas.SIZE || y >= PixelCanvas.SIZE) return false;
        PixelCanvas.Tool keep = canvas.tool();
        if (erase) canvas.tool(PixelCanvas.Tool.ERASER);
        canvas.apply(x, y);
        canvas.tool(keep);
        return true;
    }

    private boolean palette(double mx, double my) {
        int i = (int) Math.floor((mx - px) / 14), j = (int) Math.floor((my - py) / 14);
        if (mx < px || my < py || i < 0 || i > 1 || j < 0 || j > 7) return false;
        canvas.pick(j * 2 + i);
        return true;
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (palette(e.x(), e.y())) return true;
        if (paint(e.x(), e.y(), e.button() == 1)) return true;
        return super.mouseClicked(e, doubleClick);
    }

    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (paint(e.x(), e.y(), e.button() == 1)) return true;
        return super.mouseDragged(e, dx, dy);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        int grid = cell * PixelCanvas.SIZE;
        g.text(font, "laconia/depot/" + name + (canvas.dirty() ? " *" : ""), gx, 8, 0xFFFFFFFF, true);
        g.fill(gx - 1, gy - 1, gx + grid + 1, gy + grid + 1, 0xFF000000);
        for (int y = 0; y < PixelCanvas.SIZE; y++)
            for (int x = 0; x < PixelCanvas.SIZE; x++) {
                int c = canvas.get(x, y);
                if ((c >>> 24) == 0) c = ((x + y) & 1) == 0 ? 0xFF9A9A9A : 0xFF6A6A6A; // damier = transparent
                g.fill(gx + x * cell, gy + y * cell, gx + (x + 1) * cell, gy + (y + 1) * cell, c);
            }
        for (int k = 0; k < PixelCanvas.PALETTE.length; k++) {
            int x = px + (k % 2) * 14, y = py + (k / 2) * 14;
            int c = PixelCanvas.PALETTE[k];
            if (c == canvas.color() && canvas.tool() == PixelCanvas.Tool.BRUSH) g.fill(x - 1, y - 1, x + 13, y + 13, 0xFFFFFF00);
            g.fill(x, y, x + 12, y + 12, c);
        }
        g.text(font, canvas.tool() == PixelCanvas.Tool.BRUSH ? "outil : pinceau" : "outil : gomme", px + 32, py, 0xFFE0E0E0, true);
        if (!status.isEmpty()) g.text(font, status, gx, gy + grid + 6, 0xFFE0E0E0, true);
    }

    @Override public boolean isPauseScreen() { return false; }
}
