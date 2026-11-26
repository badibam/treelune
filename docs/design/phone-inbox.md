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
