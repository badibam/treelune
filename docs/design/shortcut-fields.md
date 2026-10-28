# Les champs d'un raccourci

Un raccourci de Suivi porte aujourd'hui un nom, et pour un nombre sa valeur et son unité (`TrackingShortcut`, la clé `items` de la config). Les champs de l'utilisateur ne prennent que leur valeur par défaut (`default_value`), la même pour tous les raccourcis de l'outil. Un usage qui revient en demande une par raccourci : des repas notés d'un toucher, « Saumon » pointant vers la fiche du saumon et « Œufs » vers celle des œufs, dans un champ Référence vers une table d'aliments ; sans elle, le repas noté n'a pas de nutriments. De même une douche froide notée avec ses murs à 0, un verre de vin rangé dans sa catégorie.

## Le principe

Un raccourci porte, en plus de son nom, de sa valeur et de son unité, des valeurs pour des champs de l'utilisateur : `{"name": "Saumon", "value": 150, "unit": "g", "extra": {"aliment": {"kind": "ENTRY", "id": "…"}}}`. Un toucher écrit l'entrée avec ces valeurs ; la fenêtre de saisie qu'il ouvre (sans valeur) les présente remplies, changeables. Un champ que le raccourci ne nomme pas prend sa valeur par défaut, comme aujourd'hui.

Rien ne change pour un raccourci sans `extra`.

## La config

- `extra` sur chaque élément de `items`, ses clés des champs de l'outil (`extra_fields`), chaque valeur vérifiée comme une valeur de ce champ (`FieldValueValidator`, le schéma de valeur) : une Référence désigne une chose qui existe, du genre que le champ prend ; un Choix fermé, une de ses options.
- Le formulaire du raccourci propose, sous son nom et sa valeur, les champs de l'outil, chacun laissé vide par défaut.
- L'IA et le connecteur l'écrivent par la config, comme le reste des raccourcis.

## Ce qui suit les champs

- **Un champ supprimé** de l'outil : sa valeur sort des raccourcis qui la portaient, sans les refuser.
- **Un champ dont le type ou les réglages changent** : la migration des champs (`FieldConfigComparator`) traite les valeurs des raccourcis comme celles des entrées, comptées avant d'enregistrer ; une valeur qui ne passe plus sort du raccourci.
- **La chose désignée supprimée** (une fiche d'aliment) : la Référence du raccourci reste, lue « supprimée », comme dans une entrée ; rejoint le contrôle « qui dépend de ça ? » (`TODO.md`, « À concevoir »).
- **L'export et l'import d'une partie** : les Références des raccourcis suivent les ids comme celles des entrées.

## À trancher

- **La tuile** : montrer ou non, sous le nom d'un raccourci, une valeur qu'il porte (« Saumon · 150 g »).
- **Le Choix suivi** (un Suivi de type choix, dont les options sont des raccourcis implicites) : leur donner aussi des champs, ou réserver la chose aux raccourcis de `items`.
- **Une Tâche ou l'IA qui crée un raccourci** depuis une entrée existante (« garder ce repas comme raccourci ») : un geste de l'écran de l'entrée, ou pas.
