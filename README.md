# InfinityLink

![InfinityLink](modrinth/banner.png)

**Français** · [English](#english)

Mod client Minecraft **26.3** et **26.4-snapshot-2** (Fabric Loader 0.19.5, **sans Fabric API**) du serveur **∞SMP** — anciennement *SAGE Link*, puis *Infinity_Link* (∞link).
Il relie le client au serveur : rendu lointain natif, onglets créatifs par mod, armures 3D des mods, créations des
joueurs (préchargement, envoi et éditeur 16×16), chat vocal de proximité (1.1.2), HUD et touches pilotés par le serveur.
Sur un autre serveur, le mod n'a aucun effet.

**Chat vocal et touches (1.1.2).** Façon Simple Voice Chat : parler en appuyant (Verr. Maj) ou activation vocale,
chuchotement, groupes, micro coupé (M), sourdine (N), menu vocal (V), parole de groupe (G), icônes (H). Le son est
spatialisé en stéréo. Les trames sont chiffrées en AES-128-GCM sous une clé par joueur, remise par la capacité Link
« voix » après connexion au compte. Codec IMA-ADPCM 16 kHz, sans bibliothèque native. S'y ajoutent des touches pour le
rendu lointain, l'éditeur 16×16, le HUD et l'état du mod. Toutes se règlent dans Options > Commandes (`Keys.java`,
`OptionsMixin`, libellés FR/EN par `ClientLanguageMixin`). Relais côté serveur : `libs/sage_voix`, `crates/sage_server/src/voix.rs`.
Messages Link : `crates/sage_proto/src/link_voix.rs`. Banc de bout en bout : `tools/voix_test.py`.

**Scènes 3D et modèles BlockBench (1.1.4).** Capacités Link « maillages » (`sage:link/meshes`, `meshes_have` :
scènes glTF à niveaux de détail, instances par cellules, cache disque par serveur vérifié par SHA-256) et « modeles »
(`sage:link/models` : modèles `.bbmodel` aplatis en quads). Un `item_display` marqué `custom_data {"sage":"scene:<id>"}`
ou `"modele:<id>"` est dessiné par le mod à la place de son item. Décodage Java pur dans `core/scenes` (`test/ScenesTest.java`),
rendu dans `mc/scenes`. Contrats : `DOCS/INFINITYLINK-MAILLAGES-PROTOCOLE.md` et `DOCS/INFINITYLINK-MODELES-PROTOCOLE.md`.

- Page Modrinth : https://modrinth.com/project/infinity_link
- Licence : [Mozilla Public License 2.0](LICENSE) (voir aussi [NOTICE](NOTICE))

## Compiler

Pas de Gradle ni de Loom : le mod se compile directement contre les noms Mojang du client 26.3, avec Fabric Loader et
Sponge Mixin, par `build.sh`.

Prérequis :

- un **JDK 25** ;
- les bibliothèques d'une instance Minecraft 26.3 avec Fabric Loader 0.19.5 (dossier `libraries` d'un lanceur comme
  MultiMC ou Prism Launcher) et le fichier de métadonnées de la version 26.3.

```sh
JDK="/chemin/vers/jdk-25" \
MC_LIBS="/chemin/vers/le/lanceur/libraries" \
MC_META="/chemin/vers/le/lanceur/meta/net.minecraft/26.3.json" \
bash build.sh
```

Le script actuel vise **Windows** (il appelle les exécutables `.exe` du JDK). `bash build.sh --mc 26.4-snapshot-2`
produit le jar du snapshot. Les sources 26.3 y sont portées par `port/26.4-snapshot-2/port.json` : chaque remplacement
est vérifié, cinq mixins de codec de blocs sont remplacés par `BlockStateCodecMixin`, et la dépendance Fabric vise
`26.4-alpha.2`, forme normalisée par Fabric Loader. Le build rejoue alors les mêmes tests, les cibles `javap` et le
démarrage sans fenêtre contre le jar client du snapshot. Le build lance les tests en Java pur (`test/*Test.java`), vérifie par `javap` que chaque cible de mixin existe dans le
jar client 26.3 (`test/mixin_targets.txt`), compile le mod, produit `dist/infinitylink-<version>+26.3.jar`, puis lance le jeu sans fenêtre (Knot) pour
appliquer les vrais mixins. Depuis la 1.1.1, l'identifiant du mod est `infinitylink` ; il déclare `provides: sage_link`,
si bien que Fabric charge InfinityLink et écarte un ancien `sage-link-*.jar` resté dans `mods/` (constaté avec Fabric
Loader 0.19.5). Le protocole (canaux
`sage:link/*`) ne change pas : le mod reste compatible avec tout serveur LACONIA.

## Organisation du code

| dossier | contenu |
|---|---|
| `src/main/java/infinitylink/core` | protocole, état, rendu lointain, assets, armures 3D : Java pur, testé sans Minecraft |
| `src/main/java/infinitylink/mc` | point d'entrée client, rendu, onglets, blocs, écran de l'éditeur |
| `src/main/java/infinitylink/mc/mixin` | mixins (noms Mojang, `remap = false`) |
| `src/main/java/infinitylink/core/voice`, `mc/voice`, `mc/Keys.java` | chat vocal (trames, codec, gigue, spatialisation ; micro, réseau, écran) et touches (1.1.2) |
| `port/<version>/` | portage des sources vers une autre version de Minecraft (`build.sh --mc <version>`) |
| `src/main/resources` | `fabric.mod.json`, configuration des mixins, icône, licence |
| `infinitylink/mc/Migration.java` | reprise, au premier lancement, des réglages et du cache de SAGE Link / Infinity_Link 1.x |
| `test/` | tests et script de build ; `test/ref/` : trames de référence |
| `modrinth/` | textes de la page Modrinth, logo, bannière |

## Contribuer

Les correctifs sont bienvenus. Toute version modifiée des fichiers du mod que vous redistribuez reste sous MPL-2.0,
avec son code source.

---

## English

Client mod for Minecraft **26.3** and **26.4-snapshot-2** (Fabric Loader 0.19.5, **no Fabric API**) for the **∞SMP** server — formerly *SAGE Link*, then *Infinity_Link* (∞link).
It connects the client to the server: native distant rendering, per-mod creative tabs, 3D armor from mods, player-made
content (preloading, upload and a 16×16 editor), proximity voice chat (1.1.2), and a server-driven HUD and key bindings.
On any other server the mod has no effect.

**Voice chat and keys (1.1.2).** In the spirit of Simple Voice Chat: push to talk (Caps Lock) or voice activation,
whisper, groups, mute (M), deafen (N), voice menu (V), group talk (G), icons (H). Sound is spatialized in stereo.
Frames are encrypted with AES-128-GCM under a per-player key handed over by the "voix" Link capability after account
login. IMA-ADPCM 16 kHz codec, no native library. There are also keys for distant rendering, the 16×16 editor, the HUD
and the mod status. All keys are set in Options > Controls. Since 1.1.4, the server can also send high-definition 3D
scenes ("maillages" capability, glTF with levels of detail, per-server disk cache) and BlockBench models ("modeles"),
drawn by the mod in place of a marked `item_display`. `bash build.sh --mc 26.4-snapshot-2` builds the snapshot jar
from the same sources, ported by `port/26.4-snapshot-2/port.json`.

- Modrinth: https://modrinth.com/project/infinity_link
- License: [Mozilla Public License 2.0](LICENSE) (see also [NOTICE](NOTICE))

### Building

No Gradle, no Loom: the mod compiles directly against the Mojang names of the 26.3 client jar, with Fabric Loader and
Sponge Mixin, through `build.sh`. You need a **JDK 25**, the libraries of a Minecraft 26.3 instance with Fabric Loader
0.19.5 (the `libraries` folder of a launcher such as MultiMC or Prism Launcher) and the 26.3 version metadata file:

```sh
JDK="/path/to/jdk-25" \
MC_LIBS="/path/to/launcher/libraries" \
MC_META="/path/to/launcher/meta/net.minecraft/26.3.json" \
bash build.sh
```

The current script targets **Windows** (it calls the JDK `.exe` tools). The build runs the pure-Java tests, checks with `javap` that every mixin target exists in the 26.3 client jar, and
builds the mod, writes `dist/infinitylink-<version>+26.3.jar`, then starts the game headless (Knot) to apply the real mixins.
Since 1.1.1 the mod id is `infinitylink`; it declares `provides: sage_link`, so Fabric loads InfinityLink and skips an old
`sage-link-*.jar` left in `mods/` (observed with Fabric Loader 0.19.5). The protocol (`sage:link/*` channels) is unchanged: the mod works with any LACONIA server.

### Contributing

Fixes are welcome. Any modified version of the mod's files that you redistribute stays under MPL-2.0, with its source
code.
