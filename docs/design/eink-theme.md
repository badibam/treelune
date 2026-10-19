# Le thème liseuse

Conçu le 2026-10-19. Un neuvième thème : l'app comme sur une liseuse. Noir et blanc pur, aucun gris, des trames à la place des couleurs, et rien qui bouge. Premier des registres en attente par la note de l'utilisateur (`docs/theme-tracks.json`), à égalité avec Carnet et Terminal, choisi parce qu'aucun thème ne couvre cet usage : un écran e-ink, le plein soleil, le contraste le plus fort.

Maquette (écran d'une zone) : `docs/theme-tracks.html`, carte « Monochrome / e-ink ».

## Ce qui est décidé

Toutes les lignes du 2026-10-19, dans l'ordre de la liste de `docs/THEMES.md`.

1. **Le registre** : un fond blanc, l'encre noire, des cadres de 2 dp sans arrondi ; les titres de groupe en capitales, soulignés d'un trait de 3 dp ; la bande du haut de page inversée, noire au texte blanc. Il n'est ni le suisse (aucun rouge, des tuiles cadrées), ni le rétro (aucun pixel visible), ni décoratif : ni ombre douce, ni transparence, ni dégradé, ni mouvement.
   - **Aucun gris** : tout ce que le cœur donne en couleur devient une trame (hachures, points, quadrillage) ou un trait. Un gris tramé se lit mal en petit, et un écran e-ink n'en rend pas d'autre.
2. **Ce qu'il dessine lui-même** : tout, dialogues et sélecteurs de date et d'heure compris ; rien de Material (ni onde, ni surface teintée, ni élévation).
3. **La police** : Atkinson Hyperlegible Next (Braille Institute, SIL Open Font License 1.1, sans nom réservé), copiée dans `third_party/atkinson-next/` (la police variable, son italique, `OFL.txt`, `VERSION`). Un script `scripts/make_atkinson_fonts.py` en tire quatre graisses fixes : 400 le texte, 600 les libellés, 700 les noms, 800 les titres et les chiffres ; de l'italique, 400 et 600, pour l'italique d'un texte lu. Mesuré le 2026-10-19 sur la version de Google Fonts : axe de graisse 200 à 800, Windows-1252 sauf `µ`, `≤ ≥ ≠ ≈ − ×`, les chiffres à largeur fixe (`tnum`) ; il lui manque l'espace fine insécable (U+202F), que le script ajoute (une espace vide d'un huitième de cadratin), les flèches `← → ↑ ↓` et `✓ ✗`, laissés au repli d'Android : seules les lignes de commande d'un message de l'IA les écrivent (`ChatComponents`).
4. **Les palettes** : claire, fond `#ffffff` et encre `#000000` ; sombre, l'inverse. Contraste 21 dans les deux modes.
   - L'encre secondaire est l'encre pleine : la hiérarchie passe par la graisse et la taille.
   - Les couleurs d'état sont à l'encre : un état se dit par son icône et son texte.
   - Chaque couleur de tag (et d'icône) est une trame : rouge, plein ; orange, hachures `/` ; jaune, points clairsemés ; vert, quadrillage ; turquoise, lignes horizontales ; bleu, hachures `\` ; violet, hachures croisées ; rose, points serrés ; gris, un carré vide.
   - La teinte n'a rien à tourner : le thème le déclare (ajout B) et Réglages › Interface cache sa bande.
   - `EinkPaletteTest` tient les contrastes des deux palettes, comme pour tout thème.
5. **La géométrie** : un pas de 8 dp ; `UI.Space` XS 4, S 8, M 16, L 24, XL 32, `EDGE` 16. Cases carrées, 8 dp entre deux colonnes et entre deux rangées, laissés vides par la grille. Le contenu d'une tuile commence 8 dp à l'intérieur de son cadre de 2 dp. La cible du doigt à 48 dp. `drawingUnit` 1.
6. **La taille** : la taille de texte d'Android suivie ; le cran (0 à 3) agrandit tout par ×1, ×1,12, ×1,25 et ×1,4, sauf les espacements, la marge de bord et les écarts de la grille, fixes en dp ; les traits arrondis au pixel entier. Le texte courant à 16 sp, un cran au-dessus des autres thèmes : c'est un thème de lecture.
7. **Les composants** :
   - cadres : la tuile et la carte, un cadre de 2 dp ; le dialogue et la fenêtre flottante, un cadre de 2 dp sur une ombre noire pleine décalée de 6 dp vers le bas et la droite, sans flou ; la bulle de l'utilisateur, un aplat d'encre au texte du fond, poussée à droite ; celle de l'IA, un cadre de 2 dp, à gauche ; la carte d'interaction, un double cadre (2 dp, un vide de 2 dp, 2 dp) ;
   - boutons : principal en aplat d'encre, libellé du fond en 600 ; secondaire en contour de 2 dp ; tertiaire et neutre, le libellé souligné ; danger en contour de 2 dp, libellé en 800 précédé de son icône ; désactivé, un contour en tirets, sans réaction au toucher ; activé en aplat d'encre ; en icône seule, l'icône sans cadre dans 48 dp ; le bouton flottant, un carré d'encre de 56 dp entouré d'un liseré du fond de 3 dp ;
   - saisie : le champ, un cadre de 2 dp, 3 dp au focus ; la sélection, le même cadre et un chevron, son menu un panneau comme le dialogue, l'option choisie inversée ; la case, un carré au contour de 2 dp, cochée un aplat d'encre et une coche du fond ; l'interrupteur, un rectangle au contour de 2 dp et un curseur carré, allumé plein d'encre ; oui / non et les onglets, des segments collés, le choisi inversé ; le curseur, un rail de 2 dp et un carré d'encre ; la jauge, un cadre de 2 dp rempli d'encre ; la bande de la teinte, cachée (ligne 4) ;
   - repères : le tag, un carré de 12 dp à la trame de sa couleur devant son texte, le tout dans un cadre de 1,5 dp ; le séparateur, un filet de 2 dp ; la poignée, deux barres d'encre ; la marque d'attente, un triangle d'encre au coin haut droit ; la marque de chrono, un petit carré plein qui s'allume et s'éteint chaque seconde, par saut ; l'icône colorée, l'icône à l'encre et un carré de 8 dp à sa trame au coin bas droit, la même place sans couleur ; les textures, à l'encre seule, sans la couleur de l'icône, la faible plus clairsemée que la forte ;
   - attente : le chargement et l'IA qui réfléchit, trois carrés qui se remplissent l'un après l'autre, par saut toutes les 400 ms ;
   - messages : le toast et la snackbar, un aplat d'encre au texte du fond entouré d'un liseré du fond ; l'action de la snackbar en capitales soulignées ;
   - date et heure : la date dans le cadre du dialogue, les jours sur 7 colonnes, le choisi inversé, aujourd'hui encadré de 2 dp ; l'heure, deux compteurs entre des boutons − et + au contour de 2 dp, sans cadran ;
   - en-tête de page et marque : la bande d'encre porte le titre et ses boutons au fond ; la marque de l'app, le calque monochrome de l'icône (`make_launcher_icon.py`) au fond sur la bande ;
   - la barre d'un texte sélectionné : un aplat d'encre, ses mots du fond séparés de filets du fond, le mot touché inversé.
8. **Le toucher et le mouvement** : rien ne bouge. L'élément pressé s'inverse d'un coup ; une apparition et une fermeture se font d'un coup, sans fondu ; l'élément soulevé prend l'ombre pleine des dialogues (4 dp) et se décale de 4 dp vers le haut et la gauche. Animations coupées dans Android : seuls les sauts des marques d'attente et du chrono s'arrêtent, sur leur état plein.
9. **Les icônes** : Lucide (`LUCIDE`), tel quel.
10. **Les graphiques** : dessinés par le thème (`EinkDrawing`). Une couleur de série devient sa trame pour un aplat (barre, part) et un motif de tirets pour un trait, sauf si le cœur en donne un ; un mélange (`Mix`) et une opacité, une trame de points régulière plus ou moins dense ; ce qui manque, hachuré ; les niveaux d'encre, fort un trait de 2 dp, moyen de 1 dp, faible en pointillé de 1 dp.
11. **Le mode d'édition de la grille** : une case, un carré en tirets de 1 dp ; la tuile choisie, la tuile soulevée de la ligne 8.
12. **Le registre sonore** : du papier, sec et mat : une page tournée, une feuille tapotée, un crayon qui coche ; l'opposé des clics du suisse. À chercher dans BigSoundBank et Freesound. Le thème part muet.
13. **Les textes** : nom « Liseuse » / « E-reader » ; phrase « Comme une liseuse : noir et blanc pur, des trames à la place des gris, rien qui bouge » / « Like an e-reader: pure black and white, patterns instead of greys, nothing that moves ». Pas de nom de palette.

## Les ajouts au contrat

Décidés avec le thème, pour les huit autres comme pour lui (`docs/THEMES.md`, « Ajouter une pièce au contrat »).

- **A. La pastille d'une couleur** : `UI.TagSwatch`, d'où l'on choisit la couleur d'un tag ou d'une icône, passe aujourd'hui par `StatusIndicator`, qui ne reçoit qu'une couleur : dans ce thème, les neuf pastilles seraient noires. Une pièce `TagSwatch(color: TagColor, size: Dp)` la donne au thème ; les huit autres gardent leur rond de la couleur (`getTagColor`), celui-ci dessine sa trame.
- **B. Un thème sans teinte** : un thème déclare s'il a une teinte à tourner (`hueShifts`), vrai pour les huit autres ; Réglages › Interface ne montre la bande que s'il en a une.

Ce que le cœur garde : le voile derrière un dialogue est celui d'Android, un noir translucide, commun à tous les thèmes. C'est le seul gris de l'écran.

## Ce qui reste ouvert

- Le seuil : le banc du thème, vu dans les deux modes.
- Rien n'a tourné sur un téléphone : la passe de `docs/THEMES.md`.
- Les sons, à la planche d'écoute.
