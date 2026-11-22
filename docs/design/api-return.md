# Le retour d'une API

Conception du 2026-11-22, à coder. Le besoin : une API dont la réponse dépend de qui l'appelle (hypnogen, qui suit les consignes d'un parcours, `docs/design/hypnosis-paths.md`) ne peut pas tout déclarer d'avance ; une recette ne rend aujourd'hui que du texte, des nombres, des booléens et des fichiers (`RecipeSpec.kt:40`).

## Décisions

- **Toute API a d'office une valeur `retour`** : la réponse entière, un objet, en plus de ses valeurs déclarées. Rien ne se déclare pour elle ; elle ne casse aucune API existante.
- **Une valeur se lit par un chemin**, plus seulement par son nom : `{"result": {"step": 2, "value": "retour.seance.titre"}}`. Vaut pour `retour`, pour une valeur déclarée de type objet et pour les clés de « Assembler ». L'étape répétée (`for_each`) parcourt une liste trouvée à un chemin, ses éléments lus par leurs champs sans déclaration.
- **Le type d'une valeur lue dans `retour` vient du champ où elle s'écrit**, comme celui d'une constante.
- **Un texte écrit dans un champ Fichier est téléchargé** : une adresse telle quelle (déjà le cas, `TaskRunner.kt:472`) ; pour une recette, un nom de fichier de `result/` résolu en adresse sur la branche de son appel au moment de l'écriture, comme `RecipeCall.values` le fait aujourd'hui pour une valeur déclarée Fichier.
- **Les valeurs déclarées restent**, pour ce qu'une API promet toujours : vérifiées à l'enregistrement de la Tâche, proposées par l'éditeur selon le champ, absentes = l'appel échoue à la réception, avant toute écriture ; le contrat lisible dans Réglages et par « Essayer ». `retour` porte la partie ouverte, dont la forme dépend de qui appelle.
- **Une clé ou un chemin absent de `retour`** échoue à l'étape qui le lit, en le nommant ; jamais refusé à l'enregistrement, faute de déclaration.

## Ouvert

- « Essayer » qui propose les chemins vus dans une réponse réelle, pour compenser l'absence de contrôle à l'enregistrement.
