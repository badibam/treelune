# Des étapes de Tâche plus souples

Décidé le 2026-10-30. Spec transitoire : élaguée une fois codée, ses garanties devenues tests (`docs/reference.md`).

## Pourquoi

Deux besoins réels, qu'une Tâche ne sait pas monter aujourd'hui.

- **Les questionnaires de l'utilisateur** : le questionnaire du matin part dans sept Tâches, celui du soir dans six (« Matin → Sommeil », « Matin → État au réveil »…). Chacune a le même déclencheur avec un filtre de plus (« telle question a une réponse ») et une étape qui crée l'entrée ailleurs. Elles sont treize parce qu'une étape ne peut pas être sautée : la seule condition est celle du déclencheur, qui vaut pour toute la Tâche. Avec une condition par étape, elles deviennent deux.
- **bica** (le site de portugais, `../bica/docs/treelune-handoff.md`, et ce qu'il change pour Treelune : `../bica/docs/treelune-changes.md`) : une Tâche lancée à la main envoie à bica ce que l'apprenante sait (des listes), attend la fin de la séance (l'API différée d'aujourd'hui), puis range le résultat : une fiche par mot nouveau, sans recréer celles qui existent, une fiche par personne rencontrée, ce qu'elle a raconté ajouté à sa fiche, l'épisode validé ou à refaire.

Pas de code propre à bica, pas de routine : l'app devient plus souple, et le montage se fait avec ses briques. Chaque point étend une brique existante avant d'en ajouter une.

## 1. « Seulement si » sur une étape

- Toute étape peut porter une liste de conditions ; **toutes vraies**, elle se fait, sinon elle est **sautée** et la Tâche passe à la suivante. Pas de « ou » : deux étapes le font.
- Une condition est la brique Condition (`{"left", "op", "right"}`), jugée une fois au moment où l'étape s'exécute ; ses côtés sont tout ce qu'une étape sait lire : un Term (constante, variable, Lecture), une valeur rendue par une étape précédente (`result`), l'entrée ou la valeur reçue du déclencheur (`received`), et, sur une étape répétée, l'élément en cours (point 2).
- Une étape qui lit le résultat d'une étape **sautée** est sautée aussi, sans erreur. Son écran d'exécution dit « sautée », et pourquoi (sa condition fausse, ou l'étape N sautée).
- Forme proposée : `"when": [ {condition}, … ]` sur l'étape.
- Exemple : une Tâche « Matin », déclenchée par le questionnaire rempli ; une étape par destination, chacune « seulement si » sa question a une réponse (`{"left": {"received": {"field": "extra.etat_au_reveil"}}, "op": "present"}`).

## 2. « Pour chaque élément » d'une liste

- Toute étape peut se répéter pour chaque élément d'une liste ; une liste vide la fait zéro fois, sans erreur. C'est un réglage de l'étape, pas un bloc d'étapes emboîtées : aucun cas réel ne demande le bloc aujourd'hui.
- La liste vient d'une valeur rendue par une API. Une API apprend à déclarer une valeur de type **liste** (son chemin dans la réponse, `new_elements`), et les champs de chaque élément (nom, type, chemin dans l'élément : `pt`, `theme`). Pas de liste dans une liste.
- Dans l'étape répétée, une entrée peut lire un champ de l'élément en cours ; sa condition « seulement si » aussi, jugée pour chaque élément.
- Le résultat d'une étape répétée **ne se lit pas** : une étape qui le lit est refusée à l'écriture de la Tâche, en le disant (« l'étape 3 lit le résultat de l'étape 2, qui se répète »).
- **Tout ou rien** : une étape répétée compte pour un seul pas de l'historique ; un élément refusé retire ce que les précédents ont écrit, l'exécution échoue en nommant l'élément et pourquoi. Annuler retire tous ses éléments d'un coup. À vérifier en codant : qu'un pas de l'historique sait tenir plusieurs écritures (une importation le fait peut-être déjà).
- Formes proposées : `"for_each": {"result": {"step": 0, "value": "new_elements"}}` sur l'étape ; `{"item": {"field": "pt"}}` pour lire l'élément en cours.

## 3. Ne pas recréer ce qui existe

- L'étape « créer une entrée » gagne un réglage : **si une entrée de l'outil a déjà la même valeur dans [un champ choisi]** : créer quand même (aujourd'hui, et par défaut), ne rien faire, ou la modifier avec les valeurs de l'étape.
- La valeur comparée est celle que l'étape écrit dans ce champ.
- Exemples : une fiche de portugais reconnue à son `element` (ne rien faire, un épisode rejoué) ; une personne à son `person` (ne rien faire) ; un épisode à son `episode` dans un outil « Épisodes » à une entrée par épisode, dont le statut passe de « à refaire » à « validé » (la modifier).
- Forme proposée : `"existing": {"field": "extra.element", "then": "create" | "skip" | "update"}`.

## 4. « Ajouter à la ligne » dans un champ de texte

- Une étape qui modifie un champ de texte (« modifier une entrée », ou « la modifier » du point 3) choisit pour chaque valeur : **remplacer** (aujourd'hui, et par défaut) ou **ajouter à la ligne**, à la suite de ce que le champ tient.
- Exemple : le champ « ce qu'elle a raconté » d'une fiche Personne, qui grandit d'une ligne par fait que bica rend ; il vit sur la fiche parce qu'une face de Flashcards ne se compose que des champs de sa ligne. Ailleurs : tenir un journal dans une seule note.
- Forme proposée : `"append": ["extra.raconte", …]` sur l'étape, les chemins dont la valeur s'ajoute.

## 5. Une liste en entrée d'API

- Une entrée d'API peut être de type **liste**, remplie par une **Sélection d'entrées** (la brique : un outil, une période, des filtres, les champs gardés) : un seul champ gardé donne une liste de valeurs, plusieurs une liste d'objets à un niveau, nommés par leurs champs. Elle part dans le corps JSON en tableau.
- La Lecture reste à une valeur : variables, conditions, Graphiques et Objectifs en dépendent partout. « Toutes les valeurs » en réduction aurait été proposée, puis refusée, à chacun de ces endroits.
- Exemples pour bica : `cards`, les fiches de portugais filtrées « element présent », champs `id` et `element` ; `done`, les Épisodes filtrés « statut = validé », champ `episode` ; `reviewed_today`, les révisions des Flashcards de la période « aujourd'hui » (au calendrier de l'utilisateur), champs `data.source_entry` et `state.stability`. Le lien entre une révision et l'`element` de sa fiche, bica le fait.
- L'item de `TODO.md` « une Lecture qui rend plusieurs valeurs » (les trois derniers retours d'un outil, pour une API) est couvert par ce point.

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
