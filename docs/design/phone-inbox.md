# Les points arrivés du téléphone (2026-11-26)

Les items de la section « En provenance du tel » de `TODO.md`, tranchés un à un, pour implémentation ultérieure.

## Décidé : « Replié au départ » du Schéma

Le bug : une boîte qui en contient d'autres, réglée « Replié au départ » = non, se replie quand même.

La cause : un schéma plus large que la place replie lui-même ses groupes, le plus large d'abord (`DiagramSceneBuilder.build`, `core/drawing/diagram/DiagramScene.kt`), en ne laissant ouverts que ceux rouverts à la main pendant la visite (`keptOpen`). Le réglage est un booléen lu `optBoolean(FOLDED, false)` (`SchemaConfig.node`) : « non » et « rien de réglé » sont la même valeur, et le repli automatique les traite pareil. La tuile passe par le même calcul au niveau texte (`tileShown`, `fit = true`).

La décision :
- Le réglage prend trois valeurs : « Oui » (replié au départ), « Non » (jamais replié par l'app), « Selon la place » (l'app replie pour tenir, comme aujourd'hui).
- Rien de réglé = « Selon la place » : les schémas existants gardent leur comportement.
- « Non » est absolu, sur la page comme sur la tuile : le repli automatique ne choisit jamais un groupe en « Non ». Un schéma qui ne tient toujours pas se réduit jusqu'à `MIN_SCALE`, puis défile sur le côté (page) ou descend de niveau de détail (tuile).
- À vérifier avant de coder : si le formulaire de config écrit aujourd'hui un `false` explicite. Si oui, une migration doit décider ce que deviennent ces `false` (les lire comme « Non » rend absolus des refus qui n'en étaient peut-être pas ; les lire comme « Selon la place » efface les « Non » posés à la main).
- Ce que touchent les trois valeurs : `DiagramNode.folded` (core, partagé avec le Parcours, qui ne règle pas de repli), `SchemaConfig`, le champ du formulaire (`SchemaToolType`, `field_folded`), le format Mermaid (`SchemaMermaid` n'écrit que `true`), ce que l'IA lit du schéma.

## Cacher une tuile

L'idée : une tuile cachée n'apparaît que quand un bouton à bascule, une icône en haut à droite avec les autres, montre les cachées ; le fil d'Ariane passe sous la rangée des boutons, sur sa propre ligne (aujourd'hui dans la même rangée, entre le retour et ceux de droite, `UI.PageHeader`), sur toutes les pages.

- Se cache toute tuile d'une page : outil, variable, Tâche, routine, repère, et les zones sur l'accueil. Un seul mécanisme, celui de la grille de la page, pas un réglage par sorte d'élément.
- Les cachées d'un groupe gardent leur groupe et vont dans sa partie cachée, une section fantôme rangée juste après lui : sa propre grille, où elles se rangent entre elles (place, taille) comme dans un groupe. La partie sans groupe a la sienne, l'accueil aussi (les groupes de zones). Les positions d'une tuile cachée valent dans cette grille, jamais dans la grille visible, qui change sans elles.
- Le stockage : un « caché » à côté de ce que chaque élément porte déjà de sa place (groupe, `grid_x`, `grid_y`, `tile_size`).
- Cacher et montrer passent par le mode Ranger (`GridEditor`), qui déplace déjà une tuile aux flèches d'une section à la voisine en changeant son groupe : une partie cachée n'y est qu'une section de plus. Descendre une tuile dans la partie cachée la cache, l'en sortir la montre. Un bouton « Cacher » dans la barre de déplacement l'envoie d'un coup dans la partie cachée de son groupe.
- Le mode Ranger montre toujours les parties cachées, même vides, pour qu'on puisse y descendre une tuile. Hors de lui, avec la bascule ouverte, une partie cachée vide ne se montre pas.
- La bascule ne se souvient pas : chaque visite repart cachées masquées.
- Un groupe dont toutes les tuiles sont cachées disparaît, titre compris, bascule fermée ; il revient avec la bascule et en mode Ranger.
- Une tuile cachée qui attend quelque chose (une marque d'attente, un chronomètre qui tourne) met sa marque sur le bouton à bascule, la tuile de la zone gardant la sienne.
- Un repère caché remonte toujours en tête de page s'il y est réglé : cacher sa tuile ne touche pas sa place en tête.
- L'IA, celle de l'app comme celle du connecteur (`ZONE_CONTENTS`, `app_context`), voit les cachées comme les autres, marquées cachées : une cachée travaille toujours, et l'ignorer ferait recréer ou casser une tuyauterie. Elle peut cacher une tuile, comme elle en déplace en rangeant une page (`docs/design/ai-page-layout.md`).
- Cacher ne touche que la page : une cachée choisie comme pièce sur la tuile de sa zone y reste, une boîte du Schéma ou un lien qui la vise l'ouvre toujours.

## Une variable à plusieurs valeurs

L'idée : une variable qui rend plusieurs valeurs d'un coup (« Apports du jour » : calories, protéines, glucides), sa série portant plusieurs champs par ligne, qu'un Graphique ou un repère lit ensemble. Aujourd'hui une variable rend une valeur, et sa série (la vue `series`, `VariableSeries`) une ligne par pas avec le seul `data.value`.

- Les termes (`Term` : une constante, une autre variable, une lecture d'entrées) se déclarent une fois dans la variable et chaque valeur a sa formule sur eux : une sélection, sa période et ses filtres écrits une fois au lieu d'être recopiés dans trois variables, chaque terme lu une fois par instant pour toutes les valeurs. Une valeur peut se calculer sur les autres termes partagés (« part des protéines = protéines × 4 / calories »).
- Un seul modèle : toute variable est une liste de valeurs, une seule par défaut. Une migration fait de la formule (ou de la constante) de chaque variable existante sa valeur unique. Jamais deux sortes de variables.
- Chaque valeur a une clé, unique dans sa variable et jamais montrée (`protein`), un libellé montré (« Protéines ») et son champ (NUMERIC, SCALE ou DURATION). Une formule la vise toujours par sa clé, `{var:ID.protein}`, même dans une variable d'une seule valeur : une valeur ajoutée plus tard ne rend ambigu rien de ce qui la lisait. À l'écran, une variable d'une valeur se dit par son nom seul, une de plusieurs par « Apports › Protéines ».
- La migration donne la clé `value` à la valeur unique de chaque variable existante : sa série garde `data.value`, ce qui la lit ne change pas ; une valeur ajoutée arrive en `data.<clé>` à côté.
- La tuile montre une ligne par valeur, dans l'ordre de la liste, sous le libellé de la variable ; une case « Sur la tuile » par valeur, cochée par défaut, retire une valeur de calcul. La taille de la tuile reste celle choisie : ce qui ne tient pas est coupé par « … », jamais la tuile ne grandit seule. Une variable d'une valeur se montre comme aujourd'hui.
- La place d'une valeur sur la tuile passe par la brique `ItemLayout`, réglée pour toute la tuile comme Notes : en demi (2 cases) le libellé au-dessus de la valeur, en entière le libellé et la valeur côte à côte ; « Une colonne » les empile déjà. Le nombre de lignes n'est pas proposé (une valeur, c'est un libellé et une valeur). Une variable d'une valeur n'en a pas besoin : sa valeur se place comme aujourd'hui selon la taille de la tuile, à côté de l'en-tête en 4×1, dessous en 2×2 (`VariableCard`, `UI.LookCard`).
- Une valeur s'écrit dans la plus grande taille qui la fait tenir sur une ligne à sa place, plafonnée, le thème donnant ses crans (le retro, des multiples entiers de son pixel) ; le libellé reste petit. Aujourd'hui elle est en `TextType.BODY` sur deux lignes, dans une demi-tuile. Même règle que celle voulue pour les icônes de zone en retro (« le plus grand cran qui tient ») : à poser une fois pour les deux.
- En taille icônes à deux cases, l'icône en case 1, la valeur en case 2, par la case que `UI.LookCard` offre déjà (`second`, que remplissent la Tâche, la routine, Messages) ; la variable gagne ainsi cette taille. Une seule valeur y va, la première de la liste parmi celles « Sur la tuile » : sans libellé, plusieurs nombres ne se distingueraient pas.
- Ce que l'IA écrit et lit d'une variable (`CREATE_VARIABLE`, `UPDATE_VARIABLE`, le connecteur) suit le modèle : une liste de valeurs, chacune sa clé, son libellé, son champ et sa formule, les termes à côté.

## Les icônes de zone

L'idée : l'icône d'une zone sur sa tuile au plus grand cran qui tient dans sa place, avec une marge minimale, les pièces grandissant avec elle. Aujourd'hui elle a la taille de toute icône de tuile (`UI.TileIcon` : la rangée d'icône moins le cadre, divisée par deux), sans regard sur la place ; une pièce en fait les deux tiers, jamais moins qu'un repère (`RetroGrid.smallIconUnit`). Rejoint l'item de `TODO.md` qui veut distinguer l'icône de la zone de celles de ses outils.

- Tous les thèmes : la même règle que la valeur d'une variable (le plus grand cran qui tient, plafonné), écrite une fois ; seuls les crans diffèrent, continus dans un thème lisse, multiples entiers du pixel en retro.
- L'icône de la zone seule, sur sa tuile : l'icône d'en-tête d'une tuile d'outil, sans pièces, ne bouge pas et laisse sa place au nom. Les pièces gardent leurs deux tiers de l'icône de la zone et grandissent avec elle ; l'écart de taille distingue la zone de ses pièces, ce qui règle l'item de `TODO.md`.
- La marge minimale est un espacement du thème, par un nom existant de `UI.Space` (jamais des dp écrits, `scripts/check_spacing.py`) ; en retro un nombre entier de pixels de sa grille.
- En taille icônes, chaque case de la bande a sa règle : l'icône de la zone au plus grand cran qui tient dans sa case, les pièces aux deux tiers.

## En cours : refaire la démo et le Guide

L'idée : une fois l'outil Parcours fait, reprendre la démo et le Guide pour montrer ce que l'app sait faire aujourd'hui (Giulia pourrait y introduire l'identité) ; le site ne change pas d'apparence, il gagne seulement une mention que l'app a évolué depuis.

- Le site cesse de lire la démo vivante. Aujourd'hui `scripts/make_site_data.py` tire la zone Italien de la démo dans `site/private/generated.json` (ses outils, sa routine, son identité, leurs icônes), et relève une empreinte des fichiers de la démo que `--check`, lancé par `./run release-tag`, compare : une démo changée bloquerait la release jusqu'à une régénération, qui réécrirait la zone du site depuis la nouvelle démo. La partie zone de `generated.json` reste donc telle qu'elle est, l'empreinte disparaît ; le script ne garde de l'app que ce qui ne casse pas l'apparence du site (types d'outils, thèmes, Android minimal, icônes). Les captures et vidéos actuelles font référence.
- Les captures françaises et les cartes du site encore en attente (`TODO.md`, « Site treelune.app ») se prennent avant de toucher la démo, sinon elles partiraient de la nouvelle.
