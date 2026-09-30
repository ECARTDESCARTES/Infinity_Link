## 1.1.5 — Onglet créatif des modèles BlockBench

### Nouveau : onglet créatif « Modèles BBmodel »

- Un **onglet créatif dédié** liste les modèles `.bbmodel` que le serveur envoie (plugin `modeles_bb`, capacité « modeles »). Il occupe sa propre place, et les 128 onglets du catalogue serveur restent intacts.
- **Accès** : dans l'inventaire créatif, avancez avec la flèche `>` au-dessus des onglets jusqu'à la page InfinityLink, puis ouvrez l'onglet **Modèles BBmodel**.
- Chaque modèle y apparaît comme un **objet de sélection** (une feuille de papier renommée). Il porte le nom du modèle, ou à défaut son identifiant, et une infobulle : `BBmodel · <id>`, « Clic droit : placer à vos pieds », « Créatif · échelle 1 ».
- **Recherche créative** : les modèles se retrouvent par leur nom et par l'identifiant `bbmodel:<id>`.
- **Catalogue à jour** : il suit les modèles que le serveur ajoute, remplace ou retire à chaud. Il est vidé à la déconnexion.

### Nouveau : pose d'un modèle par clic droit

- En mode créatif, un **clic droit** dans l'air avec un objet de sélection envoie la commande `/modele pose <id> 1`. Le plugin du serveur pose alors le modèle (à vos pieds, échelle 1). Sur un bloc, un second clic peut être nécessaire.
- Seuls les identifiants présents dans le catalogue reçu sont acceptés. Un clic qui suit le précédent de moins de 250 ms n'envoie pas de nouvelle commande.
- Les objets de sélection portent leur propre marqueur, `infinitylink_bbmodel`. Il est distinct du marqueur `sage` des objets du catalogue, que le serveur LACONIA vérifie et refuse s'il ne les connaît pas.
- Le clic droit garde son comportement vanilla hors mode créatif, avec la capacité « modeles » coupée, avec le réglage `modeles` désactivé, ou avec les onglets Link désactivés (Fabric API présente, ou `-Dinfinitylink.onglets=off`). Dans ces trois derniers cas, l'onglet ne propose aucun modèle. Un objet dont le modèle a été retiré du catalogue retrouve lui aussi le comportement vanilla.

### Modifié

- La reprise des touches enregistrées à `key.keyboard.-1` (plantage corrigé en 1.1.3) s'applique désormais à toutes les touches des options, et plus seulement à celles d'InfinityLink. Elle ne traite plus que cette valeur exacte.

### À noter

- Cette version a été construite à partir d'une autre copie des sources que la 1.1.4 publiée. Trois éléments présents dans les jars 1.1.4 n'y figurent pas :
  - la redemande automatique d'une ressource 3D refusée quand la réception est saturée ;
  - la tolérance d'un fichier illisible dans le cache disque des scènes : en 1.1.5, un seul fichier illisible au démarrage fait ignorer tout le cache disque pour la session ;
  - la mention des scènes 3D et des modèles BlockBench dans la description du mod (`fabric.mod.json`, affichée dans la liste des mods et dans Mod Menu). Ces fonctions restent présentes.

### Réglages

Aucun nouveau réglage. L'onglet et la pose suivent le réglage existant `modeles` de `config/infinitylink.properties`.

Un client sans InfinityLink, ou avec une version antérieure, n'a pas cet onglet.

Jars : Minecraft 26.3 et 26.4-snapshot-2, Fabric Loader 0.19.5 (dépendance déclarée : 0.19.0 ou plus), sans Fabric API. **Pas de jar 26.4-snapshot-1 pour cette version.**

| Jar | Minecraft | SHA-256 |
|---|---|---|
| `infinitylink-1.1.5+26.3.jar` | 26.3 | `fb11bd847dc75e737f55991b0c9c5e0e62197449dd04a06157e5103edad671c9` |
| `infinitylink-1.1.5+26.4-snapshot-2.jar` | 26.4-snapshot-2 (26.4-alpha.2) | `5c5e79f9f157a67279647a1825a384830f57a178cb9c4ba40f970a8b80bb6186` |

Binaires seulement : les sources exactes de ce build n'ont pas été conservées. Le code joint à cette release (tag) est celui de la 1.1.8 (branche `sources-1.1.8`), qui contient ces fonctions.

---

## 1.1.5 — Creative tab for BlockBench models

### New: "Modèles BBmodel" creative tab

- A **dedicated creative tab** lists the `.bbmodel` models sent by the server (`modeles_bb` plugin, "modeles" capability). It has its own slot, and the 128 server catalogue tabs are left untouched.
- **Access**: in the creative inventory, use the `>` arrow above the tabs to reach the InfinityLink page, then open the **Modèles BBmodel** tab.
- Each model appears as a **selection item** (a renamed sheet of paper). It carries the model's name, or its identifier if there is no name, and a tooltip: `BBmodel · <id>`, "Clic droit : placer à vos pieds", "Créatif · échelle 1".
- **Creative search**: models can be found by name and by the `bbmodel:<id>` identifier.
- **Live catalogue**: it follows the models the server adds, replaces or removes on the fly. It is cleared on disconnect.

### New: place a model with right-click

- In creative mode, **right-clicking** in the air with a selection item sends `/modele pose <id> 1`. The server plugin then places the model (at your feet, scale 1). On a block, a second click may be needed.
- Only identifiers from the received catalogue are accepted. A click less than 250 ms after the previous one does not send a new command.
- Selection items carry their own marker, `infinitylink_bbmodel`. It is separate from the `sage` marker of catalogue items, which the LACONIA server checks and rejects when it does not know them.
- Right-click keeps its vanilla behaviour outside creative mode, with the "modeles" capability off, with the `modeles` setting off, or with the Link tabs disabled (Fabric API installed, or `-Dinfinitylink.onglets=off`). In those last three cases, the tab offers no model. An item whose model has been removed from the catalogue also goes back to vanilla behaviour.

### Changed

- Repairing keys saved as `key.keyboard.-1` (crash fixed in 1.1.3) now applies to every key in the options, not only InfinityLink's. It only handles that exact value.

### Note

- This version was built from a different copy of the sources than the published 1.1.4. Three things found in the 1.1.4 jars are missing:
  - automatically requesting again a 3D resource refused while reception is saturated;
  - tolerating an unreadable file in the scene disk cache: in 1.1.5, a single unreadable file at startup makes the whole disk cache ignored for the session;
  - the mention of 3D scenes and BlockBench models in the mod description (`fabric.mod.json`, shown in the mod list and in Mod Menu). These features are still there.

### Settings

No new setting. The tab and placement follow the existing `modeles` setting in `config/infinitylink.properties`.

Players without InfinityLink, or with an older version, do not get this tab.

Jars: Minecraft 26.3 and 26.4-snapshot-2, Fabric Loader 0.19.5 (declared dependency: 0.19.0 or later), no Fabric API. **No 26.4-snapshot-1 jar for this version.** SHA-256 values are in the table above.

Binaries only: the exact sources of this build were not kept. The source code attached to this release (tag) is the 1.1.8 code (`sources-1.1.8` branch), which contains these features.
