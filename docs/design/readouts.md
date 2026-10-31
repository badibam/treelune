# Repères (Readouts)

Conception du 2026-10-31, à implémenter puis élaguer.

## Le besoin

Savoir ce qui reste à faire sans avoir à s'en souvenir. La pastille dit qu'un questionnaire ou un rappel attend ; rien ne dit qu'un outil rempli à la main n'a rien reçu aujourd'hui, et ses tuiles sont dispersées dans leurs groupes, à leur place logique. Une variable qui compte les entrées du jour, posée dans une section en tête de zone, en est la version d'aujourd'hui : une seule valeur, pas de phrase, pas de couleur, rien qui remonte sur l'accueil, et son toucher ouvre la variable, pas l'outil.

## Ce qu'est un Repère

Une chose nouvelle, créée dans un groupe d'outils d'une zone, au même niveau qu'un outil, une variable ou une Tâche. Il affiche une phrase dont des valeurs sont lues au moment de l'affichage. Il ne calcule rien pour les autres : une formule, une condition, un graphique lisent une variable, jamais un Repère.

- **Sa phrase** : un texte où des valeurs nommées s'écrivent `{nom}` (« Verres d'eau : {fait} / {attendu} »), chaque valeur déclarée sous la phrase et étant un Terme (une constante, une variable, une Lecture), choisi par `TermPicker`. Lue avec maintenant pour référence, relue quand un outil ou une variable qu'elle lit change, comme une tuile de variable (`tools_read`).
- **Sa cible** : une chose de l'app (`Reference` : une zone, un outil…), ouverte par le toucher.
- **Ses couleurs** : une liste ordonnée de lignes `condition → couleur`, la première vraie l'emportant ; aucune vraie, pas de couleur. La couleur est un nom de la palette des icônes (`IconColor`), que le thème dessine. Une case **« Signaler »** par ligne fait aussi allumer la pastille par cette ligne ; une ligne qui dit que tout va bien ne la coche pas, pour que la pastille reste un appel.
- **Son habillage** : la brique Look entière (icône, sa couleur, texture, mode d'affichage), comme toute tuile. Chaque endroit en prend ce qui lui sert.

## Où il s'affiche

Il a toujours sa tuile propre dans son groupe ; pour qu'elle prenne peu de place, on la met en Icône, comme une Tâche. En plus, sa config coche les endroits où il remonte :

- la tête de son groupe ;
- la tête de sa zone ;
- la tuile de sa zone, à l'accueil ;
- la tête de son groupe de zones, à l'accueil ;
- la tête de l'accueil.

Plusieurs Repères au même endroit se regroupent en une seule tuile, dans l'ordre de l'arbre : l'ordre des zones à l'accueil, puis celui des groupes, puis la place du Repère dans son groupe. Un Repère qui lit les outils d'autres zones s'affiche en tête de l'accueil : la transversalité vient de ses Termes, pas d'une appartenance à l'app.

| Endroit | Ce qu'il prend du Repère |
|---|---|
| Sa tuile propre | tout, mode d'affichage et texture compris |
| Une tuile regroupée | l'icône, sa couleur, la phrase dans sa couleur, une ligne chacun |
| La tuile de sa zone, assez grande | les lignes qui tiennent, les suivantes coupées |
| La tuile de sa zone en Icône | des points côte à côte, un par Repère qui signale, trois au plus, dans l'ordre de l'arbre, chacun dans sa couleur |

## Le créer, le modifier

- Créer : le « + » d'une section, comme un outil ou une variable.
- Modifier, supprimer : un appui long sur sa tuile propre ou sur sa ligne, là où elle s'affiche, ouvre sa config.

## Hors de la première version

- La remontée des tuiles d'outil (leur résumé, leurs boutons) par les mêmes cases : la mécanique des endroits est écrite pour qu'elle s'y étende.
- Un Repère qui ne s'affiche qu'à certains moments (le soir, le dimanche).

## À trancher en le codant

- Une condition de couleur compare les valeurs nommées du Repère (« fait < attendu ») : un Terme ne sait pas aujourd'hui désigner une valeur locale. Un nouveau genre de Terme, réservé à ce contexte, ou la condition qui répète la Lecture.
- Le toucher d'un Repère sans cible.
- La tuile de zone en Minimal : des points, ou rien.
- Ce que l'IA en lit et en écrit (commandes, schéma, texte de départ), son passage dans l'export d'une partie et dans les copies, un chapitre du Guide, une fiche de la démo.
- Un Repère qui lit un outil ou une variable supprimés : lu « supprimé », comme une désignation, et le contrôle « qui dépend de ça ? » du TODO.
