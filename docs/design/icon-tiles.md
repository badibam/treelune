# Tuiles en icônes (Icon tiles)

Conception du 2026-11-19, à implémenter puis élaguer. Deux idées venues du téléphone, conçues ensemble : le mode Icône étendu au-delà d'une case, et un raccourci sur la tuile d'une zone.

## Le besoin

D'abord l'agencement : plus de formes de tuile que l'Icône 1×1 et les tailles à texte de `docs/design/tile-sizes.md` (largeur 2 ou 4). Ensuite, sur une tuile de zone, atteindre ses pièces sans l'ouvrir.

La version la plus simple, une Icône 1×h dont l'icône reste seule en haut, ne suffit pas : une case étroite ne vaut que par ce qu'elle porte, et sans texte elle ne porte qu'une icône, un geste ou un état.

## La règle

Une tuile en icônes est sa tuile normale moins tout le texte : l'icône de la chose, et ce que son résumé dit sans mots — son geste canonique, à défaut un état. Pas de mot ; un nombre seulement là où il est l'état de l'outil (Flashcards, Messages).

## La tuile d'un outil, d'une Tâche, d'une routine

Trois formes : l'Icône (1×1, celle d'aujourd'hui), la paire en colonne (1×2), la paire en ligne (2×1). La paire en ligne n'est pas le `2x1` d'aujourd'hui, qui porte l'en-tête en texte : une taille à part.

- **Case 1** : l'icône, qui ouvre la chose ; elle porte la pastille (`WaitingMark`), comme aujourd'hui.
- **Case 2** : sous l'icône en colonne, à sa droite en ligne.

| Pièce | Case 2 | Son toucher |
|---|---|---|
| Journal, Liste, Notes, Agenda | + | une nouvelle entrée |
| Suivi | + | la saisie libre, aucun raccourci |
| Questionnaire | remplir | le remplissage qui attend, sinon un remplissage libre |
| Tâche, Séquence | play | la lancer |
| Routine | play ou bulle, selon son usage | la lancer, ou ouvrir sa conversation |
| Objectif | le verdict de la tentative en cours | ouvre l'outil |
| Flashcards | le nombre de cartes à revoir, une coche sans | ouvre l'outil (le paquet se choisit sur son écran) |
| Messages | le nombre de non lus, une coche sans | ouvre l'outil (sur « Non lus ») |
| Graphique | rien, l'icône seule centrée | |

Quand la case 2 montre ce qui attend (Flashcards, Messages), l'icône perd son point : la même chose ne se lit pas deux fois. Une case 2 d'état ouvre l'outil comme l'icône ; seul un geste fait autre chose.

## L'usage d'une routine

Quatre usages au lieu de trois : ce que font son planning et ses déclencheurs, et ce qu'elle permet en plus.

| Usage | À son heure (planning, déclencheurs) | Permet aussi | Geste de sa paire |
|---|---|---|---|
| Seule uniquement (`alone`) | la lance seule | — | play |
| Seule, ou en conversation | la lance seule | une conversation depuis sa tuile | play |
| En conversation, ou seule | invite à la conversation | d'être lancée seule (à la main, par l'IA, par une Tâche) | bulle |
| En conversation uniquement (`conversation`) | invite à la conversation | — | bulle |

`both` devient « Seule, ou en conversation », son comportement d'aujourd'hui. Touchés : le schéma lu par l'IA (`routine_schema_usage`), le planificateur (lancer ou inviter), le refus d'un lancement, les parties, le Guide, la migration.

## La tuile d'une zone

Sa config porte une liste ordonnée de pièces de la zone, chacune « avec son résumé » ou sans. Une seule liste, que chaque taille dessine.

**En bande d'icônes** — une colonne (1×h) ou une ligne (w×1, 4 au plus), de longueur libre :

- la première case est toujours l'icône de la zone, qui l'ouvre, avec sa pastille et les points de ses Repères ; une bande d'une case est l'Icône d'aujourd'hui ;
- chaque pièce prend une case (son icône, qui l'ouvre), ou deux avec son résumé (son icône et sa case 2, comme sa paire) ; une paire n'est jamais coupée ;
- ce qui ne tient pas n'est pas montré sur la tuile ; la config le dit, en nommant les pièces.

**En taille à texte** (2×h, 4×h) — la liste est le corps de la tuile, rangée sur la grille des cases, chaque pièce dessinée comme sa tuile, sans cadre (le cadre de la zone dit seul « tuile »), séparée par le séparateur du thème :

| Largeur de la zone | Avec son résumé | Sans |
|---|---|---|
| 4 | Ligne (4×1) | Minimal (2×1), deux par ligne |
| 2 | Condensé (2×2) | Minimal (2×1) |

Ce qui ne tient pas suit la règle des corps : « +N » à la dernière place.

## Ouvert

- Le mode 1 colonne : la paire en ligne, la bande en ligne de 4.
- Le stockage (`tile_size` : les paires, les bandes de chaque longueur, distinctes de `2x1` et `4x1`) et le sélecteur (une forme Texte / Icônes avant la largeur et la hauteur ?).
- La variable et le Repère : leur paire, leur place dans la bande.
- Le Suivi dans une liste de zone : sa case 2 reste la saisie libre, ou un raccourci choisi ?
