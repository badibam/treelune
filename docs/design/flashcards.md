# Outil Flashcards

Réviser par répétition espacée les lignes d'un autre outil : chaque carte revient juste avant d'être oubliée, de plus en plus tard à chaque bonne réponse. Premier usage : un lexique thématique bilingue (`français`, `italien`, `nature`, `thème`) tenu dans un outil Données structurées. Conçu le 2026-10-19.

## Ce qu'est un paquet

- Un paquet est une instance de l'outil Flashcards. Il ne contient pas les mots : il pointe vers les entrées d'un autre outil, la source. Ajouter un mot au lexique ajoute une carte ; le supprimer la retire.
- Une carte = une ligne de la source dans un sens. Ses seules données propres sont ses révisions, rangées dans le paquet.
- L'état d'une carte appartient au paquet, jamais à la ligne : deux paquets sur le même lexique ont chacun leurs états.

## La config

Faite de briques existantes (`docs/BRICKS.md`) :

- `source` : une Sélection d'entrées sans période (`EntrySelection`) — l'outil pointé, et des filtres facultatifs pour écarter ce qu'on ne révise jamais (`nature ≠ nom propre`).
- `group_by` : un Champ facultatif (`FieldPicker`), par exemple `data.thème`. Il découpe le paquet en groupes tirés des valeurs présentes dans la source : un nouveau thème saisi fait apparaître son groupe, un thème vidé disparaît. Une ligne à plusieurs valeurs est dans chacun de ses groupes, avec un seul état. Les groupes sont des vues du paquet, sans réglages à eux.
- `directions` : la liste ordonnée des sens. Un sens a une clé donnée à sa création et gardée ensuite (`completeConfig`, renommer un sens garde ses états), un nom, ses champs de recto et de verso (des Champs) : `[{"key": "d_it_fr", "name": "IT → FR", "front": ["name", "extra.nature"], "back": ["extra.traduction"]}, …]`. Pas limité à deux.
- `one_direction_after_another` : coché, le sens suivant d'une ligne n'entre que quand le précédent, dans l'ordre de la config, est acquis pour cette ligne.
- `new_per_day` (20) et `reviews_per_day` (200) : des plafonds, pas des objectifs, comptés en cartes, pour le paquet entier quel que soit le groupe révisé.
- `desired_retention` : 0,9.

Le paquet vérifie à l'écriture de sa config que les champs qu'il nomme existent dans la source (`refuseConfig`, comme les colonnes d'un Graphique).

## Les entrées : une par révision

- Une révision est écrite une fois, jamais modifiée (`refuseUpdate`) : `timestamp` = l'instant, `data` = `{"source_entry", "direction", "rating"}` (une RÉFÉRENCE vers la ligne, la clé du sens, `AGAIN`, `HARD`, `GOOD`, `EASY`), `state` = l'état de la carte après elle, calculé par FSRS (`stability`, `difficulty`, `due`, `fsrs_version`).
- L'état courant d'une carte est celui de sa dernière révision. Aucune table de cartes, aucun compteur stocké : rien ne peut diverger du journal.
- Une carte nouvelle : une ligne de la source sans révision dans ce sens.
- Toutes les notes sont gardées, les reprises d'une même séance comprises.
- Les révisions d'une ligne supprimée de la source restent sans carte et sont ignorées.

## Le calcul des cartes à proposer

Un seul calcul, d'où sortent la tuile, la page, la fenêtre de lancement et la séance : à un instant, depuis les lignes de la source filtrées, le journal et la config.

- Dues : les cartes dont l'échéance tombe avant la fin de la journée (due dès le début du jour où tombe son échéance, décidé le 2026-10-19 : la séance du matin prend une carte notée le soir), et celles dont la dernière note est Raté. Nouvelles : dans l'ordre où les lignes ont été ajoutées à la source, jusqu'à ce qui reste de `new_per_day`.
- Les plafonds se comptent en cartes : une carte commencée aujourd'hui compte dans les nouvelles, une carte déjà vue et revue aujourd'hui dans les révisions ; une ratée du jour reproposée ne prend rien de plus.
- « Aujourd'hui » commence à l'heure de début de journée de l'app (`dayStartHour`, `TimeResolver`), dans son fuseau.
- Cartes sœurs : deux sens d'une même ligne ne sont jamais proposés le même jour. Pas de réglage.
- Acquis : une stabilité FSRS d'au moins 7 jours (le souvenir tient au moins une semaine). Seuil fixe tant que rien ne le demande.
- Le calcul tourne en SQL sur le journal (la dernière révision de chaque carte) ; sa durée se mesure sur le téléphone avant d'être gardée.

## FSRS

- L'algorithme d'Anki depuis 2023, sous licence MIT, avec ses paramètres par défaut. Il estime la stabilité et la difficulté de chaque carte, et fixe l'échéance où la probabilité de s'en souvenir tombe à `desired_retention`.
- Porté en Kotlin depuis py-fsrs 6.3.2 (FSRS-6), sa version fixée et écrite dans chaque révision ; sans les paliers d'apprentissage de py-fsrs (la séance fait revenir une ratée elle-même, chaque note donne une échéance en jours) et sans le flou des échéances (une même suite de notes donne toujours les mêmes dates). Des tests le comparent à l'implémentation de référence sur les mêmes suites de notes : ils ne sont pas facultatifs, un portage faux donnant des dates fausses sans rien casser.
- Le journal complet permet un jour d'ajuster les paramètres sur l'historique de l'utilisateur.

## La séance

- On retourne la carte et on se note : Raté, Difficile, Bon, Facile.
- Une liste fixée au lancement : les dues et les nouvelles du groupe et du sens choisis, dans les plafonds du jour.
- Une carte ratée revient plus tard dans la séance, après trois autres cartes et au moins une minute ; si plus rien d'autre n'attend, elle revient tout de suite : la séance ne fait jamais patienter. Difficile, Bon et Facile finissent une carte (décidé le 2026-10-19 : Difficile est retrouvé, FSRS le compte réussi) ; la séance finit quand aucune carte n'est restée sur un Raté.
- On s'arrête quand on veut : une ratée pas encore repassée reste due.
- Annuler revient à la carte précédente, verso caché : sa dernière note est supprimée, l'état d'avant revient par le calcul. Plusieurs fois de suite, en remontant la séance.

## La face d'une carte

- Le premier champ d'une face est la question ou la réponse, en grand ; les suivants sont des précisions, en petit, sans libellé.
- Au toucher, le verso apparaît sous un trait, le recto restant affiché ; les quatre notes en dessous.
- Chaque champ s'affiche selon son type, par ce que l'app sait déjà afficher.
- Une case vide (validé avec l'utilisateur, pas un repli) : sur le premier champ du recto, la carte n'existe pas — pas de question à poser ; ailleurs, le champ ne s'affiche pas.
- Un champ disparu de la source : une erreur qui le nomme (« le champ italien n'existe plus dans Lexique »), jamais une carte vide.

## Les écrans

- La tuile : `14 à revoir · 6 nouvelles`, tous sens confondus, une ligne par groupe dans les modes à corps, et le point d'attente quand des cartes sont dues (`countWaiting`, compté par le paquet : une carte due n'est pas une entrée).
- La page : une ligne « Tout » puis une ligne par groupe, avec ses dues et ses nouvelles. « Tout » n'est pas la somme des groupes (une ligne peut être dans plusieurs). Les nouvelles d'un groupe montrent au plus ce qui reste au paquet aujourd'hui : deux groupes peuvent afficher 6 pour 6 possibles en tout — à expliquer dans le chapitre du Guide.
- Toucher un groupe ouvre la fenêtre de lancement : une ligne par sens et « Tous les sens », avec leurs nombres ; toucher une ligne lance la séance. Un paquet à un seul sens lance directement.

## Changer la config

- Changer la source supprime toutes les révisions ; supprimer un sens, les siennes. Dans les deux cas par `entriesRemovedByConfig`, donc après l'accord de l'utilisateur ou de l'IA (`confirm_migration`).
- Un changement de la source elle-même (colonne renommée ou supprimée) n'est pas vu par elle : c'est l'item de `TODO.md` « Un outil changé dont dépend une condition ailleurs ». Le paquet en devient un client quand il sera traité.

## Les opérations

Les mêmes pour l'écran et l'IA :

- `flashcards.due` : les cartes à proposer maintenant, pour un groupe et un sens, avec les valeurs de leurs faces — le calcul unique.
- `flashcards.counts` : les nombres de Tout, de chaque groupe (sa valeur et son libellé) et de chaque sens, ce que montrent la page et sa fenêtre, en une lecture de la source.
- `flashcards.review` : noter une carte (ligne source, sens, note) ; calcule l'état par FSRS et écrit l'entrée. Seule façon d'écrire une révision : une écriture ordinaire est refusée avec ce qu'il faut faire (`EntryStart` sans statut), un import aussi, les champs d'une révision étant écrits par l'app.
- Lire les révisions : `tool_data.get` ordinaire (historique, filtres, lectures du cœur pour un Graphique).
- Annuler : la suppression ordinaire d'une entrée, permise pour la seule dernière révision d'une carte (`refuseDelete`).

Avec `due` et `review`, l'IA fait réviser en conversation ou dans une routine, l'utilisateur se notant lui-même.

## Plus tard

- Les statistiques (cartes acquises, prévision des jours à venir).
- Un rappel quotidien, par une routine plutôt qu'un réglage du paquet.
- La liste des cartes et leur état, et l'annulation hors séance.
- La réponse tapée, en option d'un sens.
- Le jugement d'une réponse libre par l'IA.
- Les champs image, quand ils existeront.
- Supprimer les révisions d'une ligne disparue de la source, ou les garder dans l'historique.

## La démo

- Dans la zone Italien, Lessico (24 mots, traduction, nature, thème) et Ripasso, qui le révise dans les deux sens par thème ; trois semaines de séances du soir jusqu'à la veille, simulées par le même calcul, la même séance et FSRS (`DemoData.deckReviews`), laissent des cartes à revoir le jour de l'installation.

## À passer sur le téléphone

La section « Flashcards » de `docs/design/device-checks.md`.
