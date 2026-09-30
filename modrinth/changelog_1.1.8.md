# InfinityLink 1.1.8 — Contact physique corrigé / Collision contact fix

## Français

- Correction des requêtes de collision : une forme seulement adjacente n’est plus ajoutée comme chevauchement.
- Le joueur reste debout contre un modèle lorsqu’il a la place ; suppression des faux passages en posture rampante et des rappels serveur associés.
- Deux contrôles de régression dans le banc utilisant les vraies classes Minecraft : contact adjacent libre et volume réellement traversé détecté.
- Inclut l’onglet BBmodel, les 165 miniatures et les collisions des versions 1.1.5 à 1.1.7.

Builds locaux : Minecraft 26.3, 26.4-snapshot-1 et 26.4-snapshot-2, Fabric Loader 0.19.5, sans Fabric API. Serveur et plugin compatibles nécessaires aux collisions. Les éléments inclinés gardent une collision par enveloppe rectangulaire.

Tests en jeu sur Monde Plat, client 26.4-snapshot-2 : mur, appui, rotation/échelle, passage libre, marches, mise à jour à chaud, retrait et contrôle serveur. Aucun quartier construit dans cette mise à jour.

### Défauts connus

Cette version a été construite à partir d'une autre copie des sources que la 1.1.4 publiée. Deux correctifs de la 1.1.4 n'y figurent pas :
- une ressource 3D refusée quand la réception est saturée n'est plus redemandée automatiquement : une partie d'une scène ou d'un modèle peut rester absente jusqu'à la révision suivante ou une reconnexion ;
- un fichier illisible dans le cache disque des scènes fait ignorer tout le cache pour la session (les ressources sont alors retéléchargées).

La description du mod (`fabric.mod.json`) ne mentionne plus les scènes 3D et les modèles BlockBench ; ces fonctions sont bien présentes.

### Sources

Le code de cette release est publié tel quel sur la branche `sources-1.1.8` (tag `v1.1.8`) : recompilé, il redonne ces jars à l'identique (730 entrées sur 730 pour 26.3, 727 sur 727 pour chaque snapshot, hors manifeste). La branche `main` reste en 1.1.4 jusqu'à une version qui réintègre les deux correctifs ci-dessus.

| Jar | Minecraft | SHA-256 |
|---|---|---|
| `infinitylink-1.1.8+26.3.jar` | 26.3 | `37e1746621153d857798df1da4e5f4ec9aa4496d2726510c87b46d13133f05a9` |
| `infinitylink-1.1.8+26.4-snapshot-1.jar` | 26.4-snapshot-1 | `4f15d9bb77c72baa880cbdff14547e8f02e9b2388a9321b2d43147319f29bcbf` |
| `infinitylink-1.1.8+26.4-snapshot-2.jar` | 26.4-snapshot-2 | `376f7f22c4e4cdc060e93a80cba6f053f92fc394e719ec14561927019bba2a6e` |


---

## English

- Collision queries no longer treat merely adjacent shapes as overlaps.
- Players stay standing beside models when there is enough room, fixing false crawling poses and the associated server corrections.
- Two regression checks use the real Minecraft classes: adjacent contact stays clear and an intersected volume is detected.
- Includes the BBmodel creative tab, 165 previews and placed-model physics from versions 1.1.5–1.1.7.

Local builds: Minecraft 26.3, 26.4-snapshot-1 and 26.4-snapshot-2, Fabric Loader 0.19.5, no Fabric API. Collisions require the compatible server and plugin. Tilted pieces still use individual bounding-box collisions.

In-game checks on Monde Plat with 26.4-snapshot-2 cover walls, support, rotation/scale, open passages, steps, hot reload, removal and server validation. This update does not construct a district.

### Known issues

This version was built from a different copy of the sources than the published 1.1.4. Two 1.1.4 fixes are missing:
- a 3D resource refused while reception is saturated is no longer requested again automatically: part of a scene or model can stay missing until the next revision or a reconnection;
- an unreadable file in the scene disk cache makes the whole cache ignored for the session (resources are downloaded again).

The mod description (`fabric.mod.json`) no longer mentions 3D scenes and BlockBench models; these features are still there.

### Sources

The code of this release is published as is on the `sources-1.1.8` branch (tag `v1.1.8`): rebuilt, it gives back these jars identically (730 of 730 entries for 26.3, 727 of 727 for each snapshot, manifest aside). The `main` branch stays at 1.1.4 until a version brings back the two fixes above.

SHA-256 values are in the table above.
