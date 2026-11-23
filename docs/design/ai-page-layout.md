# Le rangement d'une page par l'IA

Sur demande, l'IA améliore la disposition et l'apparence d'une page : une zone ou l'accueil. Conception du 2026-11-23, rien n'est codé.

## Principes

- Le système est complet sur la présentation : ordre, sections, tailles, icônes, couleurs, textures, disposition du contenu des tuiles, pièces d'une zone.
- La commande ne porte aucune règle de goût : elle montre la page, applique ce que l'IA décide, refuse ce qui est impossible. Un pattern constaté s'ajoute au guide de conception (`ai_design_guide`).
- L'IA ne manipule jamais de coordonnées : l'ordre d'une liste donne la place, l'app pose chaque tuile à la première place libre. Un trou volontaire ou une position forcée ne se demandent pas.
- Une commande vise une page. « Refais toute l'app » enchaîne une commande par page, chacune annulable seule.
- Déplacer un outil vers une autre zone reste hors de cette commande (`UPDATE_TOOL` et `zone_id`) : cela change l'organisation des données, pas leur présentation.

## Lire : le plan

Une requête de l'IA, appelée seulement pour ranger, rend le plan d'une page : son empreinte, puis pour chaque section le dessin de sa grille à 4 colonnes et une ligne par tuile.

```
plan a3f9
## Entraînement
 T1  T1  T1  T1
 T1  T1  T1  T1
 t2  t2  T3  T3
 T4  T4  t5   .
T1 tool «Sorties» 4x2 footprints ORANGE - corps:demis,2l
t2 routine «Alléger la semaine» icons_2x1 feather GREEN -
T3 tool «Renfo» 2x1 dumbbell RED -
T4 tool «Étirements» 2x1 activity - -
t5 tool «Fractionné» icon timer ORANGE DIAGONALS
```

- Le plan sort de `Grid`, avec les positions enregistrées.
- La taille est la valeur enregistrée (`TileSize.stored`) : `2x1` est une demi-tuile avec son nom, `icons_2x1` une bande de deux cases en icônes. Une minuscule dans le dessin marque une tuile en icônes.
- `T1`, `T2`… numérotent les tuiles de toute la page dans l'ordre de lecture, sans repartir à chaque section. L'app ne mémorise rien : elle renumérote à l'écriture.
- L'empreinte couvre ce que le plan dit de la page. Elle prouve à l'écriture que les repères désignent toujours les mêmes tuiles.
- Une tuile `4xcontent` est marquée comme suivant son contenu : le dessin ne lui invente pas de hauteur.
- La ligne d'une tuile porte aussi la largeur et le nombre de lignes de ses éléments quand elle a un corps ; le plan d'une zone porte la tuile de la zone et ses pièces.
- Réglage « Une colonne » actif : une ligne en tête le dit, et le dessin de ce mode s'ajoute à celui des 4 colonnes, seul enregistré.
- Ce que chaque taille affiche (nom, résumé, corps) est dit une fois dans le prompt de la commande, pas sur chaque ligne.

Coût mesuré sur une maquette, les 5 zones de la démo, 61 tuiles : 4 115 caractères pour le plan, dont 1 232 pour les dessins, 1 344 pour la plus grosse zone ; au moins 16 785 pour les mêmes tuiles en JSON de `ZONE_CONTENTS`. La maquette ne passe pas par `Grid` et dessine mal les tailles spéciales.

## Écrire : `ARRANGE_PAGE`

```json
{
  "type": "ARRANGE_PAGE",
  "zone_id": "demo-course",
  "plan": "a3f9",
  "section_order": ["Aujourd'hui", "Entraînement", "Analyse"],
  "sections": [
    {
      "name": "Aujourd'hui",
      "tiles": [
        { "tile": "T7", "tile_size": "4x2", "item_width": "halves", "item_lines": 2 },
        { "tile": "T3", "tile_size": "icons_2x1", "icon_color": "RED" },
        "T4"
      ]
    },
    {
      "name": "Entraînement",
      "tiles": ["T1", { "tile": "T2", "texture": null }, "T5"]
    }
  ],
  "zone_tile": {
    "tile_size": "4x2",
    "icon_color": "ORANGE",
    "tile_pieces": ["T7", "T3"],
    "tile_pieces_icons": true
  }
}
```

- La cible est `zone_id`, ou `"home": true` pour l'accueil, dont les tuiles sont les zones.
- Un repère seul garde l'apparence de la tuile et lui donne sa nouvelle place. Un objet change les réglages qu'il nomme ; `null` retire une couleur ou une texture.
- Une section listée est donnée entière. Une section non listée ne bouge pas. Une tuile qui quitte une section oblige à lister cette section.
- Un nom nouveau crée une section, `"tiles": []` supprime la section.
- `section_order`, facultatif, donne l'ordre de toutes les sections.
- La réponse contient le nouveau plan : l'IA voit le résultat réel et peut corriger sans relire.

## Refus

Tout ou rien, rien n'est écrit :

- l'empreinte ne correspond plus à la page : relire le plan ;
- un repère inconnu, ou une tuile nommée deux fois ;
- une tuile absente d'une section listée alors qu'elle y était, sans être placée ailleurs ;
- une taille interdite pour ce type de tuile : `TileSize.ZONE` pour une zone, `TileSize.PIECE` pour une pièce ;
- une valeur hors des listes fermées (icône, couleur, texture, largeur et lignes des éléments), vérifiée comme par les services de chaque type de tuile.

## Application

- Une commande de l'IA est une étape de l'historique : tout passe par `Writes`, dans une seule transaction, et s'annule d'un coup. Une tuile modifiée après coup fait refuser l'annulation, qui nomme ce qui a changé.
- Les niveaux de validation s'appliquent (`ValidationResolver`) : ceux de la zone pour une zone, ceux de l'app pour l'accueil. Sans validation demandée, la page change tout de suite.
- Après un rangement, l'IA dit en une ou deux phrases la logique suivie.

## Prompt

- La description des tailles gagne les bandes d'icônes (`icons_…`), absentes aujourd'hui.
- La règle « only the user moves a tile » sort, remplacée par la nouvelle commande.
