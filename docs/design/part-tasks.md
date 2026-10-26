# Une partie emporte ses Tâches, ses API et ses événements

Une partie (`docs/DATA.md`, « A part of the app ») porte aujourd'hui zones, outils, entrées, variables, routines et identités. Les Tâches, les API et les événements, déjà dans la sauvegarde complète, n'y sont pas : une Tâche ne se partage pas, et une routine ou un outil branchés sur un événement arrivent avec un identifiant qui ne désigne rien.

## Les Tâches

- Elles se cochent dans leur zone, comme une routine (`PartSelection.tasks`, `PartTree`, élément `TASK`).
- Leurs liens se trouvent comme les autres (`IdWords` sur leur ligne : étapes, déclencheurs, événement émis). Vers un outil, ses entrées, une variable, une routine ou une API, ils **bloquent** : le service refuse une Tâche dont une cible manque, l'import échouerait.
- Elles arrivent **éteintes**, sans exécutions, comme une copie : un planning ou un déclencheur la ferait partir chez quelqu'un qui ne l'a pas relue.
- À l'import : un nouvel identifiant, `tasks.create` après les outils, les variables et les routines, sa tuile placée avec les autres de sa section ; supprimée avec le reste si l'import est défait.

## Les API

- Un bloc « API » de la partie, hors des zones : chacune se coche, Tâche ou pas (`PartSelection.apis`).
- Sa **clé ne part jamais**, comme un fournisseur d'IA : ses réglages secrets (`SecretSettings.names` de `ApiSettings`) sont retirés à la découpe. Chez qui importe, un appel échoue en disant que la clé manque, jusqu'à ce qu'elle soit saisie.
- Elle **garde son identifiant**, comme une identité. L'app la tient déjà : gardée, ou remplacée si demandé (la case des identités), sa clé locale restant. Une autre API porte son nom : elle arrive sous « Nom (2) », « Nom (3) »…, les étapes la nommant par son identifiant.

## Les événements

- Pas de case. Part chaque événement que nomme ce qui est coché : un outil qui prévient, une routine, une Tâche, qui l'émettent ou l'écoutent.
- Sa zone d'arrivée : la sienne quand elle est cochée ; sinon celle du premier élément coché qui le nomme, dans l'ordre de la liste, l'écran de partage le disant par un avertissement qui ne bloque pas (« L'événement X arrivera dans la zone B »).
- À l'import : un nouvel identifiant comme un outil, `events.create` avant tout ce qui peut le nommer.

## À reprendre ailleurs

- `docs/DATA.md`, « A part of the app » : les Tâches, les API, les événements.
- L'écran de partage et celui de l'import : les Tâches dans leur zone, le bloc API, l'avertissement d'un événement déplacé, la case de remplacement d'une API tenue.
- `PartImport` : le commentaire qui dit qu'une partie ne porte pas de Tâche.
