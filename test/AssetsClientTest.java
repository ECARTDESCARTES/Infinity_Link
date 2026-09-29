import infinitylink.core.AssetsMsg;
import infinitylink.core.LinkState;
import infinitylink.core.Msg;
import infinitylink.core.assets.AssetsClient;
import infinitylink.core.assets.PixelCanvas;
import infinitylink.core.assets.Png;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

/** Java pur (lot Ph4-PA-L9a) : chemins du dossier de dépôt, découpe d'un envoi (≤ 30 Kio par part), préchargement des
 *  époques (SHA-1, taille, cache, codes de assets_ready), table SGB2 par blocks_delta, commandes /lk, encodeur PNG
 *  (relu par javax.imageio), modèle de l'éditeur 16×16, branchement du Sink dans LinkState. */
public final class AssetsClientTest {
    static int ok, ko;

    static void check(String name, boolean cond, String detail) {
        if (cond) { ok++; System.out.println("OK   " + name); }
        else { ko++; System.out.println("FAIL " + name + " : " + detail); }
    }

    static boolean refusedName(Path depot, String name) {
        try { AssetsClient.depotFile(depot, name); return false; } catch (IllegalArgumentException e) { return true; }
    }

    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("sage-assets-test");
        Path depot = tmp.resolve("laconia").resolve("depot");
        Files.createDirectories(depot);

        // ---- chemins
        check("depot : nom simple accepte", AssetsClient.depotFile(depot, "lampe.png").equals(depot.toAbsolutePath().normalize().resolve("lampe.png")), "");
        for (String bad : new String[] {"", "..", ".", "../x.png", "..\\x.png", "a/b.png", "a\\b.png", "C:x.png", "C:\\x.png", "/etc/passwd",
                "\\\\srv\\x", ".cache", "x.png.", " x", "CON.png", "nul", "a..png", "x\u0000.png", "x\n.png", "a*b", "x".repeat(65)})
            check("depot : refuse « " + bad.replace("\u0000", "\\0").replace("\n", "\\n") + " »", refusedName(depot, bad), "accepte");

        byte[] big = new byte[70 * 1024 + 17];
        for (int i = 0; i < big.length; i++) big[i] = (byte) (i * 31 + 7);
        Files.write(depot.resolve("son.ogg"), big);
        Files.write(depot.resolve("vide.bin"), new byte[0]);
        Files.write(tmp.resolve("laconia").resolve("secret.txt"), "hors depot".getBytes(StandardCharsets.UTF_8));
        check("depot : fichier voisin hors depot inaccessible", throwsIo(() -> AssetsClient.readDepot(depot, "../secret.txt"))
            || refusedName(depot, "../secret.txt"), "lu");
        check("depot : fichier absent refuse", throwsIo(() -> AssetsClient.readDepot(depot, "absent.png")), "");
        check("depot : fichier vide refuse", throwsIo(() -> AssetsClient.readDepot(depot, "vide.bin")), "");
        Files.write(depot.resolve("trop.bin"), new byte[AssetsMsg.MAX_UPLOAD + 1]);
        check("depot : fichier > 2 Mio refuse", throwsIo(() -> AssetsClient.readDepot(depot, "trop.bin")), "");
        try {
            Files.createSymbolicLink(depot.resolve("lien.txt"), tmp.resolve("laconia").resolve("secret.txt"));
            check("depot : lien symbolique refuse", throwsIo(() -> AssetsClient.readDepot(depot, "lien.txt")), "lu");
        } catch (IOException | UnsupportedOperationException e) {
            System.out.println("SKIP depot : lien symbolique (creation impossible ici : " + e.getClass().getSimpleName() + ") — non compte comme preuve");
        }

        // ---- découpe d'un envoi
        AssetsClient.Upload u = AssetsClient.prepareUpload(depot, "son.ogg");
        AssetsClient.Upload u2 = AssetsClient.prepareUpload(depot, "son.ogg");
        check("envoi : numeros croissants", u2.id() == u.id() + 1 && u.id() >= 1, u.id() + " " + u2.id());
        check("envoi : 3 parts pour 70 Kio", u.parts() == 3 && u.bodies().size() == 5, "parts " + u.parts());
        boolean small = true;
        for (byte[] b : u.bodies()) small &= b.length <= 32_767 - 64;
        check("envoi : chaque corps tient sous la limite vanilla de 32 Kio", small, "");
        AssetsMsg.Begin begin = AssetsMsg.Begin.decode(u.bodies().get(0));
        check("envoi : upload_begin (nom, taille, parts)", begin.name().equals("son.ogg") && begin.size() == big.length && begin.parts() == 3
            && begin.upload() == u.id(), begin.toString());
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        boolean order = true, bound = true;
        for (int i = 0; i < 3; i++) {
            AssetsMsg.Part p = AssetsMsg.Part.decode(u.bodies().get(1 + i));
            order &= p.index() == i && p.upload() == u.id();
            bound &= p.data().length <= AssetsMsg.PART_SIZE;
            joined.writeBytes(p.data());
        }
        check("envoi : parts dans l'ordre, ≤ 30 Kio", order && bound, "");
        check("envoi : reassemblage identique", Arrays.equals(joined.toByteArray(), big), "");
        AssetsMsg.End end = AssetsMsg.End.decode(u.bodies().get(4));
        check("envoi : upload_end porte le SHA-1 du fichier", Arrays.equals(end.sha1(), AssetsMsg.sha1(big)) && end.upload() == u.id(), "");

        // ---- préchargement
        byte[] packA = "pack A".getBytes(StandardCharsets.UTF_8), packB = "pack B".getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> net = new HashMap<>();
        net.put("http://s/p/1.zip", packA);
        net.put("http://s/p/2.zip", packB);
        AtomicInteger fetches = new AtomicInteger();
        AssetsClient.Fetcher f = (url, max) -> {
            fetches.incrementAndGet();
            byte[] b = net.get(url);
            if (b == null) throw new IOException("404");
            return b;
        };
        Path cache = tmp.resolve("infinitylink").resolve("cache");
        List<AssetsMsg.Epoch> offer = List.of(new AssetsMsg.Epoch(1, AssetsMsg.sha1(packA), packA.length, "http://s/p/1.zip"),
            new AssetsMsg.Epoch(2, AssetsMsg.sha1(packB), packB.length, "http://s/p/2.zip"));
        AssetsMsg.Ready r = AssetsClient.preload(offer, f, cache);
        check("prechargement : tout pret, code OK", r.code() == AssetsMsg.READY_OK && r.epochs().equals(List.of(1, 2)), r.toString());
        check("prechargement : cache/<sha1> ecrit", Arrays.equals(Files.readAllBytes(cache.resolve(AssetsClient.hex(AssetsMsg.sha1(packA)))), packA), "");
        int before = fetches.get();
        r = AssetsClient.preload(offer, f, cache);
        check("prechargement : cache reutilise sans telechargement", fetches.get() == before && r.code() == AssetsMsg.READY_OK, "fetch " + fetches.get());
        check("prechargement : assets_ready encode/decode", AssetsMsg.Ready.decode(r.encode()).equals(r), "");
        byte[] wrong = AssetsMsg.sha1("autre".getBytes(StandardCharsets.UTF_8));
        r = AssetsClient.preload(List.of(new AssetsMsg.Epoch(3, wrong, packA.length, "http://s/p/1.zip")), f, cache);
        check("prechargement : SHA-1 faux -> READY_HASH, rien de pret", r.code() == AssetsMsg.READY_HASH && r.epochs().isEmpty(), r.toString());
        check("prechargement : pack faux non ecrit dans le cache", !Files.exists(cache.resolve(AssetsClient.hex(wrong))), "");
        r = AssetsClient.preload(List.of(new AssetsMsg.Epoch(3, AssetsMsg.sha1(packA), packA.length + 1, "http://s/p/1.zip")), f, cache);
        check("prechargement : taille fausse -> READY_HASH", r.code() == AssetsMsg.READY_HASH, r.toString());
        r = AssetsClient.preload(List.of(offer.get(0), new AssetsMsg.Epoch(4, AssetsMsg.sha1("pack C".getBytes(StandardCharsets.UTF_8)), 6, "http://s/p/absent.zip")), f, cache);
        check("prechargement : echec reseau -> READY_DOWNLOAD, epoques bonnes gardees", r.code() == AssetsMsg.READY_DOWNLOAD && r.epochs().equals(List.of(1)), r.toString());
        r = AssetsClient.preload(List.of(new AssetsMsg.Epoch(5, AssetsMsg.sha1(packB), packB.length, "file:///etc/passwd")), f, cache);
        check("prechargement : URL non http(s) -> READY_BAD_OFFER", r.code() == AssetsMsg.READY_BAD_OFFER && r.epochs().isEmpty(), r.toString());
        check("prechargement : offre vide -> OK []", AssetsClient.preload(List.of(), f, cache).equals(new AssetsMsg.Ready(AssetsMsg.READY_OK, List.of())), "");

        // ---- table SGB2
        AssetsClient.BlocksTable t = new AssetsClient.BlocksTable();
        byte[] sgb2 = "SGB2\0\1".getBytes(StandardCharsets.ISO_8859_1);
        check("table : delta applique", t.apply(new AssetsMsg.BlocksDelta(3, AssetsMsg.sha1(packA), sgb2,
            List.of(new AssetsMsg.Case(7, "joueur:lampe"), new AssetsMsg.Case(9, "joueur:vase")))) && t.size() == 2 && "joueur:lampe".equals(t.id(7)), "");
        check("table : case liberee par id vide", t.apply(new AssetsMsg.BlocksDelta(4, AssetsMsg.sha1(packB), sgb2, List.of(new AssetsMsg.Case(9, ""))))
            && t.size() == 1 && t.id(9) == null && t.epoch() == 4 && t.packSha1().equals(AssetsClient.hex(AssetsMsg.sha1(packB))), "");
        check("table : delta d'epoque perimee ignore", !t.apply(new AssetsMsg.BlocksDelta(2, AssetsMsg.sha1(packA), sgb2,
            List.of(new AssetsMsg.Case(1, "joueur:vieux")))) && t.id(1) == null && t.epoch() == 4, "");
        check("table : en-tete SGB2 conserve", Arrays.equals(t.header(), sgb2), "");

        // ---- commandes
        check("lk : envoyer", AssetsClient.parse("lk envoyer lampe.png").equals(new AssetsClient.Command("envoyer", "lampe.png")), "");
        check("lk : editeur sans nom", AssetsClient.parse("lk editeur").equals(new AssetsClient.Command("editeur", "")), "");
        check("lk : seul -> aide", AssetsClient.parse("lk").verb().equals("aide") && AssetsClient.parse("lk zzz").verb().equals("aide"), "");
        check("lk : autres commandes passent au serveur", AssetsClient.parse("asset depot lampe block") == null
            && AssetsClient.parse("lkx envoyer a") == null && AssetsClient.parse("tp 0 0 0") == null, "");

        // ---- PNG
        int[] argb = new int[16 * 16];
        for (int i = 0; i < argb.length; i++) argb[i] = (i % 3 == 0) ? 0 : (0xFF << 24) | (i * 0x010305 & 0xFFFFFF);
        argb[5] = 0x80FF0000;
        byte[] png = Png.encode(16, 16, argb);
        check("png : signature", png[0] == (byte) 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G', "");
        check("png : CRC des blocs corrects", crcOk(png), "");
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        boolean same = img != null && img.getWidth() == 16 && img.getHeight() == 16;
        for (int y = 0; same && y < 16; y++) for (int x = 0; x < 16; x++) {
            int want = argb[y * 16 + x], got = img.getRGB(x, y);
            if (((want >>> 24) == 0 ? (got >>> 24) != 0 : got != want)) { same = false; break; }
        }
        check("png : relu par javax.imageio, pixels et alpha identiques", same, "");
        check("png : dimensions invalides refusees", throwsIae(() -> Png.encode(16, 16, new int[3])) && throwsIae(() -> Png.encode(0, 1, new int[0])), "");

        // ---- éditeur
        PixelCanvas c = new PixelCanvas();
        c.pick(14);
        check("editeur : pinceau", c.apply(3, 4) && c.get(3, 4) == PixelCanvas.PALETTE[14] && c.dirty(), "");
        c.tool(PixelCanvas.Tool.ERASER);
        check("editeur : gomme -> transparent", c.apply(3, 4) && c.get(3, 4) == 0, "");
        check("editeur : hors grille ignore", !c.apply(16, 0) && !c.apply(-1, 5), "");
        c.pick(4);
        check("editeur : choisir une couleur repasse au pinceau", c.tool() == PixelCanvas.Tool.BRUSH && c.color() == PixelCanvas.PALETTE[4], "");
        c.apply(15, 15);
        BufferedImage e = ImageIO.read(new ByteArrayInputStream(c.toPng()));
        check("editeur : PNG 16x16 enregistrable", e.getWidth() == 16 && e.getRGB(15, 15) == PixelCanvas.PALETTE[4] && (e.getRGB(0, 0) >>> 24) == 0 && !c.dirty(), "");
        Path saved = AssetsClient.depotFile(depot, "dessin.png");
        Files.write(saved, c.toPng());
        AssetsClient.Upload du = AssetsClient.prepareUpload(depot, "dessin.png");
        check("editeur : le PNG enregistre part en une seule part", du.parts() == 1 && AssetsMsg.Begin.decode(du.bodies().get(0)).name().equals("dessin.png"), "");

        // ---- Sink dans LinkState
        LinkState s = new LinkState();
        s.assetsCapable = true;
        List<String> seen = new ArrayList<>();
        s.assetsSink = new AssetsClient.Sink() {
            public void offer(List<AssetsMsg.Epoch> ep) { seen.add("offer" + ep.size()); }
            public void delta(AssetsMsg.BlocksDelta d) { seen.add("delta" + d.epoch()); }
            public void result(AssetsMsg.Result res) { seen.add("result" + res.verdict()); }
        };
        s.beginHello("26.3");
        s.onInbound(AssetsMsg.ASSETS_OFFER, AssetsMsg.encodeOffer(offer));
        check("sink : rien avant la capacite accordee", seen.isEmpty(), seen.toString());
        s.onInbound(Msg.MANIFEST, new Msg.Manifest(1, "Serveur", List.of("hud", "assets"), List.of()).encode());
        s.onInbound(AssetsMsg.ASSETS_OFFER, AssetsMsg.encodeOffer(offer));
        s.onInbound(AssetsMsg.BLOCKS_DELTA, new AssetsMsg.BlocksDelta(4, AssetsMsg.sha1(packA), sgb2, List.of()).encode());
        s.onInbound(AssetsMsg.UPLOAD_RESULT, new AssetsMsg.Result(1, AssetsMsg.REFUSED, List.of("image > 512 px")).encode());
        check("sink : offre, delta, verdict transmis", seen.equals(List.of("offer2", "delta4", "result1")), seen.toString());
        check("verdict : texte du chat", AssetsClient.describe(new AssetsMsg.Result(1, AssetsMsg.REFUSED, List.of("image > 512 px")))
            .equals("envoi 1 refuse : image > 512 px"), "");

        System.out.println("AssetsClientTest : " + ok + " OK, " + ko + " FAIL");
        if (ko > 0) System.exit(1);
    }

    interface Io { void run() throws Exception; }
    static boolean throwsIo(Io r) {
        try { r.run(); return false; } catch (IOException | IllegalArgumentException e) { return true; } catch (Exception e) { return false; }
    }
    static boolean throwsIae(Runnable r) {
        try { r.run(); return false; } catch (IllegalArgumentException e) { return true; }
    }

    static boolean crcOk(byte[] png) {
        int at = 8;
        while (at < png.length) {
            int len = ((png[at] & 0xFF) << 24) | ((png[at + 1] & 0xFF) << 16) | ((png[at + 2] & 0xFF) << 8) | (png[at + 3] & 0xFF);
            CRC32 crc = new CRC32();
            crc.update(png, at + 4, 4 + len);
            int at2 = at + 8 + len;
            int want = ((png[at2] & 0xFF) << 24) | ((png[at2 + 1] & 0xFF) << 16) | ((png[at2 + 2] & 0xFF) << 8) | (png[at2 + 3] & 0xFF);
            if ((int) crc.getValue() != want) return false;
            at = at2 + 4;
        }
        return at == png.length;
    }
}
