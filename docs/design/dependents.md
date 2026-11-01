# Ce qui dépend d'une chose qu'on supprime

Conception du 2026-11-01. Le 2026-10-31, six champs retirés de Repas ont mis trois variables du jour en échec sans un mot. Avant de supprimer, l'app dit ce qui lit la chose ; elle ne nettoie rien.

## Décisions

- **À l'écran** : la confirmation de la suppression nomme ce qui dépend de la chose, groupé par sorte, et laisse faire. Rien n'est refusé à une personne.
- **L'IA et le connecteur** : la suppression est refusée tant que quelque chose en dépend, le refus nommant les dépendances ; elle repasse avec `confirm_dependents: true`, une fois l'utilisateur prévenu (comme `confirm_migration`).
- **Après la suppression, rien n'est nettoyé** : ce qui dépendait de la chose reste tel quel et échoue visiblement, avec les messages qui existent (une variable en échec sur sa tuile, une référence « supprimée », un graphique qui dit sa colonne, un raccourci refusé à son toucher). Une suppression défaite reconnecte tout, chacun gardant l'identifiant qu'il lisait. Pas d'exception : un raccourci de Suivi qui désigne une entrée supprimée est refusé quand on le touche, l'écriture d'une référence étant vérifiée (`ReferenceCheck`).

## Les quatre cas, dans l'ordre

1. **Un champ retiré d'un outil** (`tools.update` dont la config perd un champ de l'utilisateur ou de son type) : ce qui lit ce champ de cet outil.
2. **Un outil supprimé** (`tools.delete`) : ce qui le nomme.
3. **Une variable supprimée** (`variables.delete`) : ce qui la nomme, par son id ou, dans une formule, par son nom.
4. **Une entrée supprimée** (`tool_data.delete`, `batch_delete`) : ce qui la désigne, configs et champs Référence des autres entrées.

Hors de cette version : le pas d'une série de variable changé (une modification), les conversations passées (de l'histoire), l'accès des clients du connecteur.

## Où l'on cherche

Une passe sur les configs enregistrées, chacune lue comme du texte JSON : un identifiant est un UUID, qu'aucune autre valeur ne prend.

- les outils (`config_json`), sauf la chose elle-même ;
- les variables (`definition_json`) ;
- les routines (déclencheurs, accès, message de départ : les messages de leur session de départ) ;
- les Tâches (étapes, déclencheurs) ;
- les repères (`settings_json`) ;
- les événements (`values_json`).

Par cas :

- un **outil**, une **entrée** : la config contient son id ;
- une **variable** : la config contient son id, ou une formule (`formula`, `per_entry`) contient son nom comme mot ;
- un **champ** `f` de l'outil `T` : la config contient l'id de `T` et le chemin du champ (`"extra.f"`, `"data.f"`) ou `f` comme mot dans une formule par entrée ; un outil autre que `T` peut nommer le même champ dans la même config, et la passe le compte alors aussi : elle penche du côté de trop dire ;
- une **entrée**, en plus : les entrées des autres outils dont les données la désignent, comptées par outil, en SQL (`LIKE` sur `data` et `extra`).

## Ce que l'on rend

`dependents.of` (service `dependents`), lecture libre : `target` (`{"kind": "TOOL_INSTANCE" | "VARIABLE" | "ENTRY", "id"}`, ou `{"kind": "FIELD", "tool_instance_id", "path"}`), et rend `dependents`, une liste de `{"kind", "id", "name"}` (`tool`, `variable`, `routine`, `task`, `readout`, `event`) puis de `{"kind": "entries", "tool_instance_id", "name", "count"}`. La confirmation de l'écran les dit groupés par sorte, chacun nommé, une liste longue coupée par « et N autres ».

## Où on l'appelle

- Le service qui supprime (outils, variables, entrées) et `tools.update` qui retire un champ : hors d'une personne (`Protections.byAPerson()`), refus tant que `confirm_dependents` n'est pas donné et que la liste n'est pas vide ; le refus nomme la liste.
- L'écran, avant sa confirmation (`rememberDependents`, la liste dans le message) : un outil (sa config), une variable (son écran), une fiche d'une table (`StructuredScreen`), les champs qu'un enregistrement de config retire (une fenêtre avant l'écriture, puis celle des entrées touchées s'il y en a). Les autres écrans qui suppriment une entrée (l'historique d'un Suivi, le journal, les notes) n'en disent rien : leur bouton confirme lui-même, une recherche par ligne affichée serait lourde, et leurs entrées sont rarement désignées.
- Les commandes de l'IA (`DELETE_TOOL`, `DELETE_VARIABLE`, `DELETE_DATA`, `UPDATE_TOOL`) déclarent `confirm_dependents`, leur texte dit quand le donner.

## En passant

Le refus d'une référence vers une entrée qui n'existe plus dit « l'entrée désignée par le champ X a été supprimée » plutôt qu'un identifiant brut.
