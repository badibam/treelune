# Le thème plan d'architecte

Conçu le 2026-10-18. Un septième thème, à côté du défaut, du rétro, du cosy, du suisse, de l'encre et lavis et du métro : l'app comme un plan d'architecte, des traits fins sur un papier quadrillé, des cotes et des hachures. Choisi par l'utilisateur hors du classement de `docs/theme-tracks.json` (Papier découpé, premier, est fait en parallèle dans un autre worktree). Son identifiant est `blueprint`.

Maquette (écran d'une zone) : `docs/theme-tracks.html`, carte « Plan d'architecte ».

## Ce qui est décidé

Toutes les lignes du 2026-10-18, dans l'ordre de la liste de `docs/THEMES.md`.

1. **Le registre** : des traits fins tirés à la règle sur un papier quadrillé, des cotes, des hachures, un triangle de révision pour l'attente ; la main se voit au lettrage des titres et au trait qui déborde à chaque angle (ligne 7). Il n'est ni crayonné, ni tremblé (l'encre), ni noir et blanc (le suisse), ni en relief (le cosy).
   - **Le sombre est le tirage bleu** (traits blanc bleuté sur bleu, la maquette), **le clair est le tirage diazo** (traits bleus sur papier blanc cassé) : le thème garde son allure dans les deux modes, et le clair répond à « les traits blancs sur bleu fatiguent à la longue » (note de l'IA, `docs/theme-tracks.json`).
2. **Ce qu'il dessine lui-même** : tout, dialogues et sélecteurs de date et d'heure compris, en partant des pièces plates du suisse et du métro.
3. **La police** : deux, celles de la maquette, toutes deux sous SIL Open Font License 1.1, copiées dans `third_party/` depuis Google Fonts ; un script `scripts/make_blueprint_fonts.py` en tire les `res/font/`.
   - **Architects Daughter** (Kimberly Geswein, aucun nom réservé), le lettrage : titres, noms de tuile, libellés, en capitales espacées. Une seule graisse. Il lui manque (mesuré le 2026-10-18) les flèches, `≤ ≥ ≠ ≈`, `✓ ✗`, `‰`, le trait d'union conditionnel (U+00AD) et l'espace fine insécable (U+202F) : le script ajoute ces deux derniers, sa licence le permettant ; le reste va au repli d'Android, rare dans un titre.
   - **IBM Plex Mono** (nom réservé « Plex »), tout le reste : texte, valeurs, chiffres, comme les cotes d'un plan. 400 le texte, 500 les valeurs et le gras d'un texte lu, l'italique 400 pour l'italique. Elle couvre Windows-1252, les flèches et `≤ ≥ ≠ ≈ − ×` ; il lui manque l'espace fine et `✗` (mesuré le 2026-10-18), laissés au repli d'Android, le nom réservé interdisant de les ajouter.
4. **Les palettes** (OKLCH, réglées au banc ; contraste visé 4,5, tenu à chaque décalage de 15° par `BlueprintPaletteTest`) :
   - sombre, d'après la maquette : papier bleu `#1f4f8f`, encre `#eaf2ff` (7,2 mesuré), la secondaire à 80 % de l'encre (5,3 ; à 70 % elle tombe à 4,45) ; le quadrillage à 16 % (grand carré) et 6 % (petit) de l'encre ;
   - clair, le diazo : papier `#f7f5ef`, encre du bleu de la maquette (7,5 mesuré), le quadrillage de cette encre, pâle ;
   - l'alerte (marque d'attente, danger, carte d'interaction) : rouge en clair ; en sombre, un jaune de surligneur, le rouge ne se lisant pas sur le bleu (`#e2231a` 1,7, `#ffd166` 5,7, mesurés le 2026-10-18) ;
   - les tags et les couleurs d'icône sont des crayons de couleur : pastel en sombre, assez clairs pour tenir 4,5 sur le bleu, foncés en clair ;
   - la teinte tourne le bleu (papier, encre, quadrillage) ; états et tags gardés.
5. **La géométrie** : un pas de 8 dp ; `UI.Space` XS 4, S 8, M 16, L 24, XL 32, `EDGE` 16. Cases carrées, 12 dp entre deux colonnes et deux rangées, laissés vides par la grille, assez pour que les traits qui débordent de deux tuiles voisines ne se touchent pas. La cible du doigt à 48 dp. `drawingUnit` 1. Le quadrillage du fond, petit carré de 8 dp, grand de 40 dp, n'est pas aligné sur les tuiles : la case change de taille avec la largeur de l'écran.
6. **La taille** : la taille de texte d'Android suivie ; le cran (0 à 3) agrandit tout par ×1, ×1,12, ×1,25 et ×1,4, comme le suisse et le métro, sauf les espacements, la marge de bord et les écarts de la grille, fixes en dp.
7. **Les composants** :
   - le trait : 1,5 dp, tiré à la règle, sans tremblement ; à chaque angle d'un cadre, les deux côtés se croisent et dépassent un peu (le trait qui déborde d'une esquisse d'architecte), ce qui remplace les repères de coupe de la maquette ; la longueur du dépassement se règle au banc, 0 compris ;
   - cadres : la tuile, un contour au trait qui déborde, un fond plein du papier qui cache le quadrillage ; la carte (variable, routine, ligne d'une liste), pleine, contour simple ; le titre de groupe (`CardType.SECTION_HEADER`), en lettrage espacé suivi d'un trait d'axe en tirets (maquette) ; le dialogue et la fenêtre flottante, un cartouche (le cadre de titre d'un plan) à double contour, le titre dans sa case du haut ; la bulle de l'utilisateur, un aplat d'encre, celle de l'IA un contour ; la carte d'interaction, contour et hachures de la couleur d'alerte ;
   - boutons : principal, un aplat d'encre, libellé du papier ; secondaire, un contour ; tertiaire et neutre, le libellé seul ; danger, contour et libellé d'alerte ; désactivé, un contour en tirets, libellé secondaire ; activé, l'aplat ; en icône seule, l'icône sans cadre dans 48 dp ; le bouton flottant, un cercle de détail de 56 dp à double trait, l'icône au centre ;
   - saisie : le champ, un trait sous le texte, plus épais au focus, le libellé en lettrage au-dessus ; la sélection, le même champ et un chevron, son menu un panneau comme le dialogue ; la case, un carré, cochée d'une coche ; l'interrupteur, une pilule en contour et un rond à croix de centre ; oui / non, deux segments collés, le choisi en aplat ; le curseur, une ligne de cote à ses flèches, le même rond à croix sous le doigt ; la jauge, un rail en contour rempli de hachures à 45° ; la bande de la teinte en couleurs saturées ; les onglets, le choisi souligné d'un double trait ;
   - repères : le tag, un contour de sa couleur et son texte ; le séparateur, un trait fin en tirets ; la poignée, deux traits ; la marque d'attente, le triangle de révision et « ! », de la couleur d'alerte ; la marque de chrono, un cercle à croix de centre ; l'icône colorée, tracée de son crayon ; les textures, des motifs de plan (hachures à 45°, croisillons, points, briques), faibles, sur la tuile et sur tout le fond d'une page texturée ;
   - attente : le chargement, un compas qui trace un cercle puis l'efface ; l'IA qui réfléchit, une ligne de cote qui se trace entre ses deux repères ;
   - messages : le toast et la snackbar, un petit cartouche plein ;
   - date et heure : dans le cadre du dialogue, les jours sur le quadrillage, le choisi entouré d'un cercle tracé, aujourd'hui souligné ; l'heure, deux compteurs − et +, comme le suisse, sans cadran ;
   - en-tête de page et marque : le titre en lettrage dans un cadre, suivi de la case d'échelle « 1:1 » (maquette), ce texte dans les textes du thème ; la marque de l'app, le calque monochrome de l'icône à l'encre ;
   - la barre d'un texte sélectionné : un cartouche en contour, ses mots séparés de traits fins.
8. **Le toucher et le mouvement** : l'élément pressé se remplit de hachures légères ; une apparition, un fondu de 120 ms ; l'élément soulevé épaissit son trait et laisse sa place en tirets, décalée de 4 dp. Animations coupées dans Android : tout instantané.
9. **Les icônes** : Lucide (`LUCIDE`), retracé par le thème d'après ses tracés (`IconStrokes`, la brique du cœur faite pour l'encre) : un trait de 1,5 au lieu de 2, et chaque tracé ouvert prolongé à ses deux bouts dans sa direction, le trait qui déborde des cadres ; un tracé fermé ne déborde pas. Sa longueur se règle au même banc que celle des cadres, quelques icônes et une tuile, dans les deux modes : à 24 dp, le dépassement peut ne pas se voir ou se lire comme un rendu raté, et seul l'œil le dit.
10. **Les graphiques** : dessinés par le thème (`BlueprintDrawing`). Les barres en contour hachurées à 45° ; la courbe un trait fin, ses points de petites croix ; l'objectif en trait mixte (tiret-point, celui d'un axe) ; les axes en traits fins, les étiquettes en Plex Mono. La cote « ← 7 j → » de la maquette n'est pas tenue : le dessin du cœur ne dit pas la durée couverte.
11. **Le mode d'édition de la grille** : une case, un rectangle en tirets au trait qui déborde ; la tuile choisie, la tuile soulevée de la ligne 8.
12. **Le registre sonore** : celui de la carte des sons (`docs/design/theme-sounds.md`) : des outils de dessin, secs et fins ; le porte-mine qu'on clique (cocher, cran), la règle posée (confirmer), le tampon de révision (refuser) ; BigSoundBank, la règle cherchée sur Freesound. Le thème part muet.
13. **Les textes** : nom « Plan d'architecte » / « Blueprint » ; phrase « Comme un plan d'architecte : des traits fins sur un papier quadrillé, des cotes et des hachures » / « Like an architect's blueprint: fine lines on grid paper, dimensions and hatching ». Pas de nom de palette.

## Le seuil

Passé le 2026-10-18 : le banc (`./run themes`, « Plan d'architecte · palettes et trait ») vu et approuvé dans les deux modes, ses nombres dans `palettes.json` (l'encre secondaire du clair foncée à 0,5 pour tenir 4,5 à chaque décalage). Le trait : 1,5 dp, le débord des cadres 3 dp ; les icônes tracées à 1,5 sur 24, chaque bout ouvert et chaque angle vif (plus de 25°) prolongés de 1,2 sur 24 — pris d'office, à reprendre sur le téléphone.

## Ce qui reste ouvert

- Rien n'a tourné sur un téléphone.
