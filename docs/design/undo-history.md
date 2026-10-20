# Historique des écritures et annulation

Chaque écriture dans la base est gardée dans un historique, pour pouvoir l'annuler pas à pas, sur toute l'app ou dans un seul outil (ou une zone). Conçu le 2026-10-05.

## Ce qui est couvert

- Une ligne suit ce à quoi elle appartient.
- Couvert : zones, outils (leur config), entrées, variables, automations, incarnations (`identities`), masques des clients du connecteur (`mcp_clients`), réglages de l'app — et ce qui leur appartient : les images d'une entrée, le message d'une routine (sa session de départ, `seed_session_id`, ses messages et leurs images), qui n'est une session que par sa forme.
- Hors historique : les conversations, leurs messages et leurs images (ce qui s'y écrit — un message, une réponse, ses tokens — n'a pas de sens à annuler, et noierait les gestes de l'utilisateur), fournisseurs, ceux de l'utilisateur compris (`ai_provider_configs` : la clé d'API ne se recopie pas), jetons du connecteur (`mcp_tokens`), journal de l'app, fichiers joints à une session.
- L'historique ne revient que sur la base : une notification envoyée, un appel à l'IA facturé restent faits.

## Le pas

- Un pas = une opération demandée par quelqu'un : un geste à l'écran, une commande de l'IA, une commande d'un client du connecteur. Il regroupe toutes les lignes qu'elle écrit, y compris par les opérations qu'elle appelle en chemin, celles que l'app y écrit elle-même comprises (la démo installée par un geste).
- Ce que l'app écrit d'elle-même, hors de toute demande, n'est pas un pas : ni affiché, ni annulable. L'annuler serait annuler le temps passé — un questionnaire à remplir que le planificateur recrée au tick suivant, un message envoyé qui repartirait — et le plus récent pas d'un outil serait souvent celui du planificateur. Ce sont : les entrées que créent les planificateurs (occurrences d'une série d'Agenda, séances prévues, questionnaires à remplir, tentatives d'Objectif, occurrences de Messages) et celles qu'ils suppriment (les occurrences de Messages qui ne suivent plus la récurrence) ; les statuts que l'app fait avancer (une tentative à valider, un message envoyé, l'étape d'une séance au bout de son minuteur, `SequenceRunService`) ; ses pense-bêtes (plus bas).
- Ces écritures sont tout de même notées, sans être des pas, pour qu'un refus d'annuler les nomme : « changée par l'app le 20 octobre à 8 h ». Une ligne créée par l'app ne s'annule pas ; supprimée ensuite par l'utilisateur, l'annulation de cette suppression la remet.
- Les opérations appelées en chemin repassent par le coordinateur : il n'ouvre un pas que si aucun n'est en cours dans le contexte. Supprimer la démo (`demo.remove`, qui appelle `app_config.set` pour les groupes de l'accueil) est un seul pas.
- Une commande de l'IA est un pas à elle seule ; le pas garde l'id de sa session, pour les montrer regroupés par réponse.
- Un pas s'annule entier ou pas du tout.
- Le numéro du pas voyage dans le contexte de la coroutine, comme l'origine (`Origin`, `currentOrigin()`) : le nombre d'instances du coordinateur qui tournent en même temps n'y change rien. Une écriture hors de toute opération est une erreur.
- Un pas garde : son origine, son heure, l'id de session s'il vient de l'IA, sa phrase, son état (fait / annulé, avec qui et quand), et pour chaque ligne touchée : la table, l'id, l'outil et la zone concernés, la ligne entière avant (vide pour une création) et après (vide pour une suppression).
- La phrase est écrite au moment du pas par le `verbalize()` du service, et figée : recalculée à l'affichage, elle mentirait après un renommage et ne dirait rien d'une chose supprimée.

## Où il est écrit

- Toute écriture d'une table couverte passe par une seule classe, qui lit la ligne d'avant, écrit, puis écrit l'historique, dans la même transaction.
- Un script de contrôle lancé par `./run test` refuse tout appel d'écriture de ces tables ailleurs. Les écritures qui contournent aujourd'hui un service (`GoalService`, `DefaultExtendedToolDataDao`, les `DELETE` en SQL de `DemoRemoval`…) y passent.
- Les suppressions en cascade se font dans SQLite (`onDelete = CASCADE` sur outils, entrées, variables) : avant une suppression, la classe lit elle-même les lignes qui partent avec, sinon elles ne seraient pas restaurables. Une restauration remet les parents avant les enfants. Un test compare cette descente aux clés étrangères du schéma (`PRAGMA foreign_key_list`) et échoue dès qu'une cascade vers une table couverte lui manque.
- Le fichier d'une image d'entrée reste sur le disque tant qu'un pas de l'historique nomme sa ligne : la suppression ne retire que la ligne, et le nettoyage du démarrage n'efface que les fichiers que ni la base ni l'historique ne nomment. Sa taille (`size_bytes`) compte dans la limite de volume.
- Pas de déclencheurs SQLite : ils ne savent pas à quel pas appartient une écriture sans une transaction ouverte pendant toute l'opération, qui bloquerait les autres écritures pendant un appel de l'IA.

## Migrations

- L'historique est toujours à la version courante de la base : chaque migration le transforme avec les mêmes transformations que les données et l'import d'une sauvegarde.

## Profondeur

- Deux limites, réglables dans les réglages de l'app : 30 jours, et 500 Mo. Le tick de `CoreScheduler` supprime les pas de plus de 30 jours, puis les plus anciens tant que le total dépasse la limite de volume — jamais le plus récent : un gros import (estimé, non mesuré : 65 à 130 Mo d'historique pour les 64 659 lignes du 2026-10-01) reste annulable même seul au-dessus de la limite.
- Le volume vient surtout de petites écritures répétées sur des lignes lourdes (une tuile déplacée réécrit la ligne entière de chaque outil décalé, config comprise) ; aucun chiffre ne le mesure encore, et le défaut de 500 Mo se revoit d'après ce que l'historique en service mesurera.
- L'écran montre la place prise et la date du plus ancien pas gardé.

## Annuler et rétablir

- Annuler : le pas le plus récent encore fait. Rétablir : le pas le plus récemment annulé. Dans un outil ou une zone, on ne regarde que les pas qui les touchent.
- Une annulation n'est pas un pas : elle change l'état du pas annulé.
- Avant d'annuler ou de rétablir, chaque ligne touchée doit être encore dans l'état où le pas l'a laissée (sa copie « après » pour annuler, « avant » pour rétablir). Sinon : refus, en nommant le pas qui l'a changée depuis. Jamais de fusion ni d'écrasement. Conséquence : après une nouvelle écriture, un pas annulé qu'elle touche ne se rétablit plus ; il reste visible, annulé.
- Une annulation a les droits du geste qu'elle équivaut à faire : celle qui supprime une ligne passe par le même contrôle « qui dépend de ça ? » qu'une suppression par le service. Ce contrôle est écrit une fois, appelé par les deux ; il n'existe pas encore (le refus conçu dans `docs/design/typed-conditions.md`), et s'appliquera aux deux le jour où il sera codé.
- Les champs que l'app tient seule sont hors de l'historique : ceux qu'un schéma d'entrée marque `system_managed` (`SystemManagedFields`), les colonnes des tables du cœur que l'app seule écrit (`created_at`, `updated_at`…), que la porte d'écriture connaît par une liste, et ses pense-bêtes, des clés de `state` ou de réglages qu'aucune marque ne désigne aujourd'hui et que chaque outil, ou la catégorie de réglages, déclare : le rappel envoyé d'un événement d'Agenda (`reminded_until`, `reminded_for`), l'échéance signalée d'un élément de Liste (`due_notified`), l'avertissement de sauvegarde vu (`data_seen`, écrit par `AutoBackup.seen` comme un geste, `processUserAction` hors de toute opération : à écrire comme l'app). Une écriture de l'app qui ne touche qu'eux n'est pas un pas ; la vérification avant d'annuler les ignore, et l'annulation les laisse dans leur état présent. Exemple : un message programmé dont on a changé le texte, envoyé depuis, retrouve son ancien texte sans que son prochain envoi recule, ce qui le renverrait. Ce n'est pas une fusion : l'utilisateur et l'IA n'écrivent jamais ces champs, l'app n'écrit qu'eux, aucune valeur n'a deux auteurs.
- Elle ne repasse pas par la validation par schéma : la ligne remise était valide à son écriture, et les migrations l'ont tenue à jour.

## Le service `history`

- `history.list` (filtre par outil, zone, période ; origine, heure, phrase, état), `history.undo` et `history.redo` (un pas par son id). L'écran et l'IA appellent les mêmes opérations ; `history.list` est documenté dans le prompt.
- Pas de règle à part pour l'IA qui annule un pas de l'utilisateur : elle peut déjà écrire les mêmes lignes ; c'est la validation des actions de l'IA qui en décide.

## Sauvegardes

- L'export n'emporte pas l'historique.
- La restauration vide l'historique, et sa confirmation le dit ; l'écran de restauration propose d'exporter avant.
- La démo et l'import sont des pas ordinaires.

## Écran

- Un écran Historique, ouvert depuis les Réglages (toute l'app) et depuis le menu d'un outil ou d'une zone (filtré).
- Les pas du plus récent au plus ancien, avec leur état ; Annuler et Rétablir en haut ; toucher un pas déplie ses lignes avant et après.
- Pas de bandeau « annuler » après chaque geste pour l'instant — à l'usage, s'il manque.
