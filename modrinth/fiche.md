# Fiche Modrinth : ∞link (Infinity_Link)

Champs à recopier dans les paramètres du projet Modrinth.

## Nom

∞link (Infinity_Link)

## Résumé court (≤ 256 caractères)

**FR** (212 caractères) :
> Mod client du serveur ∞SMP : rendu lointain natif, onglets créatifs par mod, armures 3D, créations de joueurs et éditeur 16×16. Fabric, sans Fabric API. Sans effet sur les autres serveurs. Anciennement SAGE Link.

**EN** (201 caractères) :
> Client mod for the ∞SMP server: native distant rendering, per-mod creative tabs, 3D armor, player-made content and a 16×16 editor. Fabric, no Fabric API. No effect on other servers. Formerly SAGE Link.

Modrinth n'accepte qu'un seul résumé : choisir la langue principale de la page.

La description est aussi un seul texte : coller `description_en.md` (public plus large) ou `description_fr.md`, ou les deux à la suite séparés par une ligne `---`.

## Catégories proposées

- Principales : `utility`, `social`, `equipment`
- Supplémentaires possibles : `decoration`

(Catégories existantes des mods Modrinth ; `utility` pour le rendu lointain, les onglets et le HUD, `social` pour les créations partagées entre joueurs, `equipment` pour les armures 3D.)

## Environnement

| | |
|---|---|
| Client | **Requis** |
| Serveur | **Non pris en charge** |

## Version à téléverser

| Champ | Valeur |
|---|---|
| Fichier | `sage-link-0.3.3+26.3.jar` |
| Numéro de version | `0.3.3+26.3` |
| Chargeur | **Fabric** |
| Versions du jeu | **26.3** |
| Dépendances | aucune (Fabric API **non** requise) |
| Canal | à choisir (release, beta ou alpha) |

## Licence

Licence : **Mozilla Public License 2.0** (`MPL-2.0`), à choisir dans la liste Modrinth « Mozilla Public License 2.0 ».
Texte : fichier `LICENSE` du mod (aussi à la racine du jar) ; notice de copyright : `NOTICE`.
Code source public : https://github.com/ECARTDESCARTES/Infinity_Link (à mettre dans le lien « Source code » de la page Modrinth).

## Liens à renseigner

- Code source : à renseigner
- Suivi des problèmes (issues) : à renseigner
- Wiki : à renseigner (facultatif)
- Discord ou page du serveur ∞SMP : à renseigner
- Adresse du serveur : `∞.ecartdescartes.eu` (à retirer si tu ne veux pas la publier)

## Images

- Icône du projet : `icon.png` (Paramètres > Général)
- Bannière : `banner.png`, à téléverser dans la galerie, puis copier son URL dans les descriptions (emplacements `URL_DE_LA_BANNIERE` / `BANNER_URL` et `URL_DE_L_ICONE` / `ICON_URL`).

## Note sur l'identifiant technique

Le mod s'affiche désormais sous le nom ∞link (Infinity_Link), mais son identifiant technique reste `sage_link` (mod id Fabric, fichier `config/sage_link.properties`, dossier `.minecraft/sage_link/cache`). Il est conservé pour la compatibilité : les réglages et le cache existants restent valables. Le slug Modrinth `infinity_link` peut différer de cet identifiant sans conséquence.
