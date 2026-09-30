## 1.1.7 — Collisions des modèles BlockBench posés

### Nouveau : collisions des poses BlockBench (capacité « collisions »)

- Les modèles `.bbmodel` posés par le serveur (plugin `modeles_bb`) peuvent désormais être **solides**. Le serveur envoie leurs volumes physiques (canal `link/model_collisions`, format `COL1`), et le client les ajoute aux **collisions d'entités** de Minecraft (`getEntityCollisions`), que le jeu utilise pour le déplacement et l'appui. Les essais en jeu (mur, appui, marches, linteau) ont été faits ou refaits avec la 1.1.8.
- Les volumes sont des **boîtes alignées sur les axes, calculées par le serveur** : un volume par cube du modèle. Les éléments en maillage libre n'ont pas de collision. Le client se contente de placer ces boîtes à la position de l'entité, à chaque tick. Tant que l'entité n'est pas chargée dans le client, elles restent à la position indiquée dans l'envoi.
- **Index par colonnes de 16×16 blocs** : une recherche de collision ne consulte que les poses des colonnes qu'elle traverse.
- Un nouvel envoi pour la même pose **remplace** ses anciens volumes, ce qui permet au serveur de mettre à jour une pose après la modification de son modèle. Un envoi sans boîte **retire** les volumes de la pose.
- Les volumes sont aussi **retirés** quand l'entité disparaît du client, quelle qu'en soit la raison (y compris un déchargement par distance), et à la déconnexion. Ils ne reviennent que si le serveur les renvoie, par exemple quand l'entité est de nouveau chargée.
- Les entités en mode sans physique (`noPhysics`, par exemple le spectateur) ne sont pas bloquées.
- La capacité `collisions` est annoncée en même temps que `modeles` et n'est active que si le serveur l'accepte. Il n'y a pas de nouveau réglage : `modeles=false` coupe aussi les collisions.

### Sécurité

- Chaque envoi est vérifié avant usage : identifiant positif, origine finie (x et z bornés à ±30 000 000), coordonnées locales bornées à ±16 384, au plus **32 768 boîtes par pose**, boîtes non vides, longueur exacte. Un envoi mal formé est ignoré et journalisé (le journal ne garde que les 20 premiers messages).
- Dépasser **1 000 000 de boîtes dans le client**, ou une pose dont l'emprise dépasse 16 384 colonnes, coupe la connexion avec le message « InfinityLink : volumes physiques invalides ».

### Problème connu

- Au contact direct d'un modèle, une face seulement adjacente pouvait être comptée comme un obstacle (marge de 1e-6 ajoutée à la zone testée). Le joueur pouvait alors passer à tort en posture rampante, et le serveur le replaçait. **Corrigé en 1.1.8 : préférer la 1.1.8.**

### Compatibilité

- Les collisions demandent un serveur LACONIA et un plugin `modeles_bb` qui envoient ces volumes. Sans eux, les modèles ne sont pas solides.
- Avec un client plus ancien, le client ne prévoit pas ces volumes. Le serveur contrôle quand même les déplacements et renvoie le joueur en arrière s'il traverse un modèle.
- Côté serveur, le plugin `modeles_bb` donne aux éléments inclinés leur enveloppe rectangulaire, et ne produit pas de volume pour les éléments en maillage libre. Les blocs du terrain ne changent pas.

### Défauts connus

Cette version a été construite à partir d'une autre copie des sources que la 1.1.4 publiée. Deux correctifs de la 1.1.4 n'y figurent pas :
- une ressource 3D refusée quand la réception est saturée n'est plus redemandée automatiquement : une partie d'une scène ou d'un modèle peut rester absente jusqu'à la révision suivante ou une reconnexion ;
- un fichier illisible dans le cache disque des scènes fait ignorer tout le cache pour la session (les ressources sont alors retéléchargées).

La description du mod (`fabric.mod.json`) ne mentionne plus les scènes 3D et les modèles BlockBench ; ces fonctions sont bien présentes.

Jars : Minecraft 26.3 et 26.4-snapshot-2 (Fabric Loader 0.19.0 ou plus, sans Fabric API). **Pas de jar 26.4-snapshot-1 pour cette version.**

| Jar | SHA-256 |
|---|---|
| `infinitylink-1.1.7+26.3.jar` | `8c169c14d3fe9eb18d15e6999e1d0ae62280bc8da068a1dfea55e9da47489004` |
| `infinitylink-1.1.7+26.4-snapshot-2.jar` | `2c6f4647d3d5704fe23895e2760d91136ba20bb69d13327e58eaf68c2997167f` |

Binaires seulement : les sources exactes de ce build n'ont pas été conservées. Le code joint à cette release (tag) est celui de la 1.1.8 (branche `sources-1.1.8`), qui contient ces fonctions.

---

## 1.1.7 — Collisions for placed BlockBench models

### New: placed BlockBench model collisions ("collisions" capability)

- `.bbmodel` models placed by the server (`modeles_bb` plugin) can now be **solid**. The server sends their physical volumes (`link/model_collisions` channel, `COL1` format), and the client adds them to Minecraft's **entity collisions** (`getEntityCollisions`), which the game uses for movement and support. In-game tests (wall, standing on, steps, lintel) were done or redone with 1.1.8.
- Volumes are **axis-aligned boxes computed by the server**: one volume per model cube. Free-form mesh elements have no collision. The client only places these boxes at the entity's position, every tick. Until the entity is loaded on the client, they stay at the position given in the message.
- **Index by 16×16-block columns**: a collision lookup only checks the placements in the columns it crosses.
- A new message for the same placement **replaces** its old volumes, so the server can update a placement after its model changes. A message with no boxes **removes** the placement's volumes.
- Volumes are also **removed** when the entity leaves the client, for any reason (including unloading by distance), and on disconnect. They only come back if the server sends them again, for example when the entity is loaded again.
- Entities without physics (`noPhysics`, such as spectators) are not blocked.
- The `collisions` capability is announced together with `modeles` and only turns on if the server accepts it. There is no new setting: `modeles=false` also turns collisions off.

### Safety

- Every message is checked before use: positive id, finite origin (x and z bounded to ±30,000,000), local coordinates bounded to ±16,384, at most **32,768 boxes per placement**, no empty boxes, exact length. A malformed message is ignored and logged (the log keeps only the first 20 messages).
- Going over **1,000,000 boxes in the client**, or a placement spanning more than 16,384 columns, disconnects with "InfinityLink : volumes physiques invalides".

### Known issue

- Right next to a model, a merely adjacent face could count as an obstacle (1e-6 margin added to the tested area). Players could then switch to the crawling pose by mistake, and the server would pull them back. **Fixed in 1.1.8: use 1.1.8 instead.**

### Compatibility

- Collisions need a LACONIA server and a `modeles_bb` plugin that send these volumes. Without them, models are not solid.
- With an older client, the client does not predict these volumes. The server still checks movement and pulls the player back if they pass through a model.
- On the server side, the `modeles_bb` plugin gives tilted elements their rectangular bounding box and produces no volume for free-form mesh elements. Terrain blocks are unchanged.

### Known issues

This version was built from a different copy of the sources than the published 1.1.4. Two 1.1.4 fixes are missing:
- a 3D resource refused while reception is saturated is no longer requested again automatically: part of a scene or model can stay missing until the next revision or a reconnection;
- an unreadable file in the scene disk cache makes the whole cache ignored for the session (resources are downloaded again).

The mod description (`fabric.mod.json`) no longer mentions 3D scenes and BlockBench models; these features are still there.

Jars: Minecraft 26.3 and 26.4-snapshot-2 (Fabric Loader 0.19.0 or later, no Fabric API). **No 26.4-snapshot-1 jar for this version.**

| Jar | SHA-256 |
|---|---|
| `infinitylink-1.1.7+26.3.jar` | `8c169c14d3fe9eb18d15e6999e1d0ae62280bc8da068a1dfea55e9da47489004` |
| `infinitylink-1.1.7+26.4-snapshot-2.jar` | `2c6f4647d3d5704fe23895e2760d91136ba20bb69d13327e58eaf68c2997167f` |

Binaries only: the exact sources of this build were not kept. The source code attached to this release (tag) is the 1.1.8 code (`sources-1.1.8` branch), which contains these features.
