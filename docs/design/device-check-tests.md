# Les garanties de la recette à tenir par des tests

Sorties de la recette sur l'appareil (`device-checks.md`) le 2026-10-21 : chacune casserait sans se voir, ou en perdant des données. Chacune devient un test, ou sort si un test la tient déjà ; une ligne tenue sort, le fichier vidé s'efface.

## Mise à jour et démarrage

- Importer une sauvegarde faite avant la mise à jour (base 52 ou plus ancienne) : chaque zone montre ses outils dans le même ordre, l'accueil ses zones de même ; les récurrences et les pointeurs se relisent. Juste après, l'écran des journaux filtré sur « Error » ne montre aucune ligne `MIGRATION`.

## Démo

- Pendant un import d'un gros fichier (Données structurées) : la bande compte les lignes écrites par 500.

## Outils et saisie

- Messages, récurrence illisible (écrire en base un `schedule` portant `"enabled": true`) : avertissement sur l'écran de l'outil, erreur dans la config.

## Données structurées

- « Importer un fichier » sur une table d'aliments : l'écran relit la table une fois l'import fini.

## Objectif

- Un objectif récurrent quotidien, sa période finie : l'onglet « En cours (1) », la tentative à valider au-dessus de celle du jour.

## Questionnaire

- Une réponse donnée puis l'écran quitté : « Remplir » reprend à la deuxième question.

## Séance

- La notification d'une séance planifiée ouvre l'outil.

## Les incarnations (branche incarnations)

- Choisir une autre icône et une couleur pour une incarnation, enregistrer : la tuile les montre.
- Une conversation dont l'incarnation est supprimée : son composeur remplacé par la raison.

## L'export des entrées (branche post-release)

- Le dossier choisi dans Réglages › Données : le fichier d'un export y paraît (le sélecteur de dossiers d'Android, qu'un test ne simule pas).

## Partager une partie de l'app (branche post-release)

- Importer… sur ce fichier : tout coché, entrées comprises, sans le choix « Tout restaurer ». Une zone dans « Une zone existante » : celle du même nom en tête de liste. Un outil Messages ou Agenda dit qu'il peut envoyer des notifications.
