# Des étapes de Tâche plus souples

Décidé le 2026-10-30. Spec transitoire : élaguée une fois codée, ses garanties devenues tests (`docs/reference.md`).

## Pourquoi

Deux besoins réels, qu'une Tâche ne sait pas monter aujourd'hui.

- **Les questionnaires de l'utilisateur** : le questionnaire du matin part dans sept Tâches, celui du soir dans six (« Matin → Sommeil », « Matin → État au réveil »…). Chacune a le même déclencheur avec un filtre de plus (« telle question a une réponse ») et une étape qui crée l'entrée ailleurs. Elles sont treize parce qu'une étape ne peut pas être sautée : la seule condition est celle du déclencheur, qui vaut pour toute la Tâche. Avec une condition par étape, elles deviennent deux.
- **bica** (le site de portugais, `../bica/docs/treelune-handoff.md`, et ce qu'il change pour Treelune : `../bica/docs/treelune-changes.md`) : une Tâche lancée à la main envoie à bica ce que l'apprenante sait (des listes), attend la fin de la séance (l'API différée d'aujourd'hui), puis range le résultat : une fiche par mot nouveau, sans recréer celles qui existent, une fiche par personne rencontrée, ce qu'elle a raconté ajouté à sa fiche, l'épisode validé ou à refaire.

Pas de code propre à bica, pas de routine : l'app devient plus souple, et le montage se fait avec ses briques. Chaque point étend une brique existante avant d'en ajouter une.

## 1. « Seulement si » sur une étape

Codé : `StepConditions`, `docs/CORE.md` (« Tasks »), ses garanties dans `StepConditionsTest`.

## 2. « Pour chaque élément » d'une liste

Codé : `for_each` et `{"item": {"field"}}` (`TaskSteps`, `TaskRunner.repeated`), une valeur de type liste d'une API (`ApiSettings.LIST`), `docs/CORE.md` ; ses garanties dans `StepForEachTest`. Un champ qu'un élément n'a pas est vide (la `correction` d'un tour de bica, présente ou non) ; une API différée ne se répète pas.

## 3. Ne pas recréer ce qui existe

Codé : `existing` de `create_entry` (`{"field", "then": "skip" | "update"}`, absent : toujours créer), `docs/CORE.md` ; ses garanties dans `StepForEachTest`. L'entrée trouvée est la plus récente qui tient la valeur ; l'étape la donne comme son résultat.

## 4. « Ajouter à la ligne » dans un champ de texte

Codé : `append` d'une étape qui modifie une entrée (`update_entry`, ou `create_entry` qui modifie celle déjà là), refusé sur une étape qui crée ; `docs/CORE.md` ; ses garanties dans `StepForEachTest`.

## 5. Une liste en entrée d'API

Codé : une entrée d'API de type liste (POST seul), remplie par une Sélection d'entrées (`{"selection"}`, `TaskSteps.Input.Selection`), sa période comprise ; les entrées dans l'ordre où elles ont eu lieu, un champ gardé donnant ses valeurs, plusieurs un objet par entrée sous la dernière partie de chaque chemin, l'`id` de l'entrée en option ; `docs/CORE.md` ; ses garanties dans `StepForEachTest`. Pour bica : `cards` sous `{"id", "element"}`, `reviewed_today` sous `{"source_entry", "stability"}`.

## Écarté

- **Un type d'outil propre à bica**, ou un type « séance chez un service externe » : un seul cas, une app publique sur F-Droid.
- **Une routine qui range le résultat** : « rien n'est rangé deux fois » reposerait sur un modèle.
- **Ouvrir une adresse depuis une Tâche**, et couper l'appel d'une API différée de son attente pour lire l'adresse de la séance avant : bica a une page fixe par apprenante, qu'elle ouvre elle-même.
- **Un événement à la fin des Flashcards du jour** : l'apprenante lance la Tâche à la main quand elle veut, flashcards faites ou non.
- **Composer du JSON imbriqué, ou suivre une Référence pour lire un champ de l'entrée visée** : bica reçoit des listes plates et fait les rapprochements.

## Le montage bica, une fois tout codé

Pour l'essayer, et pour le Guide ou un montage type plus tard. Réglage de l'API : différée, `session` pour l'identifiant, `sessions.php?session={id}` pour l'état, prête à `state` = `terminee`, attente d'environ 18 h au plus (une séance abandonnée lâche la Tâche avant le lendemain : une exécution qui attend met en file le lancement suivant de la même Tâche).

| # | étape | réglages |
|---|---|---|
| 1 | Appeler bica : `done`, `attempted`, `cards`, `reviewed_today`, `people_facts` (cinq Sélections) | attend `terminee` |
| 2 | Créer dans Fiches de portugais : pt, fr, element, thème, kind | pour chaque `new_elements` ; si `element` existe : ne rien faire |
| 3 | Créer dans Carnet : question, réponse, exemples | pour chaque `questions` |
| 4 | Créer dans Personnes : person, nom, rôle, description, contexte, liens, date | pour chaque `people` ; si `person` existe : ne rien faire |
| 5 | Créer dans Personnes : person, ce qu'elle a raconté (ajouté à la ligne) | pour chaque `people_facts` ; si `person` existe : la modifier |
| 6 | Créer dans Épisodes : episode, statut « validé » | seulement si `validated` ; si `episode` existe : la modifier |
| 7 | Créer dans Épisodes : episode, statut « à refaire » | seulement si `validated` est faux ; si `episode` existe : la modifier |

Les `examples` d'une question sont une liste dans un élément : ils n'entrent pas dans le Carnet tant qu'une liste dans une liste n'est pas lue (point 2). À trancher au montage : les ignorer, ou que bica les rende aussi en un texte.
