<!-- Changelog de la version 1.1.3 pour Modrinth. Modrinth n'a qu'un champ : coller la partie FR ou la partie EN (ou les deux). -->

## 1.1.3 — Correctif : plantage à l'entrée en jeu

### Corrigé

- **Plantage à l'entrée en jeu** avec un profil neuf (IndexOutOfBoundsException dans `InputConstants.isKeyDown`). La 1.1.2 enregistrait « sans touche » avec la valeur -1, alors que Minecraft 26.x utilise 0. Au retour du focus, le jeu interrogeait alors le clavier à l'indice -1. Les sept touches sans valeur par défaut (chuchoter, mode vocal, rendu lointain, éditeur, HUD, état) utilisent maintenant la valeur « sans touche » du jeu.
- **Réparation automatique** : un `options.txt` écrit par la 1.1.2 (`key.keyboard.-1`) est corrigé au chargement. Rien à supprimer à la main.

### Inchangé

- Mêmes fonctions que la 1.1.2 : chat vocal de proximité, touches dans Options > Commandes, rendu lointain, onglets, armures 3D, créations des joueurs.
- Un jar par version de Minecraft : `+26.3`, `+26.4-snapshot-1`, `+26.4-snapshot-2`. Fabric Loader 0.19.5 ou plus, sans Fabric API.
- **Mise à jour conseillée** pour tous les utilisateurs de la 1.1.2 : remplacer le jar 1.1.2 par le jar 1.1.3 de la même version de Minecraft.

---

## 1.1.3 — Fix: crash when entering a world

### Fixed

- **Crash when entering a world** with a fresh profile (IndexOutOfBoundsException in `InputConstants.isKeyDown`). Version 1.1.2 stored "no key" as -1, while Minecraft 26.x uses 0. When the game regained focus, it then read the keyboard at index -1. The seven keys without a default binding (whisper, voice mode, distant rendering, editor, HUD, status) now use the game's own "no key" value.
- **Automatic repair**: an `options.txt` written by 1.1.2 (`key.keyboard.-1`) is fixed on load. Nothing to delete by hand.

### Unchanged

- Same features as 1.1.2: proximity voice chat, keys in Options > Controls, distant rendering, tabs, 3D armor, player-made content.
- One jar per Minecraft version: `+26.3`, `+26.4-snapshot-1`, `+26.4-snapshot-2`. Fabric Loader 0.19.5 or later, no Fabric API.
- **Recommended update** for everyone on 1.1.2: replace the 1.1.2 jar with the 1.1.3 jar for the same Minecraft version.
