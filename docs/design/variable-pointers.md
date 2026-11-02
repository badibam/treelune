# Pointer une variable dans le message d'une routine

Conception du 2026-11-02, **à revérifier entièrement avant tout code** : écrite en discutant, sans relire le code des pointeurs en entier (`EnrichmentText`, `EnrichmentProcessor`, `PointerSelector`, `SelectionPicker`). Rien n'est implémenté.

## Le besoin

Au rituel du 2026-11-01, l'IA disait manquer les constantes du pied sain (`pied_sain_grand_diametre`, `pied_sain_petit_diametre`) : le message d'une routine ne sait pas les joindre. Le sélecteur de pointeurs propose une zone, un outil, une entrée ; `EnrichmentText.place` et `EnrichmentProcessor.generatePointerQueries` refusent ou ignorent `VARIABLE`.

## Le modèle

Une variable est une valeur que l'app sait calculer à n'importe quel instant, chacun de ses termes se plaçant par rapport à l'instant lu. Une série (`docs/design/variable-series.md`) n'en est qu'un cas : la même valeur calculée à la fin de chaque pas. D'où ce qu'un pointeur vers une variable peut joindre :

- **Sa valeur à un instant**, toute variable : au départ de l'exécution par défaut, ou à un instant choisi par la brique Instant, fixe ou relatif au départ de l'exécution (comme la période d'un pointeur d'outil). « Pied sain : grand diamètre = 24,5 cm » ; « le solde calorique à la fin d'hier », lu par le bilan du matin.
- **Ses valeurs sur une période**, une variable à série seulement : comme les entrées d'un outil, une valeur par pas, filtrables sur `data.value` et `timestamp` (« le jeûne des 7 derniers jours », « les nuits où le jeûne < 14 h »). La période doit avoir un début, comme dans une Lecture.
- **Sa définition**, à joindre ou non : la formule et ce qu'elle lit, comme `attach.config` d'un outil, pour que l'IA sache d'où vient le chiffre.

Une valeur passée n'est juste que si les termes de la variable se placent par rapport à l'instant lu ; une constante écrite à la main donne sa valeur d'aujourd'hui à tout instant. L'app ne le vérifie pas, comme pour les séries.

Ce choix revient sur la conception des séries, qui excluait qu'un pointeur vise une variable.

## Ce qui va avec

- **Le pointeur d'une zone** joint aussi ses variables, leur valeur au départ de l'exécution, comme il joint ses outils.
- **Sous un masque**, pointer une variable, c'est lire ce qu'elle lit : refusé comme le serait un pointeur vers ces outils.
- **Une variable qui ne se calcule pas** (une formule cassée, un terme sur un outil supprimé) : l'exécution le dit, jamais une valeur vide ou zéro joints sans un mot.
- **Pour l'IA**, une durée ou une date jointe en ISO 8601 (`PT16H20M`), jamais en millisecondes, comme tout ce qui sort vers elle (`CommandExecutor`).
- **L'IA et le connecteur** écrivent ce pointeur : la description du message d'une routine (`ai_prompt_chunks.xml`, ROUTINE_CONFIG) le dit, avec un exemple.

## Ouvert

- La forme stockée du pointeur : la sélection d'une variable avec un instant, à côté de sa période ; à poser une fois le code des pointeurs relu.
- Ce que le sélecteur montre pour une variable sans série (l'instant seul) et à série (l'instant ou la période).
- Ce qui manque encore à cette liste : la relecture du code dira le reste.
