# Parcours — conception

Conception en cours, rien de codé ni mesuré. Ce qui est marqué « a priori » est retenu sans être définitivement tranché ; le reste a été validé en séance (2026-11-23 et 24). Les exemples sont en vraie syntaxe, plus claire que le français pour cette conception.

## Le but

Tenir où on en est dans quelque chose qui se déroule par morceaux, aussi général que possible, propre et simple conceptuellement. L'outil s'appelle Parcours (Path en anglais) ; « progression » reste le nom de ce document.

## La méthode

- Partir des cas réels de l'utilisateur (le cycle de dessin, le konnakol), les écrire entièrement, et ne garder dans l'outil que ce qu'au moins l'un d'eux utilise. Les scènes imaginées servent de test, pas de cahier des charges.
- Un point à la fois, concis ; une situation concrète, de vrais embranchements, puis un avis présenté comme un avis.
- Ne rien traiter comme acquis tant que l'utilisateur ne l'a pas décidé, et ne pas reconduire une piste parce qu'elle a déjà été écrite.

## Les entrées et le journal

- Une entrée = une brique (a priori), propre à son passage (`run`), avec son parent, sa position et son historique de mouvements dans `state.moves`. La config ne tient que la grammaire : les sortes, leurs états, leurs règles.
- Pourquoi : le vrai cycle de dessin compte 60 exercices et 48 901 caractères pour juillet seul, trop pour une config que l'IA réécrirait à chaque exercice changé ; une brique doit pouvoir être visée par une Référence. Écartés : deux formes d'entrées dans le core (un seul outil s'en servirait), le plan dans une Fiche à part (trop fragile), le plan en config (sa taille).
- Un mouvement est fixe, `{"at", "state", "note"}`, sans champ configurable. Ce qu'on note une fois par passage est un champ de la brique ; le détail d'une pratique vit dans l'outil qui la fait (une Séance, un Suivi), qu'une règle peut lire.
- Rien de déduit n'est stocké : l'état de chaque brique se calcule en rejouant ses mouvements. Corriger le journal corrige tout.
- Défaire : une erreur se corrige en supprimant le mouvement ; un vrai recul écrit un mouvement (« perdu »).

## Les vues

- Deux vues calculées, jamais stockées, rendent le Parcours lisible par les Lectures, les Graphiques et les Conditions : `bricks`, une ligne par brique avec son état et `since` (la date du mouvement qui a donné l'état) ; `moves`, une ligne par mouvement.
- `bricks` répond à « où en suis-je ? », `moves` à « que s'est-il passé, et quand ? ». Une ligne de `bricks` n'a qu'une date : « atteints en avril » posé à elle manquerait une brique atteinte en avril, perdue, puis réatteinte en mai. Une sélection sur `bricks` refuse donc `period` ; un filtre sur `since` reste possible.
- Lue à une autre référence, une vue ne rejoue que les mouvements d'avant cet instant. Limite acceptée : une date passée du passage en cours se relit avec ses briques actuelles.

Ce que ça demande au core :

- Une seule notion de lignes calculées, passant par la même porte que les entrées, dont la série d'une variable deviendrait un cas particulier. La sélection gagne un paramètre, `view`. Usages : Parcours (`bricks`, `moves`), Flashcards (`cards` : acquises, en retard, nouvelles), Séance (`playable`).
- Une Référence vers une ligne de vue, qui peut comme pour une entrée descendre jusqu'à un champ (`field`). Elle demande des identifiants de ligne stables (celui d'une entrée, d'un programme), jamais une position.
- Le coût à mesurer : un Graphique recalculerait la vue à chaque point de temps.

## Les briques

- Une brique est une chose qui a un état, qui change dans le temps. « Palier », « atteint », « ouvert », « verdict » ou « validation » ne sont pas des notions de l'outil.
- Un Parcours déclare des sortes (Module, Année), chacune avec ses champs, ce qu'elle peut contenir (une ou plusieurs sortes, facultatif), ses états, ses `marks` et ses `effects`. Pas de frontière atome/groupe : toute brique peut être notée et contenir, si sa sorte le permet. La racine a sa sorte, dont les champs décrivent le cycle (son cap, ses besoins).
- Les champs configurables n'existent que par sorte : Parcours n'a pas les champs supplémentaires classiques (`takesUserFields() = false`, comme les Flashcards).
- Une brique peut avoir un champ « lien vers des briques », dont le sens vient de son nom et des règles qui le lisent (attend, exclut, couvert aussi par). Le système sait seulement le suivre, calculer dans le bon ordre et refuser les boucles.

### Ce qu'attend une brique

- Liste, suite et arbre ne sont pas trois formes mais une seule mécanique (une brique est disponible quand ce qu'elle attend est fait) dont seule la source change. Une sorte de groupe porte donc un réglage unique, `waits`, pour les briques de son contenu : `nothing` (toujours disponibles), `previous` (la brique d'avant, par sa position dans le plan, sans rien écrire), `named` (ce que nomme un champ de lien, écrit à la main). Il épargne du travail (60 séances sans écrire un lien) ; c'est sa seule justification.
- Comme `closing`, `waits` reste un réglage actif : il écrit et possède les états des sortes de son contenu (fait, disponible, fermé), affichés mais non éditables, et fixe l'affichage par défaut ; « Écrire les règles moi-même » le détache.
- Chaque niveau a le sien et ils s'emboîtent (le cycle attend le précédent, un mois rien). Des sortes différentes se mêlent au même niveau, et le réglage s'applique à toutes. Une brique qui n'attend rien dans un groupe `named` est ouverte comme dans une liste : pas besoin de groupe à part.
- Pas possible : un même niveau à moitié `previous`, à moitié `named` (écrire les liens à la main, ou faire un groupe) ; une brique dans deux groupes (un seul parent) ; des liens en boucle (refusé) ; une sorte gouvernée par deux réglages différents (refusé ; deux groupes du même réglage, oui).
- Piste, seulement si un cas réel la demande : `waits` comme valeur par défaut que chaque brique peut changer, ce qui rendrait possible le niveau mixte.

```json
{ "key": "cycle", "contains": ["month"], "waits": "previous" }
```

### Les états

- L'état d'une brique est le premier de sa liste dont la règle est vraie ; le dernier, sans règle, sert de « sinon ».
- `"manual": true` : l'état se note, et il est actif quand le dernier mouvement de la brique porte son nom. `when` ne sert qu'à ce qui se calcule ; les deux se combinent (une date OU un déblocage à la main).
- `note_if`, sur un état manuel seulement : quand on a le droit de le noter, pour tout auteur (geste, IA, Tâche). Sans lui, il se note toujours ; interdire est donc un choix de chaque Parcours.
- Deux écritures d'un état calculé : « si la règle est vraie », il suit la réalité et peut retomber ; « quand la règle devient vraie » (`record`), il s'inscrit une fois par un vrai mouvement et reste. Une règle déjà vraie au premier calcul compte : « 10 livres dans ma vie » et « 10 livres de plus à ce palier » diffèrent par la période de la règle. Un vrai franchissement s'écrit dans la règle (a été au-dessus depuis l'ouverture, et est en dessous).
- Proposer est un état comme un autre (« à valider » si la règle est vraie), suivi d'un geste.
- Les noms d'états sont libres, et ne doivent pas se confondre avec les mouvements `start` et `end`.

```json
"states": [
  { "key": "done", "manual": true,
    "note_if": { "left": { "self": "state" }, "op": "in", "right": { "constant": ["available"] } } },
  { "key": "available", "when": { "left": { "self": "day" }, "op": "<=", "right": { "constant": "today" } } },
  { "key": "locked" }
]
```

### Les règles

- Ce sont des Conditions du core, étendues plutôt qu'un langage propre au Parcours : le ET et le OU ; une valeur `self`, lue sur la brique jugée ; un quantificateur, un ensemble, une condition sur chacun, une quantité (`"all"`, `"any"`, `"none"` ou un nombre pour « au moins N »), qui peut aussi se lire sur la brique (`{"self": "waits_for_count"}`).
- Ce que `self` atteint : ses champs, `content`, `parent`, `siblings`, `previous`, `last_move`, `state`, et les références de date.
- « Tous » sur un ensemble vide est vrai : juste pour une brique sans prérequis, faux pour un groupe sans contenu, dont la règle ajoute « au moins un existe ».
- Une règle de groupe nomme ce qu'elle compte (« au moins 2 parmi [Sorties, Événements] sont [Participée, Passé] ») : pas de case « compte comme acquis », et une sorte ajoutée ne compte qu'une fois nommée.
- Deux briques qui dépendent l'une de l'autre lisent `last_move`, un fait, plutôt que `state`, qui formerait une boucle ; l'enregistrement refuse une règle circulaire.
- Une date relative est un Instant du core (`{"relative": {"unit": "DAY", "offset": 15, "edge": "START"}}`) résolu contre une référence nommée : `run_start`, `run_end`, `opened_at` (l'ouverture de la brique), `since`. Sans référence nommée, c'est le moment de la lecture. L'outil ne suppose rien de ce qu'une date représente ; décaler un plan n'est pas une fonction, on modifie les briques à la main ou par l'IA.

```json
{
  "of": { "self": "waits_for" },
  "where": { "left": { "field": "data.status" }, "op": "in", "right": { "constant": ["done"] } },
  "count": "all"
}
```

### Les `marks` et les `effects`

- `marks` : des indicateurs calculés qui s'ajoutent à l'état sans entrer en concurrence avec lui (un lot de maison fermé et en retard).
- `effects` : « quand ceci arrive, ces briques passent à tel état ». `on` est une Condition qui ne lit que l'état des briques ; `set` est un mouvement virtuel par brique visée, jamais écrit. Le rejeu applique chaque mouvement réel, puis les effets dont le `on` vient de devenir vrai : supprimer un mouvement supprime ce qu'il avait déclenché, et le rejeu sait dire d'où vient un état. Une boucle, refaire seulement ce qui est raté, une rechute en chaîne sont des `set`.
- `set` n'est pas une Tâche : il ne touche que les briques de son Parcours et n'écrit rien. Une date ou une donnée externe n'entre que par `record`. Pour agir ailleurs, le Parcours émet un événement qu'une Tâche écoute. Un plafond doit empêcher un effet d'en relancer un autre sans fin.

```json
{
  "on": { "of": { "self": "content" }, "where": { "left": { "field": "data.status" }, "op": "in", "right": { "constant": ["done"] } }, "count": "all" },
  "set": { "of": { "self": "content" }, "state": "todo" }
}
```

## Le passage

- Un passage délimite un parcours, sa racine en dit l'état. Recommencer = une fin si le passage est ouvert, puis un départ, avec ses briques dupliquées ou un plan neuf. Un plan est propre à son passage : un cycle de dessin a le sien, écrit phase après phase.
- Un passage est fermé quand le journal contient sa ligne `end` : ses briques deviennent non modifiables et leur état final s'écrit sur elles, sinon corriger une donnée externe changerait un résultat clos. Rouvrir = supprimer la ligne `end`. Pas d'état « en pause » : un passage ouvert sans activité en est déjà une.
- La ligne `end` vient du geste « Arrêter » ou de la racine entrant dans un état `closes_run`. `run_end` n'est qu'une date : quand elle passe, l'état de la racine est recalculé à cette date (même si le téléphone dormait), et c'est lui qui décide.
- Le rythme dit quand un passage commence (à la main, ou planifié) et quand il se termine. Plusieurs passages peuvent être ouverts à la fois, un seul étant courant.
- `closing` dit qui termine le passage et déplie les états de la racine, alors affichés mais non éditables ; « Écrire les règles moi-même » le retire. Les issues sont des états aux noms libres (« Terminé » par défaut), avec une couleur facultative. Avec une date de fin, la première issue vraie l'emporte et la dernière peut servir de « sinon » ; sans date, chaque issue a son `when`.

```json
"closing": { "by": "nobody" }
"closing": { "by": "person", "outcomes": ["validée", "redoublée"], "expire_after": "P7D" }
"closing": { "by": "rule", "outcomes": [{ "key": "terminé", "when": { "…": "tout est fait" } }] }
```

- Le système ne connaît en dur que les clés d'un état (`key`, `manual`, `when`, `note_if`, `record`, `closes_run`), les mouvements `start` et `end`, et les références de date. Prévenir se règle en général : quand une brique entre dans tel état.

## Les écrans

- Le plan est fait d'entrées, l'éditeur de plan ne touche jamais la config. Il s'ouvre par un bouton « Plan » de l'écran de l'outil, sur sa propre page : l'usage de l'écran reste « toucher pour noter ou lancer », et la règle de l'app tient (la config dans l'écran de config, les entrées depuis l'écran de l'outil). Un passage fermé montre son plan en lecture seule. Écarté : un bouton dans l'écran de config, qui aurait mêlé une config enregistrée d'un bloc et des briques écrites tout de suite.
- L'écran de config montre les sortes et la liste de leurs états, chaque ligne disant si l'état se note à la main, sa règle et son `note_if`. Une sorte unique reste invisible, et une sorte n'apparaît sur une brique qu'à partir de deux.
- À la création, le Parcours a une sorte, sans aucune brique ; le choix de `waits` pour son contenu remplace les « formes ».
- Un seul dessin, de haut en bas (le portrait) : le graphe des liens. Les briques sans lien se rangent côte à côte sur autant de colonnes que la place permet (l'ancienne grille) ; une chaîne est une colonne avec son fil (l'ancien chemin) ; un graphe se dispose par niveaux (les anciennes branches). Avec plusieurs champs de lien, l'affichage dit lequel il trace.
- Tout est déplié, groupes compris (dessinés en cadres) : la vue d'ensemble est l'écran normal. Écarté : un niveau à la fois avec un fil d'Ariane (la maquette), et un repli automatique du passé.
- La linéarisation garde le tout plaisant à lire : le graphe se coupe en sections empilées, chacune sur toute la largeur, et une flèche coupée devient une paire de renvois qui se répondent (« (A) »), un toucher défilant de l'un à l'autre, comme les connecteurs hors page des organigrammes. Un degré règle ce qui est coupé, selon le nombre de couches qu'une flèche saute (0 : rien ; jusqu'à une section par couche) : une valeur par défaut en config, un curseur sur l'écran le temps de la visite, sans rien enregistrer. À la main, un groupe peut être marqué « en section ». Faisable a priori, le placement du Schéma travaillant par couches (code du placement non lu). Le Schéma en profiterait aussi, lui qui replie des groupes, réduit le dessin puis défile.
- Sur une tuile, le dessin a des niveaux de détail, du plus riche au plus pauvre ; un niveau tient tant que le dessin, réduit à la tuile, garde au moins une part de sa taille, une borne par niveau, à fixer sur le téléphone : (1) le texte, les groupes repliés au besoin comme sur la page (resserrer, replier les plus larges ; la tuile ne replie jamais aujourd'hui, `fit = !reduced`) ; (2) l'icône seule, plus aucun texte, libellés des flèches compris, une boîte sans icône passant à sa forme ; (3) la forme seule ; (4) des pastilles ; puis, pour le Parcours, le compte (« 4 / 9 »), et pour le Schéma son résumé seul. Les flèches gardent leurs angles droits, plus fines aux derniers niveaux.
- Chaque niveau replace le dessin, ses boîtes à leur nouvelle taille, sans quoi l'icône flotterait dans une boîte à la taille de son texte et le dessin ne grandirait pas. La taille de la tuile dit d'avance le niveau probable : on part de lui, on ne descend qu'au besoin.
- Toute boîte du dessin partagé peut porter une icône devant son texte : la marque d'état d'une brique (`look`), une icône dans le Schéma.
- Sur une tuile seulement, le dessin passe à l'horizontale quand le corps de la tuile a plus de colonnes que de rangées (`TileSize.body`, `Room`) : 4×2, 4×3, 4×4, 2×3. Carré (4×5, 2×4), plus haut que large, ou une tuile qui suit son contenu, il reste vertical ; la page reste toujours verticale. Le sens se lit sur les cases, jamais en plaçant le dessin dans les deux sens pour garder le meilleur, ce qui doublerait le temps d'affichage. Le placement fait l'horizontale en retourné, comme le « de bas en haut » du Schéma : largeur et hauteur échangées, la hauteur de la tuile pour limite, x et y échangés au retour ; seul le titre d'un groupe demande une place à part, réservée à gauche dans le calcul pour revenir en haut (`GraphLayout`). Ce que le dessin suppose du haut et du bas (pointes, boucles, « suite ») reste à vérifier dans `SchemaScene`.
- Une boîte reprend les règles du Schéma : mesurée sur son texte, coupée avec « suite » qui ouvre le texte entier. Elle gagne une marque d'état devant et le repère derrière.
- Sous le coude, à décider plus tard : trois fils qui linéarisent le sens plutôt que le dessin, « à faire maintenant » (un filtre sur des états), « chemin vers » une brique (ce qu'elle attend, dans un ordre faisable), « ce que ça débloque » ; les deux derniers demandent que `self` atteigne ancêtres et descendants par un champ de lien.
- Ce dessin est celui de l'outil Schéma (boîtes, groupes, flèches, de haut en bas), dont la mise en page sort dans le core : le Schéma en est la traduction canonique en outil, le Parcours un autre utilisateur, ses couleurs venant des états. Un Parcours pourrait aussi être une source du Schéma, comme la carte de l'installation. Le placement est dans le core (`core/drawing/layout/GraphLayout.kt`, Kotlin sans Android), à côté du dessin (`core/drawing`). Mesuré dans la branche du Schéma : au plus 40 ms sur l'ordinateur pour 11 schémas de banc (jusqu'à une carte de 30 choses) ; 135 à 343 ms par état sur le téléphone en debug pour un schéma de 12 boîtes (formes, groupes, une boucle).
- Le coût du placement dépend surtout des flèches, pas du nombre de boîtes (déduit de la méthode en couches, non mesuré sur ce code) : des boîtes sans flèches rangées en lignes coûtent peu ; des flèches qui se croisent obligent à chercher l'ordre qui en croise le moins ; une flèche longue ajoute un point de passage par couche traversée ; les groupes imbriqués se placent un par un ; un dessin trop large est recalculé (resserré, groupes repliés, réduit). D'où, sans mesure : le cycle de dessin, plus de 100 boîtes mais presque aucune flèche, devrait coûter peu ; un arbre de compétences dense est le cas lourd ; la linéarisation allège le calcul, une flèche remplacée par deux renvois perdant ses points de passage. On code d'abord, et on optimise si c'est trop lent : le placement tourne déjà hors du fil de l'écran, et les leviers sont connus (garder le dessin tant que rien n'a changé, ne recalculer que le groupe touché). Une fois codé, regarder sur le téléphone, en release, le cycle de dessin entier et un arbre d'une cinquantaine de briques bien reliées.
- Imbriquée, la linéarisation suit le placement groupe par groupe : dans le parent, des cadres côte à côte passent l'un sous l'autre, comme des sections ; une flèche qui traversait d'un cadre à l'autre devient une paire de renvois ; et l'intérieur d'un cadre se découpe à son tour, indépendamment.
- Un parcours conduit par un maître (le projet `parcours`, que Treelune remplacera en grande partie) n'utilise ni règles ni verrous : l'IA écrit le plan phase après phase, et tout se note à la main. L'IA est donc la principale autrice des configs riches.

## L'apparence d'une brique

Ce que la maquette du 2026-11-24 écrivait en dur, et que le modèle doit dire, l'outil ne connaissant pas le sens des états :

- Chaque état dit comment il se montre (`look` : une icône Lucide, estompé ou non). Un état sans `look` se montre neutre, avec son numéro dans une chaîne. Ce n'est que de l'apparence : l'outil ne sait toujours pas qu'un état est une réussite.
- Chaque sorte peut porter un modèle de repère, calculé pour chaque brique avec `self` : la forme du repère du core (`core/readouts`), des valeurs lues à l'affichage, une forme courte qui les place, des couleurs selon des conditions. Il porte la progression (« {done} / {total} », « 3 / 5 séances » par une Lecture) et la couleur d'une brique. Nouveau : un repère lu une fois par brique, avec une brique jugée.

```json
{ "key": "week", "readout": {
    "values": [
      { "name": "done",  "term": { "of": { "self": "content" }, "where": { "…": "état in [faite]" }, "count": "number" } },
      { "name": "total", "term": { "of": { "self": "content" }, "count": "number" } } ],
    "core": "{done} / {total}",
    "colors": [ { "when": { "left": { "field": "done" }, "op": ">=", "right": { "field": "total" } }, "color": "green" } ] } }
```

- Le quantificateur peut rendre un nombre (`"count": "number"`) au lieu de vrai ou faux.
- La fiche d'une brique offre un bouton par état manuel que `note_if` autorise, avec son libellé, dans l'ordre de la liste. Parmi les états manuels, cet ordre est l'échelle : un bouton vers un état plus bas est un retour (« ↓ Revenir à Déchiffré », en retrait), avec un libellé facultatif (`back_label` : « Oublié (→ Déchiffré) »). Revenir écrit un vrai recul ; « Annuler « Su » » efface le dernier mouvement, sans date dans le bouton. La fiche montre l'historique des états de la brique pour le passage en cours.
- Restent de la maquette, à traiter : un groupe peut aussi être noté, donc avoir sa fiche (un bouton en tête du niveau, toucher un groupe y entrant) ; la sorte dit quel champ s'affiche en tête de son niveau ; « + » dans l'éditeur de plan propose les sortes que le groupe peut contenir.

## Lancer une Séance depuis une brique

- Le détail d'une pratique (chaque exercice fait ou sauté, son temps) vit dans la Séance, qui le garde déjà. Une brique s'arrête à « Séance 3 » ; ses exercices ne sont pas des briques.
- La Séance expose une vue `playable`, une ligne par chose qu'elle sait lancer, quel que soit son mode : plan (une ligne), média (les entrées de sa source), programmes (un programme par ligne). « Que jouer ? » en devient l'affichage. Une brique garde une Référence vers une de ces lignes.
- Un mode « programmes » de la Séance : plusieurs programmes en config, chacun avec ses étapes, écrits avec l'éditeur d'étapes existant. La brique ne garde qu'une Référence ; une séance jouée garde sa copie des étapes, donc modifier un programme ne réécrit pas l'historique. Écartés : les étapes comme briques du Parcours (une Séance dépendrait d'un Parcours), une Fiche d'étapes (plan dispersé).
- Deux listes de champs dans la Séance : les champs des programmes, valeurs gardées en config comme les raccourcis du Suivi (`SettingNode.FieldValues`), colonnes de `playable` pour trier et filtrer ; les champs supplémentaires actuels, sur les séances jouées, dont seule la section est renommée « À remplir en fin de séance ».
- La description d'une séance s'écrit une fois, dans son programme ; un champ de la brique la suit, sans copie, comme une étape de Séance suit déjà le média d'une entrée (`ConfigTerms`). Les étapes se voient dans la fiche de lancement, que la brique ouvre. Pas de colonne résumé calculée, qui serait de la mise en forme déguisée en donnée.
- Une sorte dit quel champ son toucher ouvre (`opens`, un champ Référence vers une ligne de `playable`). Le Parcours n'affiche jamais lui-même un programme : chaque outil montre ce qui est à lui.
- Ouvrir un outil sur une de ses lignes est une capacité du core, pas du Parcours : `EntryToOpen` devient `OpenAt` (rien, du neuf, une entrée, une ligne de vue), renommé dans le même geste (89 usages, 28 fichiers). Chaque outil décide ce qu'ouvrir une ligne veut dire : pour la Séance, la fiche du programme avant « Démarrer ». Une Tâche, un pointeur ou une notification pourront s'en servir. Le chemin existe (vérifié) : `ToolRequests.open` (le Graphique s'en sert pour ouvrir l'entrée d'un point) → `MainScreen` → `Navigator.openings`, gardé à côté de la place et non dans son adresse → `ToolPlace` le reprend → `getUsageScreen(…, openEntry)`. Il suffit d'y faire passer `OpenAt` et d'étendre sa sauvegarde ; la Séance reçoit déjà `openEntry` mais ne le transmet pas à son écran, c'est son seul ajout ici.
- Deux gestes : `sequence.start` avec la ligne démarre tout de suite (IA, Tâche) ; toucher la brique navigue vers la Séance avec la ligne à lancer, la fenêtre de lancement s'ouvrant directement sur sa fiche, puis « Démarrer » mène à la séance en cours. Lancée depuis son outil, la Séance garde sa liste. Une seule séance tourne à la fois : sinon, refus dit.
- Le retour se fait sans rien écouter : `start` garde `data.launched_for`, la brique, et l'état « faite » est une règle qui lit la Séance (« une séance terminée lancée pour moi » ; le konnakol en compte 5 après l'ouverture de la brique).
- Ce qui change dans la Séance : la fenêtre de lancement qui part d'un élément choisi ; l'écran qui reçoit une ligne à lancer ; `launched_for` ; le mode programmes ; les champs des programmes ; la vue `playable`.

## Les cas réels

- **Le cycle de dessin** (projet `parcours`, base lue le 2026-11-23) : Été 2026 › Juillet › Semaine 1 › Séance 3, chaque séance étant un programme de Séance (« à regarder avant » en première étape, puis Emoji 8 min, La Bean 40 min, Plante 12 min). Les éléments hors séance (la lecture du mois, montrer le carnet le dimanche) sont des briques cochées à la main. Le cap, l'intention, la synthèse sont des champs des briques racine et mois. Aucune règle ni date : tout se note à la main.
- **Le konnakol** : une brique par niveau, liée à sa vidéo dans une Séance en mode média ; « passée » quand 5 séances terminées ont eu lieu strictement après son ouverture (`timestamp > self.opened_at`, une période ayant des bornes incluses), état `record`. Une révision d'un ancien niveau compterait pour le niveau courant : à filtrer par la vidéo jouée si ça gêne.
- **L'anatomie** (pour le dessin, puis une éventuelle reconversion vers le massage) : pas encore conçue ; elle travaille une même matière sur plusieurs plans (savoir, dessiner, plus tard palper), et « les cartes de cette région sont sues » demande la vue `cards` des Flashcards.

Les scènes de test, toutes tenues sur le papier : badges, calendrier de l'Avent, rééducation avec rechute, boucle d'entraînement, marathon, licence (N parmi M), prêt immobilier (convergence), guitare (prérequis), reconversion (choix exclusif par `siblings`), répertoire (états successifs), vaccins (expiration), maison (dépendances et retards par `marks`).

## La fusion avec l'Objectif (souhaitée, en exploration)

- L'Objectif s'écrit en briques : critère = brique à règle, sa valeur saisie un champ de la brique ; sous-objectif = groupe ; « au moins N » = quantificateur ; tentative = passage planifié ; « essentiel » = une règle. Ce n'est pas un point de départ : c'est N parmi M avec un rythme et une fin.
- Elle demande des passages planifiés, plusieurs passages ouverts, une vue des passages (une ligne par passage et son état). Une lecture sans période y lit implicitement la tentative ; dans Parcours, ça s'écrit avec `run_start`.
- Pas de reprise des données : les Objectifs existants ne servent qu'à l'utilisateur, qui les retire ; une migration retire le type. Condition : l'Objectif reste aussi simple à monter qu'aujourd'hui. Ordre : coder le Parcours, et ne retirer l'ancien outil qu'une fois le nouveau au moins aussi agréable.

## Ouvert

- Le nom de la brique : « brick » désigne déjà les briques du core (`docs/BRICKS.md`).
- Une rechute en chaîne : un geste groupé, ou un `set` sur ce qui dépend de la brique.
- Supprimer une brique emporte son historique : refuser si elle a des mouvements, ou l'archiver.
- Le toucher d'une brique sans Séance : sa fiche avec « Fait », une case à cocher sur la ligne, ou les deux.
- Une brique qui attend une brique d'un autre groupe (une séance d'août qui attend une de juillet) : la règle sait le lire, le dessin ne trace que les liens d'un même niveau.
- Les notifications, la gamification, ce que l'IA peut faire sur un Parcours.

## Faits relevés dans le code

- Une Condition est une seule comparaison, sans ET ni OU. Seules les Tâches et les routines écoutent une condition (`ConditionTriggers`), lancées quand elle passe de fausse à vraie ; un premier jugement ne lance rien.
- Une Lecture ou un filtre vise un champ à un seul niveau (`data.x`) ; aucun type de champ ne contient une liste d'objets. Une période a des bornes incluses.
- Une Référence peut désigner une entrée jusqu'à un de ses champs, réservé aux entrées (`Reference.kt`).
- La série d'une variable est faite de lignes calculées, filtrées par le même SQL que les entrées (`VariableSeries`) ; seule une Lecture sait la lire.
- Les Flashcards sont un journal : une révision par entrée, l'état de la carte après elle dans `state`, l'état actuel relu par leur propre SQL (`FlashcardsJournal`) ; elles refusent les champs supplémentaires (`takesUserFields`).
- `sequence.start` accepte déjà l'entrée à jouer (`entry`) ; seul l'écran passe par la liste (`LaunchDialog`). Une Séance jouée garde la copie de ses étapes.
- Une config peut garder des valeurs de champs (`SettingNode.FieldValues`, les raccourcis du Suivi).
- Dupliquer un outil copie sa config, pas ses entrées : dupliquer un Parcours copierait sa grammaire sans son plan.

## Reprise

La marche à suivre, chaque étape s'appuyant sur la précédente (les étapes 1 à 3 valent même si Parcours tardait) :

1. Fusionner le Schéma : fait le 2026-11-24, la démo ayant gagné un Schéma au Balcon. Reste, sur le téléphone, le schéma « Test » recopié puis l'app debug effacée avant d'installer (`docs/design/schema.md`, « Reprise »).
2. Sortir le placement dans le core : fait le 2026-11-24, `core/drawing/layout/GraphLayout.kt`, le code de calcul inchangé, les tests du Schéma verts.
3. Les améliorations communes, essayées sur le Schéma : niveaux de détail sur une tuile, linéarisation et renvois, marque et repère dans une boîte.
4. Les ajouts au core : vues calculées, Référence vers une ligne de vue, Condition avec ET, OU, `self` et quantificateur, `OpenAt`.
5. La Séance : `playable`, mode programmes, ouverture sur une ligne, `launched_for`.
6. L'outil Parcours.

Reste à concevoir en parallèle : le konnakol en entier sur le modèle, l'éditeur de plan, les points de « Ouvert ».
