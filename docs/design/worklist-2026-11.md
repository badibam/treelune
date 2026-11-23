# Chantier de novembre 2026

Les retours du téléphone et les items décidés, classés le 2026-11-22. « À discuter » marque une question ouverte, pas une décision. Un item fait sort du fichier ; le fichier entier sort quand il est vide.

## 2. Tuile de zone, en mode icônes et en mode compact

- Distinguer l'icône de la zone de celles de ses outils : une taille différente, ou autre chose. À discuter.

## 6. L'IA

- Que l'IA, sur demande, range visuellement une zone ou l'accueil de façon pertinente, sans deviner et sans placer des coordonnées à la main. À concevoir dans une prochaine session, sans certitude qu'on le fasse. Relevé le 2026-11-23 : l'IA lit les tuiles d'une zone dans l'ordre de lecture (`ZONE_CONTENTS`) et change leur section, leur taille, leur icône, leur couleur et leur texture ; aucune position n'est dans son prompt ; les positions s'écrivent par `SectionGrid.place`. Proposition non retenue à ce stade : l'IA décide l'ordre, les tailles et les sections, l'app calcule les positions.

## Reprise

État au 2026-11-23 :

- Le fournisseur « Recette sur GitHub » est codé. Son workflow et la recette `claude-session` ont tourné sur GitHub avec un script à la place de l'app ; son code Kotlin n'a jamais tourné.
- La migration unique 119 → 120 est écrite, essayée sur une base 119 reconstruite, jamais sur les vraies données.
- Avant tout build de debug sur le téléphone : `app.treelune.debug` est en base 121, ses données sont à effacer, sur décision de l'utilisateur.

Reste, dans l'ordre : concevoir le rangement visuel ; lancer une fois `./run test` ; la passe sur le téléphone, où se décide aussi l'icône de la zone face à celles de ses outils.
