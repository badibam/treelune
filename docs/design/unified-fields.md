# Un seul système de champs — ce qui reste

Conception commencée le 2026-09-25. Les blocs A à C sont en place : le modèle (types de champ, `data` / `extra` / `state`, chronomètre, valeurs par défaut portées par un champ, CHOICE, réglages déclarés en champs) est décrit dans `docs/DATA.md`, section « Champs et entrées », et les modules de communication dans `docs/AI.md`. Reste ce qui suit : ce qui n'est pas encore dans le code, et ce qui attend d'être vu sur le téléphone.

## À mettre en œuvre

- **D. Le pointeur** : fait pour une zone et un outil (`docs/DATA.md`) ; l'entrée comme cible attend un besoin (`pointer.md`).

## Lecture seule pour l'IA (codé le 2026-10-16, à passer sur le téléphone)

Une valeur que la personne saisit et que l'IA lit sans l'écrire. La validation des actions de l'IA ne fait pas ce travail : elle porte sur tout un outil et demande à chaque fois.

- Un réglage de champ, `ai_read_only` (« Lecture seule pour l'IA »), décoché par défaut : sur un champ de l'utilisateur, après « Toujours afficher », et sur le champ saisi d'un critère d'Objectif, une fois son type choisi. Une question que pose l'IA (module de communication) ne l'a pas.
- Refusé à l'IA en conversation, à une routine et au connecteur (origine `AI` ou `EXTERNAL`, appel relayé par l'app compris : un import lancé par l'IA écrit en son nom) ; le reste de l'app l'écrit (import de la personne, démo). `ToolDataService` refuse la commande entière qui le contient, valeur effacée par `null` comprise, chronomètre lancé ou arrêté aussi, avec une erreur qui nomme les champs et demande de renvoyer sans eux (`AiReadOnly`).
- Le schéma que lit l'IA porte `"ai_read_only": true`, rendu « entered by the user, read only: never sent ».
- Cocher est permis à tous ; décocher un champ gardé sous le même nom, à une personne seulement (`tools.update`) : l'IA pourrait sinon décocher, écrire, recocher. Une config réécrite en entier par l'IA sans la clé garde la case, comme « Protéger les données enregistrées » ; seule une case envoyée décochée est refusée. Supprimer le champ reste une perte de données que la protection fait confirmer.
- Le verdict d'un Objectif n'est pas un champ : il se ferme par un réglage de l'Objectif (`missing-tools.md`, « Validation fermée à l'IA »), même règle d'origine, même règle pour le décocher.

## Ouvert

- Un champ obligatoire dans `extra` : une saisie rapide ouvrirait alors la fenêtre d'édition préremplie.
- Ce que `name` veut dire pour chaque outil (nom du raccourci dans le tracking, titre d'une note).
- Réglage d'un champ : label affiché ou non.
- Les quasi-doublons d'un CHOICE ouvert (« Travail » / « travail » / « boulot ») : aujourd'hui chacun devient une option.
- Corriger une proposition de l'IA avant de la valider, avec le composant de saisie : l'IA doit apprendre ce qui a été changé.
- Vérification sur papier restante : Graphique.
