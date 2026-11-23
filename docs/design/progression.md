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

## Le montage retenu

Validé dans son principe le 2026-11-23. Rien n'est codé ni mesuré.

- **Le journal** : le plan est en config, une entrée = un mouvement (départ, atteint, perdu, fin). Les champs supplémentaires d'un mouvement portent « palier 3 en 28 min, genou un peu raide » ; ceux de la fin, le bilan du passage.
- **La vue** : l'outil présente à la lecture des lignes calculées, jamais stockées, une par brique et par passage : brique, sorte, parent, passage, courant, état, depuis (la date du mouvement qui a donné l'état). Lue à une autre référence, elle ne rejoue que les mouvements d'avant cet instant.
- **Qui répond à quoi** : la vue répond à « où en suis-je ? », le journal à « que s'est-il passé, et quand ? ». Une ligne de vue n'a qu'une date : « atteints en avril » posé à la vue manquerait un palier atteint en avril, perdu, puis réatteint en mai. Une sélection sur une vue refuse donc `period`, et un filtre sur `depuis` reste possible.
- **Défaire** : une erreur se corrige en supprimant le mouvement ; un vrai recul écrit un mouvement « perdu ».
- **Le passage** : en cours, il suit le plan actuel, modifiable librement ; sa fin garde une copie du plan, et il ne change plus. Recommencer = une fin si le passage est ouvert, puis un départ. Rouvrir = supprimer le mouvement de fin. Pas d'état « en pause » : un passage ouvert sans activité en est déjà une.
- **Limite acceptée** : une date passée du passage en cours se relit avec le plan actuel.

Ce que ça demande au core :

- Une seule notion de lignes calculées, passant par la même porte que les entrées, dont la série d'une variable deviendrait un cas particulier, plutôt qu'un cas `view` de plus dans chaque lecteur. La sélection y gagne un paramètre, `view`.
- Une Référence vers une ligne de vue, à inventer si un besoin de désigner un élément apparaît.
- Le coût à mesurer : un Graphique recalculerait la vue à chaque point de temps.
- Second usage pressenti : une vue `cards` des Flashcards (acquises, en retard, nouvelles), lisible par toute l'app.

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

## Les briques (retenu a priori)

- Une brique est une chose qui a un état, qui change dans le temps. « Palier », « atteint », « perdu » ou « ouvert » ne sont pas des notions de l'outil.
- Une progression déclare des sortes de briques (Module, Année), chacune avec ses champs (un jour, une validité, des prérequis), ce qu'elle peut contenir (une ou plusieurs sortes, facultatif), ses états et leurs règles. Un cas simple n'a qu'une sorte.
- Pas de frontière atome/groupe : toute brique peut être notée et contenir, si sa sorte le permet.
- L'état d'une brique est le premier de sa liste dont la règle est vraie. Une règle lit le dernier mouvement, la date, ses champs ou son contenu, donc un état n'est ni écrit ni déduit par nature : « disponible si son jour est arrivé OU si son dernier mouvement est « ouvert » ».
- Une règle de groupe nomme ce qu'elle compte : « au moins 2 parmi [Sorties, Événements] sont [Participée, Passé] », une lecture de la vue filtrée sur le parent. Une sorte ajoutée ne compte qu'une fois nommée dans la règle ; pas de case « compte comme acquis ».
- Un état a deux écritures : « si la règle est vraie », il suit la réalité et peut retomber ; « quand la règle devient vraie », il s'inscrit une fois par un mouvement et reste. Une règle déjà vraie au premier calcul compte : la différence entre « 10 livres dans ma vie » et « 10 livres de plus à ce palier » est dans la période de la règle. Un vrai franchissement (« passer sous 80 kg » en pesant 78) s'écrit dans la règle : a été au-dessus depuis l'ouverture, et est en dessous.
- Un état écrit peut dire « se note si » : une condition (l'état est disponible, le rythme, l'auteur). Sans elle, il se note toujours ; l'interdiction est donc un choix de chaque progression.

Exemple :

```text
CALENDRIER DE L'AVENT
  chaque brique a : un jour
  états :
    faite        si son dernier mouvement est « faite » ; se note si elle est disponible
    disponible   si son jour est arrivé
    fermée       sinon
```

Risques : écrire ces règles revient un peu à programmer, d'où des modèles tout prêts à prévoir (« suite », « à renouveler », « prérequis ») ; la vue doit calculer du bas vers le haut, et une règle ne doit pas pouvoir dépendre d'elle-même.

## Ouvert

- Le nom de la brique.
- Le choix exclusif (la reconversion, scène 9) : couvert ou hors de l'outil.
- Quelles scènes l'outil doit-il vraiment couvrir, et où s'arrête-t-il ?
- Une règle devenue vraie : inscrire l'état, ou le proposer ? Piste : la proposition est un état comme un autre (« à valider »), suivi d'un geste.
- Plusieurs conditions sur un élément : toutes, ou N parmi M ?
- « En cours » : un état qu'on écrit, ou simplement ce qui est ouvert et pas encore atteint ?
- Un recul en chaîne (une rechute qui fait redescendre de deux niveaux) : un geste qui écrit plusieurs mouvements, ou une règle.
- Les champs supplémentaires d'un élément dans le plan (sa consigne, sa séance).
- Retirer un élément du plan : son identifiant ne doit pas être une option d'un champ Choix obligatoire, dont le retrait supprimerait ses mouvements (`EntryMigration`).
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
- Une Lecture ou un filtre vise un champ à un seul niveau (`data.x`), et aucun type de champ ne contient une liste d'objets.
- Les Flashcards sont un journal : une entrée par révision, l'état de la carte après elle dans `state`, l'état actuel de chaque carte relu par leur propre SQL (`FlashcardsJournal`).
- La série d'une variable est faite de lignes calculées, filtrées par le même SQL que les entrées (`VariableSeries`) ; seule une Lecture sait la lire.

## Reprise

Reprendre à la proposition : une règle devenue vraie qui attend une validation. Puis repasser les scènes sur les briques. La liste des variables reste aussi à valider (proposition en attente : « qui a la main sur le passage » rangé dans la variable 3), puis à croiser avec les scènes.
