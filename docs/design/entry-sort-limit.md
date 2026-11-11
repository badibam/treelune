# Tri et limite dans la sélection d'entrées

Conception du 2026-11-11, à implémenter puis élaguer.

## Le besoin

Donner « les 3 Escales les plus récentes » à un pointeur : aujourd'hui une sélection choisit lesquelles (outil, période, filtres, champs gardés), jamais dans quel ordre ni combien. Le tri existe, mais à part, à côté de la seule source d'une Séance média (`media_sort`) ; il a été laissé hors de la sélection parce qu'une lecture ne triait pas, et « la dernière » d'une lecture reste la dernière par date, un ordre écrit dans le code que personne ne choisit. Une lecture a pourtant besoin d'un ordre dès qu'elle prend « la moyenne des 5 dernières pesées » : la raison de laisser le tri à part ne tient plus.

Premier usage : la Tâche qui remplacera la routine « Préparer la séance » (TODO, « À concevoir »), dont un pointeur prend les 3 Escales les plus récentes en entier.

## Ce que dit la sélection

Lesquelles, dans quel ordre, combien : `{"target", "period", "filters", "fields", "sort", "limit"}`.

- **Le tri** est la brique Tri (`EntrySort`) entière, plusieurs clés appliquées l'une après l'autre, chacune un champ et un sens. Il entre dans la sélection ; `SettingNode.Sort` à côté d'une `SettingNode.Selection` disparaît.
- **Jamais vide.** Une sélection neuve arrive avec une clé écrite, « Date · Décroissant » (`timestamp`), « Créée · Décroissant » (`created_at`) pour un outil dont les entrées n'ont pas de date. Elle s'affiche et se change comme toute clé ; la dernière ne se retire pas, elle se remplace. Le texte « La plus récente en tête » (`sort_none`) disparaît : le sélecteur montre ce qui est stocké.
- **Le défaut, à un seul endroit** : une fonction de la brique (`EntrySort.defaultFor(tool)`), appelée par le sélecteur à la création, par la migration, et par l'écriture d'une sélection arrivée sans tri — l'IA, dans l'app ou par le connecteur. Cette dernière la complète une fois, puis la stocke complétée ; le schéma que l'IA lit le dit (« absent : Date · Décroissant »). Défaut validé par l'utilisateur le 2026-11-11.
- **La limite**, `"limit": N`, absente pour toutes ; dans le sélecteur, une ligne sous le tri, « Les N premières », un champ nombre, vide pour toutes. Ordre d'application : période et filtres, puis tri, puis limite.
- **Avec un regroupement** (une lecture, un graphique), la limite porte sur les entrées, avant le regroupement : « les 20 derniers moments partagés, regroupés par personne ». Un classement de groupes est autre chose (« Idée sans besoin »).

## Les clés de tri

Les clés proposées sont les champs filtrables d'un outil (`EntryFilters.filterableFields`), plus les clés d'état **triables**. Une clé d'état déclare à part si elle est filtrable et si elle est triable (`StateField`) : la position d'une Note ou d'un élément de Liste (`ManualOrder.POSITION`, `filterable = false` aujourd'hui) devient triable sans devenir filtrable. Suivre l'ordre d'une liste de Notes s'écrit « Position · Croissant » ; aucune clé « ordre de l'outil » n'existe, ni de défaut propre à un type.

## Où tri et limite valent

| Sélection | Tri et limite |
|---|---|
| Pointer d'un message, d'une étape `run_routine` | oui ; le rendu suit le tri |
| Entrée d'API de type liste d'une Tâche | oui |
| Source d'une Séance média | oui ; `media_sort` y entre |
| Lecture (Repère, variable, condition, critère d'Objectif, graphique) | oui |
| Série d'une variable (`VariableSeries`) | oui, deux clés seules : la date, la valeur |
| Une zone (la période seule) | refusés : « une zone se limite par sa période » — sa lecture et son rendu vont outil par outil |
| Déclencheur « Une entrée est écrite » | refusés, comme la période : il juge une seule entrée |

Le refus passe par `EntrySelection.problem`, comme celui des champs gardés ou de la période là où ils n'ont pas de sens.

## Les réductions

- **« Dernière » devient « Celle de la première entrée »** : la valeur de l'entrée en tête dans l'ordre de la sélection. Avec le tri par défaut, toute lecture d'aujourd'hui donne la même valeur. Ce qu'elle ouvre : trier par un champ et en lire un autre (« le Rythme de la séance la mieux notée »), ce que Maximum ne fait pas.
- **`EARLIEST` et `LATEST` fusionnent dans `MIN` et `MAX`** : le même calcul, sur une date ou une heure. Le libellé suit le type (« Minimum » pour un nombre, « La plus tôt » pour une date ou une heure) ; une seule notion stockée par calcul.

## Côté IA

`tool_data` prend déjà `sort` et `limit`. Le schéma d'une sélection (`selection_schema`, et ce que `SchemaModelView` en tire) prend les mêmes clés que la commande, avec le défaut écrit. Les exemples du prompt (`scripts/check_prompt_examples.py`) et le Guide (`scripts/check_guide.py`) suivent.

## Migration

Une seule montée de version de la base :

- chaque sélection stockée qui accepte un tri reçoit sa clé explicite ; une zone et un déclencheur ne reçoivent rien ;
- `media_sort` passe dans `media_source.sort` ;
- `LAST` devient la nouvelle réduction, `EARLIEST` devient `MIN`, `LATEST` devient `MAX`, partout où une réduction est stockée (une lecture, un regroupement).

L'inventaire des endroits où une sélection est stockée est à faire en codant, pas déjà connu : pointeurs des messages de routine, blocs d'étape `run_routine`, entrées d'API et déclencheurs des Tâches et des routines, Repères, variables, graphiques, critères d'Objectif, Séance média, la démo et une partie exportée (un fichier d'un format antérieur, relu par `versioning`).

## Ouvert

- Le nom de la nouvelle réduction dans le code (`FIRST`, `FIRST_ENTRY`) et sa phrase en anglais.
- Le libellé des sens pour une clé de date (« Croissant » ou « La plus ancienne d'abord »).
