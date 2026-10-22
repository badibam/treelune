# Le site treelune.app

Le site fait découvrir l'app et donne envie de l'installer. Il montre ce qu'une capture seule ne dit pas : les pièces qui s'emboîtent, l'IA qui se sert des mêmes pièces que toi, la même app dans des univers très différents. Il ne recopie pas le Guide, qui vit dans l'app et que `scripts/check_guide.py` garde d'accord avec le code.

Public : les curieux un peu techniques (celui qui installe depuis F-Droid), et ceux qui veulent donner à une IA un accès à leur quotidien sans tout confier à un cloud.

## La page

1. Ouverture : sur un téléphone tenu droit, le logo et le nom en grand au-dessus du texte, celui de l'en-tête masqué tant que ce premier écran est là ; la phrase du README (« Chaque app de suivi fait une chose, à sa façon. Treelune te donne les pièces, et tu construis celle qu'il te faut. »), puis l'IA en deuxième idée ; la capture de l'accueil et le sélecteur de thème.
2. « Tes données restent chez toi » : un titre et trois lignes, chacune avec son icône, un écran entier sur un téléphone tenu droit : pas de compte ; l'IA que tu choisis, ta clé ou ton propre serveur ; une IA extérieure (claude.ai en premier) entre dans l'app le temps que l'accès est ouvert, la connexion arrivant sur le téléphone sans serveur de Treelune.
3. La zone Italien : ses outils et sa routine en liste ; le choix entoure sa tuile sur la capture de la zone et dit à quoi il sert. Elle n'entre pas dans les outils, que la partie 5 montre.
4. En action : des vidéos courtes, sans son, en boucle, une à la fois dans le même cadre de téléphone, choisies dans la liste. Pour commencer : l'IA crée un outil et on le valide, une saisie rapide, une vraie discussion, une routine (le « Bilan de la semaine » de Course), Claude sur claude.ai qui lit et écrit dans l'app par l'accès externe. Ensuite : créer un outil à la main, ranger une zone, lire un graphique, réviser des Flashcards, une alerte, annuler une écriture, importer un fichier.
5. Les outils : leur liste, la capture de l'outil choisi, sa phrase `tagline`, comme `scripts/make_readme.py`. Puis les autres pièces, en cartes de texte, chacune avec son icône : routines, variables, identités, conditions et alertes, pointeurs, accès externe, ambiances.
6. Les données en version longue, l'installation, et peut-être des pages à lire sur l'ordinateur (brancher une IA, l'accès externe, brancher un client MCP) : leur présence et leur contenu se décident sur la maquette.

Les parties 1, 3, 4 et 5 partagent un motif, le geste du site : des choix à côté d'un téléphone, et choisir change ce que le téléphone montre. Les ambiances se choisissent dans une liste ; ailleurs, les choix sont des tuiles, plusieurs par ligne, chacune avec son icône Lucide sur une pastille de sa couleur d'étiquette, et le détail du choix s'affiche dessous dans une boîte de taille fixe, marquée de bleu nuit, pour que rien ne saute et que l'œil y aille. Les choix changent de côté d'une partie à l'autre. Sur un téléphone tenu droit, une telle partie tient en un écran, sans sa phrase d'introduction : son titre, le téléphone, un détail court de quatre lignes au plus, puis un carrousel : le choix en place entre deux flèches de place fixe, son rang (« 3 / 12 »), et un balayage sur le téléphone qui passe au suivant ; sur un écran couché et bas, la liste reste à l'écran à côté d'un téléphone calculé sur la largeur.

Français et anglais, comme l'app, textes selon `docs/WRITING.md`.

## Les thèmes

- Sur ordinateur, l'ouverture occupe exactement l'écran sous l'en-tête, en trois colonnes (le texte, la capture, le sélecteur), et ses tailles suivent la hauteur autant que la largeur.
- Sur un téléphone tenu droit, elle tient en deux écrans exacts : le texte et les boutons, avec en bas une flèche ↓ sans texte, qui amène le second écran sous l'en-tête ; puis la capture aussi haute que la place le permet, et sous elle le même carrousel, le nom de l'ambiance dans sa police, avec l'interrupteur clair / sombre. Une page web ne peut pas imposer le portrait.
- Sur un écran couché et bas (moins de 500 px de haut), le même premier écran ; le second dépasse en hauteur : le sélecteur déplié à gauche, qui reste à l'écran au défilement, la capture large d'un tiers de l'écran.
- Le sélecteur ne vit que dans l'ouverture : sur ordinateur, une colonne à droite de la capture, introduite par « L'app se décline en plusieurs ambiances : », chaque nom de thème écrit dans sa police, sans la phrase qui le décrit. Il change la capture de l'accueil et le fond du bandeau (couleur, texture).
- Sous la colonne, un interrupteur clair / sombre : il part du réglage que le navigateur transmet (`prefers-color-scheme`), change le mode de tout le site, et le navigateur s'en souvient. En sombre, le bandeau prend la palette sombre du thème et une capture de l'accueil prise en sombre : 18 captures d'accueil, les seules doublées.
- Chaque autre capture a son propre thème et son propre mode, clair ou sombre, fixés une fois, le thème nommé en petit dessous ; deux captures voisines n'ont jamais le même thème, et les deux modes se mélangent le long de la page. Une capture ne se prend qu'une fois : une capture par thème et par écran coûterait trop.
- Le cadre du site ne change jamais : le bleu nuit de l'icône pour les titres, les liens et les boutons, sur un blanc cassé à peine bleuté ; en sombre, le fond devient ce bleu nuit. L'arbre et le croissant en petit dans l'en-tête, en grand une fois en bas de page. Une police sobre ; les polices des thèmes restent dans le bandeau. Les captures sur un gris très clair, dans un cadre de téléphone fin et dessiné.

## Les captures

- Prises sur un téléphone qui n'a que la démo, jamais sur des données réelles.
- Le pilotage par adb de `scripts/phone_ui.sh` les prend, et `adb shell screenrecord` filme les vidéos dont les gestes se rejouent ; la vraie discussion avec l'IA se filme une fois.

## Rester d'accord avec la démo

- Un script de construction extrait sous `site/` ce que les pages lisent : la liste des outils, les identifiants de la démo (`app/src/main/assets/demo/`), les noms des thèmes.
- Il garde une empreinte des fichiers de la démo tels qu'ils étaient quand le site a été écrit ; `./run release-tag` signale qu'elle ne correspond plus, et le site se met à jour à la main.

## Où il vit

- Dans le dépôt, sous `site/`, en PHP sans framework selon le module de sagesse `php-heberge-leger`, cas du site statique pur : un seul dossier servi.
- Hébergé chez OVH, sur le compte `coursmp` (`cluster100`), en Multisite : racine `treelune/public_html`, hors de `www/` que le domaine technique sert en entier. Envoi par `./run deploy` (SFTP par `curl`, mot de passe dans le trousseau KDE), lancé à la main après une version.
- Aucun traceur, aucune statistique de visite, aucune ressource chargée ailleurs : polices et vidéos servies par le site.

## Ouvert

- `www.treelune.app` : son entrée du Multisite, puis le certificat régénéré pour couvrir les deux noms.
- Les pages pour l'ordinateur, sur la maquette.
- « Depuis une autre IA » dans En action : la place du téléphone exploitée autrement qu'avec une capture d'écran de navigateur en 16:9, à préciser.
- Sur ordinateur, ce qui mène de l'ouverture à la suite, le bouton « Voir comment ça marche » retiré.

## La maquette

`docs/design/website-mockup.html` : une page HTML statique, sans PHP, pour juger l'enchaînement, les proportions et le dosage des thèmes. Les captures F-Droid existantes et des cadres vides annotés tiennent la place des vraies captures.
