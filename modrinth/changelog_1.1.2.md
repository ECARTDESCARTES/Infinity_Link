<!-- Changelog de la version 1.1.2 pour Modrinth. Modrinth n'a qu'un champ : coller la partie FR ou la partie EN (ou les deux). -->

## 1.1.2 — Chat vocal de proximité et touches

### Nouveau : chat vocal

- **Chat vocal de proximité**, dans l'esprit de Simple Voice Chat : on entend les joueurs proches avec un son **stéréo spatialisé** (gauche/droite selon la direction, volume décroissant jusqu'à 48 blocs par défaut).
- **Parler en appuyant** (Verr. Maj) ou **activation vocale** avec un seuil réglable. Le niveau du micro s'affiche en direct dans le menu vocal.
- **Chuchoter** : rayon réduit à 8 blocs par défaut.
- **Groupes** : on rejoint un groupe depuis le menu vocal ou avec `/voix groupe <nom>`. La voix de groupe s'entend à toute distance, et une touche bascule entre parole de groupe et parole de proximité.
- **Micro coupé**, **sourdine** (on n'entend rien et on n'envoie rien), volumes du micro et de la sortie. Les réglages sont enregistrés dans `config/infinitylink_voix.properties`.
- **HUD** : état du micro, joueurs en train de parler. Une touche les masque.
- **Menu vocal** (touche V) : mode, micro, sourdine, seuil, volumes, groupe, icônes.
- **Sécurité** : chaque trame est chiffrée en AES-128-GCM avec une clé propre au joueur, remise par InfinityLink seulement une fois connecté à son compte. Les trames rejouées ou falsifiées sont refusées par le serveur.
- **Sans bibliothèque native** : codec IMA-ADPCM 16 kHz (64 kbit/s), micro et haut-parleurs par Java Sound.

### Nouveau : touches

Toutes les touches se règlent dans **Options > Commandes**, catégories « InfinityLink — chat vocal » et « InfinityLink ». Elles sont enregistrées dans `options.txt` comme les touches vanilla, et leurs libellés existent en français et en anglais.

| touche | par défaut |
|---|---|
| Parler (appuyer) | Verr. Maj |
| Chuchoter (appuyer) | aucune |
| Couper le micro | M |
| Sourdine (désactiver le chat vocal) | N |
| Menu du chat vocal | V |
| Parler au groupe / en proximité | G |
| Mode : appuyer / activation vocale | aucune |
| Masquer les icônes vocales | H |
| Rendu lointain (activer / couper) | aucune |
| Éditeur de textures 16×16 | aucune |
| Masquer le HUD InfinityLink | aucune |
| État d'InfinityLink (dans le chat) | aucune |

### Nouveau : Minecraft 26.4-snapshot-1 et 26.4-snapshot-2

- Deux jars supplémentaires, `infinitylink-1.1.2+26.4-snapshot-1.jar` et `infinitylink-1.1.2+26.4-snapshot-2.jar`, visent **Minecraft 26.4-snapshot-1** et **26.4-snapshot-2** avec Fabric Loader 0.19.5. Il est construit depuis les mêmes sources et apporte les mêmes fonctions.
- Le serveur ∞SMP parle le protocole 26.3. Un client 26.4 ne s'y connecte pas directement : il faut un serveur qui accepte ce protocole.

### Prérequis

- Minecraft **26.3**, **26.4-snapshot-1** ou **26.4-snapshot-2** (le jar du même nom), Fabric Loader **0.19.5** ou plus, **sans Fabric API**. Mod côté client uniquement.
- Chat vocal : serveur ∞SMP à jour (capacité « voix »). Le trafic passe en UDP sur le port du jeu. Sur un autre serveur, les touches vocales restent sans effet.
- Commandes du serveur : `/voix` (état), `/voix groupe <nom>`, `/voix quitter`, `/voix sourdine <joueur>`, `/voix entendre <joueur>`, `/voix groupes`.
- Pour couper la voix : `-Dinfinitylink.voix=off`.

---

## 1.1.2 — Proximity voice chat and key bindings

### New: voice chat

- **Proximity voice chat**, in the spirit of Simple Voice Chat: nearby players are heard with **spatialized stereo sound** (left/right by direction, volume fading out up to 48 blocks by default).
- **Push to talk** (Caps Lock) or **voice activation** with an adjustable threshold. The microphone level is shown live in the voice menu.
- **Whisper**: radius reduced to 8 blocks by default.
- **Groups**: join a group from the voice menu or with `/voix groupe <name>`. Group voice is heard at any distance, and a key toggles between group talk and proximity talk.
- **Mute microphone**, **deafen** (hear nothing and send nothing), microphone and output volumes. Settings are saved in `config/infinitylink_voix.properties`.
- **HUD**: microphone status and players currently speaking. A key hides them.
- **Voice menu** (V key): mode, microphone, deafen, threshold, volumes, group, icons.
- **Security**: every frame is encrypted with AES-128-GCM under a per-player key, handed over by InfinityLink only once you are logged in to your account. The server refuses replayed or forged frames.
- **No native library**: IMA-ADPCM 16 kHz codec (64 kbit/s), microphone and speakers through Java Sound.

### New: key bindings

All keys are set in **Options > Controls**, under the "InfinityLink — voice chat" and "InfinityLink" categories. They are saved in `options.txt` like vanilla keys, and their labels exist in English and French.

| key | default |
|---|---|
| Push to talk | Caps Lock |
| Whisper | none |
| Mute microphone | M |
| Disable voice chat | N |
| Voice chat menu | V |
| Toggle group talk | G |
| Toggle push to talk / voice activation | none |
| Hide voice icons | H |
| Toggle distant rendering | none |
| 16×16 texture editor | none |
| Hide InfinityLink HUD | none |
| InfinityLink status (chat) | none |

### New: Minecraft 26.4-snapshot-1 and 26.4-snapshot-2

- Two extra jars, `infinitylink-1.1.2+26.4-snapshot-1.jar` and `infinitylink-1.1.2+26.4-snapshot-2.jar`, target **Minecraft 26.4-snapshot-1** and **26.4-snapshot-2** with Fabric Loader 0.19.5. It is built from the same sources and brings the same features.
- The ∞SMP server speaks the 26.3 protocol. A 26.4 client cannot join it directly: it needs a server that accepts that protocol.

### Requirements

- Minecraft **26.3**, **26.4-snapshot-1** or **26.4-snapshot-2** (the matching jar), Fabric Loader **0.19.5** or later, **no Fabric API**. Client-side only.
- Voice chat: an up-to-date ∞SMP server ("voix" capability). Traffic uses UDP on the game port. On other servers the voice keys do nothing.
- Server commands: `/voix` (status), `/voix groupe <name>`, `/voix quitter`, `/voix sourdine <player>`, `/voix entendre <player>`, `/voix groupes`.
- To turn voice off: `-Dinfinitylink.voix=off`.
