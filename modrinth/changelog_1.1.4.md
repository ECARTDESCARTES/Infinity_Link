<!-- Changelog de la version 1.1.4 pour Modrinth. Modrinth n'a qu'un champ : coller la partie FR ou la partie EN (ou les deux). -->

## 1.1.4 — Scènes 3D haute définition et modèles BlockBench

### Nouveau : scènes 3D haute définition (capacité « maillages »)

- **Modèles et scènes glTF** envoyés par le serveur (plugin `modeles_3d`), de quelques triangles à plusieurs millions, dessinés **nativement** à la place de l'item d'un `item_display`.
- **Aucun lag, même sur une petite configuration** : la géométrie reste en mémoire graphique, et le fil de rendu ne fait que le culling et les appels de dessin. Réception, vérification, décodage, textures et mipmaps passent par des fils de travail.
- **Niveaux de détail automatiques** par objet, choisis selon la taille à l'écran. Un objet répété est dessiné en instances, et les zones hors champ sont écartées d'un seul test.
- **Budgets réglables** : triangles et appels par image, mémoire graphique, taille des textures, téléversement par image. Le mode **économie** est choisi automatiquement sur les petites configurations.
- **Cache disque par serveur** : à la connexion suivante, rien n'est retéléchargé.
- **Lumière, brouillard et jour/nuit vanilla** ; faces à double face et transparence (découpe et translucide).

### Nouveau : modèles BlockBench (capacité « modeles »)

- Les modèles `.bbmodel` déposés sur le serveur (plugin `modeles_bb`) s'affichent avec leurs textures, **chargés à chaud** et sans redémarrage, avec la position, l'orientation, l'échelle et l'interpolation de l'entité.

### Corrigé

- (Déjà corrigé en 1.1.3.) Sur un profil neuf, le client 26.3 plantait à l'entrée en jeu à cause des touches InfinityLink sans valeur par défaut (« key.keyboard.-1 »). Ces touches sont désormais « aucune touche », et une valeur déjà enregistrée est corrigée au démarrage.

### Réglages

`config/infinitylink.properties` : `maillages`, `modeles`, `maillages_qualite` (auto / normale / economie), `maillages_seuil_px`, `maillages_triangles`, `maillages_appels`, `maillages_vram_mb`, `maillages_texture_max`, `maillages_cache_mb`, `maillages_envoi_kio`. Quand une valeur vaut 0, c'est celle de la qualité choisie qui s'applique.

Un client sans InfinityLink, ou avec une version antérieure, voit l'objet de remplacement choisi par le serveur.

Jars : Minecraft 26.3, 26.4-snapshot-1 et 26.4-snapshot-2 (Fabric Loader 0.19.5, sans Fabric API).

---

## 1.1.4 — High-definition 3D scenes and BlockBench models

### New: high-definition 3D scenes ("maillages" capability)

- **glTF models and scenes** sent by the server (`modeles_3d` plugin), from a few triangles to several million, rendered **natively** in place of an `item_display`'s item.
- **No lag, even on low-end PCs**: geometry stays in GPU memory, and the render thread only culls and issues draw calls. Downloading, checking, decoding, textures and mipmaps all run on worker threads.
- **Automatic levels of detail** per object, picked from its size on screen. Repeated objects are drawn with instancing, and off-screen areas are skipped with a single test.
- **Adjustable budgets**: triangles and draw calls per frame, GPU memory, texture size, upload per frame. **Economy** mode is picked automatically on small machines.
- **Per-server disk cache**: nothing is downloaded again on the next connection.
- **Vanilla light, fog and day/night**, double-sided faces and transparency (cutout and translucent).

### New: BlockBench models ("modeles" capability)

- `.bbmodel` files dropped on the server (`modeles_bb` plugin) show up with their textures. They are **hot-loaded**, with no restart, and follow the entity's position, rotation, scale and interpolation.

### Fixed

- (Already fixed in 1.1.3.) On a fresh profile, the 26.3 client crashed when joining a world because of InfinityLink keys with no default ("key.keyboard.-1"). Those keys are now unbound, and a saved -1 value is repaired at startup.

### Settings

`config/infinitylink.properties`: `maillages`, `modeles`, `maillages_qualite` (auto / normale / economie), `maillages_seuil_px`, `maillages_triangles`, `maillages_appels`, `maillages_vram_mb`, `maillages_texture_max`, `maillages_cache_mb`, `maillages_envoi_kio`. A value of 0 means the quality preset applies.

Players without InfinityLink, or with an older version, see the fallback item chosen by the server.

Jars: Minecraft 26.3, 26.4-snapshot-1 and 26.4-snapshot-2 (Fabric Loader 0.19.5, no Fabric API).
