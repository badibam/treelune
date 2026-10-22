# Ce que l'IA lit d'un type d'outil

Conception du 2026-10-22, à coder puis élaguer. Vaut pour les douze types d'outils et pour toute IA : celle de l'app comme l'IA extérieure du connecteur MCP.

## Le constat

Le 2026-10-22, sur la démo, l'IA de l'app (claude-sonnet-5-5) a créé un suivi « Resting heart rate » sur la phrase « I'd like to track my resting heart rate each morning. » Deux défauts :

- aucun raccourci : chaque entrée est libre, son nom à taper à la main à chaque mesure ;
- un champ ajouté « Measured at » (une heure), qui double le moment que l'entrée porte déjà (`timestamp`).

Ce que l'IA avait lu, mesuré sur la conversation :

- au premier tour, avant toute lecture, le prompt fixe seul, où le Suivi n'est que son texte d'usage ; elle y a demandé « Note the time of measurement too? » ;
- puis le schéma de config `tracking_config`, où `items` n'arrive qu'avec son libellé (« Shortcuts ») et `extra_fields` avec « Fields » ;
- jamais le schéma des entrées, où vivent `name` et `timestamp`, et dont les descriptions sont de toute façon celles du noyau, les mêmes pour tous les types (« Name of the entry »).

## Le principe

Une entrée a la même forme dans toute l'app ; chaque type remplit des cases communes. Le noyau déclare une fois chaque case commune (`name`, `timestamp`, `data`, `extra`, `state`) et chaque réglage commun d'un outil (`extra_fields`, son nom, son affichage…), avec une description générique. Chaque type peut donner son propre sens à n'importe laquelle, et le doit quand le sens change ; aujourd'hui seul le libellé du nom le peut (`EntryFields.nameLabel`). Tout ce que l'IA lit d'un type sort de ces déclarations.

Une description dit ce qu'est la chose dans ce type, jamais ce qu'il ne faut pas faire : une erreur de l'IA se corrige en rendant juste la description de ce qu'elle a mal compris. Une case ne reçoit un sens propre que si ses champs ne le disent pas déjà (le `data` du Suivi se décrit par `value` et `unit`).

## Les deux niveaux de l'entrée

| | Niveau 1 : le type, commun à tous ses outils | Niveau 2 : un outil précis |
|---|---|---|
| `name`, `timestamp` | présence (obligatoire, facultative, absente) et sens | présence exacte, contraintes |
| `data` | son rôle | ses champs, selon la variante et la config |
| `extra` | son rôle (au Suivi, des détails à côté de la valeur ; au Questionnaire, les réponses) | ses champs, ceux de l'outil (`extra_fields`) |
| `state` | son rôle | ses champs |
| état de départ (`EntryStart`) | la règle du type | — |

`id`, `created_at`, `updated_at` restent génériques, écrits une fois par le noyau.

Seul le niveau 2 dépend de la config, à une exception : `name` des Notes, présent ou absent selon l'option des titres. Le niveau 1 le dit dans son sens (« The note's title, above its text; only when the tool's titles are on ») ; le schéma des entrées d'un outil Notes, calculé sur sa config, a la ligne `name` ou ne l'a pas.

`extra` et `extra_fields` sont la même chose vue des deux côtés : un seul texte, déclaré par le type, montré aux deux endroits.

## Qui lit quoi

- Le schéma des entrées d'un outil = niveau 1 de son type + niveau 2 de l'outil.
- Le schéma de config d'un type = ses réglages + niveau 1 de son type.
- Le niveau 1 est une lecture à part, de cible propre (`entry:<tooltype>`), envoyée une fois par conversation, que l'une ou l'autre lecture l'amène la première (`SessionReads`, qui met en `CACHED` un texte déjà reçu). Le niveau 2 ne redit pas le sens : sa ligne `name` ne porte que ses contraintes.
- L'IA extérieure, sans conversation tenue par l'app, reçoit tout à chaque lecture.

À voir dans le code : `SessionReads` rattache aujourd'hui une cible à une commande ; une lecture qui en amène une seconde est à construire.

## Les astuces d'ergonomie

Dans la consigne du type réservée à l'IA (`getAIDetail`, `ai_detail`), que le prompt fixe met sous le titre du type : la seule place lue avant le premier tour, là où la question de l'heure a été posée. Une sous-partie à côté des règles d'emploi qu'y ont l'Agenda, le Graphique et les Flashcards ; quelques lignes, ce que l'IA ne peut pas déduire des descriptions. Pour le Suivi : un suivi d'une seule chose lui donne un raccourci à son nom, chaque mesure notée d'un toucher.

Ce qui vaut pour tous les types va une fois dans le prompt fixe, au passage « The user's extra fields » d'`ai_prompt_chunks.xml` : un champ ajouté ne double pas ce que l'entrée du type porte déjà, que dit son niveau 1.

## Dans l'app

Un suivi sans raccourci : une entrée libre prend d'office le nom du suivi, modifiable. Avec des raccourcis, l'entrée libre sert à une autre chose et son nom reste vide. C'est ce qui rend un suivi mal réglé praticable, quoi que l'IA ait compris.

## Les trous connus

- Une liste de réglages n'a pas de description (`SettingNode.ListOf`) : `schema_config_items` du Suivi n'est lu par rien.
- La description d'un schéma (« Configuration schema for numeric tracking ») n'est pas rendue dans ce que lit l'IA (`SchemaNotation`).
- Les Notes et le Journal ont leurs textes pour `name` et `timestamp` (`schema_data_name`, `schema_data_timestamp`), que `CoreFields` ne lit pas : repris comme sens de leur type.

## L'exemple du Suivi

- `name` — What was noted: a shortcut's name, or one typed for a free entry
- `timestamp` — When it was measured or happened; the present if omitted, and can be set in the past
- `data` — rien de propre : `value` et `unit` se décrivent
- `extra` / `extra_fields` — Details noted with each value: the context of a weighing, the quality of a night
- `items` — Shortcuts, each noting one thing in one touch

À vérifier : le sens de `timestamp` pour un chronomètre (son départ ?).

## Le test

Celui que prévoit `docs/AI.md` après un changement de ce que lit l'IA : la même phrase, sur la démo, le même modèle, avant et après. Regarder si elle pose la question de l'heure, si elle ajoute un champ d'heure, si elle crée un raccourci.
