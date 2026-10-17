# Les textures d'outil et de zone — conception

Conçu et codé le 2026-10-16, sur la branche `theme-recipe` fusionnée ; le fond de page et les pages de choix conçus le 2026-10-17 (branche `texture-background`). Rien n'a tourné sur un téléphone (`device-checks.md`), et les tons sont un premier jet à régler au banc. Une zone et un outil peuvent poser un motif discret sur le fond de leur tuile, pour qu'on les reconnaisse d'un coup d'œil dans une grille, et sur tout le fond de leur page, pour qu'on y retrouve la tuile qu'on vient de toucher. Maquette, à ouvrir dans un navigateur : `docs/design/tool-texture-mockup.html` (les trois thèmes, les seize choix, la couleur d'icône et les deux intensités ; motifs dessinés au jugé, intensités non égalisées).

## Ce qu'est une texture

- Un **motif répété** sur le fond d'une surface, à faible contraste, sous le texte.
- Il se pose sur **la tuile** (celle d'un outil dans la grille d'une zone, celle d'une zone à l'accueil) et sur **tout le fond de la page** de la chose : la page d'une zone, celle d'un outil et les écrans qu'elle ouvre en elle (une fiche, un historique). Pas sur la page de config d'une zone ou d'un outil, un formulaire, ni sur une fenêtre ouverte par-dessus la page (le formulaire d'une entrée).
- La tête de la page n'a pas de motif à elle : elle est posée sur le fond texturé, comme le reste de la page.
- Pas une image choisie par l'utilisateur : une photo casserait le rétro (pixels à facteur entier), rendrait le texte illisible, demanderait un fichier à sauvegarder, et l'IA n'en pourrait rien dire.

## Les noms

Une brique `Texture` (sur le modèle de `IconColor`) liste quinze noms, sait les lire et refuse les autres. `null` ou absent : pas de texture, la surface comme aujourd'hui ; c'est un état normal, pas un repli, et aucune donnée existante ne change.

| Famille | Noms |
|---|---|
| Lignes | `LINES` (rayures), `DIAGONALS` (biais), `GRID` (quadrillage), `ZIGZAG` (chevrons), `WAVES` (vagues) |
| Semis | `DOTS` (points), `CROSSES` (petites croix), `STARS` (étoiles), `RINGS` (anneaux), `DIAMONDS` (losanges), `CONFETTI` (petits traits épars, dans un ordre fixe) |
| Pavages | `CHECKER` (damier à grands carrés), `BRICKS` (briques), `SCALES` (écailles) |
| La chose | `ICON` (son icône, en quinconce) |

- Ce sont des **formes**, pas des matières (papier, bois…) : une forme se dessine dans tous les thèmes sans en trahir aucun, et deux matières à faible contraste finissent en grain pareil.
- Un motif entre dans la liste s'il se dessine en pixels pleins sur une petite cellule, se distingue des autres à faible contraste, et ne prend pas un sens que l'app donne déjà.
- `CHECKER` est à grands carrés (4 pixels de dessin et plus dans le rétro) pour ne pas se confondre avec le damier d'un pixel sur deux du mode d'édition de la grille du rétro (`retro-theme.md`) ; s'il s'y confond encore sur le téléphone, il sort de la liste.
- Le cosy a déjà des points sur le fond de l'écran : son `DOTS` se dessine autrement que ce fond, plus gros.
- `ICON` n'a de sens que pour une chose qui a une icône ; voir « Le choix ».

## Qui la dessine

Le cœur ne connaît que les noms ; **chaque thème dessine les quinze**, chacun à sa manière : traits fins dans le défaut, pixels pleins sur sa grille dans le rétro, formes rondes et dodues dans le cosy. Le choix est stocké sans dépendre du thème et survit à un changement de thème. Écarté : un dessin du cœur avec les seules couleurs du thème (il ne tomberait pas sur la grille du rétro), une liste propre à chaque thème (le choix ne survivrait pas au changement de thème, et il faudrait un motif de remplacement).

- **Un motif est une cellule SVG**, un petit carreau répété sur la surface : `resources/app/treelune/themes/<thème>/textures/<nom en minuscules>.svg`, un fichier par nom et par thème, un seul format pour tous les thèmes (`TextureCell` : `viewBox` en unités entières depuis 0 0, des `<path>`, `<rect>` et `<circle>`, un trait s'il a un `stroke-width`, plein sinon ; tout autre élément est refusé).
  - Défaut et cosy : des tracés libres, pleins ou en trait, courbes comprises. Le thème lit les tracés (`PathParser`), dessine la cellule une fois à la couleur voulue, et la répète (`ImageShader` en `TileMode.Repeated`).
  - Rétro : des `<rect>` pleins à coordonnées entières, une unité par pixel de dessin, comme ses icônes (`themes/retro/icons/`). La cellule se dessine à un pixel de dessin par unité puis s'agrandit d'un facteur entier, sans lissage.
- **`ICON` n'a pas de fichier** : sa cellule est l'icône de la chose posée deux fois en quinconce (une rangée sur deux décalée d'une demi-cellule), petite ; le dessin en pixels dans le rétro, à facteur entier, l'icône Lucide ailleurs.
- **Le ton** : pour chaque motif, deux nombres par mode dans `textures.json` du thème (à côté de son `palettes.json`, le thème par défaut n'en ayant pas), un par intensité (voir « L'intensité »), et la saturation d'un motif teinté (`TextureTones`). Un ton est un écart de clarté par rapport à la surface où le motif est posé (la tuile, le fond de la page). Avec une couleur d'icône (`icon_color`), le motif prend la teinte de cette couleur à cette clarté ; sans couleur, la teinte de la surface. Jamais de transparence : le rétro reste en tons pleins, comme ses autres dessins.
- **Le contrat, la tuile** : `ToolCardContainer` et `ZoneCardContainer` reçoivent la texture de la chose (`ItemTexture` : le motif, l'icône, la `TagColor` ou `null`), comme la couleur d'icône, et la posent à l'intérieur de leur propre cadre. Le peintre est commun (`rememberTextureBrush`) : chaque thème lui donne la couleur de la surface et sa façon de poser (`TextureStyle` : la taille d'une unité, en pixels entiers ou lissée, la taille de l'icône d'`ICON` et l'air autour).
- **Le contrat, la page** : `PageGround(texture, content)`, que tout thème écrit. La pile des lieux l'appelle à une seule place, autour de chaque page (`PlaceScreen`, `MainScreen.kt`), avec la texture de la zone ou de l'outil de la page, `null` pour toute autre page ; un écran d'outil n'en sait rien. Avec une texture, le thème couvre toute la surface de la page, bords compris, de la couleur de son fond d'écran et du motif au ton de ce fond, et cache ce qu'il dessine d'autre sur ce fond (les points du sable du cosy) : deux motifs l'un sur l'autre feraient du bruit. Sans texture, il ne dessine rien, et le fond de `FullScreen` se voit comme sur toute page.
- **La lisibilité** : un texte long n'est jamais posé sur le motif. La carte d'une entrée (`Card`, `CardType.DEFAULT`) et les autres conteneurs de texte sont pleins dans chaque thème ; seul ce qui est court est posé sur le fond (la tête de page, un titre de section, la phrase d'une liste vide), comme le nom et le résumé sur une tuile. Les tons de la page sont donc ceux de la tuile, calculés depuis le fond de l'écran : si le banc montre qu'il en faut de plus pâles, ils viendront de lui.

## L'intensité

- À réglage égal, les motifs ne se voient pas pareil (un damier couvre la moitié de la surface, des points quelques pour cent) : **chaque thème donne un ton par motif**, réglé pour que tous se voient autant.
- **Un banc** les montre côte à côte, par thème et par mode, aux deux intensités, sur la tuile ou sur une page (un fond texturé, une tête de page, un titre de section, une phrase posée sur le fond et deux cartes), sans couleur et dans une couleur d'icône choisie : `themes/texture-bench.html`, ouvert par `./run themes`. Il lit **les mêmes fichiers** que l'app (les cellules SVG, `textures.json`) et les surfaces aux sources de chaque thème (le `palettes.json` du rétro et du cosy, le banc des couleurs du défaut), et enregistre les tons dans `textures.json` (`POST /textures/<id>`).
- **Un réglage de l'app, et un seul** : Réglages › Interface, à côté de la teinte, une clé `texture_intensity` à deux valeurs, `SUBTLE` (discrète, par défaut) et `MARKED` (marquée), lue dans `Appearance` ; il vaut pour la tuile comme pour le fond de la page, et son écran montre quelques tuiles texturées, avec une phrase qui le dit. Deux crans et pas un curseur : le rétro n'a que des tons pleins, et le banc égalise chaque cran. Pas d'intensité par outil, qui ferait ressortir certaines tuiles plus que d'autres.
- La clé manque dans les réglages existants, et `Appearance.from` rend `null` quand une clé manque : la migration 74 → 75, qui ajoute la colonne des zones, écrit aussi `SUBTLE` dans les réglages d'interface (`UiTextureIntensityAtV75`, que l'import d'une sauvegarde passe aussi).

## Où elle vit

- **Outil** : une clé `texture` de sa config, à côté de `icon_name` et `icon_color`, dans le schéma de config commun à tous les types (`ToolConfigSettings`) : une liste fermée des quinze noms. `null` ou vide, la clé est retirée. Pas de migration.
- **Zone** : une colonne `texture` de `Zone`, à côté de `icon_color` (migration 74 → 75 ; l'import d'une sauvegarde la lit absente comme `null`) ; un champ de `ZoneSettings`.
- Un outil sans texture ne prend pas celle de sa zone : l'héritage peindrait tous les outils de la zone pareil, quand la texture sert à les distinguer.

## Le choix

- **À l'écran, une page par choix** : dans la config d'une zone et dans celle d'un outil, sous le bouton de l'icône, deux lignes de résumé, « Couleur de l'icône » (l'icône dans sa couleur, le nom de la couleur) et « Texture » (une petite tuile au motif, son nom). Toucher une ligne ouvre sa page, qui ne tient que la grille de ce choix.
- **La page est une page du formulaire** (`SettingsForm`, `docs/design/settings-pages.md`) : un réglage dont l'éditeur donne sa ligne de résumé et sa page s'ouvre comme un groupe, avec le chemin en haut, la flèche et la touche retour qui remontent, le brouillon gardé, les boutons du bas sur la page racine seule. **Toucher un choix le prend et ramène au formulaire** : un seul geste pour une seule valeur ; la page, rouverte, marque le choix en cours. Pas une fenêtre comme celle de l'icône : la grille y serait à l'étroit.
- **Les textures** (`TextureSelector`) : les seize choix, « Sans texture » d'abord, autant par rangée que la largeur en tient (`ChoiceGrid`), chacun une petite tuile de la chose telle que le thème la dessine avec ce motif (son `ToolCardContainer`), dans sa couleur d'icône et à l'intensité réglée, son nom dessous ; le choix en cours marqué d'une coche devant son nom.
- **Les couleurs** (`IconColorSelector`) : « Sans couleur » puis les couleurs, chacune une petite tuile de la chose avec sa texture, l'icône dans cette couleur, son nom dessous, le choix en cours marqué de la même coche. On voit ainsi les deux choix ensemble sur chaque page.
- **`ICON` et l'icône** : la combinaison « sans icône + `ICON` » n'existe jamais. À l'écran, la case `ICON` n'est pas offerte tant que la zone n'a pas d'icône ; l'écran ne sait pas retirer une icône, seulement en choisir une autre. Un outil a toujours une icône : la sienne, ou celle de son type qu'il montre sans. Le service refuse la combinaison, d'où qu'elle vienne. Jamais de « aucune » dessinée à la place d'un `ICON` sans icône : ce serait un repli.
- **Par l'IA** : CREATE_ZONE et UPDATE_ZONE prennent `texture` ; un outil la reçoit dans sa config. `null` retire la texture. Un nom inconnu, ou `ICON` sur une chose sans icône (une écriture qui retire l'icône en gardant `ICON` comprise), est refusé par le service, avec la liste des noms valides ou la raison. L'aperçu de l'app n'en dit rien, et le prompt ne pousse pas l'IA à en poser d'elle-même : c'est un choix de goût, qu'elle fait quand on le lui demande. Le réglage d'intensité lui reste fermé : il est dans l'apparence, hors des catégories de `AppSettings.OPEN_TO_AI`.
- Le Guide en parle là où il parle de la couleur d'icône.

## Tests

- `TextureTest` : les quinze noms dans leur ordre, un nom inconnu ou en minuscules refusé, « aucune » acceptée, `ICON` refusé sans icône.
- `TextureCellTest` : chaque thème a une cellule pour chacun des quatorze noms qui ont un fichier, et rien d'autre dans son dossier `textures/`, chacune lue par le chemin de l'app ; une cellule du rétro n'a que des `<rect>` pleins à coordonnées entières ; une cellule fautive est refusée.
- `UiTextureIntensityAtV75Test` : les réglages d'interface gagnent l'intensité, discrète.
- `TextureTonesTest` : chaque thème a deux tons pour chacun des quinze noms dans les deux modes, le marqué plus loin de sa surface que le discret ; un motif va vers l'encre de sa surface. Le banc lit `textures.json` lui-même : rien à tenir égal.
- Le refus de « sans icône + `ICON` » et d'un nom inconnu par les services de zone et d'outil.
- La migration, l'import et le rendu passent par la recette sur l'appareil, avec `CHECKER` en mode d'édition de la grille dans le rétro, le fond de page dans les trois thèmes, et les pages de choix (retour, brouillon).
