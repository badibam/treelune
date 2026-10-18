# Le thème papier découpé

Conçu le 2026-10-18. Un septième thème, à côté du défaut, du rétro, du cosy, du suisse, de l'encre et lavis et du métro : l'app en feuilles de couleur découpées aux ciseaux, posées un peu de travers sur du kraft. Quatrième des thèmes retenus, suivant le classement de `docs/theme-tracks.json`. Son identifiant est `paper`.

Maquette (écran d'une zone) : `docs/theme-tracks.html`, carte « Papier découpé ».

## Ce qui est décidé

Toutes les lignes du 2026-10-18, dans l'ordre de la liste de `docs/THEMES.md`.

1. **Le registre** : des feuilles de papier de couleur, découpées, posées un peu de travers sur du kraft, chacune sur une ombre dure décalée (2 dp à droite, 3 dp en bas, sans flou), aux coins un peu irréguliers (un rayon différent à chaque coin, de 2 à 7 dp). Il n'est ni rond et dodu, enfoncé dans son ombre (le cosy : le papier est plat, mince, anguleux, et ses tuiles changent de couleur l'une à l'autre), ni au pixel (le rétro), ni calme (l'encre).
2. **Ce qu'il dessine lui-même** : tout, dialogues et sélecteurs de date et d'heure compris, en partant des pièces du métro (même découpage : `PaperPalette`, `PaperSize`, `PaperPieces`, `PaperDrawing`, `PaperTheme`).
3. **La police** : Bricolage Grotesque, celle de la maquette, licence SIL Open Font License 1.1 ; copiée dans `third_party/bricolage/`, un script `scripts/make_bricolage_fonts.py` en tire les graisses fixes : 500 le texte, 800 les noms, les titres et les chiffres. Sa couverture (Windows-1252, U+202F, les flèches, `≤ ≥ ≠ ≈`), un nom réservé et son italique restent à mesurer sur la copie ; sans italique, celui d'un texte lu est le penché d'Android, un repli à valider. Si la couverture manque, Archivo, déjà copiée.
4. **Les palettes** (OKLCH, réglées au banc ; contraste visé 4,5, tenu à chaque décalage de 15° par `PaperPaletteTest`) :
   - claire, d'après la maquette : le kraft `#d6b688` et ses fibres à 115°, la feuille crème `#f7efe0`, l'encre `#2a2118`, le corail `#e5674e` (accent et alerte), le bleu nuit `#2f4466` (boutons) ;
   - sombre : un carton brun très sombre au fond, la feuille crème devenue un papier noir (anthracite chaud) à l'encre crème, les feuilles de couleur gardées, assombries d'un cran ;
   - les tags sont les couleurs des feuilles (moutarde, sarcelle, ciel, lilas…), mates comme un papier Canson ; ce qui s'écrit sur une feuille de couleur est à l'encre ou crème, celle des deux qui contraste le plus, comme dans le métro ;
   - la teinte tourne le kraft, le corail et le bleu nuit ; états et feuilles de couleur gardés.
5. **La géométrie** : un pas de 8 dp ; `UI.Space` XS 4, S 8, M 16, L 24, XL 32, `EDGE` 16. Cases carrées, 12 dp entre deux colonnes et deux rangées, la place de l'ombre et de l'inclinaison. La cible du doigt à 48 dp. `drawingUnit` 1.
6. **La taille** : la taille de texte d'Android suivie ; le cran (0 à 3) agrandit tout par ×1, ×1,12, ×1,25 et ×1,4, sauf les espacements, la marge de bord, les écarts de la grille, fixes en dp. Le titre de page à 26 sp, 800.
7. **Les composants** :
   - l'inclinaison : une tuile penche de −1,5° à +1,5° (la maquette va à 2° : au-delà, le texte se brouille), un angle fixé par l'identifiant de l'outil ou de la zone (ajout au contrat, plus bas) ; les cartes et tout ce qui porte un texte long (note, bulle, champ, dialogue) restent droits, avec leurs coins irréguliers et leur ombre dure ;
   - cadres : la tuile d'outil et de zone, une feuille de la couleur de son icône, crème sans couleur ; la tuile en icône seule, la même feuille, l'icône sur un rond crème découpé à la main ; le titre de groupe, un bandeau corail penché de −1,5°, texte crème, suivi d'une bande crème déchirée (maquette) ; le dialogue et la fenêtre flottante, une feuille crème droite ; la bulle de l'utilisateur, une feuille bleu nuit, celle de l'IA une feuille crème ; la carte d'interaction, une feuille crème coiffée d'un ruban corail ;
   - boutons : principal, une feuille bleu nuit sur son ombre dure ; secondaire, une feuille crème au trait d'encre de 2 dp ; tertiaire, le libellé seul 800 ; danger, une feuille corail ; désactivé, le dessin de son type sans ombre, pâli ; le bouton flottant, un rond corail posé sur un rond moutarde décalé (maquette) ;
   - saisie : le champ, une feuille blanche au trait d'encre de 2 dp, corail au focus ; la case, un carré blanc aux coins inégaux penché de −4°, rempli de corail cochée (maquette) ; l'interrupteur, une bande de papier et un rond découpé ; le curseur et la jauge, une bande sombre et sa bande moutarde coupée de biais (maquette) ; la bande de la teinte en couleurs saturées, le choix cerclé ; les onglets, le choisi une feuille qui dépasse ;
   - repères : le tag, une étiquette de sa couleur penchée de −3° à +3° d'après son texte ; la marque d'attente, un rond corail découpé penché de 8°, « ! » 800 (maquette) ; le séparateur, une bande déchirée fine ; les textures, des motifs de papier (fibres, trame de Canson, confettis), faibles ;
   - attente : trois confettis qui tombent l'un après l'autre ;
   - messages : le toast et la snackbar, une feuille bleu nuit, texte crème ;
   - date et heure : le calendrier sur une feuille crème, le jour choisi dans un rond corail découpé ; l'heure, deux feuilles de chiffres entre − et + ;
   - en-tête de page et marque : le titre sur une bande crème penchée de −2° (maquette) ; la marque de l'app (`LauncherMark`) sur un rond découpé ;
   - la barre d'un texte sélectionné : une bande bleu nuit, ses mots crème.
8. **Le toucher et le mouvement** : l'élément pressé se plaque sur son ombre, son décalage ramené à 0, sans onde ; une apparition tombe en 160 ms d'une inclinaison de 3° à la sienne, comme une feuille posée ; l'élément soulevé grandit son ombre à 6 dp et penche de 2° de plus. Animations coupées dans Android : tout instantané.
9. **Les icônes** : Lucide (`LUCIDE`), redessiné par le thème en pièces de papier découpées, d'après les tracés que lit la brique du cœur `core/icons/IconStrokes`, comme le pinceau de l'encre : le trait épaissi à 2,6, ses bouts coupés droits et ses angles vifs, les courbes coupées en petits segments droits (la facette des ciseaux), une ombre dure décalée d'environ 1 dp pour 24 dp ; la couleur, celle que le cœur donne. Calculé une fois par icône et par taille. Réglé au banc des icônes (`themes/paper/bench/icons.html`) : l'épaisseur, la longueur des facettes, les icônes qui se bouchent.
10. **Les graphiques** : dessinés par le thème (`PaperDrawing`). Les barres sont des bandes découpées aux coins inégaux, penchées tour à tour de ±1,5° ; la courbe, un trait de 3 dp, ses points de petits ronds découpés ; l'objectif en tirets bruns de 2 dp ; les couleurs que l'utilisateur donne aux séries gardées. La dernière barre au corail de la maquette n'est pas tenue : le dessin du cœur ne dit pas quelle barre est aujourd'hui.
11. **Le mode d'édition de la grille** : une case, un rectangle en tirets bruns sur le kraft ; la tuile choisie, la tuile soulevée de la ligne 8.
12. **Le registre sonore** : sa ligne de la carte des sons (`docs/design/theme-sounds.md`) : du papier épais, sans note, une attaque nette ; les ciseaux pour confirmer, une feuille posée sur le kraft pour ouvrir et fermer, une feuille froissée brève pour le refus ; pris à BigSoundBank. Le thème part muet.
13. **Les textes** : nom « Papier découpé » / « Cut paper » ; phrase « Des feuilles de couleur découpées aux ciseaux, posées de travers sur du kraft » / « Coloured sheets cut with scissors, laid askew on kraft paper ». Pas de nom de palette.

## Ajout au contrat

`ToolCardContainer` et `ZoneCardContainer` ne savent pas quelle tuile ils dessinent : `ToolGrid` les passe par leur rang, et un angle tiré du rang changerait celui de toutes les tuiles suivantes à la suppression d'une seule. Décidé le 2026-10-18 : un paramètre `seed: Long`, l'identifiant de l'outil ou de la zone. Pour chaque thème existant : le défaut, le rétro, le cosy, le suisse, l'encre et le métro l'ignorent, leur tuile restant telle qu'elle est.

## Ce qui reste ouvert

- La couverture de Bricolage Grotesque, un nom réservé, son italique : à mesurer sur la copie.
- Les palettes, au banc, dans les deux modes ; le banc des icônes.
- Rien n'a tourné sur un téléphone, `IconStrokes` compris (pas encore vu avec l'encre non plus).
- Les sons, à la planche d'écoute.
