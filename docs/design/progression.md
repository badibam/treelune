# Progression — conception

Conception en cours, rien de codé. Seule la section « Décidé » engage ; tout le reste se discute. « Élément » est un mot provisoire pour la chose qu'on suit : la nommer fait partie de ce qui est ouvert.

## Le but

Tenir où on en est dans quelque chose qui se déroule par morceaux. Comme tout outil de Treelune, il doit être :

- le plus général possible ;
- capable de couvrir les usages variés courants ;
- propre et simple conceptuellement.

## La méthode

- Partir de scènes de vie de formes différentes, chercher ce qu'elles ont en commun, et seulement ensuite définir la notion.
- Un point à la fois, concis.
- Chaque question donne une situation concrète et sa logique, ce qui est en jeu, de vrais embranchements, puis un avis présenté comme un avis.
- Ne rien traiter comme acquis tant que l'utilisateur ne l'a pas décidé, et ne pas reconduire une piste parce qu'elle a déjà été écrite.

## Décidé

- Ce qui rend un élément atteint s'écrit avec la brique Condition, pas avec une Tâche. Un cas complexe passe par ce que la Condition sait déjà lire : une variable, une lecture, peut-être le résultat d'un Objectif, par exemple.
- Les périodes de ces conditions gardent tous les choix de la lecture, avec une borne de plus : « l'ouverture de l'élément ». Elle permet d'écrire « 10 sorties à ce palier ». Une période vide veut toujours dire sans limite, et la référence reste le moment de la lecture.

## En discussion : que sont les `tool_data` ?

Rien n'est tranché. Exemple : Camille monte 5 paliers de course, arrête l'hiver, puis recommence au printemps sans vouloir perdre les dates de l'an dernier. Une remise à zéro détruirait ces données précieuses.

- **Une entrée = un élément** : le plan est dans les entrées, chaque élément porte son état. On peut viser un élément par une Référence, mais recommencer écrase ses dates sans mécanisme en plus.
- **Plan en config, une entrée = un mouvement** (« palier 3 atteint le 12 mars ») : rien n'est écrasé, mais l'utilisateur trouve cette donnée peu réutilisable.
- **Plan en config, une entrée = un passage**, qui inclut les états de ses éléments : recommencer est naturel, et le passage peut garder une copie du plan, comme une tentative d'Objectif. Non vérifié : une Tâche pourrait réagir à un élément atteint si la progression émet un événement, comme l'Objectif émet le sien.

Si une entrée est un passage, les champs supplémentaires peuvent aller sur le passage (« reprise après une entorse », un bilan), sur l'élément dans le plan (sa consigne, sa séance), ou sur les deux : à discuter, en regardant si le passage seul est restrictif. Une troisième place existe, l'élément par passage (« palier 3 en 28 min, genou un peu raide ») : la plus riche, au prix d'une deuxième liste de champs et d'une saisie à chaque élément atteint.

## Les scènes

De la forme la plus simple à la plus complexe :

1. Sans ordre : des badges de lecture, obtenus comme ils viennent.
2. Ligne stricte : un calendrier de l'Avent, une case par jour.
3. Ligne avec retours : une rééducation du genou, une rechute fait redescendre de deux niveaux.
4. Boucle : un entraînement haut du corps, bas du corps, repos, qui repart sans fin.
5. Hiérarchie : préparer un marathon, avec des blocs, des semaines, des séances.
6. N parmi M : une licence, 6 modules sur 10 pour passer à l'année suivante.
7. Convergence : un prêt immobilier, salaires, assurance et apport réunis avant de signer.
8. Arbre de prérequis : la guitare, le barré ouvre certains morceaux, le rythme d'autres.
9. Aiguillage : une reconversion, le bilan envoie vers une formation ou vers une création d'entreprise.
10. Niveaux par élément : un répertoire de 20 morceaux, chacun déchiffré, puis su, puis joué en public.
11. États qui expirent : vaccins et révision de la voiture, à jour puis à refaire.
12. Tout mêlé : construire une maison, avec phases, tâches parallèles, dépendances et retards.

## Les variables

Liste à valider, pour vérifier que les scènes couvrent assez de combinaisons :

1. Forme : linéaire, parallèle, mixte.
2. Le temps : sans effet, fait passer sans valider, fait avancer.
3. Autre chose que le temps (une donnée, un geste, une IA, un événement) : sans effet, fait passer sans valider, fait avancer.
4. Désactiver un élément passé : impossible, sans effet sur les suivants, avec effet sur eux.
5. Accès : tout ouvert, après le précédent, N parmi M, tous les prérequis, un seul chemin choisi.
6. Issue d'un élément : fait ou non, réussi ou raté, plusieurs niveaux.
7. Après un raté ou un oubli : ça bloque, on continue, on recule.
8. Durée de l'acquis : définitif, expire, s'entretient.
9. Répétition : une fois, N fois avant de passer, sans limite.
10. Fin : une fin, une boucle, ouvert.
11. Profondeur : plat ou imbriqué.
12. Stabilité du plan : fixe, modifié en route, écrit au fur et à mesure.
13. Refaire : une seule fois, ou plusieurs passages.

## Une esquisse, comme hypothèse

Les variables semblent se ranger en trois questions :

- **La carte** : qu'est-ce qui ouvre quoi ? (forme, accès, fin, profondeur)
- **Les états** : où en est chaque élément ? (issue, durée de l'acquis, répétition)
- **Les règles** : qu'est-ce qui change un état ? (temps, autre source, raté ou oubli)

Si elle tient, ligne, arbre, boucle et aiguillage sont une seule chose, des liens entre éléments, et « ouvert » se déduit de la carte et des états sans être écrit. Le risque est d'obtenir un moteur de workflow, où il faut programmer sa progression.

## Ouvert

- Qu'est-ce qu'un élément ? Une capacité, une période et une action ont peut-être en commun d'être « une chose dont on peut dire qu'elle est atteinte ». Et son nom.
- La découpe carte, états, règles est-elle juste ?
- Quelles scènes l'outil doit-il vraiment couvrir, et où s'arrête-t-il ?
- Que fait une condition devenue vraie : atteindre l'élément, ou le proposer ? possibilité : que ce soit un réglage
- Une condition déjà vraie à l'ouverture d'un élément l'atteint-elle aussitôt ? -> réglage ? est ce la distinction "devenir vrai" vs "être vrai" ?
- Plusieurs conditions sur un élément : toutes, ou N parmi M ?
- « En cours » : un état qu'on écrit, ou simplement ce qui est ouvert et pas encore atteint ?
- L'état global : toujours déduit des éléments ?
- Toucher un élément : son texte et un lien vers un geste (lancer sa séance) ; à améliorer.
- Les affichages : plusieurs rendus très différents, comme le Graphique (chemin, achievements, tableau de voyants qui s'éteignent).
- Les notifications : quand est-on prévenu ?
- La gamification : quels aspects la permettent ?
- L'Objectif : le réduire à la mesure et au jugement, la progression consignant dans le temps ?
- Que peut faire l'IA sur une progression ?

## Faits relevés dans le code

- Une Condition est une seule comparaison, sans ET ni OU. L'Objectif assemble plusieurs conditions par ses critères et son « au moins N ». -> si nécessaire, on peut envisager d'améliorer le core.
- Seules les Tâches et les routines écoutent une condition (`ConditionTriggers`). Elles sont lancées quand elle passe de fausse à vraie ; un premier jugement ne lance rien.
- Une lecture n'a qu'une référence, partagée par les deux côtés de la condition : ses dates relatives et ses variables se lisent à cet instant.
- Dupliquer un outil copie sa config, pas ses entrées (`ToolInstanceService.handleDuplicate`).
- Une config peut garder des valeurs remplies de champs supplémentaires : c'est le cas des raccourcis du Suivi (`SettingNode.FieldValues`). Des éléments déclarés en config pourraient donc porter leur texte et leurs champs.
- Une Référence vise une entrée, pas un morceau de config.

## Reprise

Reprendre à « En discussion » : que sont les `tool_data`, puis où vont les champs supplémentaires. La liste des variables reste aussi à valider (proposition en attente : « qui a la main sur le passage » rangé dans la variable 3), puis à croiser avec les scènes.
