package infinitylink.mc.tabs;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Aperçus transparents rendus depuis les fichiers BBmodel, intégrés au mod.
 * Le pack est branché sur le rechargement vanilla : pas de Fabric API nécessaire. */
public final class ModelPreviews {
    private ModelPreviews() {}
    public static final String PACK_ID = "infinitylink_bbmodel_previews";
    private static final Set<String> IDS = index();

    private static Set<String> index() {
        try (var in = ModelPreviews.class.getResourceAsStream("/infinitylink/bbmodel-previews.txt")) {
            if (in == null) return Set.of();
            return Set.copyOf(new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(id -> id.matches("[a-z0-9_]{1,64}")).toList());
        } catch (IOException e) { throw new IllegalStateException("Index des aperçus BBmodel illisible", e); }
    }

    public static int count() { return IDS.size(); }

    public static Identifier itemModel(String id) {
        return IDS.contains(id) ? Identifier.fromNamespaceAndPath("infinitylink", "bbmodel/" + id) : null;
    }

    public static List<PackResources> packs(List<PackResources> original) {
        if (IDS.isEmpty()) return original;
        if (original.stream().anyMatch(pack -> PACK_ID.equals(pack.packId()))) return original;
        var container = FabricLoader.getInstance().getModContainer("infinitylink");
        if (container.isEmpty()) return original;
        List<PackResources> packs = new ArrayList<>(original.size() + 1);
        var info = new PackLocationInfo(PACK_ID, Component.literal("InfinityLink · aperçus BBmodel"), PackSource.BUILT_IN, Optional.empty());
        for (var root : container.get().getRootPaths()) packs.add(new PathPackResources(info, root));
        // Les packs choisis par le joueur et le serveur conservent leur priorité.
        packs.addAll(original);
        return packs;
    }
}
