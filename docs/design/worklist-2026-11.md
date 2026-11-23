# Chantier de novembre 2026

Les retours du téléphone et les items décidés, classés le 2026-11-22. « À discuter » marque une question ouverte, pas une décision. Un item fait sort du fichier ; le fichier entier sort quand il est vide.

## Base de données

La 0.9.11 publiée et le téléphone sont en base 119. Les migrations 120 et 121 n'ont tourné nulle part : elles se fondent avec ce qui suit en **une seule migration 119 → 120**, écrite une fois la liste définitive et validée par l'utilisateur, avant la première passe sur le téléphone. Aucune migration n'est écrite sans cette validation.

- Déjà codé en 120 : `tile_pieces_icons`, les repères cochés « la tuile de sa zone » passés dans les pièces.
- Déjà codé en 121 : `zones.identity_id` retiré.
- À ajouter, le modèle étant déjà codé : `files.linked` (`INTEGER NOT NULL DEFAULT 0`, à 1 pour toute ligne qui a une `uri`), et le même champ dans les copies de `files` gardées par l'historique (`HistoryCopies`).

## 2. Tuile de zone, en mode icônes et en mode compact

- Distinguer l'icône de la zone de celles de ses outils : une taille différente, ou autre chose. À discuter.

## 6. L'IA

- Le fournisseur « Recette sur GitHub », `docs/design/github-worker.md` : réécrit le 2026-11-22 sous forme générique (l'app ne sait rien de Claude, la session Claude est une recette du dépôt de l'utilisateur). Mesuré sur GitHub : le job éveillé entre deux tours et le cache de préfixe. Décidé le 2026-11-23 : le dialogue du job avec la recette, son délai d'attente, sa surveillance de la branche. Prochain morceau : le code.
- Que l'IA, sur demande, range visuellement une zone ou l'accueil de façon pertinente, sans deviner et sans placer des coordonnées à la main. Entièrement à discuter, sans certitude qu'on le fasse.
