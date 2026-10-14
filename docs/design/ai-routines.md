# L'IA et les routines

Conçu le 2026-10-14. L'IA lit, crée, modifie et supprime les routines (`automations` dans le code), comme elle le fait des outils et des variables ; elle lit leurs exécutions pour en corriger une qui a raté. Aujourd'hui seul l'utilisateur les touche, depuis l'écran.

## Ce que l'IA fait

- **Lire, créer, modifier, supprimer, activer, désactiver.** Activer et désactiver passent par `UPDATE_AUTOMATION` et le réglage `is_enabled`, pas par une action à part.
- **Pas « lancer maintenant »** : une seule session tourne à la fois, une routine lancée depuis une conversation attendrait la fin de celle-ci.
- **Une routine peut créer ou modifier des routines**, elle-même comprise : pas de limite a priori, le masque d'accès la borne (plus bas). Point d'attention : une routine qui réécrit son propre message ou son planning le fait sans personne pour relire.
- **Les déclencheurs** (`trigger_ids`) ne s'écrivent pas et ne se montrent pas : rien ne les déclenche avant les events du cœur.
- **Le connecteur MCP** reçoit les mêmes commandes, borné par son masque par client.

## Les commandes

Sur le modèle de `TOOL_INSTANCES` et `TOOL_CONFIG` (`AICommands`) :

- **`APP_STATE`** porte les routines sous leur zone, à côté des outils et des variables : id, nom, activée, le planning en une phrase. Pas le message.
- **`AUTOMATIONS`** (`zone_id` facultatif) : la même liste à la demande, qui montre aussi une routine créée pendant la session.
- **`AUTOMATION_CONFIG`** (`id`) : les réglages de `AutomationSettings` (nom, fournisseur, groupe, activée, planning, rattrapage), le masque d'accès et le message en blocs, avec leurs schémas, comme une config d'outil.
- **`AUTOMATION_RUNS`** (`automation_id`, `period`, `limit`) : une ligne par exécution — heure prévue, heure réelle, raison de fin, coût, allers-retours —, décrite par un schéma déclaré dont les instants portent `"format": "epoch-millis"`, donc en ISO pour l'IA. Pas le chemin de `TOOL_DATA` : une exécution n'est pas une entrée d'outil.
- **`AUTOMATION_RUN`** (`id`, `messages` : `"all"` par défaut ou `"errors"`) : la session telle que son IA l'a reçue, rendue par le code de l'historique du prompt (`PromptManager`), les données jointes au départ remplacées par leurs pointeurs (l'IA les relit si elle veut), tenue au seuil de taille des données.
- **`CREATE_AUTOMATION`** (`zone_id`, `settings`, `message`, `access`), **`UPDATE_AUTOMATION`** (`automation_id`, partiel : ce qui manque garde sa valeur, comme `automations.update`), **`DELETE_AUTOMATION`** (`automation_id`).
- Créer écrit la session SEED, son message et la routine ensemble, ou rien.

## Le message

Une liste de blocs, lue et écrite dans la même forme : `{"type": "text", "text"}` et `{"type": "pointer", "pointer": {"selection", "attach": {"config", "entries"}}}`, la forme stockée de `PointerConfig`. La sélection est celle que l'IA écrit déjà dans la config d'un Graphique (`SettingNode.Selection`, son schéma par `SettingsSchemaGenerator`), dates en ISO converties par `CommandTransformer` ; le service la lit par `EntrySelection.fromJson`, le même lecteur que pour un pointeur posé à l'écran. L'IA garde, retire ou crée un pointeur ; un fichier ou une image ne s'y met pas, un message de routine n'en accepte pas.

## Le masque d'accès

- **L'IA l'écrit, jamais plus large que le sien.** Une conversation n'a pas de masque : elle écrit ce qu'elle veut, sous la validation. Une routine ou le connecteur ne donne qu'une partie de ce qu'il atteint ; un masque vide (tout) lui est refusé dès qu'il a des limites.
- **Une routine n'écrit que les routines d'une zone qu'elle atteint en complète**, comme pour les outils (`AccessRules`).
- **La validation** : les routines entrent dans le niveau d'une zone (`zones.validate`), avec ses outils, ses groupes et ses variables ; une zone protégée fait valider toute routine qu'on y crée ou modifie.

## Après la fusion

Les chapitres du Guide « Les routines » et « Parler à l'IA » disent que l'IA crée et modifie les routines.
