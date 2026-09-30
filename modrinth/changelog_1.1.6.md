## 1.1.6 — Miniatures des modèles BlockBench dans l'onglet créatif

### Nouveau : miniatures des modèles BBmodel

- Les objets de l'onglet créatif **Modèles BBmodel** (ajouté en 1.1.5) affichent désormais une **miniature du modèle** au lieu d'une simple feuille de papier. L'objet garde sa miniature une fois pris dans la barre rapide.
- **165 miniatures** sont intégrées au mod : des images PNG transparentes de 128 × 128 pixels (environ 1,9 Mo au total), rendues par Blockbench à partir des fichiers BBmodel du serveur, avec pour chacune un modèle d'objet Minecraft et une définition d'objet.
- Les miniatures sont **figées dans le mod** : un modèle modifié sur le serveur garde sa miniature d'origine jusqu'à la prochaine version d'InfinityLink.
- Un modèle dont la miniature n'est pas intégrée au mod garde l'**icône de repli** (la feuille de papier), plutôt qu'une texture manquante. Son nom, son identifiant et le clic droit pour le poser fonctionnent comme avant.

### Pack de ressources interne

- Les miniatures sont fournies par un pack de ressources interne, `InfinityLink · aperçus BBmodel`, **chargé automatiquement** : rien à activer dans les options de packs de ressources. Ce pack n'apparaît pas dans la liste des packs de ressources et ne peut pas y être désactivé.
- Ce pack est placé **sous tous les autres**, y compris le pack de base du jeu. Un pack du joueur ou du serveur qui fournit les mêmes chemins `infinitylink:…/bbmodel/<id>` garde donc la priorité.
- Ce chargement fonctionne **sans Fabric API**, comme le reste du mod.

### Compatibilité

Le fonctionnement de l'onglet ne change pas par rapport à la 1.1.5 : même catalogue envoyé par le serveur, même pose par clic droit à l'échelle 1 via le plugin `modeles_bb`. Aucun réglage n'a été ajouté. Un client InfinityLink 1.1.5 garde l'icône papier ; un client plus ancien ou sans InfinityLink n'a pas cet onglet.

### Défauts connus

Cette version a été construite à partir d'une autre copie des sources que la 1.1.4 publiée. Deux correctifs de la 1.1.4 n'y figurent pas :
- une ressource 3D refusée quand la réception est saturée n'est plus redemandée automatiquement : une partie d'une scène ou d'un modèle peut rester absente jusqu'à la révision suivante ou une reconnexion ;
- un fichier illisible dans le cache disque des scènes fait ignorer tout le cache pour la session (les ressources sont alors retéléchargées).

La description du mod (`fabric.mod.json`) ne mentionne plus les scènes 3D et les modèles BlockBench ; ces fonctions sont bien présentes.

Jars : Minecraft 26.3 et 26.4-snapshot-2 (Fabric Loader 0.19 ou plus récent, sans Fabric API). Il n'y a **pas de jar 26.4-snapshot-1** pour cette version. Le jar passe d'environ 0,4 Mo à environ 2,4 Mo à cause des miniatures intégrées.

| Jar | SHA-256 |
|---|---|
| `infinitylink-1.1.6+26.3.jar` | `dc2fa5fc8cb7ed2d77163393838cd4d0489ad7a40867ad25cb761b8ecaf30ecc` |
| `infinitylink-1.1.6+26.4-snapshot-2.jar` | `75503313543684cc4dd7d3bb13071d0611d51e1779b7cb35bfd4f89661f22bbf` |

Binaires seulement : les sources exactes de ce build n'ont pas été conservées. Le code joint à cette release (tag) est celui de la 1.1.8 (branche `sources-1.1.8`), qui contient ces fonctions.

---

## 1.1.6 — BlockBench model thumbnails in the creative tab

### New: BBmodel thumbnails

- Items in the **BBmodel Models** creative tab (added in 1.1.5) now show a **thumbnail of the model** instead of a plain sheet of paper. The item keeps its thumbnail once moved to the hotbar.
- **165 thumbnails** are bundled with the mod: transparent 128 × 128 PNG images (about 1.9 MB in total), rendered by Blockbench from the server's BBmodel files, each with a Minecraft item model and an item definition.
- Thumbnails are **frozen in the mod**: a model changed on the server keeps its original thumbnail until the next InfinityLink release.
- A model whose thumbnail is not bundled keeps the **fallback icon** (the sheet of paper) instead of a missing texture. Its name, its identifier and right-click placement work as before.

### Built-in resource pack

- Thumbnails come from a built-in resource pack, `InfinityLink · aperçus BBmodel`, **loaded automatically**: nothing to enable in the resource pack options. This pack does not appear in the resource pack list and cannot be disabled there.
- This pack sits **below all others**, including the game's base pack. A player or server pack that provides the same `infinitylink:…/bbmodel/<id>` paths therefore takes priority.
- Loading works **without Fabric API**, like the rest of the mod.

### Compatibility

The tab works as in 1.1.5: same catalogue sent by the server, same right-click placement at scale 1 through the `modeles_bb` plugin. No setting was added. An InfinityLink 1.1.5 client keeps the paper icon; an older client, or one without InfinityLink, does not get this tab.

### Known issues

This version was built from a different copy of the sources than the published 1.1.4. Two 1.1.4 fixes are missing:
- a 3D resource refused while reception is saturated is no longer requested again automatically: part of a scene or model can stay missing until the next revision or a reconnection;
- an unreadable file in the scene disk cache makes the whole cache ignored for the session (resources are downloaded again).

The mod description (`fabric.mod.json`) no longer mentions 3D scenes and BlockBench models; these features are still there.

Jars: Minecraft 26.3 and 26.4-snapshot-2 (Fabric Loader 0.19 or later, no Fabric API). **No 26.4-snapshot-1 jar for this version.** The jar grows from about 0.4 MB to about 2.4 MB because of the bundled thumbnails. SHA-256 values are in the table above.

Binaries only: the exact sources of this build were not kept. The source code attached to this release (tag) is the 1.1.8 code (`sources-1.1.8` branch), which contains these features.
