# Chantier de novembre 2026

Les retours du téléphone et les items décidés, classés le 2026-11-22. « À discuter » marque une question ouverte, pas une décision. Un item fait sort du fichier ; le fichier entier sort quand il est vide.

## 2. Tuile de zone, en mode icônes et en mode compact

- Distinguer l'icône de la zone de celles de ses outils : une taille différente, ou autre chose. À discuter.

## 5. Modèle de données, import et export

- Le lien entre une identité et une zone, retiré : une routine de discussion qui a une identité fait la même chose. L'export inclut les identités des routines ; leur place dans la liste d'export, au premier niveau comme les API, est à discuter.
- Relier un lien introuvable, `docs/design/linked-files.md` : une partie importée (un montage partagé avec son dossier de fichiers à part) ou une sauvegarde restaurée ailleurs retrouve ses fichiers liés par leur nom et leur taille dans les dossiers ouverts ; l'import ne bloque jamais, et la même recherche reste dans Réglages › Données. Décidé.
- Le lien attendu, et les liens absents grisés dans « Que jouer ? », à faire avec « Relier un lien introuvable » qu'il complète. Le besoin : monter dans l'app une bibliothèque complète depuis les fichiers du PC (une série de volumes numérotés, dans une Fiche), alors que le téléphone n'en porte qu'une partie à la fois, les volumes y entrant et en sortant selon la place. Décidé.
  1. Le lien attendu : `LINK_FILE` (connecteur et IA) accepte, au lieu de `folder_id` + `path`, la seule description d'un fichier (nom, taille à l'octet, format, durée), lue sur le PC par un script ; il prépare un lien sans adresse sur le téléphone, que l'entrée fait sien comme un autre. Aujourd'hui `uri` nulle veut dire « fichier copié » : le lien attendu a besoin d'une autre marque, et chaque lecteur de `files` qui teste `uri == null` est à relire. Un lien attendu se comporte en tout comme un lien introuvable : dit sur la fiche, refusé au lancement, gardé par la sauvegarde et l'export.
  2. La recherche de « Relier » (par nom et taille, dans tous les dossiers ouverts, à l'import, à la restauration et au bouton « Chercher les fichiers introuvables » de Réglages › Données) relie aussi les liens attendus, le fichier trouvé leur donnant leur adresse. Le geste : copier un volume sur le téléphone, toucher le bouton, ses fiches se relient ; le retirer les rend de nouveau introuvables sans rien perdre, le remettre et toucher le bouton les relie encore.
  3. « Que jouer ? » de la Séance « Son ou vidéo » (`LaunchDialog`) : une entrée dont le média est introuvable ou attendu reste à sa place dans la liste, grisée, avec sous son nom « Pas sur ce téléphone » ; touchable, sa fenêtre montre son nom et ses champs « Montrés avant de lancer », « Lancer » y étant déjà grisé (codé). Les cacher est écarté : dans une bibliothèque ordonnée, voir la suite non copiée fait partie de la vue d'ensemble. Le gris demande de tester chaque lien à l'ouverture de la liste, ce que la conception excluait (« pas de vérification à l'avance de tous les liens ») ; un lien attendu n'a rien à tester. Pour une liste d'environ 80 entrées, le coût de ces requêtes à Android (sans lire les fichiers) est à mesurer sur le téléphone avant de lever l'exclusion.
  Ouvert, à ne pas coder maintenant : un filtre « seulement ce qui est sur le téléphone », pour une bibliothèque sans ordre de progression où les présents sont épars.

## 6. L'IA

- `app_context` : l'alléger, ou en avoir deux versions. À discuter.
- Le fournisseur « Abonnement Claude », `docs/design/github-worker.md` (Claude Code lancé sur GitHub Actions dans le dépôt de Réglages › GitHub, le jeton de l'abonnement jamais dans l'app), ses vérifications d'abord (« Ouvert », le point 2). Décidé, à revérifier ensemble avant de le coder.
- Que l'IA, sur demande, range visuellement une zone ou l'accueil de façon pertinente, sans deviner et sans placer des coordonnées à la main. Entièrement à discuter, sans certitude qu'on le fasse.

## 7. Nouvel outil

- L'outil Schéma, le schéma écrit : `docs/design/schema.md` (config JSON au vocabulaire de Mermaid, placement par ELK, largeur, tuile, écran), ses vérifications d'abord (« Ouvert »). Décidé.

## 8. Les API

- Le retour d'une API, `docs/design/api-return.md` : `retour`, la réponse entière, d'office sur toute API ; une valeur lue par un chemin ; un texte écrit dans un champ Fichier téléchargé, le nom d'un fichier de recette résolu sur sa branche. Décidé. Les parcours d'hypnose (`docs/design/hypnosis-paths.md`) l'attendent.
- Les API non différées, trop lentes selon un utilisateur, qui voudrait l'intervalle entre deux demandes de résultat en réglage : mesurer d'abord à quelle fréquence le résultat est demandé.
