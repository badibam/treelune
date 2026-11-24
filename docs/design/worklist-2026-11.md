# Chantier de novembre 2026

Les retours du téléphone et les items décidés, classés le 2026-11-22. « À discuter » marque une question ouverte, pas une décision. Un item fait sort du fichier ; le fichier entier sort quand il est vide.

## 2. Tuile de zone, en mode icônes et en mode compact

- Distinguer l'icône de la zone de celles de ses outils : une taille différente, ou autre chose. À discuter.

## 6. L'IA

- Que l'IA, sur demande, range et habille une zone ou l'accueil : codé (`PAGE_PLAN`, `ARRANGE_PAGE`), à essayer avec une vraie IA, `docs/design/ai-page-layout.md`.

## Reprise

État au 2026-11-23 :

- Le fournisseur « Recette sur GitHub » est codé. Son workflow et la recette `claude-session` ont tourné sur GitHub avec un script à la place de l'app ; son code Kotlin n'a jamais tourné.
- La migration unique 119 → 120 est écrite, essayée sur une base 119 reconstruite, jamais sur les vraies données.
- Avant tout build de debug sur le téléphone : `app.treelune.debug` est en base 121, ses données sont à effacer, sur décision de l'utilisateur.

Reste : la passe sur le téléphone, où s'essaie le rangement par l'IA et où se décide aussi l'icône de la zone face à celles de ses outils.
