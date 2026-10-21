# Les garanties de la recette à tenir par des tests

Sorties de la recette sur l'appareil (`device-checks.md`) le 2026-10-21 : chacune casserait sans se voir, ou en perdant des données. Chacune devient un test, ou sort si un test la tient déjà ; une ligne tenue sort, le fichier vidé s'efface.

## Mise à jour et démarrage

- Importer une sauvegarde faite avant la mise à jour (base 52 ou plus ancienne) : chaque zone montre ses outils dans le même ordre, l'accueil ses zones de même ; les récurrences et les pointeurs se relisent. Juste après, l'écran des journaux filtré sur « Error » ne montre aucune ligne `MIGRATION`.

## Démo

- Réglages › Démo : désactiver « Installer la démo à chaque mise à jour » et enregistrer ne supprime rien, la démo reste ; réinstaller l'APK la laisse telle quelle ; réactiver le réglage n'installe rien avant la mise à jour suivante.
- Les photos de la démo (Tomate, Basilic, Piment) : « Supprimer la démo » puis l'app redémarrée, une ligne INFO au journal.
- Pendant un import d'un gros fichier (Données structurées), lancer « Réinstaller » la démo ou exporter une sauvegarde : refusé, le message nomme l'import en cours ; la bande compte les lignes écrites par 500.

## Outils et saisie

- Messages, récurrence illisible (écrire en base un `schedule` portant `"enabled": true`) : avertissement sur l'écran de l'outil, erreur dans la config.

## Données structurées

- « Importer un fichier » sur une table d'aliments : l'écran relit la table une fois l'import fini.

## Objectif

- Un objectif récurrent quotidien : une tentative par jour ; à la fin de la période, une notification et l'onglet « En cours (1) », la tentative à valider au-dessus de celle du jour ; sans validation, expirée après le délai. Arrêté, plus aucune ne s'ouvre.

## Questionnaire

- Une réponse donnée puis l'écran quitté : « Remplir » reprend à la deuxième question.

## Séance

- La notification d'une séance planifiée ouvre l'outil.

## Les bugs du téléphone du 2026-10-16 et le changement de config

- La carte de validation dit les comptes, même dans une zone non protégée ; par le connecteur, la notification d'accord ; une routine qui le fait passe si son accès le permet.

## Les incarnations (branche incarnations)

- Par le connecteur (un client à l'accès « toute l'app ») : `import_identity` avec le texte d'un fichier au format 2 crée l'identité, ses textes « importé » ; la même encore, sans `replace`, est refusée en disant les retouches et langues perdues ; avec `replace: true`, remplacée. La protection du niveau de l'app active, la notification d'accord vient d'abord. Un client limité à des zones est refusé.
- Modifier la voix, enregistrer : « écrit dans l'app » ; réimporter le fichier : la confirmation nomme `fr · voice` parmi les retouches perdues, et confirmée, la voix revient. Choisir une autre icône et une couleur, enregistrer : la tuile les montre.
- Les retirer, supprimer : la conversation passée se relit, son composeur remplacé par la raison.

## L'export des entrées (branche post-release)

- Le dossier choisi dans Réglages › Données : le fichier d'un export y paraît (le sélecteur de dossiers d'Android, qu'un test ne simule pas).

## Partager une partie de l'app (branche post-release)

- Importer… sur ce fichier : tout coché, entrées comprises, sans le choix « Tout restaurer ». Une zone dans « Une zone existante » : celle du même nom en tête de liste. Un outil Messages ou Agenda dit qu'il peut envoyer des notifications.
