# Le thème encre et lavis

Conçu le 2026-10-17. Un cinquième thème, à côté du défaut, du rétro, du cosy et du suisse : l'app comme un lavis à l'encre. Du papier crème, l'encre noire, un seul rouge de sceau, beaucoup de vide ; le seul thème calme. Deuxième des trois thèmes retenus après le cosy (Suisse, puis Encre et lavis, puis Signalétique de métro), parti des pièces plates du suisse. Son identifiant est `ink`.

Maquette (écran d'une zone) : `docs/theme-tracks.html`, carte « Encre et lavis ».

## Ce qui est décidé

Toutes les lignes du 2026-10-17, dans l'ordre de la liste de `docs/THEMES.md`.

1. **Le registre** : du papier crème, l'encre noire, un seul rouge, celui d'un sceau, et beaucoup de vide. Une tuile est une tache d'encre diluée (un lavis) aux bords irréguliers, sans contour ; les titres de groupe sont espacés, suivis d'un trait de pinceau qui s'effile. Tout trait du thème est tracé par un même pinceau (ligne 9). Il n'est ni en relief ni ombré (le cosy), ni sur une grille stricte (le suisse), ni grainé, ni décoré d'Asie (pas d'idéogramme, pas de bambou).
   - **Le rouge est au sceau** : la marque d'attente, la case cochée, le sceau de l'en-tête de page, le bouton de danger, le coin de la carte d'interaction. Le bouton flottant reste à l'encre (maquette).
   - La dernière barre rouge du graphique de la maquette n'est pas tenue : le dessin du cœur ne dit pas au thème quelle barre est aujourd'hui.
2. **Ce qu'il dessine lui-même** : tout, dialogues et sélecteurs de date et d'heure compris, en partant des pièces plates du suisse (même découpage en fichiers), leurs formes reprises.
3. **La police** : Literata (SIL Open Font License 1.1, sans nom réservé), retenue au banc le 2026-10-17 contre EB Garamond, dont les minuscules petites se lisaient moins bien ; copiée dans `third_party/literata/`, un script `scripts/make_literata_fonts.py` en tire trois graisses fixes : 400 le texte et 600 les noms et libellés, à la taille optique d'un texte (12), 700 les titres à celle d'un titre (36). Shippori Mincho, celle de la maquette, est écartée : il lui manque `≤ ≥ ≠ ≈ Š Ž Ÿ ƒ ‰` et l'espace fine insécable, et une graisse pèse 8,7 Mo (mesuré le 2026-10-17). Literata couvre Windows-1252, les flèches et `≤ ≥ ≠ ≈ − ×` ; il ne lui manque que l'espace fine (U+202F), que le script ajoute comme `make_archivo_fonts.py`. `✓ ✗` restent au repli d'Android, comme dans le suisse.
4. **Les palettes** (OKLCH, clarté · saturation · teinte ; contraste visé 4,5, tenu à chaque décalage de 15° par `InkPaletteTest`) ; leurs nombres dans `palettes.json`, réglés au banc (`themes/ink/bench/palettes.html`, « Encre et lavis · palettes ») :
   - claire : papier `#f3eee2` (0,95 · 0,017 · 88), encre `#1d1c1a` (0,227 ; 14,7), secondaire à clarté 0,45 (6,4 sur le papier, 4,8 sur le rail, foncée sous le `#6d6860` de la maquette, qui ne tenait que 3,9 sur un lavis), rouge à 0,514 · 0,159 (5,3 sur le papier, 4,57 au pire des décalages), le papier sur le rouge ;
   - sombre, de l'encre claire sur un papier de nuit : fond `#1b1a18` (0,218), encre `#e9e3d5` (0,917 ; 13,6), secondaire à 0,74 (7,5), rouge à 0,654 · 0,17 (5,1), le fond sur le rouge ;
   - les lavis, la part d'encre posée sur le papier : le cœur d'une tuile 6 % en clair, 8 % en sombre, une carte 5 % et 7 %, un rail (et un bouton désactivé) 14 % et 15 % ; aucun texte secondaire n'y tombe sous 4,5 ;
   - le rouge ne s'écrit qu'au bouton de danger, sur le papier : le chiffre rouge du graphique de la maquette passe à l'encre ;
   - la teinte tourne le rouge et la chaleur du papier, l'encre suivant la teinte du papier ; états et tags gardés ; les tags à clarté 0,60 · 0,13 en clair (3,2 au pire sur le papier, une marque, pas un texte), 0,72 · 0,12 en sombre, dessinés en petites taches de lavis.
5. **La géométrie** : plus de vide que le suisse ; `UI.Space` XS 4, S 8, M 16, L 24, XL 40, `EDGE` 20. Cases carrées, 16 dp entre deux colonnes, 20 entre deux rangées, laissés vides par la grille ; 14 × 8 dp à l'intérieur d'une tuile (maquette). La cible du doigt à 48 dp, le dessin pouvant être plus petit. `drawingUnit` 1.
6. **La taille** : la taille de texte d'Android suivie ; le cran (0 à 3) agrandit tout par ×1, ×1,12, ×1,25 et ×1,4, comme le suisse. Le titre de page à 30 sp, passant à la ligne coupé aux syllabes.
7. **Les composants** :
   - cadres : la tuile, un lavis aux bords irréguliers, sa texture coupée à cette forme ; une seule forme, étirée à chaque tuile (le thème ne sait pas laquelle il dessine, et l'ajouter au contrat pour varier les formes ne vaut pas son coût) ; la carte (variable, routine, ligne d'une liste), un lavis pâle mais plein, pour qu'aucun motif ne passe sous un texte ; le dialogue et la fenêtre flottante, du papier plein sous un trait de pinceau en haut ; la bulle de l'utilisateur, un lavis d'encre poussé à droite, l'IA sans bulle, son texte sur le papier ; la carte d'interaction, un lavis et un petit sceau rouge au coin ;
   - boutons : principal, une touche de pinceau pleine d'encre, libellé du papier ; secondaire, un contour au pinceau ; tertiaire et neutre, le libellé seul souligné d'un trait fin ; danger, contour et libellé rouges ; désactivé, un lavis pâle, libellé à l'encre secondaire ; activé, plein d'encre ; en icône seule, l'icône sans cadre dans 48 dp ; le bouton flottant, un galet d'encre de 56 dp, l'icône du papier ;
   - saisie : le champ, un trait de pinceau fin sous le texte, plus épais au focus, le libellé au-dessus à l'encre secondaire ; la sélection, le même champ et un chevron, son menu un panneau comme le dialogue ; la case, un rond tracé au pinceau, cochée un petit sceau rouge (maquette) ; l'interrupteur, un galet tracé, allumé rempli d'encre ; oui / non, le choisi en lavis plein ; le curseur, une goutte d'encre sur un trait ; la jauge, un trait de pinceau qui s'effile (maquette) sur un rail en lavis pâle ; la bande de la teinte en couleurs saturées ; les onglets, le choisi souligné d'un trait de pinceau ;
   - repères : le tag, une petite tache de sa couleur devant son texte ; le séparateur, un trait qui s'effile ; la poignée, deux traits courts ; la marque d'attente, un sceau rouge rond et un « ! » ; la marque du chrono, un cercle tracé d'un seul geste (ensō) ; l'icône colorée, à l'encre sur une tache de lavis de sa couleur, la même place sans couleur ; les textures, des motifs au pinceau un peu irréguliers, en lavis très pâle, sur la tuile et sur tout le fond d'une page texturée (`PageGround`) ;
   - attente : le chargement, l'ensō qui se trace ; l'IA qui réfléchit, trois gouttes qui s'étalent puis pâlissent l'une après l'autre ;
   - messages : le toast et la snackbar, un lavis foncé en bas, texte du papier, l'action soulignée ;
   - date et heure : dans le cadre du dialogue ; le jour choisi, une goutte d'encre pleine, aujourd'hui un cercle de pinceau ouvert ; l'heure, deux compteurs comme le suisse, des − et + au pinceau, sans cadran ;
   - en-tête de page et marque : le titre centré et espacé, un sceau rouge portant son initiale à sa droite (maquette), l'icône de la page sur sa tache de couleur au-dessus ; la marque de l'app, le calque monochrome de l'icône à l'encre, sans le bleu nuit ;
   - la barre d'un texte sélectionné : un lavis foncé, ses mots du papier, le mot touché souligné.
8. **Le toucher et le mouvement** : lent et doux, l'opposé de la coupe nette du suisse. L'élément pressé fonce d'un cran de lavis, sans onde ; une apparition est un fondu de 220 ms qui ralentit à la fin, une fermeture un fondu de 150 ms ; l'élément soulevé fonce et se décale de 4 dp, sans ombre. Animations coupées dans Android : tout instantané.
9. **Les icônes** : Lucide (`LUCIDE`), redessiné par le thème au pinceau. Le thème lit les tracés de chaque icône (le fichier vectoriel lu comme une liste de tracés, `ImageVector.vectorResource`) et les retrace d'une suite de disques dont la taille suit le pinceau. La lecture des tracés est une brique du cœur (`core/icons`), pour qu'un thème futur redessine Lucide à sa manière ; le geste est au thème. Le même pinceau trace tous les traits du thème (ligne 7). Ses réglages, choisis le 2026-10-17 au banc du pinceau (`themes/ink/bench/brush.html`, « Encre et lavis · pinceau » de `./run themes`), qui dessine comme le thème :
   - épaisseur 1,3 (en unités de la grille de 24 de Lucide) ;
   - sens du pinceau 0,45 : tenu de biais, un trait qui monte doucement vers la droite est le plus fin, jusqu'à 55 % × 0,45 plus fin qu'un trait en travers ;
   - appui aux coudes 1 : le trait s'épaissit jusqu'à 60 % dans un tournant d'un quart de tour ;
   - pose 0,45 : le trait part 60 % × 0,45 plus épais, revenu à sa largeur en un tiers de sa longueur (1,2 au plus) ;
   - ampleur 0,3 : un seul gonflement lent sur toute la longueur, jamais une ondulation ;
   - effilé 0,5 : la fin du trait se lève en pointe, jusqu'à 6 % de sa largeur, sur la moitié de sa longueur au plus (0,6 + 3,4 × 0,5) ;
   - les largeurs lissées le long du trait ; un trait fermé (un cercle) sans pose ni pointe ;
   - bord mouillé : un bord à demi transparent autour d'un cœur à 72 % de la largeur ; pas de brosse sèche ;
   - le geste fixé par icône (une même icône toujours dessinée pareil), calculé une fois par taille et gardé.
10. **Les graphiques** : dessinés par le thème (`InkDrawing`, parti de `SwissDrawing`). Les barres en traits de pinceau qui s'effilent, les surfaces en lavis transparents, l'objectif en pointillé, les axes en trait fin ; les couleurs que l'utilisateur donne aux séries gardées, en lavis.
11. **Le mode d'édition de la grille** : une case, un rond de lavis très pâle ; la tuile choisie, la tuile soulevée de la ligne 8.
12. **Le registre sonore** : feutré, un pinceau sur le papier, une page qu'on tourne, un bois léger ; à chercher dans les sons d'impact doux et les « drop » de Kenney. Le thème part muet.
13. **Les textes** : nom « Encre et lavis » / « Ink and wash » ; phrase « Comme un lavis à l'encre : un papier crème, l'encre noire et un seul rouge de sceau, beaucoup de vide » / « Like an ink wash painting: cream paper, black ink and a single seal red, plenty of empty space ». Pas de nom de palette.

## Ce qui reste ouvert

- Le banc du thème (`themes/ink/bench/palettes.html`), vu dans les deux modes, avant le code : vu le 2026-10-17 : la police, les nombres des palettes, les lavis. Ses formes de lavis sont approchées en CSS, et le thème les dessinera en tracés.
- Une fois redessiné Lucide tenu sur le téléphone (coût, icônes qui se bouchent) : la troisième façon de la ligne 9 de la recette, `docs/THEMES.md`.
