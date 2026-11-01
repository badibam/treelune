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
    {"id": "A", "shape": "rect", "thing": {"kind": "TOOL_INSTANCE", "id": "…"}, "color": "red", "texture": "waves"},
    {"id": "B", "label": "Ce qu'il me reste", "shape": "rounded"}
  ],
  "edges": [
    {"from": "A", "to": "B", "label": "nourrit", "line": "dotted"}
  ],
  "groups": [
    {"id": "matin", "title": "Le matin", "nodes": ["A", "B"]}
  ]
}
```

- **Direction** : `TD` (de haut en bas) ou `BT` (de bas en haut). Pas de `LR` ni de `RL` : de gauche à droite, un schéma déborde en portrait.
- **Formes** : rectangle, coins arrondis, pilule, cercle, losange, sous les noms de la syntaxe `@{ shape: … }` de Mermaid 11 (à vérifier).
- **Flèches** : un trait normal, pointillé ou épais, avec ou sans pointe, un texte facultatif.
- **Groupes** : imbriquables, un titre, `folded` pour s'ouvrir replié.
- **Couleur et texture** : sur une boîte ou un groupe, au plus une couleur d'étiquette et une texture, par leurs noms enregistrés ; le thème les dessine, jamais une couleur écrite en dur. Dans le thème E-reader, où une couleur est un motif, couleur et texture vont sur deux surfaces différentes de la boîte, comme pour une icône colorée.
- **Le lien d'une boîte** : la brique Chose (`Reference`, `{"kind", "id"}`), choisie par le navigateur des choses. Une boîte liée sans `label` porte le nom actuel de sa chose, « supprimée » si elle n'est plus ; avec `label`, le sien.
- Une flèche ou un groupe qui nomme une boîte absente est refusé, le refus nommant l'id.

## La saisie

- Le formulaire tiré de la config : les listes des boîtes, des flèches et des groupes.
- L'IA, qui écrit le JSON.
- **Importer du Mermaid**, dans une section « Mermaid » en tête de la config : un texte flowchart collé ou tapé, converti en une fois, son bilan montré avant (« 12 boîtes, 15 flèches, 2 groupes »), rien d'enregistré avant « Enregistrer » ; une erreur de syntaxe refusée en nommant sa ligne. La conversion dit ce qu'elle change ou laisse : `LR` et `RL` réécrits en `TD`, `style`, `classDef` et `linkStyle` ignorés. Sur un schéma existant, une boîte dont l'id reste garde sa chose, sa couleur et sa texture.
- **Copier en Mermaid**, dans la même section : le texte flowchart du schéma, sans les liens.
- Pas d'éditeur textuel. Le geste sur le dessin (toucher une boîte pour la modifier, tirer une flèche) est à concevoir une fois le dessin là.

## Le dessin

Par l'app, sans vue web, chaque thème à sa manière (`core/drawing`, comme le Graphique).

- Une dizaine de boîtes se lisent d'un coup d'œil en portrait, sans toucher.
- Au-delà, on défile verticalement, jamais horizontalement : une couche trop large passe sur deux lignes.
- Un groupe se replie en une boîte, qu'on touche pour le déplier.
- Pas de zoom par pincement.

## L'écran

Une page de lecture : le schéma sur toute la page, comme en FULL. Une boîte liée ouvre sa chose ; une boîte sans lien montre son texte entier s'il est coupé. Un groupe se replie et se déplie le temps de la visite, sans rien écrire. Ce qui modifie passe par la config, la roue dentée. Vide : « Ce schéma est vide. Ajoute ses boîtes dans sa config, importe un texte Mermaid, ou demande-le à l'IA. »

Qu'un formulaire de config tiré de la déclaration accepte une section de boutons d'action est à vérifier (« Essayer » d'une API).

## Le placement : ELK

Le placement des boîtes et le tracé des flèches viennent d'ELK (Eclipse Layout Kernel, `org.eclipse.elk.alg.layered`, EPL-2.0), algorithme en couches, `HierarchyHandling.INCLUDE_CHILDREN` pour les groupes, traits à angles droits ; l'app mesure les boîtes et leurs textes, ELK rend des positions, le thème dessine. Mesuré le 2026-11-01 :

- l'APK : +2,76 Mo (37,40 → 40,16) ;
- sous R8, sur le téléphone, sans règle de conservation : il tourne, 30 boîtes à deux groupes imbriqués et une boucle placées en 65 ms la première fois, 20 à 30 ms ensuite, 7 ms d'initialisation ;
- sur l'ordinateur, cinq schémas types (une boucle, une chaîne Repas → Coach, un arbre à 8 enfants, des groupes imbriqués, la carte d'une zone de 30 choses) : les trois sans largeur sont nets et tiennent en 340 dp ; l'arbre (721 dp) et la carte (1195 dp) débordent, avec ou sans borne du nombre de boîtes par couche (Coffman-Graham), qui les étale en escalier.

Son intégration demande : les fichiers propres à Eclipse en double laissés à un seul (`pickFirsts` : `plugin.xml`, `about.*`…) et `META-INF/services/org.eclipse.elk.core.data.ILayoutMetaDataProvider` fusionné (`merges`) ; `org.eclipse.xtext.xbase.lib`, qu'ELK utilise sans le déclarer ; `-dontwarn org.eclipse.**`, qui cacherait aussi un autre manque, sur un chemin non essayé : chaque option employée se vérifie dans la release sur le téléphone.

## La tuile

- **Résumé** : le nombre de boîtes et de flèches (« 7 boîtes · 9 flèches ») ; le même en LINE, CONDENSED et EXTENDED.
- **SQUARE** : le schéma réduit à la tuile ; un toucher ouvre l'outil.
- **FULL** : le schéma entier à la taille de lecture ; une boîte liée ouvre sa chose, un toucher ailleurs ouvre l'outil.

## Ouvert

- La largeur : un parent à beaucoup d'enfants, un groupe de choses sans lien entre elles.
- La carte de l'installation : le service des liens, sa portée, la forme de chaque sorte de chose, ce qu'elle replie d'elle-même.
