# Tailles de tuile (Tile sizes)

Conception du 2026-11-11, à implémenter puis élaguer.

## Le besoin

Une tuile plus haute que large en demi-largeur (« 2x1 vertical », venu du téléphone), et plus largement des tailles libres au lieu de sept modes nommés. Aujourd'hui le mode est un nom (`DisplayMode` : Icône, Minimal, Ligne, Condensé, Étendu, Carré, Complet) dont la grille déduit la taille (`Grid.size`) et l'écran le contenu (`UI.kt`, `LookCard`) ; le corps d'une tuile ne connaît que son nombre de lignes de cases, jamais sa largeur, toujours 4.

La version la plus simple, un huitième mode « Vertical » en 2×4, ne suffit pas : chaque mode nouveau s'apprend un par un à l'écran, aux thèmes et à chaque outil, et le besoin est de tailles libres.

## La taille

Une tuile est l'Icône (1×1), ou une largeur et une hauteur en cases de la grille de 4 colonnes :

- largeur 2 ou 4, jamais une autre ;
- hauteur de 1 à 10, ou « selon le contenu » en largeur 4 seulement : une tuile qui suit son contenu est seule sur sa ligne, comme Complet aujourd'hui, au pixel près, sans arrondi ;
- une hauteur fixe est une boîte fixe : elle ne rétrécit jamais quand elle a peu à montrer, le vide reste en bas de son corps. Aujourd'hui Étendu et Carré rétrécissent (`GridLayout`, `grows`) : c'est fini, seule « selon le contenu » suit son contenu.

Toutes les tailles valent pour toutes les tuiles : outil, Tâche, routine, zone, variable, Repère. Une zone, une variable, un Repère n'ont pas de corps : en grande taille, leur tuile est l'en-tête, le résumé et du vide, au choix de l'utilisateur.

## Ce que la tuile montre

| Taille | Ligne 1 | Ligne 2 | Lignes suivantes |
|---|---|---|---|
| Icône | l'icône seule | | |
| 2×1 | en-tête | | |
| 4×1 | en-tête, résumé à droite | | |
| 2×2 | en-tête | résumé | |
| 2×h, h ≥ 3 | en-tête | résumé | corps, h − 2 lignes, 2 cases de large |
| 4×h, h ≥ 2 | en-tête, résumé à droite | corps, h − 1 lignes, 4 cases de large | |
| 4 × selon le contenu | en-tête, résumé à droite | corps, la hauteur de son contenu | |

Le résumé est un seul morceau ; trois tuiles l'adaptent quand elles n'ont pas de corps (4×1, 2×2) : le Graphique (la bande de ses points), la Tâche et la routine (leurs boutons Lancer et Ouvrir). C'est le `Glance` d'aujourd'hui, inchangé.

Les tailles d'aujourd'hui y retombent : Minimal = 2×1, Ligne = 4×1, Condensé = 2×2, Étendu = 4×2, Carré = 4×4, Complet = 4 × selon le contenu.

## Le corps

Le corps reçoit sa place, largeur (2 ou 4 cases) et hauteur (en lignes de cases, ou libre), au lieu de ses seules lignes (`ToolTile.Body(rows)`). Un outil qui dessine lui-même son corps (le Graphique) lit cette place ; les autres rangent des éléments par la grille commune des tuiles (`TileGrid`), qui fait tout le calcul.

Un élément a deux réglages, communs à tous les outils à éléments, chacun déclaré par l'outil (les valeurs permises, son défaut) et choisi par tuile, le défaut présent dans le sélecteur et marqué :

- **sa largeur** : par moitiés (2 cases) ou pleine largeur. Par moitiés donne 2 colonnes dans une tuile de 4, 1 dans une tuile de 2 : un élément ne descend jamais sous 2 cases ;
- **ses lignes** : de 1 à 10 lignes de texte, à la taille du texte du thème et du réglage de taille de l'apparence — pas des fractions de case. La hauteur d'une ligne se mesure à l'affichage, jamais écrite en dur.

Ils vivent dans la config d'un outil, `item_width` (`halves`, `full`) et `item_lines` (1 à 10). Une Tâche et une routine n'en ont pas : leurs exécutions gardent une ligne fixe.

Le nombre d'éléments montrés en découle : autant que la hauteur du corps en contient, la hauteur d'un élément étant fixe (ses lignes au maximum, même quand son texte est plus court). Leurs places se calculent d'après la boîte, jamais d'après leur nombre : un élément qui arrive ou part ne déplace pas les autres ; le reste de hauteur est partagé entre ces places. Ce qui ne tient pas n'est pas montré : la dernière place du corps dit « +12 » au lieu d'un dernier élément, par le composant commun, pour tous les outils. Aucune tuile ne le dit aujourd'hui, bien que le contrat (`ToolTile`) le demande. En « selon le contenu », c'est l'inverse : le corps est aussi haut que ses éléments.

Un élément se remplit par un composant commun, dans cet ordre, coupé quand ses lignes sont épuisées :

1. sa ligne principale (un titre, un nom, un moment et un nom) ;
2. son texte, pour un outil qui en a (le début d'une note, d'une entrée de journal, d'un message) ;
3. ses champs supplémentaires, dans l'ordre de la config, compacts (`CustomFieldsDisplay`).

Ce que déclare chaque outil, ses défauts gardant l'aspect d'aujourd'hui :

| Outil | Largeur (défaut) | Lignes (défaut) | Permis |
|---|---|---|---|
| Liste | par moitiés | 2, ou 1 pour une liste à « Éléments courts » | les deux largeurs ; 1 à 10 |
| Notes | par moitiés | 4 | les deux largeurs ; 1 à 10 |
| Suivi (raccourcis, options) | par moitiés | 2 | par moitiés ; 2 : un raccourci a ses boutons |
| Journal, Messages, Fiches, Agenda, Séquence, Questionnaire, Objectif, Flashcards | pleine largeur | 2 | les deux largeurs ; 1 à 10 |
| Tâche, routine (leurs exécutions) | pleine largeur | 2 | fixes, sans réglage |
| Graphique | — | — | un dessin qui remplit son corps, sans réglage d'élément |

« Éléments courts » de la Liste disparaît, remplacé par ses lignes.

## Le mode 1 colonne

Toute tuile y est rangée comme une tuile de 2 de large, dessinée sur toute la largeur : une 2×h y reste 2×h ; une 4×h y devient 2×(h + 1), son en-tête et son résumé l'un sous l'autre, son corps de 2 cases de large ; l'Icône y prend une demi-largeur, deux par ligne ; « selon le contenu » garde la hauteur de son contenu. Le corps ne sait plus que le 1 colonne existe : il reçoit une largeur de 2, et le cas particulier de `TileGrid` (`CurrentTheme.oneColumn`) part. C'est la règle de `Grid.oneColumnSize` d'aujourd'hui, dite autrement.

## Le stockage

Une valeur texte d'une liste fermée, `tile_size` : `icon`, `2x1` … `2x10`, `4x1` … `4x10`, `4xcontent` — une brique (`docs/BRICKS.md`), son modèle, son analyse, son sélecteur. Elle remplace `display_mode` partout où il est : colonnes des zones, Tâches, routines, variables et Repères, clé de la config d'un outil, brique look (`Looks`, `LookSpec.modes` qui disparaît : toutes les tailles pour tous), schéma lu par l'IA, sauvegardes, parties, démo, Guide, exemples du prompt.

La migration est une correspondance fixe : `ICON` → `icon`, `MINIMAL` → `2x1`, `LINE` → `4x1`, `CONDENSED` → `2x2`, `EXTENDED` → `4x2`, `SQUARE` → `4x4`, `FULL` → `4xcontent` ; `short_items` vrai → 1 ligne, faux → 2. Dans la base (une `ConnectionMigration`), dans une sauvegarde ou une partie plus ancienne à l'import (`transformBackupData`). Visible : une tuile Étendu ou Carré peu remplie devient plus haute qu'aujourd'hui, du vide en bas ; aucune taille n'est devinée.

## Les thèmes

Ils reçoivent la taille au lieu du mode (`tileFrame`, `ToolCardContainer`) ; ils ne distinguent aujourd'hui que l'Icône, parfois le 2×1, et le reste pareil : les mêmes cas, rien qui change à l'écran.

## Le choix

Dans la config d'une tuile, à la place de « Mode d'affichage » : Largeur (Icône, Demi, Pleine) et Hauteur (1 à 10, « Selon le contenu » en Pleine, masquée pour l'Icône) ; dessous, pour une tuile à éléments, la largeur et les lignes d'un élément. En 1 colonne, « Selon le contenu » met la tuile en Pleine, et le sélecteur le dit.

## Hors de ce chantier

- Redimensionner sur la grille, en mode organisation, par des flèches comme on y déplace.
- Un corps pour la tuile d'une zone (ses Repères, ses outils), d'une variable (sa courbe), d'un Repère (sa phrase, ses valeurs).
- Ce que montre « selon le contenu » d'un outil à historique, coupé aujourd'hui à 10 ou à 3 (Journal, Messages, Fiches, Tâche, routine) ou jamais (Séquence) : l'item du téléphone mis de côté le 2026-11-11.

## Vérifié

Le 2026-11-11, dans le code :

- Les tuiles qui rétrécissaient (`grows` de `GridLayout`, Étendu, Carré, Complet) : en boîtes fixes, seule « selon le contenu » se mesure encore, seule sur sa ligne ; une tuile fixe prend ses cases, calculées en pixels entiers par le thème (`gridCellPx`, `gridRowPx`). Lu, pas vu à l'écran.
- La grille (`Grid` : `resize`, `move`, `pack`, `enter`, `oneColumn`) raisonne sur des rectangles et leurs chevauchements, rien de propre à un mode : des tuiles de 2 de large de hauteurs différentes côte à côte y passent. Aucun test ne les place encore : à écrire avec le code.
- Les thèmes ne distinguent que l'Icône et parfois le 2×1 (la marge du cadre, `tileFrame`).

## Ouvert

- Le nombre d'éléments d'une ligne qui tiennent dans une ligne de cases, par thème et par réglage de taille : à voir sur l'émulateur (`Pixel_4a`) ou le téléphone une fois codé.
