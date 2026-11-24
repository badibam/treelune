# Schéma (Schema)

Conception du 2026-11-01, en cours : à implémenter puis élaguer.

## Le besoin

Des briques et des flèches entre elles : une organisation, une boucle, qui nourrit qui. Deux usages, un même outil et un même dessin :

- **le schéma écrit**, par la personne ou par l'IA, qui représente ce qu'on veut, pas forcément ce que contient l'app ;
- **la carte de l'installation**, que l'app écrit seule et tient à jour : quel outil, variable, Tâche, routine, Repère lit quoi, quel événement est émis ou écouté.

La version la plus simple, un bloc de texte à chasse fixe dans une Note, ne suffit pas : l'app n'a pas de police à chasse fixe, un schéma de plus de trois boîtes déborde en portrait, un accent décale le dessin, et rien n'y est touchable. C'est un repli, pas la cible.

## Ce qu'est un schéma

Un graphe composé : des boîtes, des flèches orientées (avec ou sans texte), et des groupes qui contiennent des boîtes et d'autres groupes. Une boucle, un arbre, une séquence en sont des cas. Une frise, un tableau, un dessin libre n'en sont pas : l'Agenda, la Table et le Graphique les couvrent.

## L'ordre

1. Le schéma écrit : la config, le dessin, la saisie par le formulaire et par l'IA.
2. La carte de l'installation : une seconde source du même outil. Elle demande les liens d'une portée (une zone, un groupe, l'app) à un service du core, par le dispatcher ; le Schéma ne lit aucune config lui-même. Le core relève les liens sans connaître les types d'outil : les Tâches, routines, événements, déclencheurs, variables, Repères et conditions sont à lui ; un lien d'un outil vers un autre se reconnaît aux briques de sa config (une sélection, une lecture, une Référence), la brique disant la nature du lien (« lit », « désigne »). Que les réglages déclarés d'un outil laissent le core y trouver ses briques est à vérifier. Ce service sert aussi l'IA, à qui il rend les liens d'une zone en une lecture (« pourquoi ma routine est partie deux fois ? »).

## La config

Du JSON au vocabulaire de Mermaid (flowchart), décrit réglage par réglage comme celui du Graphique l'est en Vega-Lite : le formulaire, la vérification et le schéma lu par l'IA en sont tirés. Pas de texte Mermaid enregistré : un texte n'a ni formulaire, ni validateur, ni place pour une brique sous sa forme, et un libellé de boîte liée y serait ambigu (vieux nom ou libellé voulu). Cytoscape.js, seul format JSON connu à avoir des groupes contenants sans positions, ne fixe qu'un squelette (`data.id`, `parent`, `source`, `target`) : le reste serait inventé quand même.

```json
{
  "direction": "TD",
  "nodes": [
    {"id": "A", "shape": "rect", "thing": {"kind": "TOOL_INSTANCE", "id": "…"}, "color": "RED"},
    {"id": "B", "label": "Ce qu'il me reste", "shape": "rounded"}
  ],
  "edges": [
    {"from": "A", "to": "B", "label": "nourrit", "line": "dotted"}
  ],
  "groups": [
    {"id": "matin", "title": "Le matin", "members": ["A", "B"]}
  ]
}
```

- **Direction** : `TD` (de haut en bas) ou `BT` (de bas en haut). Pas de `LR` ni de `RL` : de gauche à droite, un schéma déborde en portrait. Seule une tuile plus large que haute (son corps, en cases) le dessine de gauche à droite, d'elle-même.
- **Formes** : rectangle, coins arrondis, pilule, cercle, losange, sous les noms de la syntaxe `@{ shape: … }` de Mermaid 11 : `rect`, `rounded`, `stadium`, `circle`, `diamond`.
- **Flèches** : un trait normal, pointillé ou épais, avec ou sans pointe, un texte facultatif.
- **Groupes** : imbriquables, un titre, `folded` pour s'ouvrir replié ; `members` liste leurs boîtes et leurs sous-groupes, un identifiant ne servant qu'une fois parmi les deux.
- **Couleur** : sur une boîte ou un groupe, au plus une couleur d'étiquette, par son nom enregistré ; le thème la dessine, jamais une couleur écrite en dur. La texture, un temps prévue, n'est pas dans l'outil : la dessiner demande une forme de plus dans `core/drawing` et dans les 9 thèmes.
- **Icône** : `icon` sur une boîte, un nom Lucide choisi avec le sélecteur de l'app (le drapeau `icon` d'un réglage), au-dessus de son texte (à côté sur une tuile où le dessin va de gauche à droite) ; sans `icon`, une boîte liée montre l'icône de sa chose. Un nom inconnu est refusé, l'id nommé ; un import Mermaid la garde à une boîte dont l'id reste, comme sa couleur.
- **Le lien d'une boîte** : la brique Chose (`Reference`, `{"kind", "id"}`), choisie par le navigateur des choses. Une boîte liée sans `label` porte le nom actuel de sa chose, « supprimée » si elle n'est plus ; avec `label`, le sien.
- Une flèche ou un groupe qui nomme une boîte absente est refusé, le refus nommant l'id.

## La saisie

- Le formulaire tiré de la config : les listes des boîtes, des flèches et des groupes.
- L'IA, qui écrit le JSON.
- **Importer du Mermaid**, dans une section « Mermaid » en tête de la config (`ToolTypeContract.ConfigTools`, qui agit sur le brouillon du formulaire) : un texte flowchart collé ou tapé, converti en une fois, son bilan montré avant (« 12 boîtes, 15 flèches, 2 groupes »), rien d'enregistré avant « Enregistrer » ; une erreur de syntaxe refusée en nommant sa ligne. La conversion dit ce qu'elle change ou laisse : `LR` et `RL` réécrits en `TD`, `style`, `classDef` et `linkStyle` ignorés. Sur un schéma existant, une boîte dont l'id reste garde sa chose et sa couleur.
- **Copier en Mermaid**, dans la même section : le texte flowchart du schéma, sans les liens.
- Pas d'éditeur textuel. Le geste sur le dessin (toucher une boîte pour la modifier, tirer une flèche) est à concevoir une fois le dessin là.

## Le dessin

Par l'app, sans vue web, chaque thème à sa manière (`core/drawing`, comme le Graphique).

- Une dizaine de boîtes se lisent d'un coup d'œil en portrait, sans toucher ; au-delà, on défile verticalement.
- Les enfants sans suite d'une même boîte, qu'elle atteint par des flèches identiques (même texte, même trait), forment un bloc rangé en lignes à la largeur de l'écran, à cadre discret, qu'une seule flèche atteint ; un groupe dont les boîtes n'ont aucune flèche entre elles, ni avec l'extérieur, se range de même, comme les parties d'un schéma qu'aucune flèche ne relie.
- Encore trop large, dans l'ordre : l'app resserre les textes et les espaces ; replie les groupes, le plus large d'abord, chacun devenant une boîte qu'on touche pour la déplier, et le dit sous le schéma (« 1 groupe replié pour tenir dans l'écran »), sans jamais refermer un groupe ouvert à la main ; réduit le dessin jusqu'à 75 %, sauf dans un thème en pixels entiers ; en dernier recours, il défile horizontalement. Le repli des blocs en « + 6 autres » n'est pas fait.
- Pas de zoom par pincement.

## L'écran

Une page de lecture : le schéma sur toute la page, comme en FULL. Ce qui est souligné se touche : le texte d'une boîte dont le lien existe, qui ouvre sa chose ; le mot « suite » d'un texte coupé, qui ouvre une fenêtre avec le texte entier, et un bouton « Ouvrir » si la boîte a aussi un lien. Un groupe se replie et se déplie le temps de la visite, sans rien écrire. Ce qui modifie passe par la config, la roue dentée. Vide : « Ce schéma est vide. Ajoute ses boîtes dans sa config, importe un texte Mermaid, ou demande-le à l'IA. »

## Le placement

`core/drawing/layout/GraphLayout.kt`, dans le core pour servir aussi au Parcours, en Kotlin sans type Android : l'app mesure les boîtes et leurs textes, le placement rend des positions et des traits à angles droits, le thème dessine. Méthode en couches, groupe par groupe : un groupe est une boîte opaque pour son parent, une flèche qui y entre est coupée à son bord.

ELK n'entre pas dans l'app. Relevé le 2026-11-22 dans les sources de Maven Central : les 500 fichiers d'ELK 0.12.1 autorisent la GPL-3.0, ceux d'EMF (`common`, `ecore`, `xmi`) et de `xtext.xbase.lib`, dont il dépend, non, en version tirée comme en dernière version. L'autre sortie, une exception ajoutée à la licence de Treelune, n'a pas été retenue.

Le banc compare le placement à ELK sur 11 schémas : `tmp/schema-bench` du worktree `schema`, ignoré par git (`../../gradlew run` depuis ce dossier, page `report.html`). Mesuré au commit du placement, nous face à ELK :

- croisements : jamais plus (Groupes traversés 2 / 3, Losange dense 18 / 18, zéro ailleurs) ;
- flèche sur une boîte, un groupe étranger ou un texte : 0 / 0 ; sur un titre de groupe : 0 / 12 ;
- largeur : arbre à 8 enfants 313 / 678 dp, carte de 30 choses 353 / 671 dp, seule à dépasser 340 dp ;
- coudes : plus qu'ELK sur 6 schémas, dont les trois à groupes (Groupes imbriqués 16 / 6, Groupes traversés 46 / 28, Boucles entre groupes 38 / 16) : la règle « pas plus de coudes qu'ELK sur les schémas à groupes » n'est pas tenue ;
- hauteur : plus haut sur 9 schémas (carte de 30 choses 1138 / 655 dp) ;
- temps : 40 ms au plus sur l'ordinateur, moins de 10 ms pour ELK ; non mesuré sur le téléphone.

Jugé visuellement acceptable par l'utilisateur à ce stade.

## La tuile

- **Résumé** : le nombre de boîtes et de flèches (« 7 boîtes · 9 flèches ») ; le même en LINE, CONDENSED et EXTENDED.
- **Sans corps** (4×1, 2×2) : la miniature à la place du résumé, comme le Graphique (`Glance`), dessinée de gauche à droite ; le résumé y reste quand même les pastilles n'y tiennent pas.
- **SQUARE** : le schéma réduit à la tuile ; un toucher ouvre l'outil.
- **FULL** : le schéma entier à la taille de lecture ; une boîte liée ouvre sa chose, un toucher ailleurs ouvre l'outil.

## Reprise

Le Schéma est fusionné dans `develop` (2026-11-24), la suite verte avant la fusion ; la démo en a un au Balcon, « Le soin du balcon ». Le téléphone a encore le build de la branche dans l'app debug, en base 121, avec une zone « Essai » et un schéma « Test » de 12 boîtes qui couvre les formes, les traits, les groupes et une boucle.

Fait : le placement, la config et sa vérification (`SchemaSpec`), le formulaire, le dessin (`SchemaScene`), l'écran (ce qui est souligné se touche, le repli le temps de la visite, le groupe touché qui reste en place), la tuile, la mise à la largeur, l'import et la copie Mermaid (`SchemaMermaid`).

Mesuré sur le téléphone en build debug, pour ce schéma de 12 boîtes : un nouvel état se calcule en 135 à 343 ms, hors du fil de l'écran. Non mesuré : la release, un schéma plus grand.

Reste à coder :

1. Une tuile FULL dont la boîte liée ouvre sa chose : aujourd'hui, tout toucher ouvre l'outil.
2. L'entrée du Schéma dans `docs/TOOLS.md`, à l'élagage de cette conception.

Jamais vu sur le téléphone :

- Le repli automatique et la réduction à 75 %, qui n'ont tourné qu'en test : il faut un schéma plus large que l'écran.
- Un schéma importé en Mermaid puis enregistré (l'import dans le brouillon est vu, pas la sauvegarde).
- Le bouton « Ouvrir » de la fenêtre d'une boîte liée au texte coupé.
- La coupe à deux lignes du résumé d'une boîte dans le formulaire.

Défauts vus, laissés :

- La longue flèche « ajuste » garde de petits décrochements près de ses extrémités.
- Replier un sous-groupe fait rétrécir le cadre du groupe parent, dont le bord gauche se déplace.
- Sur une tuile en thème rétro, le schéma réduit par crans entiers n'occupe qu'environ la moitié de la largeur.
- Le champ de l'import Mermaid garde les boutons de liste et la majuscule automatique du clavier.

Avant d'installer `develop` sur le téléphone : recopier le schéma « Test » par « Copier en Mermaid », puis effacer l'app debug (`adb shell pm clear app.treelune.debug`), que la base 120 de `develop` n'ouvre plus.

## Ouvert

Le schéma écrit est conçu ; ce qui suit reste à trancher ou à vérifier.

À vérifier avant ou pendant le code :


À juger sur le téléphone :

- Le seuil de réduction (75 %).
- Le dessin de chaque thème : vu en thème par défaut, rétro et encre ; les six autres restent à voir.

À concevoir :

- Ce que l'IA lit du type : son `usage` et son détail (écrire pour un téléphone, `TD`, une dizaine de boîtes, un lien plutôt qu'un libellé quand la boîte est une chose de l'app, un texte Mermaid collé dans la conversation converti par elle).
- Le geste sur le dessin (toucher une boîte pour la modifier, tirer une flèche), une fois le dessin là ; plus tôt si la saisie par la config se révèle trop lourde.
- La carte de l'installation, une fois le schéma écrit vu sur le téléphone : ce qu'elle montre (quelles sortes de choses, quels liens, leurs noms : « lit », « émet », « écoute »), le service du core qui relève les liens d'une portée (une zone, un groupe, l'app) et ce que l'IA en lit, la forme de chaque sorte de chose, ce qu'elle replie d'elle-même. Que les réglages déclarés d'un outil laissent le core y trouver ses briques (une sélection, une lecture, une Référence) est à vérifier d'abord.
