# Chantier de novembre 2026

Les retours du téléphone et les items décidés, classés le 2026-11-22. « À discuter » marque une question ouverte, pas une décision. Un item fait sort du fichier ; le fichier entier sort quand il est vide.

## 1. Plantages et bugs

- Un défaut d'affichage sur une tuile de zone de longueur « selon le contenu ».
- La fiche : le défilement n'est pas remis en haut au changement de fiche.

## 2. Tuile de zone, en mode icônes et en mode compact

- Distinguer l'icône de la zone de celles de ses outils : une taille différente, ou autre chose. À discuter.
- Les repères, placés automatiquement aujourd'hui : dans la liste des pièces de la tuile, et affichés aussi sur une tuile en mode icônes. À discuter.
- En mode compact, la tuile a 2 cases de large : les raccourcis d'outil y trouveraient place, dessinés comme en mode icônes.

## 3. Navigation et lisibilité des pages longues

- La page d'une identité : une hauteur maximale pour ses blocs, un geste pour en voir un en entier, et les boutons d'une page longue (en haut, et le retour en haut).
- La fiche : les boutons fiche suivante et précédente en haut seulement.
- Un écran « À propos » qui donne la version de l'app. À discuter.

## 4. Nouvelles fonctions dans des outils existants

- Fiche : les champs système (date de création…) affichables, ou clés de tri, avec un tri par défaut dans la config parmi tous les champs système. À discuter : l'ordre des champs affichés est aujourd'hui celui de la liste des champs supplémentaires.
- Graphique : se déplacer dans le temps et zoomer quand la période est variable. À discuter.

## 5. Modèle de données, import et export

- Le lien entre une identité et une zone, retiré : une routine de discussion qui a une identité fait la même chose. L'export inclut les identités des routines ; leur place dans la liste d'export, au premier niveau comme les API, est à discuter.
- Relier un lien introuvable, `docs/design/linked-files.md` : une partie importée (un montage partagé avec son dossier de fichiers à part) ou une sauvegarde restaurée ailleurs retrouve ses fichiers liés par leur nom et leur taille dans les dossiers ouverts ; l'import ne bloque jamais, et la même recherche reste dans Réglages › Données. Décidé.

## 6. L'IA

- `app_context` : l'alléger, ou en avoir deux versions. À discuter.
- Le fournisseur « Abonnement Claude », `docs/design/github-worker.md` (Claude Code lancé sur GitHub Actions dans le dépôt de Réglages › GitHub, le jeton de l'abonnement jamais dans l'app), ses vérifications d'abord (« Ouvert », le point 2). Décidé, à revérifier ensemble avant de le coder.
- Que l'IA, sur demande, range visuellement une zone ou l'accueil de façon pertinente, sans deviner et sans placer des coordonnées à la main. Entièrement à discuter, sans certitude qu'on le fasse.

## 7. Nouvel outil

- L'outil Schéma, le schéma écrit : `docs/design/schema.md` (config JSON au vocabulaire de Mermaid, placement par ELK, largeur, tuile, écran), ses vérifications d'abord (« Ouvert »). Décidé.

## 8. Les API

- Le retour d'une API, `docs/design/api-return.md` : `retour`, la réponse entière, d'office sur toute API ; une valeur lue par un chemin ; un texte écrit dans un champ Fichier téléchargé, le nom d'un fichier de recette résolu sur sa branche. Décidé. Les parcours d'hypnose (`docs/design/hypnosis-paths.md`) l'attendent.
- Les API non différées, trop lentes selon un utilisateur, qui voudrait l'intervalle entre deux demandes de résultat en réglage : mesurer d'abord à quelle fréquence le résultat est demandé.
