# Progression — conception

Conception en cours, rien de codé. Seule la section « Décidé » engage ; tout le reste se discute. « Élément » est un mot provisoire pour la chose qu'on suit : la nommer fait partie de ce qui est ouvert.

## Le but

Tenir où on en est dans quelque chose qui se déroule par morceaux. Comme tout outil de Treelune, il doit être :

- le plus général possible ;
- capable de couvrir les usages variés courants ;
- propre et simple à concevoir.

## La méthode

- Partir de scènes de vie de formes différentes, chercher ce qu'elles ont en commun, et seulement ensuite définir la notion.
- Un point à la fois, en quelques mots.
- Chaque question donne une situation concrète et sa logique, ce qui est en jeu, de vrais embranchements, puis un avis présenté comme un avis.
- Ne rien traiter comme acquis tant que l'utilisateur ne l'a pas décidé, et ne pas reconduire une piste parce qu'elle a déjà été écrite.

## Décidé

- Ce qui rend un élément atteint s'écrit avec la brique Condition, pas avec une Tâche. Un cas complexe passe par ce que la Condition sait déjà lire : une variable, une lecture, peut-être le résultat d'un Objectif.
- Les périodes de ces conditions gardent tous les choix de la lecture, avec une borne de plus : « l'ouverture de l'élément ». Elle permet d'écrire « 10 sorties à ce palier ». Une période vide veut toujours dire sans limite, et la référence reste le moment de la lecture.

## Penchants, pas encore décidés

- Chaque élément serait une entrée de l'outil, comme un item de Liste : ses champs, ses Références et ses déclencheurs viendraient d'emblée.
- Refaire un plan serait d'abord une simple remise à zéro, les passages conservés pouvant s'ajouter plus tard si le besoin se confirme.

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

- Est-ce bien un nouveau type d'outil, ou une Liste enrichie suffit-elle ?
- Qu'est-ce qu'un élément ? Une capacité, une période et une action ont peut-être en commun d'être « une chose dont on peut dire qu'elle est atteinte ». Et son nom.
- La découpe carte, états, règles est-elle juste ?
- Quelles scènes l'outil doit-il vraiment couvrir, et où s'arrête-t-il ?
- Que fait une condition devenue vraie : atteindre l'élément, ou le proposer ?
- Une condition déjà vraie à l'ouverture d'un élément l'atteint-elle aussitôt ?
- Plusieurs conditions sur un élément : toutes, ou N parmi M ?
- Quelle frontière avec l'Objectif, et faut-il le réduire ?
- Quels affichages, quelles notifications, quelle gamification ?
- Que peut faire l'IA sur une progression ?

## Faits relevés dans le code

- Une Condition est une seule comparaison, sans ET ni OU. L'Objectif assemble plusieurs conditions par ses critères et son « au moins N ».
- Seules les Tâches et les routines écoutent une condition (`ConditionTriggers`). Elles sont lancées quand elle passe de fausse à vraie ; un premier jugement ne lance rien.
- Une lecture n'a qu'une référence, partagée par les deux côtés de la condition : ses dates relatives et ses variables se lisent à cet instant.
- Dupliquer un outil copie sa config, pas ses entrées (`ToolInstanceService.handleDuplicate`).
- Décocher un item de Liste efface sa date (`checked_at`).

## Reprise

Valider la liste des variables, puis vérifier que les scènes en couvrent assez de combinaisons. Ensuite seulement, définir ce qu'est un élément.

## Notes de l'utilisateur

Plein de choses sont à revoir, voici en vrac quelques réflexions pour alimenter le brainstorming

- une possibilité: achievements ; une autre, similaire, mais avec les éléments qui "s'éteignent" automatiquement ou sur geste = sorte de dashboard avec état des différents éléements... (pas l'usage principal, mais pourrait faire partie des possibilités, suivant config)
- question ouverte : quand est on notifié ?
- + généralement, quels aspects permettent la gamification ?
- un élément peut il être 'en cours' = "le prochain, ou un des prochains à atteindre"
- probablement, ça pourrait demander plusieurs modes d'affichage, un peu comme les graphiques ont des rendus très différents.
- quand on lcique sur un élément on voit son texte, et aussi un lien vers un geste, par exemple vers une séance spécifique qui est en lien avec son accomplissement ? c'est un premier jet d'idée, je pressens qu'on peut faire mieux
- que font les champs supplménetaires ? il doivent porter sur les éléments je suppose... 
- mais que sont les tool_data d'ailleurs ? il y a plusieurs possibilités je pense... ce serait analgue aux flashcards par exemple ? ou un historique des pas effectués 1 entrée = 1 pas ?
- y a t il des états ? une trace des moments allumés éteints dasn chaque élément ?
- un état global ? toujours dérivé du reste ou pas ?
- réutilisabilité ? par exemple programme annuel ... dasn ce cas : 1 tool_data = 1 exécution , et ça édite celle ci au fur et à mesure des pas effectués ? -> si on fait ça j'ai l'impression que ça fait redite avec objectifs
- de façon générale , l'intersection n'est pas négligeable avec objectifs. peut etre faudra t il réduire objectifs et prévoir plutôt des montages de combinaison ? par exemple objectif = mesure/jugement de quelque chose et progression jouerait le rôle de consignation dasn le temps, par exemple ?
