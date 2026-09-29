<!-- Changelog de la version 1.1.1 pour Modrinth. Modrinth n'a qu'un champ : coller la partie FR ou la partie EN (ou les deux). -->

## 1.1.1 — InfinityLink

### Nouveau nom

- **Infinity_Link (∞link) devient InfinityLink.** L'identifiant du mod passe de `sage_link` à `infinitylink`, comme le nom du fichier : `infinitylink-1.1.1+26.3.jar`.
- **Mise à jour sans perte** : au premier lancement, InfinityLink copie `config/sage_link.properties` en `config/infinitylink.properties` (et `sage_link_blocs_essai.properties` en `infinitylink_blocs_essai.properties`), puis déplace le cache `.minecraft/sage_link/cache` vers `.minecraft/infinitylink/cache`. Rien n'est supprimé, et un fichier déjà présent sous le nouveau nom n'est jamais écrasé.
- **Ancien jar** : le mod déclare remplacer `sage_link`. Si l'ancien `sage-link-*.jar` est resté dans `mods/`, Fabric l'écarte et charge InfinityLink. Tu peux le retirer.
- **Options de lancement** : les options Java s'écrivent désormais `-Dinfinitylink.<nom>` (par exemple `-Dinfinitylink.blocks=off`). Les anciennes (`-Dsage.link.<nom>`, `-Dsage.blocs.essai`) restent lues.

### Inchangé

- Le protocole ne change pas (canaux `sage:link/*`, version 1). InfinityLink 1.1.1 fonctionne avec les mêmes serveurs que la 1.1.0, et un serveur ∞SMP accepte toujours les clients 1.1.0.
- Mêmes fonctions que la 1.1.0 : rendu lointain, onglets créatifs par mod, armures 3D, créations des joueurs, formes des blocs de réserve, HUD et touches.

### Prérequis

- Minecraft **26.3**, Fabric Loader **0.19** ou plus, **sans Fabric API**. Mod côté client uniquement ; sans effet sur les autres serveurs.

---

## 1.1.1 — InfinityLink

### New name

- **Infinity_Link (∞link) is now InfinityLink.** The mod id changes from `sage_link` to `infinitylink`, and so does the file name: `infinitylink-1.1.1+26.3.jar`.
- **Lossless update**: on first launch, InfinityLink copies `config/sage_link.properties` to `config/infinitylink.properties` (and `sage_link_blocs_essai.properties` to `infinitylink_blocs_essai.properties`), then moves the cache `.minecraft/sage_link/cache` to `.minecraft/infinitylink/cache`. Nothing is deleted, and a file that already exists under the new name is never overwritten.
- **Old jar**: the mod declares that it replaces `sage_link`. If the old `sage-link-*.jar` is still in `mods/`, Fabric skips it and loads InfinityLink. You can remove it.
- **Launch options**: Java options are now written `-Dinfinitylink.<name>` (for example `-Dinfinitylink.blocks=off`). The old ones (`-Dsage.link.<name>`, `-Dsage.blocs.essai`) are still read.

### Unchanged

- The protocol is unchanged (`sage:link/*` channels, version 1). InfinityLink 1.1.1 works with the same servers as 1.1.0, and an ∞SMP server still accepts 1.1.0 clients.
- Same features as 1.1.0: distant rendering, per-mod creative tabs, 3D armor, player-made content, reserve block shapes, HUD and keys.

### Requirements

- Minecraft **26.3**, Fabric Loader **0.19** or later, **no Fabric API**. Client-side only; no effect on other servers.
