# Les champs de l'utilisateur dans le Suivi et Messages

Conçu le 2026-10-14. Les autres outils montrent les champs de l'utilisateur d'une entrée par `CustomFieldsDisplay` ; le Suivi et Messages ne les montrent qu'en modification.

## Le Suivi : une carte par entrée, plus de tableau

L'historique (`TrackingHistory`) n'est plus un tableau : des colonnes de largeur fixe (date, nom, valeur, actions) coupent leur texte dès que la largeur manque — écran étroit, police agrandie dans les réglages d'Android —, et n'ont pas de place pour les champs. L'en-tête du tableau disparaît.

Chaque entrée est une carte (`UI.Card`), comme au Journal et aux Notes, marges intérieures `UI.Space.M` :
- le nom et la valeur avec son unité, le chrono en cours dit comme aujourd'hui ;
- la date, en petit ;
- les champs de l'utilisateur, `FieldsLayout.COMPACT` ;
- modifier et supprimer (supprimer demande confirmation, comme aujourd'hui).

Le texte revient à la ligne sur toute la largeur de la carte, jamais coupé dans une colonne.

## Messages

Les cartes des deux onglets, Reçus et À venir, montrent les champs de l'utilisateur sous le texte, `FieldsLayout.COMPACT`, depuis `Occurrence.extra`, déjà chargé.
