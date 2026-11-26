# Les points arrivés du téléphone (2026-11-26)

Les six items de la section « En provenance du tel » de `TODO.md`, confrontés au code. Un seul est tranché ; les autres gardent ce que le code a montré et ce qui reste à trancher, pour reprendre sans relire le code.

## Décidé : « Replié au départ » du Schéma

Le bug : une boîte qui en contient d'autres, réglée « Replié au départ » = non, se replie quand même.

La cause : un schéma plus large que la place replie lui-même ses groupes, le plus large d'abord (`DiagramSceneBuilder.build`, `core/drawing/diagram/DiagramScene.kt`), en ne laissant ouverts que ceux rouverts à la main pendant la visite (`keptOpen`). Le réglage est un booléen lu `optBoolean(FOLDED, false)` (`SchemaConfig.node`) : « non » et « rien de réglé » sont la même valeur, et le repli automatique les traite pareil. La tuile passe par le même calcul au niveau texte (`tileShown`, `fit = true`).

La décision :
- Le réglage prend trois valeurs : « Oui » (replié au départ), « Non » (jamais replié par l'app), « Selon la place » (l'app replie pour tenir, comme aujourd'hui).
- Rien de réglé = « Selon la place » : les schémas existants gardent leur comportement.
- « Non » est absolu, sur la page comme sur la tuile : le repli automatique ne choisit jamais un groupe en « Non ». Un schéma qui ne tient toujours pas se réduit jusqu'à `MIN_SCALE`, puis défile sur le côté (page) ou descend de niveau de détail (tuile).
- À vérifier avant de coder : si le formulaire de config écrit aujourd'hui un `false` explicite. Si oui, une migration doit décider ce que deviennent ces `false` (les lire comme « Non » rend absolus des refus qui n'en étaient peut-être pas ; les lire comme « Selon la place » efface les « Non » posés à la main).
- Ce que touchent les trois valeurs : `DiagramNode.folded` (core, partagé avec le Parcours, qui ne règle pas de repli), `SchemaConfig`, le champ du formulaire (`SchemaToolType`, `field_folded`), le format Mermaid (`SchemaMermaid` n'écrit que `true`), ce que l'IA lit du schéma.

## À concevoir

### Cacher un outil

L'idée : un outil caché n'apparaît que lorsqu'un bouton à bascule, en haut à droite avec les autres, montre les cachés ; le fil d'Ariane passe sous la ligne des boutons, sur sa propre ligne.

Le code : le fil d'Ariane est aujourd'hui dans la même rangée que les boutons, entre le retour et ceux de droite (`UI.PageHeader`, `core/ui/UI.kt`), et n'existe que sur un lieu empilé (`PlaceStack.breadcrumb`). Le passer sur sa ligne vaut pour toutes les pages ; c'est le morceau simple.

À trancher :
- Ce qui se cache : les outils seuls, ou toute tuile d'une page (variable, Tâche, routine, repère), et les zones de l'accueil.
- Où vit « caché » : dans la config de l'outil, ou dans la disposition de la page (la grille).
- La bascule : se souvient-elle de son état d'une visite à l'autre.
- L'IA et le connecteur voient-ils les cachés (`app_context`, `ZONE_CONTENTS`).

### Plusieurs variables en une

L'idée, telle que notée : « possible via une vue ? ». Le code : une vue (`core/views/View.kt`) rend des lignes calculées lues comme des entrées ; la série d'une variable en est déjà une, une valeur par ligne (`VariableSeries`).

À trancher d'abord : ce que veut dire « plusieurs en une » — une variable dont chaque ligne porte plusieurs champs, ou une variable qui regroupe d'autres variables existantes.

### Les icônes de zone en retro

L'idée : comme les icônes de pièces ont gagné leur propre cran de pixels, donner à l'icône d'une zone sur sa tuile le plus grand cran entier qui tient dans la place, avec une marge minimale ; ou simplement l'agrandir, pour que les pièces grandissent avec.

Le code : l'icône de la zone sur sa tuile a la taille de toute icône de tuile (`UI.TileIcon` : la rangée d'icône moins le cadre, divisée par deux), sans regard sur la place libre ; une pièce en fait les deux tiers, jamais moins qu'un repère (`RetroGrid.smallIconUnit`), et suit donc.

À trancher : retro seul ou tous les thèmes ; l'icône de la zone seule ou toute icône de tuile. Rejoint l'item « À concevoir » de `TODO.md` qui veut distinguer, en mode icônes et en compact, l'icône de la zone de celles de ses outils : les deux se décident ensemble.

## Attendent l'outil Parcours

### Intégrer le projet parcours

Le projet voisin `~/Projets/OUTILS/parcours` (Python, coaching de dessin, livrets imprimés, médiathèque lue en seule lecture) : sa partie livret resterait hors de l'app sans en être déconnectée, par une API ou en local avec un connecteur, l'ordinateur restant le camp de base de l'IA ; côté app, une version interactive et multimédia du parcours, par l'outil Parcours. À concevoir une fois l'outil Parcours fini.

### Refaire la démo et le Guide

Une fois l'outil Parcours fait, la démo et le Guide repris pour montrer ce que l'app sait faire aujourd'hui ; Giulia pourrait y introduire l'identité.

Le piège relevé : le site lit la zone Italien de la démo (`scripts/make_site_data.py` → `site/private/generated.json`) et ses captures sont prises sur la démo. Refaire la démo change le site au prochain `./run site-deploy`, alors que le site ne doit pas changer d'apparence (il gagne seulement une mention que l'app a évolué depuis). Avant de toucher la démo : figer ce que le site en lit.
