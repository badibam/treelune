# Les thèmes

Un thème dessine l'app : chaque composant visuel de `UI.*` passe par lui (`core/themes/ThemeContract.kt`), les écrans ne posant que la mise en page (Row, Column, Box). Ce doc dit ce qu'est un thème dans l'app, puis la recette pour en faire un : le déroulé de sa conception, ce qu'on décide avant de coder, ses fichiers, ce qui vérifie quoi, ses sons, les pièges vus sur le téléphone et la passe sur l'appareil. Ce qui est propre à un thème (son registre, ses choix) vit dans sa spec, `docs/design/<id>-theme.md`.

## Ce qu'est un thème dans l'app

- Cinq thèmes, inscrits dans `ThemeScanner` : le défaut (Material), le rétro (pixel art, `docs/design/retro-theme.md`), le cosy (rond et dodu, chaque pièce sur son ombre, `docs/design/cosy-theme.md`), le suisse (une grille stricte, noir et blanc et un seul rouge, `docs/design/swiss-theme.md`) et l'encre et lavis (des lavis sur un papier crème, chaque trait au pinceau, `docs/design/ink-theme.md`), ces quatre derniers dessinant tout eux-mêmes. Les autres registres maquettés, en attente, sont dans `docs/theme-tracks.html`, à relire avant d'en proposer un.
- Un thème nomme lui-même ce qui lui appartient, son nom et ses palettes, dans ses propres textes (`themes/<id>/strings.xml`, lus par `s.theme()`) : le cœur n'en connaît aucun.
- Un thème a des couleurs claires et sombres (`PaletteMode`). L'utilisateur choisit dans Réglages › Interface le thème, le mode (clair, sombre, ou comme le téléphone, suivi quand il change), la teinte (un décalage de 0 à 359° qui tourne toutes les couleurs du thème sur le cercle OKLCH, leur clarté et leur saturation gardées, sauf celles des états et des tags : `Appearance.hueShift`, `Oklch`, `HueShiftTest`) et le cran de taille (0 à 3, qu'un thème au pixel applique à son facteur entier, vers le haut seulement). `AppConfigManager` les applique à `CurrentTheme` (`Appearance`) à chaque lecture des réglages, et chaque application s'écrit au journal avec sa raison (« Theme applied »).
- L'écran montre chaque choix aussitôt fait et revient à l'apparence enregistrée si on le quitte sans enregistrer. Pour que l'écran en cours survive au changement de thème, `MainActivity` déplace le contenu de l'app d'un cadre d'écran (`FullScreen`) à l'autre sans le recréer (`movableContentOf`) : un `FullScreen` ne compose donc jamais son contenu à part (pas de `BoxWithConstraints`, dont le contenu déplacé ne suivrait plus le thème), et aucun champ ne garde le focus pendant le déplacement.
- La grille des tuiles prend du thème la largeur d'une case (`gridCellPx`), la hauteur d'une rangée (`gridRowPx`, la largeur dans le thème par défaut) et l'écart entre deux colonnes et deux rangées (`gridColumnGapPx`, `gridRowGapPx`, nuls dans le thème par défaut, qui garde l'écart dans ses tuiles).
- Une zone et un outil peuvent avoir une texture (`docs/design/tool-texture.md`), que tout thème dessine à deux places : dans le cadre de leur tuile (`ToolCardContainer`, `ZoneCardContainer`), et sur tout le fond de leur page (`PageGround`, que la pile des lieux appelle autour de chaque page) en cachant ce que le thème pose d'autre sur son fond d'écran. La tête de page n'en porte pas. Un thème garde pleines ses cartes et les conteneurs d'un texte long, pour qu'aucun motif ne passe dessous.
- Les bancs s'ouvrent par `./run themes` (`themes/bench.html`) : celui du rétro règle et enregistre ses deux palettes, celui du défaut montre ses couleurs à tout décalage, recopiées de `DefaultTheme` et tenues à elles par `DefaultColorsBenchTest`, celui des textures règle et enregistre les tons des motifs de chaque thème, et la planche d'écoute choisit les sons de chaque thème (« Les sons »).

## Le déroulé

0. **Où travailler** : avant tout travail sur un thème, l'IA demande à l'utilisateur s'il se fait dans un worktree, ou sur la branche courante.
1. **Le registre** : une maquette de l'écran d'une zone, ajoutée à `docs/theme-tracks.html` à côté des autres, et choisie à l'œil.
2. **Les décisions** : la spec `docs/design/<id>-theme.md`, remplie ligne à ligne de la liste ci-dessous.
3. **Le seuil** : chaque ligne de la liste porte une décision, et le banc du thème est vu dans les deux modes. Rien sur le téléphone avant de coder.
4. **Le code** : les fichiers du thème, puis son inscription dans `ThemeScanner`.
5. **La passe sur le téléphone**, puis ce qui reste ouvert dans la spec.

Les sons se cherchent en parallèle, sans rien bloquer : le thème part muet (`sound()` rend null), et ses sons arrivent quand ils sont choisis.

L'IA propose toutes les lignes de la liste d'un coup, chacune avec sa décision proposée et ce qui la fonde ; l'utilisateur répond en une fois, ne revenant que sur les lignes qu'il change. Elle fabrique de quoi juger (maquette, banc, planche d'écoute, captures) et calcule ce qui se mesure (contrastes, couverture de la police, licences). L'utilisateur tranche tout ce qui se voit ou s'entend : l'IA ne voit pas le téléphone et n'entend rien, elle ne valide jamais une couleur ni un son.

## Les décisions avant le code

Chaque ligne porte une décision au seuil ; « comme le thème par défaut » en est une, si elle est écrite. Une ligne vide bloque le seuil.

Avant de proposer l'allure d'une pièce, l'IA lit sa pièce dans `ThemeContract` et dit ce que le cœur impose : où il place le contenu (le nom d'une tuile, `UI.TileHeader`), les couleurs qu'il donne (celle d'une icône au motif d'une texture, celles d'un dessin), ce qui est commun à tous les thèmes (la fenêtre flottante et son voile). Une allure que le contrat ne permet pas est une proposition d'ajout au contrat (« Ajouter une pièce au contrat »), nommée comme telle, jamais une décision du thème.

1. **Le registre** : la maquette, et ce que le thème n'est pas.
2. **Ce qu'il dessine lui-même** : tout, ou des pièces reprises du thème par défaut, nommées.
3. **La police** : laquelle, sa licence (libre, embarquée dans `assets/fonts/`), sa copie (`third_party/` et un script qui en tire les `res/font/`, ou un projet voisin vérifié par `check_font_copy`), sa couverture (Windows-1252, l'espace fine insécable U+202F, les flèches, `≤ ≥ ≠ ≈`), ses graisses, son gras et son italique dans un texte lu (`ThemeContract.emphasis`), et ce qui rend un caractère qu'elle n'a pas, validé par l'utilisateur (un repli). Un caractère manquant s'ajoute par le script quand la licence ne réserve aucun nom de police (l'espace fine d'Archivo, `make_archivo_fonts.py`) ; un nom réservé laisse le repli.
4. **Les palettes** : claire et sombre, une poignée de nombres OKLCH dont tout dérive ; les contrastes visés (4,5 pour chaque encre sur son fond, la secondaire comprise, et pour les couleurs d'état) ; les tags à luminosité et saturation communes ; ce que tourne la teinte. Les contrastes se tiennent à chaque décalage de teinte, pas au seul décalage 0 : à clarté OKLCH égale, le contraste mesuré change avec la teinte (le rouge du suisse tenait 5,4 à 0 et 4,0 à 120°).
5. **La géométrie** : la grille des tuiles (case, rangée, écarts), les espacements nommés et la marge de bord (`UI.Space`, `EDGE`), la cible du doigt (48 dp au plus près), l'unité de dessin (`drawingUnit`).
6. **La taille** : la taille de texte d'Android suivie ou non, et ce que fait chaque cran de 0 à 3. Un cran agrandit ce qu'on lit et ce qu'on touche (texte, icônes, cibles, cadres), jamais l'air autour : les espacements nommés, la marge de bord et les écarts de la grille gardent la longueur du cran 0 (décidé le 2026-10-17 : en rétro taille 3, les marges mangeaient la place gagnée).
7. **Les composants**, une ligne par famille du contrat : cadres (carte, tuile, dialogue, fenêtre flottante, bulle, carte d'interaction) ; boutons (normal, désactivé, activé, flottant) ; saisie (champ, sélection, case, interrupteur, oui/non, curseur, jauge, teinte, onglets) ; repères (tag, séparateur, poignées, marques d'attente et de chrono, icône colorée, textures de tuile et de fond de page) ; attente (chargement, IA qui réfléchit) ; messages (toast, snackbar) ; sélecteurs de date et d'heure ; en-tête de page et marque de l'app ; la barre d'un texte sélectionné.
8. **Le toucher et le mouvement** : l'élément pressé, une apparition, l'élément soulevé, et les animations coupées dans Android.
9. **Les icônes** : Lucide (`LUCIDE`), ou les siennes (`OWN`), avec leur script de conversion et leur fichier de retouches.
10. **Les graphiques** : dessinés par le thème, ou ceux du défaut (`DefaultDrawing`).
11. **Le mode d'édition de la grille** : les cases et la tuile choisie (`GridCell`, `GridTile`).
12. **Le registre sonore** : le caractère des sons, leur choix se faisant à part.
13. **Les textes du thème** : son nom, sa phrase (`tagline`, lue par le README) et les noms de ses palettes, en anglais et en français.

## Ajouter une pièce au contrat

Une pièce nouvelle (la barre d'un texte sélectionné, par exemple) se décide pour chaque thème existant avant d'être codée, comme une ligne de la liste ci-dessus : sa ligne dans la spec du thème (`docs/design/<id>-theme.md`, et pour le thème par défaut, qui n'en a pas, dans le commentaire de la pièce), la ligne de la liste des décisions, et celle de la passe sur le téléphone. Le cœur garde ce que la pièce contient, où elle se place et quand ; le thème n'a que son allure. Le banc d'un thème qui a une planche des composants (le rétro) la dessine aussi. Une pièce qu'Android dessinait jusque-là (une barre, un sélecteur) se remplace par ce que Compose laisse fournir publiquement : ce qu'il garde interne reste à Android, et la ligne de la spec le dit.

## Les fichiers d'un thème

Tout est dans `themes/<id>/` (sous `app/src/main/java/app/treelune/`), sauf ce qu'Android veut ailleurs.

| Fichier | Ce qu'il porte | D'où il vient |
|---|---|---|
| `<Id>Theme.kt` | l'objet qui implémente `ThemeContract`, inscrit dans `ThemeScanner` | à la main ; ses pièces dans d'autres fichiers du dossier (`RetroFrame`, `CosyPieces`…) |
| `<Id>Palette.kt` | les couleurs dérivées des nombres, dans les deux modes et à toute teinte | à la main |
| `resources/app/treelune/themes/<id>/palettes.json` | les nombres des palettes, leur seule source | le banc, qui les enregistre |
| `bench/` | le banc : l'écran d'une zone et une planche des composants à la résolution d'un téléphone, dans la police du thème, les deux modes, la teinte essayée sans être enregistrée | à la main, lié depuis `themes/bench.html` ; un thème lisse en HTML et CSS, les deux modes côte à côte, ses contrastes mesurés à tout décalage (celui du suisse pour modèle), un thème au pixel en canvas (celui du rétro) |
| `resources/app/treelune/themes/<id>/textures/*.svg`, `textures.json` | une cellule SVG par motif de texture (`ICON` à part), et les tons de chacun par mode et par intensité | les cellules à la main, les tons au banc des textures (`texture-bench.html`), qui les enregistre |
| `strings.xml`, `strings-fr.xml` | `name`, `tagline`, les noms des palettes | à la main |
| `icons/` (thème `OWN`) | un SVG par icône Lucide, toutes | un script (`pixelize_icons.py` pour le pixel), plus les retouches à la main |
| `res/font/<police>_*.ttf`, `assets/fonts/<police>/` | la police et sa licence | un script depuis `third_party/` ou un projet voisin |
| `sounds-choice.json` | familles, présélections, choix par signal, gain, recettes | la planche d'écoute |
| `res/raw/<id>_<signal>.flac`, `sounds.json` | les sons joués et leurs crédits | `scripts/theme_sounds.py make <id>` |
| `test/.../themes/<id>/<Id>PaletteTest.kt` | les contrastes des deux palettes | à la main |
| `docs/design/<id>-theme.md` | le registre et les décisions | la conception, élaguée une fois le thème vu sur le téléphone |

Un thème s'inscrit aussi, hors de son dossier : dans `ThemeScanner` ; dans la liste des thèmes de `TextureCellTest` et `TextureTonesTest` ; dans `THEMES` de `themes/texture-bench.html`, avec ses surfaces ; par un lien vers son banc dans `themes/bench.html` ; dans les README, par `scripts/make_readme.py` ; et comme retenu, sur sa carte de `docs/theme-tracks.html` et dans la liste de `docs/reference.md`.

## Ce qui vérifie quoi

| Obligation | Ce qui la tient |
|---|---|
| Chaque pièce du contrat fournie | le compilateur (`ThemeContract`) |
| Le thème offert dans Réglages › Interface et le README | son inscription dans `ThemeScanner`, lue par `AppSettings` et `make_readme.py --check` |
| `name` et `tagline` dans les deux langues | `check_string_locales.py`, `check_string_keys.py`, `make_readme.py --check` |
| Le tutoiement dans les textes français | `check_french_address.py` |
| Un thème `OWN` a toutes les icônes de Lucide | `generate_icons.py`, qui refuse un thème incomplet |
| La copie de la police à jour | `check_font_copy.py` (Cartouche) ; le script de la police (Baloo 2) |
| Une cellule de chaque motif de texture, rien d'autre dans le dossier, en pixels entiers pour un thème au pixel ; les deux tons de chaque motif dans les deux modes | `TextureCellTest`, `TextureTonesTest` (la liste des thèmes y est écrite) |
| Les contrastes des palettes, à chaque décalage de 15° | `<Id>PaletteTest`, à écrire avec le thème (`SwissPaletteTest` pour modèle) |
| Les couleurs d'un banc d'aperçu égales à celles du code | un test comme `DefaultColorsBenchTest`, si le banc recopie au lieu de lire `palettes.json` |
| Chaque son branché sur son signal, aucun fichier oublié | `ThemeSoundsTest` |
| Les sons, leurs crédits et le choix d'accord, leurs licences acceptées | `theme_sounds.py check`, dans `./run test` |
| Un écran nomme ses espacements, le thème les mesure | `check_spacing.py` (le code des thèmes en est exempt) |
| Le reste : ce qui se voit, s'entend, se touche | la passe sur le téléphone |

## Les sons

Le cœur envoie un signal d'une liste fermée (`UISignal` : confirmer, entrer, revenir, ouvrir, fermer, cocher, cran, bout de liste, refus) et le thème dit quel son y répond (`ThemeContract.sound`), ou aucun (`docs/UI.md`, « Sons de l'interface »). Un son est en FLAC, rangé tel quel dans l'APK (`noCompress += "flac"`, `app/build.gradle.kts`).

- **Les sources** sont listées une fois pour tous les thèmes dans `themes/sound-sources.json` : titre, auteur, licence, page, archive et sa somme sha256, et la règle qui range ses sons en familles. Seules CC0 1.0 et CC BY 4.0 sont prises : libres pour F-Droid, CC BY ne demandant que le crédit que `sounds.json` donne. Aujourd'hui les six bibliothèques de sons de Kenney (CC0) ; une banque d'instruments (VSCO 2 CE, dont la Séance tire son marimba), OpenGameArt filtré par licence ou un son de Freesound s'y ajoutent de la même façon, le script apprenant leur forme d'archive le jour où l'une d'elles arrive.
- **Une recette** (dans `sounds-choice.json`) fait un son qu'aucune source n'a : un son retouché (plus aigu, coupé), plusieurs mêlés, ou un son fait de rien (`synth`), par une suite d'effets sox ; ses crédits sont ceux de ce qu'elle mêle.
- **Le flux** :
  1. L'IA : `scripts/theme_sounds.py library` télécharge les sources dans `tmp/sound-library/`, rend les recettes, écrit le catalogue.
  2. L'utilisateur, sur la planche (`./run themes`, « sons » du thème, ou `sound-bench.html?theme=<id>` pour un thème neuf) : les familles qui vont au thème, chacune s'écoutant d'un trait.
  3. L'utilisateur, signal par signal dans les familles retenues : écouter, présélectionner, choisir (★), comparer avec le son de chaque autre thème ; le gain du thème en dB, un décalage par signal. Enregistrer écrit `sounds-choice.json` : une séance reprend où la précédente s'est arrêtée.
  4. Un signal qui ne trouve rien : l'IA ajoute une source, ou écrit une recette, et relance l'étape 1.
  5. L'IA : `scripts/theme_sounds.py make <id>` écrit les FLAC (16 bits, ce que rend le décodeur du téléphone) et `sounds.json` ; un signal sans choix n'a pas de fichier.
  6. L'IA : `sound()` rend `R.raw.<id>_<signal>` pour chaque fichier.
  7. L'utilisateur, sur le téléphone : un niveau à reprendre se corrige au gain ou au décalage, puis l'étape 5.

## Les pièges vus sur le téléphone

Chacun a coûté un aller-retour sur l'appareil ; le code des trois thèmes les respecte, un thème neuf aussi.

- **Une ressource du thème se lit par son chemin absolu** (`getResourceAsStream("/app/treelune/themes/<id>/palettes.json")`) : la release renomme et déplace les classes, et un chemin relatif ne trouvait plus rien, faisant planter l'écran des réglages. Ne se voit qu'en release.
- **Un composant survit à toute taille que son parent lui donne**, même sous son minimum : le cadre rétro serré sous deux cellules calculait un nombre négatif de pièces et plantait (l'historique des conversations, un titre de section long à grande taille).
- **Un geste lit la dernière fonction qu'on lui a passée** (`rememberUpdatedState`) : le curseur rétro gardait le `onValueChange` de son premier dessin et réécrivait les réglages d'alors.
- **Les encres tiennent 4,5 de contraste sur leur fond dans les deux modes**, la secondaire comprise (descriptions, libellés), et le texte dans un bouton prend l'encre du bouton : une encre pâle sur la couleur principale ne se lisait plus.
- **Le thème par défaut nomme chaque rôle de couleur de Material** : ceux qu'il omet, Material les remplit de ses violets (un segment choisi, un menu, un sélecteur de date).
- **Tout ce que le doigt prend fait 48 dp au plus près** (un bouton, un champ, une case, une ligne de liste ; un bouton en icône seule, un carré de cette taille) ; trois cellules faisaient 28 dp.
- **Un mot plus large que sa ligne se coupe aux syllabes** (`Hyphens.Auto`), jamais à n'importe quelle lettre.
- **Un caractère absent de la police** est pris par Android dans une police système : à vérifier sur le texte français, l'espace fine (U+202F) en tête.
- **Une fenêtre flottante tient au-dessus du clavier elle-même** (`FloatingDialog`) : laissée à Android, ses boutons passaient sous les touches.
- **La bande de la teinte montre des couleurs saturées** : au gris du fond des cadres, un décalage ne se distinguait pas d'un autre.
- **Changer de thème recompose tout l'écran** : un état Compose d'un objet global lu pour la première fois pendant cette recomposition a planté l'accueil. Le changement de thème est donc dans la passe.

## La passe sur le téléphone

Un parcours fixe, sur l'app remplie par la démo, d'abord dans les pires cas : les deux modes et une teinte décalée ; le plus grand cran de taille avec la taille de texte d'Android au maximum ; « une colonne » ; une fois les animations coupées dans Android. Les sons activés tout du long.

1. L'accueil : la marque de l'app, les tuiles de zone, le bouton flottant, le mode d'édition de la grille.
2. Une zone : des tuiles de chaque affichage (icône, ligne, compact, étendu, plein), les cartes des variables et des routines ; des tuiles texturées, chaque motif, sans et avec couleur d'icône, aux deux intensités, et l'en-tête d'une page texturée.
3. Un outil de chaque type, son écran et sa config en pages ; un Graphique.
4. Les fenêtres : une confirmation, une fenêtre flottante longue au clavier ouvert, les sélecteurs de date et d'heure, un toast et une snackbar.
5. Le chat : les bulles, l'IA qui réfléchit, une carte de validation, le champ au clavier.
6. Réglages › Interface : l'aperçu, la teinte, le passage à un autre thème et retour, sans plantage.
7. Le Guide et sa bande ; un texte sélectionné et sa barre ; l'écran recréé en passant le téléphone en sombre.

L'IA pilote le téléphone par adb (`scripts/phone_ui.sh`) et met les captures sur une planche, un écran par condition ; l'utilisateur juge sur l'appareil ce qu'une capture ne montre pas : le toucher, le mouvement, les sons, la lecture réelle. Ce qui ne va pas devient une ligne de la spec, « Ce qui reste ouvert » ; ce qui est vu s'élague.
