# L'outil Agenda — conception

Conçu le 2026-10-16. Un événement a une heure et passe de lui-même : un rendez-vous a lieu, puis il est passé, sans qu'on coche rien. C'est ce qui le sépare d'un élément de Liste à échéance, qu'on coche et qui attend tant qu'il ne l'est pas. Un outil Agenda est un calendrier thématique (« Médical », « Perso »), comme un Journal est un type de journal ; ses entrées sont ses événements.

## Nom

`agenda` dans le code (tooltype, opérations, clés) ; « Agenda » à l'écran en français, « Calendar » en anglais.

## Les usages

| Usage | Forme |
|---|---|
| Dentiste jeudi 14 h | un événement, 45 min, lieu |
| Appeler le notaire à 11 h | un événement sans durée |
| Kiné tous les mardis 18 h | une série, chaque semaine |
| Anniversaire de Léa | une série annuelle, journée entière, dans un Agenda « Anniversaires » sans rappel |
| Congés du 3 au 14 août | un événement journée entière, 12 jours |

Ce qui n'en est pas : une chose à faire avant un moment (la Liste et son échéance), un rappel sans événement (Messages), une chose répétée dont on veut savoir qu'elle est faite (un Suivi).

## L'événement

- **Son `timestamp` est son début**, futur ou passé. Tout ce qui lit des entrées par leur date marche donc tel quel : la Période, la Sélection d'entrées, la Lecture, le Graphique, `TOOL_DATA`.
- **Son nom** est le nom de l'entrée.
- **`data`**, déclaré par le type :
  - `duration` (`duration-millis`), facultative : sans elle l'événement est un moment, avec elle un créneau. Une durée plutôt qu'une heure de fin : déplacer un rendez-vous garde sa durée vraie.
  - `all_day` (oui/non) : le `timestamp` est alors minuit du jour dans le fuseau de l'app (`DateTimeConfig.getZoneId()`), et la durée se compte en jours, `days` (1 par défaut), une journée ne faisant pas toujours 24 h. `duration` et `days` ne vont jamais ensemble.
- **Les autres champs** sont ceux de l'utilisateur (« Lieu », « Avec qui »), qu'il déclare lui-même : aucun préréglage, le type n'en sachant rien. Ils s'affichent comme dans la Liste, en compact sous le nom (`CustomFieldsDisplay`).
- **`state`**, écrit par les opérations et le planificateur seuls, jamais par l'écriture ordinaire :
  - `status` : `planned` pour un événement normal, `cancelled` (annulé : visible, son nom précédé de « Annulé · », sans rappel), `removed` (retiré : invisible du planning, de la tuile et des rappels ; occurrence de série seulement). Toujours présent, il est le statut de départ du cœur (`EntryStart`) : un événement se crée `planned`, et seules les opérations de l'outil et l'app changent ce statut ;
  - `series` : la clé de la série qui l'a créé, absente pour un événement isolé ;
  - `series_at` : l'heure de sa série pour laquelle l'occurrence a été donnée, d'où la série compte la suivante (une occurrence déplacée la garde ; celle d'une journée entière, qui commence à minuit, garde l'heure du planning) ;
  - `reminded_until` et `reminded_for` : le dernier rappel envoyé, tous ceux d'avant l'étant, et le début pour lequel ils l'ont été ; un début déplacé efface les deux (`settleEntries`).
- **Une journée entière n'a pas de durée, un événement à heure pas de jours** : le service refuse l'un avec l'autre (`refuseEntry`, un refus du type d'outil sur l'entrée entière), en création comme en modification.
- **Aucun statut fait / pas fait** : à venir, en cours ou passé se lit à l'horloge.
- **Sa fin** : le début plus `duration` ; la fin de son dernier jour pour une journée entière ; son début pour un événement sans durée.

## Les séries

Une série vit dans la config et produit des entrées réelles : ses occurrences se lisent, se déplacent et s'annulent une par une, comme tout événement.

```json
{
  "reminders": [{"relative": {"unit": "HOUR", "offset": -1, "edge": "SAME"}}],
  "all_day_reminders": [{"relative": {"unit": "DAY", "offset": -1, "edge": {"at": "18:00"}}}],
  "series": [
    { "key": "kine", "name": "Kiné", "schedule": { "...": "ScheduleConfig, chaque mardi 18:00" },
      "duration": 2700000 },
    { "key": "lea", "name": "Anniversaire de Léa", "schedule": { "...": "chaque année le 12 mai" },
      "all_day": true, "days": 1 }
  ]
}
```

- **Une série** : `key`, donnée à sa création et gardée ensuite (`completeConfig`, comme la clé d'un critère d'Objectif) ; `name`, le nom de ses occurrences ; `schedule`, la Planification (`ScheduleSettings.group`) ; `duration`, ou `all_day` et `days`. Pas de valeurs pour les champs de l'utilisateur : leur forme dépend de l'outil, qu'une config ne sait pas déclarer (`TODO.md`) ; une occurrence naît sans elles, et se remplit comme tout événement. Pas de fin à elle : la Planification sait les dates précises, et ses limites sont les siennes (« toutes les 2 semaines » attend l'item de `TODO.md` sur la brique de planning).
- **Le planificateur crée les occurrences 90 jours devant, et toujours au moins la prochaine** (l'anniversaire dans 8 mois existe déjà). Il ne crée que celles qui viennent après la dernière déjà donnée (`series_at`), jamais avant maintenant : une occurrence déplacée garde sa place, une retirée n'est pas recréée. Une occurrence porte `state.series` et `state.series_at`.
- **Une occurrence se modifie seule** : son heure, sa durée, ses champs ; elle s'annule ou se retire. Elle ne se supprime pas : le service refuse la suppression d'une entrée qui porte `state.series` (`refuseDelete`, un refus du type d'outil qui n'empêche pas la modification), en renvoyant à `agenda.remove`. Une suppression ferait revenir la dernière occurrence au passage suivant du planificateur ; retirer garde l'entrée, et donc la mémoire.
- **Le planning d'une série change** : ses occurrences futures, quelles qu'elles soient (déplacées, annulées, retirées, annotées), sont supprimées et recréées sur le nouveau planning, après confirmation (« Les 12 occurrences à venir seront recréées ; ce que tu y as changé sera perdu »). Elles passent par les entrées qu'une config supprime (`entriesRemovedByConfig`, `confirm_migration`), que le comptage à blanc (`tools.update dry_run`) compte. Le passé ne bouge jamais.
- **Le reste d'une série change** (nom, durée, journée entière) : le changement descend sur ses occurrences futures, sur chaque valeur qui est encore l'ancienne valeur de la série ; une valeur changée à la main reste. Sans confirmation : rien ne se perd. Aucune marque « retouchée » : on compare à la config précédente.
- **Supprimer une série** supprime ses occurrences futures, après confirmation, et garde les passées.

## Les rappels

- **Réglés dans la config seulement** : `reminders` pour les événements à heure, `all_day_reminders` pour les journées entières, deux listes d'Instants relatifs au début de l'événement (section suivante). Ni une série ni un événement n'en portent : des rappels différents, c'est un autre Agenda.
- **Une notification par rappel**, à son heure, par `notifications.send`, l'icône de l'outil ; la toucher ouvre la fiche de l'événement. Un événement annulé ou retiré n'en envoie pas.
- **Un rappel manqué** (téléphone éteint, app arrêtée) part en retard tant que l'événement n'est pas terminé : téléphone allumé à 8 h, l'anniversaire du jour se rappelle. Après sa fin, il est abandonné.
- **Ce qui est parti** se note dans `state.reminded_until` : le dernier rappel envoyé. Les rappels dus ensemble partent en une notification. Un rappel ajouté à la config après coup part s'il tombe après le dernier envoyé ; avant lui, il est tenu pour envoyé.
- **La notification ouvre la fiche de l'événement** : `notifications.send` prend `entry_id`, et l'app ouvre l'outil sur cette entrée plutôt que sur la plus ancienne qui attend.
- **Les events du cœur n'y sont pour rien** : l'Agenda ne les attend pas. Le jour où ils existent (`TODO.md`, « Events du cœur »), « un événement commence » en sera une source, à laquelle une routine pourra s'accrocher.

## L'Instant étendu

Un rappel est un Instant relatif, la brique de `docs/BRICKS.md`, que le contexte limite aux dates relatives, sa référence étant le début de l'événement (« Par rapport à : le début de l'événement »). Il manque à la brique trois choses, ajoutées à elle et non à côté ; les formes stockées existantes restent valides.

- **Le bord `SAME`**, « à la même heure » : la même position, N unités plus loin, sans recalage, au calcul du calendrier dans le fuseau de l'app. Référence mardi 14 mars 14 h 30, décalage −1 : HOUR → 13 h 30 ; DAY → lundi 13 mars 14 h 30, même heure affichée au changement d'heure ; WEEK → mardi 7 mars 14 h 30 ; MONTH → 14 février (un 31 mars donne le dernier jour de février) ; YEAR → 14 mars de l'année d'avant (un 29 février donne le 28).
- **Une heure comme bord**, avec l'unité jour seulement : `"edge": {"at": "18:00"}`. « La veille à 18 h » : `{"unit": "DAY", "offset": -1, "edge": {"at": "18:00"}}` ; « le jour même à 8 h » : décalage 0.
- **L'unité `MINUTE`**, avec `SAME` seulement : « 15 min avant », `{"unit": "MINUTE", "offset": -15, "edge": "SAME"}`.

`TimePoint.Relative`, `TimePoint.read`, `TimeResolver` et `InstantPicker` les prennent ; le sélecteur ne propose `SAME`, l'heure et la minute que là où le contexte le permet (les sortes permises, la précision). `PeriodType` sert aussi au sélecteur de périodes et aux pas du Graphique : `MINUTE` n'y apparaît pas.

## Le réveil précis

App fermée, le planificateur ne se réveille que toutes les 10 min (`SchedulerAlarm`) : « 15 min avant » pourrait arriver 5 min avant.

- **La même alarme, avancée** : `ToolScheduler` gagne une méthode facultative, l'heure de la prochaine action de l'outil (aucune par défaut) ; l'Agenda y répond par son prochain rappel non envoyé. À chaque tic, le cœur arme l'alarme au plus tôt entre « dans 10 min » et la plus proche de ces heures. Pas de seconde alarme, rien de neuf au manifeste.
- **Réarmée à chaque tic**, celui de la minute app ouverte compris, et non plus à sa seule sonnerie : un événement ajouté à l'écran est connu de l'alarme avant que l'app se ferme.
- **Sa limite** : en veille profonde, Android ne fait sonner les alarmes `setExactAndAllowWhileIdle` d'une app qu'environ toutes les 9 minutes ; un rappel qui suit de près une autre sonnerie peut arriver jusqu'à ~9 min en retard. Non mesuré : à essayer sur le téléphone, posé à plat écran éteint, deux rappels à 3 min d'écart, chaque notification à l'heure ou non.
- **Le repli, si la mesure montre des retards** : `setAlarmClock` pour les seuls rappels, exacte sans limite, mais qu'Android montre comme un réveil (une icône d'horloge dans la barre d'état, « Prochaine alarme » sur l'écran de verrouillage).

L'Agenda en est le premier usage ; Messages, les routines et les échéances de Liste le prendront en disant leur prochaine action.

## Les opérations

- **L'écriture ordinaire** (`tool_data.create` / `update`, écran et IA) : le `timestamp`, `duration`, `all_day`, `days`, les champs de l'utilisateur ; jamais `state`. Supprimer : un événement isolé seulement.
- **`agenda.cancel`** : annule un événement, isolé ou de série.
- **`agenda.remove`** : retire une occurrence de série ; refusé sur un événement isolé, qui se supprime.
- **`agenda.restore`** : rend son état normal à un événement annulé ou retiré.
- Les trois, écran et IA. Une série se crée, se change et se supprime par la config (`tools.update`).

## L'écran

- **En haut, le type de période** (`PeriodFilterType`), réduit à « À venir » (par défaut), Jour, Semaine, Mois ; « À venir » montre les événements en cours puis futurs, sans fin ni flèches ; Jour, Semaine et Mois ont les flèches et le calendrier au toucher du libellé (`SinglePeriodSelector`), qui mènent aussi au passé.
- **La liste, groupée par jour** (« Aujourd'hui », « Demain », « jeudi 23 octobre »), le début de journée de l'app (`dayStartHour`) compris, sauf pour une journée entière, rangée à son jour de calendrier. Une ligne : l'heure ou « Journée », le nom, la durée en petit (« 14 h – 14 h 45 »), les champs de l'utilisateur en compact ; en cours mis en avant, annulé précédé de « Annulé · », une icône de répétition sur une occurrence de série. Une journée entière de plusieurs jours apparaît à chacun de ses jours. « Retirées (n) », replié en bas, celles de la période affichée, chacune avec « Rétablir ».
- **Pas de vue calendrier** (grille du mois) : la liste par jour répond à « qu'est-ce que j'ai ».
- **« Nouvel événement »** ouvre la fiche : nom, date et heure, ou « Journée entière » et le nombre de jours, durée, puis les champs de l'utilisateur. **« Se répète »**, coché, montre la Planification et cache les champs de l'utilisateur : l'enregistrement crée alors une série dans la config, et le planificateur ses occurrences. Rien n'est écrit avant « Enregistrer ».
- **Un événement touché** ouvre sa fiche, modifiable, avec « Annuler » (ou « Rétablir ») et « Supprimer » ; sur une occurrence de série, « Retirer » à la place de « Supprimer », et « Modifier la série », qui ouvre la même fiche sur la série ; un planning changé demande la confirmation des occurrences recréées.

## La tuile

- **Le résumé** : le prochain événement ou celui en cours, son nom et son moment en relatif (« dans 2 h », « demain 14 h », « jeu. 23 », « en cours ») ; « Rien à venir » sinon.
- **Le corps** : les événements à venir, un par `TileLine` : le moment à gauche (« 14:30 » aujourd'hui, « Dem. », « Jeu. 23 »), le nom au milieu, les champs de l'utilisateur en seconde ligne discrète ; un annulé précédé de « Annulé · », un retiré absent. EXTENDED, ce que tient sa rangée ; SQUARE, ses trois rangées ; FULL, les 7 prochains jours.
- Un événement touché ouvre sa fiche (`EntryToOpen`) ; ailleurs, l'outil.

## L'IA

- **Elle lit** par `TOOL_DATA` avec une période, dates en ISO ; une série dans la config, dont le planning dit les occurrences au-delà de celles créées.
- **Elle écrit** par l'écriture ordinaire et les trois opérations, comme l'écran. Un changement de planning de série qui supprime des occurrences passe par la protection des données enregistrées (`docs/design/validation.md`) : la personne accepte d'abord.
- **Le texte `usage`** dit les frontières de « Les usages » : pour ce qui a lieu à une heure et passe de soi-même ; ni l'échéance d'une Liste, ni le rappel de Messages, ni la chose faite d'un Suivi.

## L'attente

L'Agenda n'attend jamais rien : pas de point sur la tuile (`getWaiting` vide), un événement en cours n'est pas une attente.

## L'ordre

1. L'Instant étendu (`SAME`, l'heure comme bord, `MINUTE`), sa forme au prompt de l'IA et dans `docs/BRICKS.md`.
2. Le réveil précis au cœur.
3. Ce que le changement d'une config fait aux entrées : `entriesRemovedByConfig` prend aussi la config précédente (le planning d'une série a-t-il changé) ; une méthode nouvelle du type d'outil réécrit des entrées au changement de config, dans la même transaction (le reste d'une série qui descend).
4. L'outil : type, service et opérations, planificateur (occurrences et rappels), écran, tuile, textes et Guide.

## Hors de cette spec

- Une vue calendrier (grille du mois).
- Des rappels par série ou par événement.
- L'import et l'export d'un calendrier extérieur (`.ics`, Google Agenda).
- Des participants, une visio, des pièces jointes.
- « Un événement commence » comme source des events du cœur.
