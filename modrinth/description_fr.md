<!-- Bannière : remplacer l'URL ci-dessous par celle de banner.png une fois téléversée dans la galerie Modrinth (clic droit sur l'image > copier l'adresse). -->
![Bannière ∞link](URL_DE_LA_BANNIERE)

# ∞link (Infinity_Link)

**∞link** est le mod client du serveur **∞SMP** : il permet au serveur de parler directement à ton jeu. Grâce à lui, tu profites d'un rendu lointain natif, d'onglets créatifs par mod, d'armures en 3D et des créations des autres joueurs, sans rien régler toi-même.

*Anciennement SAGE Link.*

---

## Fonctionnalités

- **Rendu lointain natif** : le serveur envoie une version simplifiée du terrain au-delà de ta distance d'affichage normale. Par défaut, la vue porte à 128 chunks, soit 2 048 blocs. La mémoire vidéo utilisée a un plafond réglable qui n'est jamais dépassé. Pour rester sous ce plafond, le mod libère d'abord les zones les plus lointaines, puis réduit la vue par paliers de 16 chunks.
- **Onglets créatifs par mod** : en mode créatif, les objets ajoutés par le serveur sont rangés dans leurs propres onglets, un par mod. Les onglets vides restent cachés. Jusqu'à 128 onglets, sur des pages de 10 (la première page garde les onglets vanilla).
- **Armures 3D des mods** : les armures qui ont un vrai modèle 3D s'affichent sur les joueurs (toi compris en vue à la troisième personne), sur les supports d'armure et sur les mobs humanoïdes, sans doublon avec l'armure classique.
- **Créations des joueurs** :
  - **Préchargement** : les contenus publiés sur le serveur sont téléchargés en arrière-plan, vérifiés (taille et empreinte) puis gardés en cache pour les prochaines connexions.
  - **Envoi** : `/lk envoyer <fichier>` envoie au serveur un fichier de ton dossier de dépôt (`.minecraft/laconia/depot`).
  - **Éditeur 16×16** : `/lk editeur` ouvre un petit éditeur de pixels (palette de 16 couleurs, pinceau, gomme) pour dessiner une texture, l'enregistrer en PNG et l'envoyer.
- **Blocs étendus et formes** (1.1.0) : 100 000 emplacements de blocs supplémentaires pour les blocs ajoutés par le serveur, avec des formes d'**escalier**, de **dalle** et de **muret** à collision, contour et modèle exacts. Le serveur les accorde aux clients ∞link 1.1.0 ; un client sans le mod voit un bloc vanilla de même forme.
- **HUD et touches pilotés par le serveur** : le serveur peut afficher des informations en haut à gauche de l'écran et réagir à certaines touches de ton clavier.
- **Journal léger** : ton fichier `latest.log` n'est plus inondé d'avertissements. À chaque rechargement des ressources, les 100 000 emplacements de réserve sans modèle sont résumés en une seule ligne, au lieu d'environ 1,7 million d'avertissements (148 Mo mesurés). Les autres modèles manquants restent signalés.

## Prérequis

| | |
|---|---|
| Minecraft | **26.3** |
| Chargeur | **Fabric Loader 0.19.0 ou plus récent** |
| Fabric API | **Pas nécessaire** |
| Côté | **Client uniquement** (rien à installer sur un serveur) |

Aucun argument Java (JVM) particulier n'est nécessaire.

## Compatibilité

∞link ne fait quelque chose que sur un serveur qui parle son protocole, comme ∞SMP. Sur un serveur vanilla ou tout autre serveur, **il n'a aucun effet**. Il envoie un seul message de présentation, que le serveur ignore, et rien d'autre.

## Installation

**Avec Prism Launcher ou MultiMC**
1. Crée une instance **Minecraft 26.3**.
2. Dans *Modifier l'instance > Version*, clique sur *Installer Fabric* et choisis une version 0.19.0 ou plus récente.
3. Dans l'onglet *Mods*, ajoute le fichier `.jar` d'∞link (ou installe-le depuis Modrinth directement dans le lanceur).
4. Lance l'instance et connecte-toi au serveur ∞SMP.

**Avec le lanceur officiel Minecraft**
1. Installe **Fabric Loader** pour Minecraft 26.3 avec l'installateur Fabric (version 0.19.0 ou plus récente).
2. Place le fichier `.jar` d'∞link dans le dossier `.minecraft/mods` (crée-le s'il n'existe pas).
3. Dans le lanceur, choisis le profil *fabric-loader-26.3* et lance le jeu.

## Configuration

Le fichier `.minecraft/config/sage_link.properties` est créé au premier lancement. Il se modifie avec un éditeur de texte, jeu fermé.

| Option | Défaut | Valeurs | Rôle |
|---|---|---|---|
| `lod` | `true` | `true` / `false` | Active ou coupe le rendu lointain. |
| `lod_view` | `128` | `0` à `512` (chunks) | Distance du rendu lointain. 128 chunks = 2 048 blocs. Le serveur en sert au plus 256. `0` coupe le rendu lointain. |
| `lod_vram_mb` | `96` | `16` à `4096` (Mio) | Plafond de mémoire vidéo réservé au rendu lointain. |
| `armures_3d` | `true` | `true` / `false` | Active ou coupe l'affichage des armures 3D. |

Les valeurs hors plage sont ramenées dans la plage. Si `lod_vram_mb` ou `armures_3d` manque, elle est ajoutée avec sa valeur par défaut ; une option `lod` ou `lod_view` absente prend sa valeur par défaut. Une valeur illisible remet les options du rendu lointain à leurs valeurs par défaut.

## Commandes

Ces commandes sont traitées par ton jeu et ne sont jamais envoyées au serveur.

| Commande | Effet |
|---|---|
| `/lk envoyer <fichier>` | Envoie au serveur un fichier du dossier `.minecraft/laconia/depot` (de 1 octet à 2 Mio, un simple nom de fichier). Le résultat s'affiche dans le chat. |
| `/lk editeur [nom.png]` | Ouvre l'éditeur de pixels 16×16. *Enregistrer* écrit le PNG dans le dossier de dépôt, *Envoyer* l'enregistre puis l'envoie. Clic droit = gomme. |
| `/lk depot` | Liste le contenu du dossier de dépôt. |
| `/lk` | Affiche l'aide. |

## Confidentialité

**Ce qui est envoyé, et quand**
- **À la connexion** : un message de présentation avec la version du protocole, la version du mod, la version de Minecraft et la liste des fonctions prises en charge.
- **Sur un serveur compatible uniquement** :
  - la distance de rendu lointain (choisie, réduite si le plafond de mémoire vidéo est atteint, 0 à la sortie) ;
  - l'appui ou le relâchement des seules touches que le serveur a demandées, et seulement quand aucun écran n'est ouvert ;
  - le résultat du préchargement (prêt ou échec) ;
  - la confirmation de réception des onglets créatifs ;
  - la réponse à un test technique des blocs étendus demandé par le serveur ;
  - les fichiers que **tu** envoies avec `/lk envoyer` ou le bouton *Envoyer* de l'éditeur. Rien n'est envoyé sans ton action.

**Ce qui est téléchargé, et d'où**
- Les contenus publiés sur le serveur, depuis les adresses (http ou https) que le serveur indique. Chaque fichier est vérifié (taille et empreinte SHA-1), puis rangé dans `.minecraft/sage_link/cache`.

## Questions fréquentes

**Le mod est-il obligatoire pour jouer sur ∞SMP ?**
Non. Tu peux rejoindre ∞SMP sans le mod : tu n'auras simplement pas les fonctionnalités ci-dessus.

**Faut-il Fabric API ?**
Non. ∞link a seulement besoin de Fabric Loader.

**Le rendu lointain fait ramer mon jeu, que faire ?**
Baisse `lod_view` ou `lod_vram_mb` dans `config/sage_link.properties`, ou mets `lod=false`.

**Les armures 3D me gênent.**
Mets `armures_3d=false` dans `config/sage_link.properties`.

**Pourquoi le fichier s'appelle-t-il encore `sage_link` ?**
Le mod s'appelait SAGE Link. L'identifiant technique `sage_link` a été gardé pour que ta configuration et ton cache restent valables.

**Et si une fonction plante ?**
Une erreur coupe seulement la fonction concernée (rendu lointain, armures 3D, HUD ou touches). Le jeu, lui, continue. Le rendu lointain reste coupé jusqu'à la fin de la session, les armures 3D jusqu'au changement de monde.

## Limites connues

- **Touches** : seules les touches courantes sont reconnues (lettres, chiffres, F1 à F24, flèches, pavé numérique, modificateurs). Les touches ne sont transmises que lorsqu'aucun écran n'est ouvert.
- **Souris** : les boutons de la souris ne sont pas transmis.
- **HUD** : 16 lignes au maximum, en haut à gauche. Il est masqué par F1 et quand l'écran de débogage (F3) est ouvert.

## Liens

- **Serveur** : ∞SMP (lien Discord ou page du serveur : à renseigner)
- **Adresse du serveur** : `∞.ecartdescartes.eu` *(à retirer si tu ne veux pas la publier)*

<!-- Icône : remplacer l'URL par celle de icon.png une fois téléversée dans la galerie Modrinth (l'icône du projet se règle aussi dans Paramètres > Général). -->
![Icône ∞link](URL_DE_L_ICONE)

## Licence

Infinity_Link est distribué sous la **Mozilla Public License 2.0** (MPL-2.0) : tu peux l'utiliser et l'inclure dans un modpack librement ; toute version modifiée des fichiers du mod doit être redistribuée sous la même licence, avec son code source.
