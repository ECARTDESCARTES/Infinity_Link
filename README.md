# ∞link — Infinity_Link

![∞link](modrinth/banner.png)

**Français** · [English](#english)

Mod client Minecraft **26.3** (Fabric Loader, **sans Fabric API**) du serveur **∞SMP** — anciennement *SAGE Link*.
Il relie le client au serveur : rendu lointain natif, onglets créatifs par mod, armures 3D des mods, créations des
joueurs (préchargement, envoi et éditeur 16×16), HUD et touches pilotés par le serveur.
Sur un autre serveur, le mod n'a aucun effet.

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

Le script actuel vise **Windows** (il appelle les exécutables `.exe` du JDK). Le build lance les tests en Java pur (`test/*Test.java`), vérifie par `javap` que chaque cible de mixin existe dans le
jar client 26.3 (`test/mixin_targets.txt`), puis produit `dist/sage-link-<version>+26.3.jar`. L'identifiant technique du
mod reste `sage_link` pour la compatibilité avec le serveur.

## Organisation du code

| dossier | contenu |
|---|---|
| `src/main/java/sage/link/core` | protocole, état, rendu lointain, assets, armures 3D : Java pur, testé sans Minecraft |
| `src/main/java/sage/link/mc` | point d'entrée client, rendu, onglets, blocs, écran de l'éditeur |
| `src/main/java/sage/link/mc/mixin` | mixins (noms Mojang, `remap = false`) |
| `src/main/resources` | `fabric.mod.json`, configuration des mixins, icône, licence |
| `test/` | tests et script de build ; `test/ref/` : trames de référence |
| `modrinth/` | textes de la page Modrinth, logo, bannière |

## Contribuer

Les correctifs sont bienvenus. Toute version modifiée des fichiers du mod que vous redistribuez reste sous MPL-2.0,
avec son code source.

---

## English

Client mod for Minecraft **26.3** (Fabric Loader, **no Fabric API**) for the **∞SMP** server — formerly *SAGE Link*.
It connects the client to the server: native distant rendering, per-mod creative tabs, 3D armor from mods, player-made
content (preloading, upload and a 16×16 editor), and a server-driven HUD and key bindings.
On any other server the mod has no effect.

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
writes `dist/sage-link-<version>+26.3.jar`. The technical mod id stays `sage_link` for server compatibility.

### Contributing

Fixes are welcome. Any modified version of the mod's files that you redistribute stays under MPL-2.0, with its source
code.
