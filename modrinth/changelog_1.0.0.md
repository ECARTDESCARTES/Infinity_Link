<!-- Changelog de la version 1.0.0 pour Modrinth. Modrinth n'a qu'un champ : coller la partie FR ou la partie EN (ou les deux). -->

## 1.0.0 — Première version publique

**SAGE Link devient ∞link (Infinity_Link)**, le mod client officiel du serveur **∞SMP**.

### Nouveau dans cette version

- **Nouveau nom** : Infinity_Link (∞link). La mise à jour est transparente : l'identifiant technique `sage_link` et le fichier de réglages `config/sage_link.properties` ne changent pas.
- **Logo** du mod, visible dans le menu des mods.
- **Licence Mozilla Public License 2.0** : texte complet (`LICENSE`) et notice (`NOTICE`) inclus dans le jar.
- **Code source public** : https://github.com/ECARTDESCARTES/Infinity_Link

### Ce que contient la 1.0.0

- **Rendu lointain natif** : le terrain au-delà de ta distance d'affichage, jusqu'à 128 chunks (2 048 blocs) par défaut, avec un plafond de mémoire vidéo réglable.
- **Onglets créatifs par mod** : les objets du serveur rangés dans un onglet par mod, sur plusieurs pages.
- **Armures 3D des mods**, sur les joueurs, les supports d'armure et les mobs humanoïdes.
- **Créations des joueurs** : préchargement vérifié des contenus publiés, envoi de fichiers (`/lk envoyer`) et éditeur de textures 16×16 (`/lk editeur`).
- **HUD et touches pilotés par le serveur.**
- **Journal léger** : les avertissements des blocs de réserve sont résumés en une ligne par rechargement.

### Historique (versions internes, avant la 1.0)

- **0.3.3** : journal allégé (jusqu'à 1,7 million d'avertissements « Missing model » mesurés dans un seul journal).
- **0.3.2** : armures 3D des mods.
- **0.3.1** : préchargement des contenus, envoi `/lk envoyer`, éditeur 16×16.
- **0.3.0** : réception des contenus du serveur.

### Prérequis et mise à jour

- Minecraft **26.3**, Fabric Loader **0.19** ou plus, **sans Fabric API**. Mod côté client uniquement ; sans effet sur les autres serveurs.
- Retire ou désactive toute ancienne version (`sage-link-*.jar`) dans ton dossier `mods/` avant d'ajouter celle-ci.

---

## 1.0.0 — First public release

**SAGE Link is now ∞link (Infinity_Link)**, the official client mod of the **∞SMP** server.

### New in this version

- **New name**: Infinity_Link (∞link). Updating is seamless: the technical id `sage_link` and the settings file `config/sage_link.properties` are unchanged.
- **Mod logo**, shown in the mod menu.
- **Mozilla Public License 2.0**: full text (`LICENSE`) and notice (`NOTICE`) included in the jar.
- **Public source code**: https://github.com/ECARTDESCARTES/Infinity_Link

### What 1.0.0 includes

- **Native distant rendering**: terrain beyond your render distance, up to 128 chunks (2,048 blocks) by default, with an adjustable video-memory cap.
- **Per-mod creative tabs**: the server's items sorted into one tab per mod, across several pages.
- **3D armor from mods**, on players, armor stands and humanoid mobs.
- **Player-made content**: verified preloading of published content, file upload (`/lk envoyer`) and a 16×16 texture editor (`/lk editeur`).
- **Server-driven HUD and key bindings.**
- **Lighter log**: reserve-block warnings are summarized in one line per resource reload.

### History (internal versions, before 1.0)

- **0.3.3**: lighter log (up to 1.7 million "Missing model" warnings measured in a single log).
- **0.3.2**: 3D armor from mods.
- **0.3.1**: content preloading, `/lk envoyer` upload, 16×16 editor.
- **0.3.0**: receiving content from the server.

### Requirements and updating

- Minecraft **26.3**, Fabric Loader **0.19** or later, **no Fabric API**. Client-side only; no effect on other servers.
- Remove or disable any older version (`sage-link-*.jar`) from your `mods/` folder before adding this one.
