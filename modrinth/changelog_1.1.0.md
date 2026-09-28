<!-- Changelog de la version 1.1.0 pour Modrinth. Modrinth n'a qu'un champ : coller la partie FR ou la partie EN (ou les deux). -->

## 1.1.0 — Formes de blocs à collision exacte

### Nouveau

- **Formes des blocs de réserve** : le serveur ∞SMP peut désormais donner aux blocs créés par les joueurs une forme d'**escalier**, de **dalle** ou de **muret**, avec une **collision, un contour et un modèle exacts**. Le mod annonce la nouvelle capacité « formes », reçoit la table des formes du serveur et l'applique.
- **Vue étendue des blocs** : quand le serveur l'accorde, le mod lit les chunks sur la largeur étendue (135 723 états). Sans accord du serveur, rien ne change.
- **Adaptatif** : un client sans ∞link, ou avec une version plus ancienne, voit à la place un bloc vanilla de même forme (escalier, dalle ou muret de pierre), avec la même collision.

### Corrigé

- Les états de réserve sont acceptés dans les chunks en vue étendue ; ils restent refusés en solo et dans les mondes enregistrés.

### Prérequis et mise à jour

- Minecraft **26.3**, Fabric Loader **0.19** ou plus, **sans Fabric API**. Mod côté client uniquement ; sans effet sur les autres serveurs.
- Remplace la 1.0.0 : retire ou désactive l'ancien `sage-link-*.jar` de ton dossier `mods/`.

---

## 1.1.0 — Block shapes with exact collision

### New

- **Reserve block shapes**: the ∞SMP server can now give player-made blocks a **stair**, **slab** or **wall** shape with **exact collision, outline and model**. The mod announces the new "formes" capability, receives the server's shape table and applies it.
- **Extended block view**: when the server grants it, the mod reads chunks at the extended width (135,723 states). Without the server's consent, nothing changes.
- **Adaptive**: a client without ∞link, or with an older version, sees a vanilla block of the same shape instead (stone stairs, slab or wall), with the same collision.

### Fixed

- Reserve states are accepted in chunks in the extended view; they are still refused in singleplayer and in saved worlds.

### Requirements and updating

- Minecraft **26.3**, Fabric Loader **0.19** or later, **no Fabric API**. Client-side only; no effect on other servers.
- Replaces 1.0.0: remove or disable the old `sage-link-*.jar` from your `mods/` folder.
