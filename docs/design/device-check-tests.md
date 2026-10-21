# Les garanties de la recette à tenir par des tests

Sorties de la recette sur l'appareil (`device-checks.md`) le 2026-10-21 : chacune casserait sans se voir, ou en perdant des données. Chacune devient un test, ou sort si un test la tient déjà ; une ligne tenue sort, le fichier vidé s'efface.

## Mise à jour et démarrage

- Importer une sauvegarde faite avant la mise à jour (base 52 ou plus ancienne) : chaque zone montre ses outils dans le même ordre, l'accueil ses zones de même ; les récurrences et les pointeurs se relisent. Juste après, l'écran des journaux filtré sur « Error » ne montre aucune ligne `MIGRATION`.

## Validation

- Une automation dont l'accès donne un outil en Lecture, à qui l'on demande d'y écrire : le refus se lit dans son historique d'exécution ; en Utilisation, l'écriture passe. Ajouter, changer, retirer une ligne de la carte d'accès, enregistrer, rouvrir : tout est gardé.
- Donner à un client du connecteur un accès limité (bouton « Accès » de sa ligne) : une lecture hors de la liste est refusée, et `app_context` dit son accès.

## Démo

- Réglages › Démo : désactiver « Installer la démo à chaque mise à jour » et enregistrer ne supprime rien, la démo reste ; réinstaller l'APK la laisse telle quelle ; réactiver le réglage n'installe rien avant la mise à jour suivante.
- Les photos de la démo (Tomate, Basilic, Piment) : « Supprimer la démo » puis l'app redémarrée, leurs fichiers partent au ménage, une ligne INFO au journal.
- Pendant un import d'un gros fichier (Données structurées), lancer « Réinstaller » la démo ou exporter une sauvegarde : refusé, le message nomme l'import en cours ; la bande compte les lignes écrites par 500.

## Chat IA

- Faire créer et modifier des entrées de tracking par l'IA : `raw` est juste.
- Même sélecteur dans le message de départ d'une automation : période et filtre de date en valeurs relatives, recalculées à l'exécution suivante. Le résumé dit le côté de chaque borne : « entre le début de « 7 jours avant » et la fin de « le jour-même » », « Pesé le depuis le début de « la veille » ».

## Automations

- Rattrapage après la mise à jour : une automation programmée « sans limite » le reste, une fenêtre de 3 heures se relit « jusqu'à un délai » de 3 h. Dans l'éditeur : sans choix, l'enregistrement est refusé ; « jusqu'à un délai » demande la durée ; retirer la planification puis enregistrer retire aussi le rattrapage.

## Outils et saisie

- Messages, récurrence illisible (écrire en base un `schedule` portant `"enabled": true`) : avertissement sur l'écran de l'outil, occurrences déjà prévues gardées et envoyées, erreur dans la config, enregistrement refusé sans rien écraser.

## Réglages et affichage

- Fuseau de l'app différent de celui du téléphone : l'historique range chaque entrée dans le jour affiché sur elle, le sélecteur de période et l'éditeur de planning montrent l'heure de l'app, « aujourd'hui » et « hier » du journal suivent. Un début de semaine changé est suivi par l'historique et les sélecteurs de période.

## Données structurées

- « Importer un fichier » sur une table d'aliments avec un CSV (nom ; kcal en décimale virgule ; une date jour/mois ; une catégorie à quelques valeurs) : chaque colonne est proposée avec son type, son écriture et un exemple lu ; un fichier dont les dates ne tranchent pas jour/mois le signale ; importer crée les champs dans l'ordre, met à jour les fiches dont le nom existe, et le compte-rendu nomme les lignes refusées. Réimporter le même fichier : tout est mis à jour, rien n'est dupliqué. Un import avec une colonne nouvelle se termine (les appels imbriqués dans la transaction ne bloquent pas), et l'écran relit la table une fois fini.

## Objectif

- Critères : un critère lu « Repas › kcal, somme ≤ 2100 » se juge sur la journée de la tentative sans toucher à la période (« Aucune choisie : celle de la tentative ») ; « ≤ » une variable à droite se juge aussi ; un critère saisi (Durée « ≥ 7 h », Choix « = bonne ») se remplit dans la tentative et se juge ; retirer la valeur saisie (bouton du groupe) ramène un critère lu. La ligne d'un critère dit sa valeur face à ce qu'elle compare, avec une jauge pour un nombre ou une durée.
- Une tentative validée : la modifier par l'IA est refusé ; « Rouvrir » la remet à valider et garde la date de réouverture.
- Renommer un critère saisi : sa valeur reste ; le supprimer puis le recréer : la confirmation de la config compte la valeur retirée.
- Un objectif récurrent quotidien : une tentative par jour ; à la fin de la période, une notification et l'onglet « En cours (1) », la tentative à valider au-dessus de celle du jour ; sans validation, expirée après le délai. Arrêté, plus aucune ne s'ouvre.

## Questionnaire

- Planifié chaque jour à une heure passée : au tick, une entrée « à remplir » datée de cette heure et une notification ; répondre à la première question puis quitter : la réponse reste, et « Remplir » reprend à la deuxième. Une absence de plusieurs jours : autant d'entrées, « Tout ignorer » les passe en ignorées.

## Séance

- Planifiée chaque jour à une heure passée : au tick, une entrée prévue, une notification qui ouvre l'outil, le point d'attente sur la tuile ; « Commencer » la reprend, « Faite » la marque faite à son heure prévue ; une absence de plusieurs jours, « Tout ignorer ».

## Agenda (branche agenda)

- « Se répète » chaque mardi 18 h : les champs supplémentaires disparaissent ; des occurrences sur 90 jours apparaissent aussitôt l'enregistrement (une série créée par l'IA ou par la roue dentée aussi), chacune avec l'icône de répétition. Une occurrence : « Retirer » la fait passer sous « Retirées (1) », « Rétablir » la remet ; « Supprimer » n'y est pas.
- « Modifier la série » : changer la durée, enregistrer sans confirmation ; les occurrences restées à l'ancienne durée la prennent, celle réglée à la main garde la sienne. Changer le jour : la confirmation compte les occurrences à venir ; acceptée, elles reviennent sur le nouveau jour, le passé intact.

## L'IA et les routines (branche ai-routines)

- Une routine à l'accès limité à une zone en `full`, dont le message demande de créer une routine : créée avec un accès dans le sien, refusée avec un accès vide ou plus large, la raison dans son historique.
- Par le connecteur MCP : `automations` et `automation_config` répondent ; une création sur une zone hors du masque du client est refusée.

## Les bugs du téléphone du 2026-10-16 et le changement de config

- Demander à l'IA de resserrer une échelle (conversation) : la carte de validation dit les comptes, même dans une zone non protégée ; refusée, rien ne change ; acceptée, les valeurs hors bornes partent. « Protéger les données passées » décoché : l'IA est refusée et doit renvoyer avec `confirm_migration`. Par le connecteur : la notification d'accord. Une routine qui le fait : passe si son accès le permet.
- Objectif : valider une tentative, changer un critère (seuil, critère retiré), rouvrir la tentative : elle est jugée selon ses anciens critères, la valeur saisie du critère retiré toujours là ; revalidée, son jugement aussi.

## Les incarnations (branche incarnations)

- Par le connecteur (un client à l'accès « toute l'app ») : `import_identity` avec le texte d'un fichier au format 2 crée l'identité, ses textes « importé » ; la même encore, sans `replace`, est refusée en disant les retouches et langues perdues ; avec `replace: true`, remplacée. La protection du niveau de l'app active, la notification d'accord vient d'abord. Un client limité à des zones est refusé.
- Modifier la voix, enregistrer : « écrit dans l'app » ; réimporter le fichier : la confirmation nomme `fr · voice` parmi les retouches perdues, et confirmée, la voix revient. Choisir une autre icône et une couleur, enregistrer : la tuile les montre.
- Les retirer, supprimer : la conversation passée se relit, son composeur remplacé par la raison.

## Les conditions typées à l'écriture (branche typed-conditions)

- Un Objectif « sommeil moyen d'au moins 7 h » créé par l'IA : sa config, rouverte à l'écran, montre 7 h, et la tentative se juge sans erreur.

## Les fournisseurs créés (branche ai-providers)


## L'historique des écritures (branche undo-history)

- Supprimer la démo puis l'annuler : la démo revient entière, ses routines et leurs messages compris.
- Un rappel d'Agenda envoyé, puis annuler le renommage de son événement : l'ancien nom revient, le rappel ne repart pas.

## Les journées de l'app (2026-10-20, branche todo-cleanup)

- La nuit, entre minuit et l'heure de début du jour : « cette semaine », « ce mois » restent ceux de la veille au soir (une variable de la semaine, le Suivi d'une semaine, un Graphique de la semaine) ; « aujourd'hui » reste la veille.
- Une série d'événements toute la journée : chaque début à l'heure de début du jour ; l'IA qui lit les événements d'aujourd'hui voit une journée entière.
- La nuit, avant 4 h : le sélecteur d'un champ DATE marque la veille comme aujourd'hui et s'ouvre dessus, celui de la date d'une entrée du Journal le jour du calendrier ; « aujourd'hui » choisi dans un champ DATE donne la veille.

## Les bugs du 2026-10-20 et les unités des durées (branche bugs)

- Un Objectif récurrent : une tentative en cours dit « reste 40m » dans sa dernière heure ; la démo réinstallée, la semaine d'italien finit le lundi 26 octobre à 00:00, malgré le changement d'heure du 25.

## L'export des entrées (branche post-release)

- Une routine dont le message demande l'export de la semaine : le fichier paraît dans le dossier à chaque exécution, sans écraser le précédent.

## Partager une partie de l'app (branche post-release)

- Importer… sur ce fichier : tout coché, entrées comprises, sans le choix « Tout restaurer ». Une zone dans « Une zone existante » : celle du même nom en tête de liste. Une variable dont le nom est pris dit son nouveau nom ; un outil Messages ou Agenda dit qu'il peut envoyer des notifications.
- Importer : la fenêtre dit les zones créées, les éléments et les entrées ; tout est là, une routine éteinte sans fournisseur ; Réglages › Historique montre un seul pas, et l'annuler retire tout.

## Le champ Image (branche image-field)

- Une sauvegarde puis sa restauration, avec des images d'entrées et de messages : toutes reviennent, chacune à son propriétaire. Une entrée supprimée, l'app redémarrée : son fichier part, une ligne INFO au journal.
