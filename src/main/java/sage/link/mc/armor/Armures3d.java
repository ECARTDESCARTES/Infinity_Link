package sage.link.mc.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import sage.link.core.armor.Armures3dSpec;
import sage.link.core.armor.Armures3dSpec.Entree;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Couche « armures 3D » (SAGE Link 0.3.2) : dessine sur les humanoïdes (joueurs, soi en vue externe, armures sur
 * support, mobs humanoïdes) les pièces d'armure marquées custom_data {"sage_armure":"&lt;pack&gt;/&lt;nom&gt;"} et connues
 * du manifeste assets/sage/armures_3d.json, partie par partie, selon le repère (f) du contrat (voir
 * {@link Armures3dSpec}). Appelée par HumanoidArmorLayerMixin (rendu vanilla de l'équipement masqué pour cet
 * emplacement) et CustomHeadLayerMixin (pas de double dessin du casque par la voie vanilla « head »).
 * Toute exception coupe la couche jusqu'au prochain monde (journal une fois) ; jamais de plantage du jeu.
 */
public final class Armures3d {
    private Armures3d() {}

    static final String CONFIG = "sage_link.properties";
    static final Identifier MANIFESTE = Identifier.fromNamespaceAndPath("sage", "armures_3d.json");

    private static boolean option = true;
    private static volatile int generation = 1;          // incrémentée à chaque rechargement des ressources
    private static int lue = 0;                          // génération du manifeste en cache
    private static Map<String, Entree> manifeste = Map.of();
    private static final Map<String, ItemStack> PILES = new HashMap<>();
    private static final Map<CustomData, String> MARQUES = new IdentityHashMap<>();
    private static boolean coupe;
    private static Object mondeCoupe;
    /** Compteurs de l'oracle : pièces dessinées, parties dessinées, entrées du manifeste rejetées. */
    public static long pieces, parties;
    public static int rejets;

    // ---------------------------------------------------------------- option
    public static void loadOptions() {
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve(CONFIG);
            Properties pr = new Properties();
            if (Files.isRegularFile(p)) try (InputStream in = Files.newInputStream(p)) { pr.load(in); }
            String v = pr.getProperty("armures_3d");
            if (v == null) {
                Files.createDirectories(p.getParent());
                Files.writeString(p, "\n# armures_3d : true/false, rendu des armures a modele 3D sur les joueurs (SAGE Link 0.3.2)\narmures_3d=true\n",
                        StandardCharsets.ISO_8859_1, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            option = v == null || !"false".equalsIgnoreCase(v.trim());
            System.err.println("[sage_link] armures 3d " + (option ? "actives" : "coupees par l'option") + " (" + p + ")");
        } catch (Throwable t) {
            System.err.println("[sage_link] option armures_3d illisible (actives par defaut) : " + t);
        }
    }

    /** ReloadableResourceManagerMixin : les packs changent, le manifeste sera relu au prochain dessin. */
    public static void rechargement() { generation++; }

    // ---------------------------------------------------------------- lecture
    /** Entrée du manifeste pour la pile portée à cet emplacement, ou null (vanilla inchangé). */
    public static Entree entree(ItemStack pile, EquipmentSlot slot) {
        if (!option || pile == null || pile.isEmpty() || !actif()) return null;
        try {
            CustomData cd = pile.get(DataComponents.CUSTOM_DATA);
            if (cd == null) return null;
            String id = MARQUES.get(cd);
            if (id == null && !MARQUES.containsKey(cd)) {
                if (MARQUES.size() > 512) MARQUES.clear();
                id = Armures3dSpec.id(cd.copyTag().getStringOr("sage_armure", ""));
                MARQUES.put(cd, id);
            }
            if (id == null) return null;
            Entree e = manifeste().get(id);
            if (e == null || !e.emplacement().equals(Armures3dSpec.emplacement(slot.name()))) return null;
            return e;
        } catch (Throwable t) {
            couper("lecture", t);
            return null;
        }
    }

    private static boolean actif() {
        if (!coupe) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != mondeCoupe) { coupe = false; return true; } // nouveau monde : nouvel essai
        return false;
    }

    private static Map<String, Entree> manifeste() throws Exception {
        int g = generation;
        if (g == lue) return manifeste;
        lue = g;
        PILES.clear();
        MARQUES.clear();
        int[] rj = {0};
        List<Map<String, Entree>> pile = new ArrayList<>();
        for (Resource r : Minecraft.getInstance().getResourceManager().getResourceStack(MANIFESTE)) {
            try (InputStream in = r.open()) {
                byte[] b = in.readNBytes(Armures3dSpec.MAX_OCTETS + 1);
                pile.add(Armures3dSpec.manifeste(new String(b, StandardCharsets.UTF_8), rj));
            } catch (Exception e) {
                rj[0]++;
                System.err.println("[sage_link] armures 3d : manifeste ignore (" + r.sourcePackId() + ") : " + e);
            }
        }
        manifeste = Armures3dSpec.fusion(pile);
        rejets = rj[0];
        System.err.println("[sage_link] armures 3d : " + manifeste.size() + " armure(s) au manifeste, " + rejets + " rejet(s)");
        return manifeste;
    }

    // ---------------------------------------------------------------- dessin
    /** Dessine chaque partie de l'armure 3D selon le repère (f). Retourne false si rien n'a pu être dessiné. */
    public static boolean dessiner(HumanoidModel<?> modele, Entree e, PoseStack ps, SubmitNodeCollector out,
                                   int lumiere, HumanoidRenderState etat) {
        try {
            Minecraft mc = Minecraft.getInstance();
            for (String p : e.parties()) {
                ModelPart part = partie(modele, p);
                if (part == null || !part.visible) continue;
                ps.pushPose();
                try {
                    modele.root().translateAndRotate(ps);
                    part.translateAndRotate(ps);
                    // (f) scale(1, -1, -1) ; translate(-0.5, -0.5, -0.5) est appliqué par ItemTransform.apply en contexte NONE
                    ps.scale(1.0F, -1.0F, -1.0F);
                    ItemStackRenderState rs = new ItemStackRenderState();
                    mc.getItemModelResolver().updateForTopItem(rs, pile(e, p), ItemDisplayContext.NONE, mc.level, null, 0);
                    rs.submit(ps, out, lumiere, OverlayTexture.NO_OVERLAY, etat.outlineColor);
                    parties++;
                } finally {
                    ps.popPose();
                }
            }
            pieces++;
            return true;
        } catch (Throwable t) {
            couper("dessin", t);
            return false;
        }
    }

    static ModelPart partie(HumanoidModel<?> m, String p) {
        return switch (p) {
            case "head" -> m.head;
            case "body" -> m.body;
            case "right_arm" -> m.rightArm;
            case "left_arm" -> m.leftArm;
            case "right_leg" -> m.rightLeg;
            case "left_leg" -> m.leftLeg;
            default -> null;
        };
    }

    private static ItemStack pile(Entree e, String p) {
        String k = e.modele(p);
        ItemStack s = PILES.get(k);
        if (s == null) {
            s = new ItemStack(Items.STICK);
            s.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("sage", k));
            PILES.put(k, s);
        }
        return s;
    }

    private static void couper(String ou, Throwable t) {
        if (!coupe) System.err.println("[sage_link] armures 3d coupees jusqu'au prochain monde (" + ou + ") : " + t);
        coupe = true;
        mondeCoupe = Minecraft.getInstance().level;
    }
}
