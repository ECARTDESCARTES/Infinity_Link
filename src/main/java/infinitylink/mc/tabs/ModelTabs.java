package infinitylink.mc.tabs;

import infinitylink.mc.Bridge;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Catalogue créatif des modèles validés par le décodeur, propre à la connexion courante.
 * Les jetons vanilla appellent le plugin serveur existant au clic droit. */
public final class ModelTabs {
    private ModelTabs() {}
    private static Object owner;
    private static long revision = -1, lastUse;
    private static Set<String> ids = Set.of();
    private static boolean enabled;

    public static void refresh(Object session, long rev, Map<String, String> names, boolean active) {
        if (session == owner && rev == revision && active == enabled) return;
        owner = session;
        revision = rev;
        enabled = active;
        Map<String, String> sorted = active ? new TreeMap<>(names) : Map.of();
        ids = Set.copyOf(sorted.keySet());
        List<ItemStack> stacks = new ArrayList<>(sorted.size());
        sorted.forEach((id, name) -> stacks.add(stack(id, name)));
        SageTabs.setModelStacks(stacks);
    }

    public static ItemStack stack(String id, String name) {
        ItemStack s = new ItemStack(Items.PAPER);
        var preview = ModelPreviews.itemModel(id);
        if (preview != null) s.set(DataComponents.ITEM_MODEL, preview);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(name.isBlank() ? id : name));
        s.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("BBmodel · " + id),
                Component.literal("Clic droit : placer à vos pieds"),
                Component.literal("Créatif · échelle 1"))));
        CompoundTag tag = new CompoundTag();
        // Un jeton client n'est pas un contenu du catalogue serveur (custom_data.sage serait refusé).
        tag.putString("infinitylink_bbmodel", id);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return s;
    }

    public static String modelId(ItemStack s) {
        if (s == null || s.isEmpty()) return null;
        CustomData data = s.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String id = data.copyTag().getStringOr("infinitylink_bbmodel", "");
        return id.matches("[a-z0-9_]{1,64}") ? id : null;
    }

    /** null laisse le clic vanilla intact ; un jeton reconnu consomme l'interaction. */
    public static InteractionResult use(Player player, InteractionHand hand) {
        if (!enabled || !Bridge.STATE.modelesOn() || player == null || !player.isCreative()) return null;
        String id = modelId(player.getItemInHand(hand));
        if (id == null || !ids.contains(id)) return null;
        var conn = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (conn == null) return null;
        long now = System.nanoTime();
        if (now - lastUse >= 250_000_000L) {
            lastUse = now;
            conn.sendCommand("modele pose " + id + " 1");
        }
        return InteractionResult.SUCCESS;
    }

    public static void clear() {
        owner = null;
        revision = -1;
        lastUse = 0;
        ids = Set.of();
        enabled = false;
        SageTabs.setModelStacks(List.of());
    }
}
