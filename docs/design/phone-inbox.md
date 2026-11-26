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

## En cours : cacher une tuile

L'idée : une tuile cachée n'apparaît que quand un bouton à bascule, une icône en haut à droite avec les autres, montre les cachées ; le fil d'Ariane passe sous la rangée des boutons, sur sa propre ligne (aujourd'hui dans la même rangée, entre le retour et ceux de droite, `UI.PageHeader`), sur toutes les pages.

- Se cache toute tuile d'une page : outil, variable, Tâche, routine, repère, et les zones sur l'accueil. Un seul mécanisme, celui de la grille de la page, pas un réglage par sorte d'élément.
