# Écrans des Tâches et des routines

Une Tâche et une routine ont la même forme : ce qu'on consulte (état, lancement, exécutions) et ce qu'on règle. Aujourd'hui la Tâche met tout sur une page et se crée avant d'être réglée, la routine se règle en deux endroits (la fenêtre « Paramètres », `CreateAutomationDialog`, et l'écran du message, `Place.Seed`) sans frontière qui tienne. Les deux prennent le modèle de l'outil : une page, une config.

## La page

Ouverte par un toucher sur la tuile et par son bouton « voir ». En haut, une carte : l'état, l'interrupteur, « Lancer maintenant » ; dessous, les exécutions, chacune s'ouvrant comme aujourd'hui ; dans l'en-tête, le bouton de config.

- Routine : `AutomationScreen` (`Place.Automation`), aujourd'hui l'historique seul, gagne la carte et le bouton de config.
- Tâche : `TaskScreen` (`Place.Task`) perd ses réglages, ses étapes, « Enregistrer » et « Supprimer ».

## La config

Un lieu à elle (`Place`), pas une fenêtre : ouvert par l'appui long sur la tuile, par le bouton de config de la page, et à la création, sans id. Rien n'existe avant « Enregistrer », qui écrit tout en un appel : `create` sans id, `update` avec (`automations.*` prend déjà message et réglages ensemble, `tasks.*` réglages et étapes). Quitter avec des changements non enregistrés demande d'abord.

Dans l'ordre, tout déplié :

1. Nom et apparence : nom, icône, couleur, groupe.
2. Quoi : le message (`AutomationEditor`, qui ne vit que de ses segments, sans session) ou les étapes.
3. Quand : planning et son rattrapage, déclencheurs (« Part aussi quand… »), posés dans l'écran, plus en fenêtres.
4. Fin : prévenir à la fin, événement émis.
5. Avancé : pour une routine, fournisseur, identité, accès ; pour une Tâche, ce qui reste.

Puis « Enregistrer », et « Supprimer » pour une chose qui existe.

Ce qui disparaît : `CreateAutomationDialog`, le mode SEED de `AIScreen` et `Place.Seed` (`GuidePath`, `MainScreen`, le bouton « éditer » de la tuile de routine), la création immédiate d'une « Nouvelle Tâche » (`ZoneScreen`).

## La tuile

Un problème qui empêche de partir (une routine sans fournisseur) ne prend plus la ligne d'état : une icône d'alerte (`circle-alert`) la précède, la ligne garde ce qu'elle dirait sans lui (le planning, la prochaine exécution). La phrase entière se lit sur la page et dans la config. Aucune marque de thème nouvelle.

## La copie

« Copier une Tâche » dans le menu « Ajouter » d'une section, comme « Copier une routine » : la zone, puis la Tâche (`DuplicateSelector`, type `TASK`), `tasks.duplicate` sur le modèle de `automations.duplicate`. La copie, « X (copie) », prend réglages et étapes, ni exécutions ni jugements de condition. Une copie, de Tâche comme de routine, naît éteinte : avec un planning ou un déclencheur, elle partirait en double de l'original.

## À reprendre ailleurs

- Le Guide : le chapitre des routines attend `automations.create` puis `automations.update` (`chapters.json`), ses textes nomment la fenêtre et l'écran du message ; le chapitre « Tâches » ouvre la page d'une Tâche pour sa condition et son étape ; `scripts/check_guide.py` dit ce qui ne colle plus.
- `docs/UI.md` : la page et la config d'une Tâche et d'une routine.
