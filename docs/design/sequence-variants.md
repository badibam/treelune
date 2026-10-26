# Les variantes de la Séance

Une Séance est aujourd'hui une suite d'étapes. Deux usages qui reviennent n'en ont pas : méditer un temps qu'on choisit au moment de s'asseoir, et écouter un programme pris dans une bibliothèque (des programmes audio guidés, des leçons de mouvement enregistrées). Les faire tenir dans des étapes force soit une Séance par programme, chacune suivant sa fiche, soit une durée fixée dans la config qu'on prolonge en cours de route.

## Trois variantes

Une Séance est d'une variante, et la variante dit ce qui fait la séance :

| Variante | Ce qui fait la séance | Demandé au lancement | Fin |
|---|---|---|---|
| **Étapes** | ses étapes et ses blocs (la config d'aujourd'hui, `SequencePlan`) | rien | la dernière étape passée |
| **Minuteur** | une durée, avec sa valeur par défaut dans la config | la durée, la valeur par défaut proposée | la durée écoulée |
| **Média** | un audio ou une vidéo, pris dans une sélection d'entrées que la config désigne, par le champ Fichier qu'elle nomme | l'entrée de cette sélection dont on joue le fichier | la fin de la lecture |

## Une étape fabriquée au démarrage

Pour l'utilisateur, Minuteur et Média n'ont pas d'étapes. Dans le code, la variante fabrique au démarrage **une seule étape** à partir de ce qu'on lui a répondu au lancement, puis tout tourne comme aujourd'hui : `SequenceRun`, `SequenceGestures`, `SequenceRunService`, la pause, la reprise après un arrêt de l'app, les signaux, la séance « à terminer », l'historique et sa correction, la tuile.

- Minuteur : une étape minutée de la durée choisie, au nom de la Séance.
- Média : une étape qui dure son média, au nom de l'entrée, le fichier lu dans son champ comme un média d'étape suivi l'est aujourd'hui (`SequenceMedia`).

Une seule fonction fait les étapes d'une variante depuis sa réponse au lancement (rien, une durée, une entrée) ; « Démarrer », `complete` et « Noter après coup » l'appellent tous trois.

## La config

En tête de la config, le choix de la variante (`SettingNode.Variant`), qui remplace la section des étapes selon le cas :

- Étapes : la liste d'aujourd'hui.
- Minuteur : une durée par défaut.
- Média : une Sélection (`SettingNode.Selection`, un outil et ses filtres, sans période), un champ Fichier audio ou vidéo de cet outil (`FieldPicker`), et un Tri.

Les signaux, le planning et l'expiration ne changent pas, pour les trois variantes.

La règle des termes de fichier (`ConfigTerms.refuseFiles`, `docs/BRICKS.md`, « Term ») ne bouge pas : la config de la Média ne lit aucun fichier, elle désigne où le prendre, et l'entrée est choisie au lancement.

## La brique Tri

Une liste de clés, chacune un champ (`FieldPicker`, le nom et la date compris) et un sens : `[{"field": "data.programme", "direction": "asc"}, {"field": "data.numero", "direction": "asc"}]`. Liste vide : la date, la plus récente en tête, l'ordre d'aujourd'hui. Appliqué dans la requête SQL, comme les filtres.

Il vit à côté de la Sélection, pas dedans : la Sélection sert aussi aux Lectures, où « le dernier » reste le dernier par date. Premier usage, la Média ; l'écran d'une Fiche, qui trie aujourd'hui en mémoire sans rien garder, pourra l'enregistrer.

## Le lancement

Seul « Démarrer », sur l'écran de la Séance, démarre une séance : la tuile et la notification d'une séance prévue ouvrent cet écran. Pour Minuteur et Média, « Démarrer » ouvre une fenêtre :

- Minuteur : la durée, remplie avec celle de la config, puis « Démarrer ».
- Média : les entrées de la sélection qui ont un fichier dans le champ, dans l'ordre du Tri ; la dernière jouée dans cette Séance marquée « Jouée en dernier », la liste ouverte à sa place ; un toucher démarre.

## Ce que garde l'entrée

Le modèle commun, sans champ propre à une variante, avec deux ajouts à l'étape gardée (`SequenceSteps.Kept`), qui servent aussi aux Étapes :

- `source_entry` : la Référence (`{"kind": "ENTRY", "id", "field"}`) de l'entrée dont l'étape a lu ses fichiers, à côté de `source`, son nom figé au démarrage. Le nom garde ce qui a été joué même l'entrée supprimée ; la Référence retrouve l'entrée, ce que lit « Jouée en dernier ». Les séances déjà stockées n'ont que `source` : aucune migration.
- la durée prévue de l'étape : la durée choisie d'un Minuteur, celle d'une étape minutée qu'on a prolongée (« 2 min 45 sur 2 min »).

Où la lecture d'un média s'est arrêtée est déjà là : le `time` de l'étape, mesuré sur l'horloge de la séance, qui s'arrête pendant les pauses.

Les compteurs (`steps_done`, `steps_skipped`, `steps_not_done`) restent écrits. L'historique les cache pour une séance d'une seule étape, règle prise sur la séance, pas sur la variante d'aujourd'hui.

## Les gestes

L'écran montre les gestes selon l'étape, jamais selon la variante :

- « Passer » et « Retour » absents quand la séance n'a qu'une étape ;
- « Prolonger » absent sur une étape qui dure son média, ce qui corrige aussi les Étapes, où l'on prolonge aujourd'hui une étape média dans le silence.

## `complete` et l'IA

`complete` prend la réponse au lancement de la variante :

- Minuteur : `duration`, la durée prévue et faite ; absente, celle de la config.
- Média : `entry`, l'id de l'entrée jouée, obligatoire, refusé hors de la sélection ou sans fichier ; la durée par défaut, celle du média.

« Noter après coup » demande la même chose, avec la même fenêtre qu'au lancement.

## Changer de variante

Rien de particulier : les séances passées gardent leurs étapes ; une séance prévue qui attend pose, démarrée, la question de la variante actuelle ; une séance en cours continue sur ce qu'elle a fabriqué ; la config perd ce que seule l'ancienne variante déclarait (`SettingVariants.switched`), « Quitter sans enregistrer ? » rattrapant une erreur.
