# Le site treelune.app

Le site fait découvrir l'app et donne envie de l'installer. Il montre ce qu'une capture seule ne dit pas : les pièces qui s'emboîtent, l'IA qui se sert des mêmes pièces que toi, la même app dans des univers très différents. Il ne recopie pas le Guide, qui vit dans l'app et que `scripts/check_guide.py` garde d'accord avec le code.

Public : les curieux un peu techniques (celui qui installe depuis F-Droid), et ceux qui veulent donner à une IA un accès à leur quotidien sans tout confier à un cloud.

## La page

1. Ouverture : sur un téléphone tenu droit, le logo et le nom en grand au-dessus du texte, celui de l'en-tête masqué tant que ce premier écran est là ; la phrase du README (« Chaque app de suivi fait une chose, à sa façon. Treelune te donne les pièces, et tu construis celle qu'il te faut. »), puis l'IA en deuxième idée ; la capture de l'accueil et le sélecteur de thème.
2. « Tes données restent chez toi » : un titre et trois lignes, chacune avec son icône, un écran entier sur un téléphone tenu droit : pas de compte ; l'IA que tu choisis, ta clé ou ton propre serveur ; avec l'accès externe, une IA extérieure lit et écrit dans l'app, la connexion arrivant sur le téléphone sans serveur de Treelune.
3. La zone Italien : ses outils et sa routine en liste ; le choix entoure sa tuile sur la capture de la zone et dit à quoi il sert ; toucher une tuile sur la capture la choisit aussi, et cette capture ne s'ouvre donc pas en plein écran. Elle n'entre pas dans les outils, que la partie 5 montre.
4. En action : des vidéos courtes, sans son, en boucle, une à la fois dans le même cadre de téléphone, choisies dans la liste. Pour commencer : l'IA crée un outil et on le valide, une saisie rapide, une vraie discussion, une routine (le « Bilan de la semaine » de Course), une IA extérieure qui lit et écrit dans l'app par l'accès externe, dans un cadre d'ordinateur en 16:9 réduit à la place du téléphone. Ensuite : créer un outil à la main, ranger une zone, lire un graphique, réviser des Flashcards, une alerte, annuler une écriture, importer un fichier.
5. Les outils : leur liste, la capture de l'outil choisi, sa phrase `tagline`, comme `scripts/make_readme.py`. Puis les autres pièces, en cartes de texte, chacune avec son icône : routines, variables, identités, conditions et alertes, pointeurs, accès externe, ambiances.
6. L'installation, un écran : le logo en signature, F-Droid et l'APK de GitHub, ce qu'il faut (Android 8.0 ou plus récent, un téléphone 64 bits), et la phrase du README sur une app encore jeune. Pas de version longue sur les données, que les trois lignes du haut disent. Puis le pied de page : licence, aucun traceur, code source, signaler un bug, l'autre langue.

Une capture ou une vidéo touchée, hors la zone Italien, s'affiche seule en plein écran, sans flèches vers la voisine ; un toucher ou Échap la referme.

Une seule page. Des pages pour l'ordinateur (brancher l'IA de l'app, ouvrir l'accès externe) viendront plus tard, si le besoin s'en montre.

Une partie, un écran : chacune occupe exactement l'écran sous l'en-tête, sur tout format, et le défilement se cale toujours sur le début d'une partie (en CSS, `scroll-snap-type: y mandatory`). L'ouverture en compte deux sur un téléphone tenu droit et sur un écran couché et bas. Les flèches haut et bas du clavier, et page précédente ou suivante, passent d'une partie à l'autre. Là où les autres pièces ne tiennent pas en un écran, leurs cartes forment une rangée qui défile de côté, chacune se calant au centre, la première et la dernière comprises, sous le même carrousel que les autres parties ; sur un téléphone tenu droit, elles sont assez hautes pour remplir l'écran. Le pied de page est au bas de l'écran d'installation. Une seule flèche ↓, au bas de l'ouverture, sur ordinateur comme sur téléphone : rien d'autre sur ce premier écran ne dit qu'il y a une suite ; dans les parties suivantes, elle prendrait la place du téléphone.

Les parties 1, 3, 4 et 5 partagent un motif, le geste du site : des choix à côté d'un téléphone, et choisir change ce que le téléphone montre. Les ambiances se choisissent dans une liste ; ailleurs, les choix sont des tuiles, plusieurs par ligne, chacune avec son icône Lucide sur une pastille de sa couleur d'étiquette, et le détail du choix s'affiche dessous dans une boîte de taille fixe, marquée de bleu nuit, pour que rien ne saute et que l'œil y aille. Les choix changent de côté d'une partie à l'autre. Sur un téléphone tenu droit, une telle partie tient en un écran, sans sa phrase d'introduction : son titre, le téléphone, un détail court de quatre lignes au plus, puis un carrousel : le choix en place entre deux flèches de place fixe, son rang (« 3 / 12 »), et un balayage sur le téléphone qui passe au suivant ; sur un écran couché et bas aussi, une telle partie tient en un écran, sans sa phrase d'introduction : son titre, puis les tuiles et le détail à côté d'un téléphone aussi haut que la place laissée.

Français et anglais, comme l'app, textes selon `docs/WRITING.md` : `/` en anglais, `/fr/` en français, chacune avec un lien vers l'autre et sa jumelle déclarée (`hreflang`), sans redirection selon la langue du navigateur. Les mots sont ceux de l'app : « l'IA de l'app » (celle qu'on règle dans l'app, qui exécute les routines et seule incarne une identité), « l'accès externe » et « une IA extérieure » (une IA qui tourne ailleurs, avec les exemples du Guide : « comme Claude, ChatGPT ou un modèle libre sur ton ordinateur avec LM Studio », jamais une seule marque).

## Les thèmes

- Sur ordinateur, l'ouverture occupe exactement l'écran sous l'en-tête, en trois colonnes (le texte, la capture, le sélecteur), et ses tailles suivent la hauteur autant que la largeur.
- Sur un téléphone tenu droit, elle tient en deux écrans exacts : le texte et les boutons, avec en bas une flèche ↓ sans texte, qui amène le second écran sous l'en-tête ; puis la capture aussi haute que la place le permet, et sous elle le même carrousel, le nom de l'ambiance dans sa police, avec l'interrupteur clair / sombre. Une page web ne peut pas imposer le portrait.
- Sur un écran couché et bas (moins de 500 px de haut), le premier écran met le logo et le nom en grand à gauche du texte ; le second tient en un écran : le sélecteur déplié à gauche, la capture aussi haute que la place le permet.
- Le sélecteur ne vit que dans l'ouverture : sur ordinateur, une colonne à droite de la capture, introduite par « L'app se décline en plusieurs ambiances : », chaque nom de thème écrit dans sa police, sans la phrase qui le décrit. Il change la capture de l'accueil et le fond du bandeau (couleur, texture).
- Sous la colonne, un interrupteur clair / sombre : il part du réglage que le navigateur transmet (`prefers-color-scheme`), change le mode de tout le site, et le navigateur s'en souvient. En sombre, le bandeau prend la palette sombre du thème et une capture de l'accueil prise en sombre : 18 captures d'accueil, les seules doublées.
- Chaque autre capture a son propre thème et son propre mode, clair ou sombre, fixés une fois, le thème nommé en petit dessous ; deux captures voisines n'ont jamais le même thème, et les deux modes se mélangent le long de la page. Une capture ne se prend qu'une fois : une capture par thème et par écran coûterait trop.
- Le cadre du site ne change jamais : le bleu nuit de l'icône pour les titres, les liens et les boutons, sur un blanc cassé à peine bleuté ; en sombre, le fond devient ce bleu nuit. L'arbre et le croissant en petit dans l'en-tête, en grand une fois en bas de page. Une police sobre ; les polices des thèmes restent dans le bandeau. Les captures sur un gris très clair, dans un cadre de téléphone fin et dessiné.

## Les captures

- Prises sur un téléphone qui n'a que la démo, jamais sur des données réelles.
- La liste des captures et des vidéos est déclarée une fois (`site/private/layout.json`) : son fichier, son thème, son mode. Une absente tient sa place en local par une capture F-Droid ou un écran dessiné, marqué comme tel, et `./run site-deploy` refuse d'envoyer tant qu'il en manque une.
- `./run site-shots` les prend sur le téléphone vidé, une par une : il dit quel écran mettre, prend la capture ou filme, et range le fichier.
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

## La maquette

`docs/design/website-mockup.html` : une page HTML statique, sans PHP, pour juger l'enchaînement, les proportions et le dosage des thèmes. Les captures F-Droid existantes et des cadres vides annotés tiennent la place des vraies captures.
