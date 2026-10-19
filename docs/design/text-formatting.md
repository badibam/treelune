# Mise en forme des textes longs

Écrire une liste, une étape numérotée, un mot en gras dans un texte de l'utilisateur, sans que le clavier Android le rende pénible. Venu des bugs du téléphone (une note, ses listes à tirets). Conçu le 2026-10-19.

## Ce qui est stocké

- Du texte, avec le petit Markdown que l'app lit déjà (`GuideMarkup`) : rien d'autre. L'IA le lit et l'écrit sans conversion ; copier, exporter, filtrer sur le texte continuent de marcher ; les textes déjà écrits sont valides, aucune migration.
- Toute l'aide est à la saisie, tout le rendu à l'affichage.

## Quels textes

- Tout champ TEXT de longueur LONG ou UNLIMITED, d'office, sans option : contenu d'une note, d'une entrée de Journal, champ de l'utilisateur, message d'une routine… Les textes SHORT et MEDIUM restent bruts.
- Là où un texte est montré en abrégé (cellule d'un tableau, aperçu d'une tuile) : une ligne, marques retirées, tronquée comme aujourd'hui.

## Le vocabulaire

- Titres `#`, `##`, `###` ; listes à puces `- ` ou `* ` ; `**gras**`, `*italique*` (une étoile entre deux lettres ou chiffres n'est pas une marque).
- Nouveau : les listes numérotées, `1. `. Numérotées à l'affichage par leur position, quel que soit le chiffre écrit : une étape insérée au milieu ne demande aucune renumérotation, et l'app ne réécrit jamais le texte tapé.
- L'icône `{nom}` n'est lue que dans les textes de l'app (Guide, mode d'emploi d'un outil) : dans un texte de l'utilisateur, `{` reste un caractère.
- Pas de cases à cocher (le rôle de l'outil Liste), de listes imbriquées, de liens ni de citations — tant qu'aucun besoin ne les demande.

## Un seul lecteur

- `GuideMarkup` sort de `core/guide` vers un endroit commun du cœur, sous un nom qui ne dit plus « Guide », et sert au Guide, aux modes d'emploi, aux identités et aux textes de l'utilisateur ; ce qui le distingue d'un usage à l'autre (les icônes) est un paramètre de la lecture.
- Le rendu reste celui des thèmes (`ThemeContract.emphasis` pour le gras et l'italique) ; une liste numérotée y gagne son dessin.

## L'aide à la saisie

Le champ montre le texte avec ses marques ; le rendu s'affiche hors édition.

- Continuation au retour à la ligne : après `- texte`, la ligne suivante commence par `- ` ; après `2. texte`, par `3. ` ; un retour à la ligne sur un élément vide retire sa marque et finit la liste.
- Deux boutons sur la ligne du label du champ : liste à puces, liste numérotée. Ils posent ou retirent la marque sur la ligne du curseur, ou sur chaque ligne de la sélection.
- Gras et Italique dans la barre du texte sélectionné, que le thème dessine déjà : ils entourent la sélection de `**` ou `*`, ou les retirent.
- Pas de bouton de titre : `## ` se tape.

## L'IA

- Le prompt dit que les textes longs acceptent ce vocabulaire, et lui seul.

## Écarté

- Une structure de blocs dans un nouveau type de champ : un éditeur riche, fragile sur Android, pour un gain nul tant que le vocabulaire se limite à celui-ci.
- Le « champ liste », une valeur par étape : ce n'est pas une mise en forme mais une donnée structurée (cocher, réordonner, compter une étape) ; il revient avec la conception de la recette.

## À passer sur le téléphone

- La continuation et les deux boutons, au clavier Android, dans une note et dans un champ de l'utilisateur.
- Gras et Italique depuis la barre de sélection, dans chaque thème.
- Une note mise en forme dans chaque thème ; un texte long abrégé dans un tableau et sur une tuile.
