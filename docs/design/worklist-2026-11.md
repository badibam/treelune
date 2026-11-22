# Chantier de novembre 2026

Les retours du téléphone et les items décidés, classés le 2026-11-22. « À discuter » marque une question ouverte, pas une décision. Un item fait sort du fichier ; le fichier entier sort quand il est vide.

## 1. Plantages et bugs

- Le plantage `NoSuchMethodError: getPickImagesMaxLimit`, 0.9.11 (43), Android 10, le 2026-11-22 : la saisie d'une Fiche qui a un champ Fichier d'images ou de vidéos plante en s'ouvrant (`MultiplePicks.visual`). Le sélecteur de photos y est présent par la mise à jour de Google Play (`isPhotoPickerAvailable` vrai), mais `MediaStore.getPickImagesMaxLimit` n'existe qu'à partir d'Android 13, ou d'Android 11 avec l'extension R 2 : tester ces deux-là, comme androidx le fait pour son propre plafond.

## 2. Tuile de zone, en mode icônes et en mode compact

- Distinguer l'icône de la zone de celles de ses outils : une taille différente, ou autre chose. À discuter.
- Les repères sur la tuile de leur zone par la liste des pièces seulement, décidé le 2026-11-22 : la case « la tuile de sa zone » retirée de la config d'un repère ; une migration ajoute aux pièces de la tuile, avec leur résumé et en tête, les repères qui l'avaient cochée ; la rangée du résumé (repères, description) et les points de couleur des tuiles en icônes retirés, sous l'en-tête rien que les pièces.
- Un réglage de la zone, « Pièces : en texte / en icônes », en texte par défaut, pour les deux largeurs, décidé le 2026-11-22 : en icônes, les pièces dessinées comme sur une tuile en mode icônes, sous l'en-tête, partout où il y a la place. En largeur Pleine, la place à côté de l'en-tête reçoit les premières pièces en icônes ; en texte, l'en-tête prend toute la rangée.
- La description d'une zone, plus jamais sur sa tuile, décidé le 2026-11-22.

## 3. Navigation et lisibilité des pages longues

- La page d'une identité : une hauteur maximale pour ses blocs, un geste pour en voir un en entier, et les boutons d'une page longue (en haut, et le retour en haut).
- Un écran « À propos » qui donne la version de l'app. À discuter.

## 4. Nouvelles fonctions dans des outils existants

- Fiche : les champs système (date de création…) affichables, ou clés de tri, avec un tri par défaut dans la config parmi tous les champs système. À discuter : l'ordre des champs affichés est aujourd'hui celui de la liste des champs supplémentaires.
- Graphique : se déplacer dans le temps et zoomer quand la période est variable. À discuter.

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
