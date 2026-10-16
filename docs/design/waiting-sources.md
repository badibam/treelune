# Les sources de l'attente — conception

Conçu le 2026-10-16. La brique « Attente » (`docs/BRICKS.md`) ne connaît que les entrées d'un outil : des conditions déclarées par son type (`ToolTypeContract.getWaiting`), comptées par le cœur par outil et par zone (`tools.waiting`, lu dans `LocalWaiting`), un point du thème au coin de l'icône (`UI.WaitingMark`). Deux marques vivent déjà à côté, chacune écrite à la main, qui ne reprennent que le point : la sauvegarde automatique sur le bouton Réglages (`AutoBackup.pending`) et le Guide sur son bouton (`Guide.pending`). Cette spec ajoute deux sources, la conversation et les routines, et donne à la brique la forme qui les tient toutes.

## Les deux formes de la brique

1. **Ce qui appartient à une zone** : les entrées des outils (inchangées) et les exécutions des routines (nouveau). Le cœur les compte par chose et par zone : `Waiting` gagne une table `automations` à côté de `tools`, le compte d'une zone additionne les deux. Une seule lecture rend les deux (`tools.waiting` élargie, ou renommée `waiting.read`), relue quand les données d'un outil changent, quand une session de routine se ferme et quand une exécution est lue. Le point se met sur la tuile ou la carte, et sur la tuile de la zone.
2. **Ce qui est unique dans l'app** : la conversation active (nouveau), la sauvegarde automatique et le Guide (ramenés dans la brique). Chacune est une source nommée qui dit en continu si elle attend, et a son geste « vu » ; chacune garde sa règle de « vu », la brique n'impose que cette interface. Le bouton qui la porte la nomme (`UI.WaitingMark(source)`) au lieu de lire un booléen à lui ; un bouton qui mène à plusieurs sources les nomme toutes, et porte le point si l'une attend.

Les deux formes restent séparées : la première demande un compte par zone lu en base, la seconde un booléen observé. Les fusionner donnerait une zone fictive à chaque source unique, ou ferait de chaque routine un booléen global. Le point du thème (`ThemeContract.WaitingMark`) est le même partout.

## La conversation

- **Ce qui attend** : la session active (une seule à la fois) dont le dernier message de l'IA adressé à l'utilisateur est plus récent que le moment où on l'a vue. Comptent la réponse de l'IA, une demande de validation et un formulaire qu'elle pose ; pas les messages système ni les résultats de commandes.
- **Lu = affiché** : la conversation à l'écran, app au premier plan. Ce moment est écrit dans une colonne `seen_at` de `ai_sessions`, tenue à jour tant qu'elle est affichée : une réponse arrivée pendant qu'on la regarde ne marque rien. Pas de compteur, un point.
- `last_user_interaction_time` ne sert pas : il nourrit le minuteur d'inactivité, et ouvrir la conversation sans écrire n'est pas une interaction.
- **Où** : le bouton flottant seul, qui ouvre la session active comme aujourd'hui. Rien dans l'historique des conversations : une session ne reçoit de message qu'active, et en changer passe par la conversation affichée, donc vue. Pas de notification Android.

## Les routines

### Le résumé, demandé à la confirmation de fin

Aujourd'hui (`AIStateMachine`), un premier `completed: true` sans commande fait relancer l'IA par `ai_completion_confirmation_required` ; un second `completed: true` sans commande ferme la session (`AWAITING_SESSION_CLOSURE`, puis `COMPLETED`). Seuls changent la relance et la réponse qui ferme :

- La relance demande en plus, si l'IA a fini, une clé `summary` : ce qu'elle a fait pendant cette exécution et pourquoi, pour l'utilisateur, en quelques phrases, en signalant ce qui mérite son attention.
- La réponse qui ferme porte `completed: true`, `summary` et aucune commande. L'app range le résumé avec l'exécution, dans une colonne `summary` de `ai_sessions`, puis ferme comme aujourd'hui.
- Un `completed: true` sans `summary` à ce moment est une réponse incomplète : l'app relance une fois avec un rappel. S'il manque encore, la session se ferme sans résumé, et l'exécution compte comme un problème.
- Un `summary` porté par le premier `completed: true` n'est pas gardé : l'IA a pu travailler entre les deux.
- Le prompt des routines dit dès le départ qu'un résumé sera demandé à la fin.
- Une exécution qui ne finit pas en `COMPLETED` (délai, réseau, limite de tours, erreur) n'a pas de résumé de l'IA : on ne la relance pas pour l'écrire. Elle compte comme un problème.

### Les faits, à côté du récit

Sous le résumé, une ligne écrite par l'app depuis les messages de la session : les écritures faites, celles refusées ou échouées, et la raison de fin quand ce n'est pas `COMPLETED` (`RunEnd.facts`). Recalculée à l'affichage, jamais stockée. Le détail par outil est écarté au codage : le résultat d'une commande ne nomme pas son outil, il faudrait le rapprocher de la commande de l'IA. L'IA raconte, l'app atteste ; un écart entre les deux se voit.

### Le réglage « Me signaler la fin »

Un réglage par routine, dans `AutomationSettings` : `always`, `on_problem`, `never` ; `always` pour une routine neuve. Le résumé est toujours écrit et gardé ; le réglage décide seulement si l'exécution attend. Un problème : une fin autre que `COMPLETED`, un résumé manquant, une erreur ou un refus dans la ligne de faits.

### Ce qui attend, où, et ce qu'on ouvre

- Une exécution attend tant que son `seen_at` est vide. À la fermeture, une exécution que le réglage ne signale pas reçoit `seen_at` à l'heure de fin. Une seule colonne pour la conversation et les routines.
- **Lu = l'écran de l'exécution ouvert** (`Place.Execution`). Ouvrir l'historique de la routine ne suffit pas.
- La tuile de la zone sur l'accueil et la carte de la routine dans sa zone portent le point. La carte ouvre l'historique de la routine (`Place.Automation`), comme aujourd'hui ; chaque exécution non lue y est marquée, la première phrase de son résumé sous sa date.
- L'écran d'une exécution montre le résumé en tête, la ligne de faits, puis la session entière comme aujourd'hui.
- Une notification Android à la fin d'une exécution qui attend : le nom de la routine et le début du résumé ; la toucher ouvre l'exécution.
- Le bouton flottant n'en dit rien.

## Ce qui change dans le stockage

La migration 76 ajoute à `ai_sessions` `seen_at` (instant, nullable) et `summary` (texte, nullable), et le réglage de fin aux routines existantes. Les sessions passées reçoivent `seen_at` à leur dernière activité : aucune ne se met à attendre après la mise à jour. La sauvegarde et l'import portent les deux colonnes.

## Pistes, non décidées

Des idées proposées pendant la conception pour éprouver le modèle, sans décision :

- Une routine qui n'a pas pu tourner (fournisseur supprimé, occurrence sautée) : sa carte elle-même attendrait, faute d'exécution à marquer (forme 1).
- L'Alerte et les events du cœur, à venir, en forme 1.
- Un fournisseur d'IA en panne (clé refusée, modèle retiré d'après `provider-facts`), marqué jusqu'à Réglages › Fournisseurs (forme 2).
- Ce qu'une IA extérieure a écrit par le connecteur pendant l'absence, une fois l'historique des écritures en place (`undo-history.md`, forme 2).

Ne sont pas des sources : la demande d'accord du connecteur (90 s, dans sa notification) et l'écran d'après plantage (il s'impose au lancement).

## Ouvert

- Une session de routine restée en `AWAITING_SESSION_CLOSURE` à la fermeture de l'app est close au démarrage (`AIStateRepository.initializeFromDb`) sans passer par `RunEnd` : son résumé n'est pas gardé et elle attend quel que soit le réglage.
