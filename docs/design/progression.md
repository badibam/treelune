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

## Penchants, pas encore décidés

- Chaque élément serait une entrée de l'outil, comme un item de Liste : ses champs, ses Références et ses déclencheurs viendraient d'emblée.
- Refaire un plan serait d'abord une simple remise à zéro, les passages conservés pouvant s'ajouter plus tard si le besoin se confirme. -> ce point reste à discuter, car ça détruit des données précieuses...

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

- Que sont les `tool_data` : un élément, un mouvement (allumé, éteint) ou un passage complet ? La réponse décide aussi des champs supplémentaires, de la trace et de la réutilisabilité.
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

Valider la liste des variables, puis vérifier que les scènes en couvrent assez de combinaisons. Ensuite seulement, définir ce qu'est un élément.
