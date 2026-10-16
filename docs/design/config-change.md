# Ce qu'un changement de config fait au passé — conception

Décidé le 2026-10-16, depuis une note du téléphone (« Revoir logique dans suivi : changement de config entraîne-t-il changement dans les données ? »).

## La règle

La config fait foi : les entrées la suivent. Rien ne se perd sans avoir été vu et choisi.

- Un changement de config qui touche des valeurs passées dit, avant d'être enregistré, ce qu'il fera : les valeurs effacées, les entrées supprimées, les valeurs relues autrement, chacune comptée.
- Les choix : accepter, ou annuler. Pour un changement de sens (type, bornes d'une échelle, plusieurs réponses vers une), un troisième : créer un nouveau champ à la place, l'ancien restant tel quel.
- Une valeur que plus rien ne lit ne se garde pas : un graphique, un filtre, une variable ne connaissent un champ que par la config (`ToolFields.filterable`).

## Cas par cas

| Changement | Ce qu'il fait aux entrées |
|---|---|
| Retirer un champ | ses valeurs effacées partout |
| Retirer une option | les entrées qui l'ont perdent la valeur du champ |
| Changer le type | les valeurs effacées ; ou un nouveau champ |
| Bornes d'une échelle | les valeurs gardées, relues entre les nouvelles bornes ; celles hors bornes effacées, comptées à part ; ou un nouveau champ |
| Une réponse vers plusieurs | chaque valeur devient une liste d'une réponse, sans rien demander |
| Plusieurs réponses vers une | une seule réponse : gardée ; plusieurs : la valeur effacée, comptée ; ou un nouveau champ |
| Type de valeur d'un Suivi | la valeur est obligatoire : les entrées supprimées, comptées |
| Libellés, couleurs, descriptions, options ajoutées | rien |

Un nouveau champ créé à la place prend une autre clé et le nouveau réglage ; l'ancien garde sa clé, ses valeurs et son réglage d'avant.

## Qui décide

- À l'écran : la fenêtre de confirmation, avec les comptes et les choix.
- L'IA (conversation, connecteur MCP) : le changement qui perd ou relit des valeurs demande ta validation, quel que soit le niveau de protection réglé ; réglage « Changements qui touchent des données passées », dans Réglages › Validation, activé par défaut. `confirm_migration` n'est plus accepté de l'IA : c'est la validation qui le pose. La carte de validation et la notification du connecteur disent les comptes.
- Une routine : le changement passe si son masque d'accès l'autorise (elle n'a personne à qui demander).

## Les relevés qui gardent leur copie

- **Objectif** : une tentative validée ou expirée garde sa définition (`data.definition`), rouverte comprise : rouvrir la rejuge selon sa copie, jamais selon la config actuelle. Un changement de config ne touche pas les valeurs d'une tentative close (`EntryMigration` les laisse, comme `refuseChange` le fait pour une écriture).
- **Séance** : une séance finie garde ses étapes (nom, état fait, sauté ou non fait, temps), recopiées de son déroulé. Les comptes se déduisent des étapes ; la corriger, c'est changer l'état d'une étape, l'heure et la durée totale. Le temps d'une étape ne se corrige pas : la durée totale fait foi. Une séance faite sans l'écran prend les étapes du déroulé du moment, toutes faites. Une séance finie avant ce changement garde ses seuls comptes.
- **Messages**, raccourcis du Suivi : inchangés (un envoi parti garde sa copie ; une entrée faite par un raccourci porte son nom et sa valeur).
