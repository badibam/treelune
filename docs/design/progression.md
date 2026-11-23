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

## Le nom

L'outil s'appelle Parcours (Path en anglais). « Progression » reste le nom de ce document de conception.

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

## Créer un parcours

- À la création, deux choix : la forme et le rythme. Une forme (cases à cocher, suite, calendrier, à renouveler, niveaux, prérequis, N parmi M, boucle) est un formulaire court qui écrit les sortes et les briques une fois ; tout reste éditable ensuite. Le rythme dit quand un passage commence et quand il se termine, pour n'importe quelle forme. « Objectif » n'est pas une forme : c'est N parmi M avec un rythme.
- Une sorte a deux listes de champs : `fields`, ce qu'est la brique (sa vidéo, sa consigne), et `move_fields`, ce qu'on note quand son état change (« 28 min, genou raide », « sommeil : 7 h »). Le bilan d'un passage est fait des `move_fields` de la racine.

## La fin d'un passage

- Un passage est fermé quand le journal contient sa ligne `end`, qui fige le plan et les états. Elle vient du geste « Arrêter » ou de la racine entrant dans un état marqué `closes_run`. `run_end` n'est qu'une date : quand elle passe, l'état de la racine est recalculé à cette date, et c'est lui qui décide.
- `closing`, un réglage de la config, dit qui termine le passage et déplie les états de la racine, alors affichés mais non éditables. « Écrire les règles moi-même » le retire et rend ces états modifiables.
- Il n'y a ni verdict ni validation dans l'outil : les issues sont des états aux noms libres, choisis en config (« Terminé » par défaut), avec une couleur facultative. Attendre une validation, c'est être dans un état qui ne ferme rien.

```json
"closing": { "by": "nobody" }
"closing": { "by": "person", "outcomes": ["validée", "redoublée"], "expire_after": "P7D" }
"closing": { "by": "rule", "outcomes": [{ "key": "terminé", "when": { "…": "tout est fait" } }] }
```

- Avec une date de fin, la première issue vraie l'emporte et la dernière peut servir de « sinon » ; sans date, chaque issue a son `when`.
- Le système ne connaît en dur que les clés d'un état (`key`, `manual`, `when`, `note_if`, `closes_run`), les mouvements `start` et `end`, et les références `run_start`, `run_end`, `opened_at`, `since`. Prévenir se règle en général : quand une brique entre dans tel état.

## La fusion avec l'Objectif (souhaitée, en exploration)

L'Objectif s'écrit presque entièrement en briques : critère = brique à règle, sous-objectif = groupe, « au moins N » = quantificateur, tentative = passage. Ce que la fusion demande :

- Des passages planifiés (`run.schedule`, une durée), le planificateur écrivant départs et fins.
- Plusieurs passages ouverts à la fois : un seul courant, les autres en attente de validation. Un état peut fermer son passage (réussi, raté, expiré).
- Des valeurs saisies sur un mouvement (`"value": 7`), lues par une règle (`self.last_value`).
- Une vue des passages : une ligne par passage et son verdict.
- Un passage ouvert se calcule, un passage fermé est figé : sa fin copie le plan **et les états**, sinon corriger une donnée externe changerait un verdict clos.
- Pas de reprise des données : les Objectifs existants ne servent qu'à l'utilisateur, qui les retire. Une migration retire le type, pour qu'une installation qui en a un ne casse pas.

Condition : « Objectif » reste un vrai modèle, avec un écran aussi simple qu'aujourd'hui. Ordre : coder la progression, puis ce modèle, et ne retirer l'ancien outil qu'une fois le nouveau au moins aussi agréable. À repenser : une tentative n'étant plus une entrée, sa Référence et ses champs personnels.

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
- Proposer n'est pas un mécanisme : c'est un état comme un autre (« à valider » si la règle est vraie), suivi d'un geste qui écrit « validé ».
- Un état qu'on peut noter le dit par `"manual": true` : il est actif quand le dernier mouvement de la brique porte son nom, sans `when`. `when` ne sert qu'à ce qui se calcule, et les deux se combinent (une date OU un déblocage à la main). Seul un état manuel peut avoir un `note_if`, qui vaut pour tout auteur (geste, IA, Tâche).
- Un état écrit peut dire « se note si » : une condition (l'état est disponible, le rythme, l'auteur). Sans elle, il se note toujours ; l'interdiction est donc un choix de chaque progression.

- Une brique peut avoir un champ « lien vers des briques », dont le sens vient de son nom et des règles qui le lisent (prérequis, exclut, couvert aussi par). Le système sait seulement suivre ce lien, calculer dans le bon ordre et refuser les boucles.
- Les règles sont des Conditions du core, étendues plutôt qu'un langage propre à la progression : le ET et le OU, et une valeur `self`, lue sur la brique jugée.

```json
{ "left": { "field": "data.brick" }, "op": "in", "right": { "self": "prerequisites" } }
```

- La Condition du core gagne aussi un quantificateur, à côté du ET et du OU : un ensemble, une condition sur chacun, une quantité (`"all"`, `"any"`, `"none"` ou un nombre pour « au moins N »).

```json
{
  "of": { "self": "prerequisites" },
  "where": { "left": { "field": "data.status" }, "op": "in", "right": { "constant": ["held"] } },
  "count": "all"
}
```

- Une sorte peut porter des `effects`, « quand ceci arrive, ces briques passent à tel état » : `on` est une Condition qui ne lit que l'état des briques, `set` un mouvement virtuel par brique visée, jamais écrit. L'état se calcule en rejouant le journal : chaque mouvement réel, puis les effets dont le `on` vient de devenir vrai. Supprimer un mouvement supprime donc ce qu'il avait déclenché, et le rejeu sait dire d'où vient un état. La boucle, refaire seulement ce qui est raté et la rechute en chaîne sont trois `set`.

```json
{
  "on": {
    "of": { "self": "content" },
    "where": { "left": { "field": "data.status" }, "op": "in", "right": { "constant": ["done"] } },
    "count": "all"
  },
  "set": { "of": { "self": "content" }, "state": "todo" }
}
```

- `set` n'est pas une Tâche : il ne touche que les briques de sa progression et n'écrit rien. Une date ou une donnée externe n'entre que par « quand la règle devient vraie », qui écrit un vrai mouvement. Pour agir ailleurs, la progression émet un événement qu'une Tâche écoute. Un plafond doit empêcher un effet d'en relancer un autre sans fin, et le coût du rejeu reste à mesurer.

- Le choix exclusif (scène 9) tient : des options dans un groupe, chacune disponible si aucune de ses voisines (`self.siblings`) n'est commencée. Deux briques qui dépendent l'une de l'autre lisent `last_move`, un fait du journal, plutôt que `status`, qui formerait une boucle ; la vue expose cette colonne, et l'enregistrement refuse une règle circulaire.

- Une sorte peut porter des `marks` : des indicateurs calculés qui s'ajoutent à l'état sans entrer en concurrence avec lui. Un lot de la maison (scène 12) peut être fermé et en retard.
- « Tous » sur un ensemble vide est vrai : juste pour une brique sans prérequis, faux pour un lot sans tâches, dont la règle ajoute « au moins une existe ».

- Une date relative est un Instant du core (`{"relative": {"unit": "DAY", "offset": 15, "edge": "START"}}`), résolu contre une référence nommée : `run_start` (le départ du passage), `opened_at` (l'ouverture de la brique), `since` (le début de son état actuel). Sans référence nommée, c'est le moment de la lecture. L'outil ne suppose rien de ce qu'une date représente.
- Décaler un plan (une blessure d'une semaine) n'est pas une fonction : un passage en cours suit le plan, qu'on modifie à la main ou par l'IA.

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
- Quelles scènes l'outil doit-il vraiment couvrir, et où s'arrête-t-il ?
- Plusieurs conditions sur un élément : toutes, ou N parmi M ?
- « En cours » : un état qu'on écrit, ou simplement ce qui est ouvert et pas encore atteint ?
- Un recul en chaîne (une rechute qui fait redescendre de deux niveaux) : un geste groupé, ou un `set` sur ce qui dépend de la brique.
- Les champs supplémentaires d'un élément dans le plan (sa consigne, sa séance).
- Retirer un élément du plan : son identifiant ne doit pas être une option d'un champ Choix obligatoire, dont le retrait supprimerait ses mouvements (`EntryMigration`).
- Toucher un élément : son texte et un lien vers un geste (lancer sa séance) ; à améliorer.
- Les affichages : plusieurs rendus très différents, comme le Graphique (chemin, achievements, tableau de voyants qui s'éteignent).
- Les notifications : quand est-on prévenu ?
- La gamification : quels aspects la permettent ?
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

Écrire les configs en vraie syntaxe, plus claire que le français pour cette conception. Le passage reste, à la racine seulement : il délimite un parcours, fige son plan et porte son bilan, là où `set` ne change que des états. Reprendre à la complexité de l'ensemble, jugée lourde et non tranchée, et à ce qu'une première version garderait. Piste en attente : un écran de config qui montre d'abord des cases à cocher et trois réglages (`opens_when`, `done_when`, `expires_after`), traduits en règles d'état. Les scènes résistantes sont testées sur le papier en vraie syntaxe. Reprendre aux exemples réels de l'utilisateur, le konnakol d'abord (une brique = une vidéo, 5 séances après son ouverture pour passer à la suivante), puis l'écran de config, avec une maquette à manipuler plutôt que du JSON. La liste des variables reste aussi à valider (proposition en attente : « qui a la main sur le passage » rangé dans la variable 3), puis à croiser avec les scènes.
