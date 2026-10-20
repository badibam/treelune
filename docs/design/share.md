# Partager une partie de l'app — conception

Conçu le 2026-10-20. Sortir des zones, des outils, des variables et des routines dans un fichier que l'app relit pour les recréer, avec leurs entrées ou sans : un modèle qu'on donne (une zone Cuisine vide), une base qu'on donne avec son contenu (une table Aliments), une zone qu'on déplace vers un autre téléphone, une zone supprimée qu'on reprend dans une sauvegarde. Dupliquer un outil dans l'app existe déjà (`tools.duplicate`) ; lire ses entrées dans un tableur est l'export CSV (`export.md`).

## Les écrans

Réglages › Données porte deux boutons, « Exporter… » et « Importer… », qui remplacent l'export et l'import de sauvegarde. Pas de raccourci depuis le menu d'une zone ou d'un outil : un seul endroit, un raccourci viendra si l'usage le montre.

**Exporter** commence par un choix : « Une partie » (par défaut) ou « Tout », la sauvegarde complète d'aujourd'hui.

```
←  Exporter

   (•) Une partie — à partager, ou à reprendre ailleurs
   ( ) Tout — une sauvegarde complète de l'app

   ── Perso ──────────────────────
   [~] Cuisine  (3 sur 5)               ▾
         Repas
         [x] Repas          Ses 214 entrées  [●  ]
         [x] Aliments       Ses 312 entrées  [●  ]
         Analyse
         [x] Calories (Graphique)
         [x] Variable « objectif_calorique »
         [ ] Routine « Bilan du dimanche »
   [ ] Course                           ▸

   [ Créer le fichier ]
```

- Deux niveaux qu'on coche, la zone et ses éléments. Les groupes de zones sont des titres de section, comme sur l'accueil ; les groupes d'outils, des petits titres dans la zone ouverte, recréés avec leurs outils.
- Une zone est fermée par défaut. Sa case prend tout ce qu'elle tient ; une partie prise la montre à moitié cochée (« 3 sur 5 »).
- Un outil coché qui a des entrées porte un interrupteur « Ses N entrées », éteint par défaut : un modèle se partage plus souvent qu'un contenu, et des données ne partent pas par mégarde. Un graphique, une variable, une routine n'en ont pas.
- Le fichier écrit, l'écran dit son nom, le nombre d'éléments et d'entrées, avec [ Partager ] (menu de partage d'Android, `FileProvider`).

**Importer** lit le fichier choisi.

- Une sauvegarde complète offre « Reprendre une partie — s'ajoute à ce que tu as » (par défaut) ou « Tout restaurer — remplace toute l'app par cette sauvegarde ». Tout restaurer cache les cases, dit ce qui est remplacé (zones, données, réglages, fournisseurs d'IA, conversations) et demande confirmation, comme aujourd'hui.
- Un partage n'offre que la reprise d'une partie : tout restaurer depuis quelques outils viderait l'app.
- Reprendre une partie montre le même arbre de cases que l'export. Chaque zone va dans une nouvelle zone (par défaut) ou dans une zone existante, choisie dans une liste où une zone du même nom vient en premier.

```
←  Importer
   Cuisine_2026-10-20_0930.zip

   Cuisine                Dans : (•) une nouvelle zone
                                 ( ) une zone existante [ Cuisine ▾ ]
     Repas
     Aliments             312 entrées
     Calories (Graphique)
     Variable « objectif_calorique »
       ⚠ Ce nom est déjà pris : elle arrive sous « objectif_calorique_2 ».
     Hydratation (Messages)
       Notifications : 3 par jour, de 9 h à 18 h.

   [ Importer ]
```

## Le fichier

- Une sauvegarde réduite à ce qui est coché : le zip de la sauvegarde (`backup.json`, `images/`), son numéro de version, les mêmes transformations pour un fichier plus ancien, le même refus d'un fichier plus récent (`docs/DATA.md`, « Export/import format », qui prévoyait cet import partiel). Une marque dans ses métadonnées dit qu'il est partiel.
- Les images d'un champ Image partent avec les entrées.
- Écrit dans le dossier de l'app choisi une fois (`AppSettings.DATA_FOLDER`, celui des sauvegardes automatiques). Nom : celui de la zone quand il n'y en a qu'une (`Cuisine_2026-10-20_0930.zip`), sinon `treelune_partage_2026-10-20_0930.zip`, passé par une fonction de nettoyage unique ; `_2`, `_3`… quand le nom existe.
- Ne partent jamais : les fournisseurs d'IA (leurs clés), les identités, les conversations, les réglages de l'app, l'historique des écritures.

## Les liens

Presque tout lien passe par une chose `{"kind", "id"}` (`docs/BRICKS.md`) ; une routine nomme en plus un fournisseur et une identité (`AutomationEntity`). Deux familles.

**Un lien qui rendrait une configuration incorrecte bloque le fichier.** Tant qu'un élément coché vise un élément qui ne l'est pas, [ Créer le fichier ] reste inactif et dit combien de liens sont à régler ; chacun est écrit sous son élément. Rien n'est coché d'office : on coche la cible, ou on décoche l'élément.

- Un outil construit d'après un autre outil ou une variable : un graphique, un objectif, une condition, tout ce qui passe par une Sélection d'entrées, une Lecture ou un Terme.
- Une variable dont la formule lit un outil ou une autre variable.
- Un champ RÉFÉRENCE dont la config vise un outil.
- Une entrée qui en désigne une autre (un repas, « Riz ») : les données de l'outil désigné doivent partir aussi.

```
   [x] Calories (Graphique)
       ⚠ Ce graphique est tracé d'après Poids : coche Poids, ou décoche Calories.
   [x] Repas          Ses 214 entrées  [●  ]
       ⚠ Les repas désignent des aliments : allume les entrées d'Aliments, ou éteins celles de Repas.
```

Pour un modèle, la cible part sans ses entrées et arrive vide. Ainsi un fichier n'a jamais de lien manquant, et l'import passe par les vérifications ordinaires (`tools.create` et les autres), les identifiants remplacés par les nouveaux. L'import partiel d'une sauvegarde complète suit la même règle sur ses cases.

**Un lien dont l'absence est un état que l'app connaît prévient, et arrive vide.**

- Le fournisseur d'une routine : sans lui, elle n'est ni allumée ni lancée. Une routine arrive donc toujours éteinte.
- L'identité d'une routine : sans elle, la routine parle par l'IA de l'app.
- Un pointeur du message d'une routine vers une cible absente : affiché « supprimé » (`pointer_target_deleted`).

```
   [x] Routine « Bilan du dimanche »
       ⚠ Elle arrivera sans fournisseur d'IA ni identité : à choisir avant de l'allumer.
```

Écarté : rebrancher un lien à l'arrivée sur un outil qu'on a déjà. Un lien vise aussi des champs (ce qu'un graphique trace, filtre, compare), que l'outil d'arrivée n'a presque jamais à l'identique ; il faudrait un écran de correspondance champ par champ.

## À l'arrivée

- Tout s'ajoute, rien n'est remplacé. Zones, outils et routines gardent leur nom, même pris. Une variable, au nom unique dans l'app, prend `_2`, et l'écran le dit ; les formules la suivent, qui la désignent par son identifiant.
- Les rappels arrivent tels qu'ils ont été configurés ; l'écran dit ceux de chaque outil.
- L'import est un pas ordinaire de l'historique (`undo-history.md`), annulable d'un coup.

## Hors de cette version

- L'IA et le connecteur n'exportent ni n'importent une partie : un geste de l'utilisateur.
- Ouvrir un fichier depuis le gestionnaire de fichiers ou le menu de partage d'Android : on passe par « Importer… ».
- Des modèles fournis avec l'app (`TODO.md`, « Idée sans besoin ») : des fichiers de ce format.

## Implémentation

Le découpage, un commit chacun ; les trois premiers sont faits (`BackupPart`, `PartSelection`, `AppFolder`, `backup.export` avec `selection` ; `PartLinks` ; `PartImport`, `backup.import_part`).

1. La sauvegarde partielle côté service — fait.
2. Le contrôle des liens : les deux familles sur une sélection, le service refusant aussi une sélection qui garde un lien bloquant ; des tests — fait.
3. L'import partiel (`backup.import_part`) : une sélection et la zone d'arrivée de chaque zone, tout par les services ordinaires dans l'ordre des dépendances (comme `DemoService.build`), un seul pas de l'historique — fait.
4. L'écran Exporter : l'arbre de cases lu d'une sauvegarde complète de l'app (`build()`), la même fonction qui lira le fichier à l'import.
5. L'écran Importer, reprendre une partie.
6. Tout restaurer devenu un choix de l'import, les anciens boutons et leurs textes retirés ; `docs/DATA.md`, le Guide s'il cite Réglages › Données, une section de la recette.

Décidé en séance :

- Un lien se repère sans lire sa forme : un élément dépend d'un autre dès que l'identifiant de l'autre apparaît comme un mot entier (une suite de `[A-Za-z0-9_-]`) n'importe où dans son texte — config, formule, entrées, message d'une routine, JSON rangé dans une chaîne compris. La même fonction remplace les identifiants à l'import. Le message d'un lien est donc générique (« Calories lit Poids : coche Poids, ou décoche Calories ») ; entre entrées, celui ci-dessus.
- Une routine part avec sa session graine et son message, sans fournisseur, identité ni exécutions ; une zone part sans son identité (`BackupPart`).
- Un nom de zone qui ne fait pas un nom de fichier refuse le partage en le disant, comme l'export CSV : pas de nom de repli.
- Les identifiants : l'import tire tous les nouveaux d'abord, les remplace dans tout le fichier en une passe, puis les donne aux services ; `GivenId` accepte, venant de l'app, ceux que l'import en cours a tirés, ceux-là seulement.
- Les groupes : une nouvelle zone arrive dans son groupe de l'accueil, ajouté s'il manque (comme la démo) ; les groupes d'outils manquants s'ajoutent à la fin d'une zone existante qui reçoit des outils.
- Une image déjà manquante à l'export : son entrée arrive sans elle, et l'écran le dit. Refuser rendrait le fichier inimportable pour une photo que personne ne peut plus fournir.
- Un import refusé en chemin retire ce qu'il a écrit, comme la démo ; les planificateurs attendent pendant l'import (`SchedulersHeld`).
- Une restauration complète refuse un fichier partiel, dès le service.
- La liste d'accès d'une routine qui vise un élément non coché prévient, et garde la ligne : elle n'atteint rien, comme celle d'un élément supprimé (l'écran et l'IA la disent « supprimé »). La retirer pourrait vider la liste, et une liste vide ouvre toute l'app.

Proposé, à trancher avant le commit qui le rencontre :

- (4) « Tout » écrit dans le même dossier que la partie, pour que Partager marche aussi ; sans dossier, l'écran demande d'en choisir un (le sélecteur de la sauvegarde automatique). `backup.export` avec `uri` disparaît alors.
