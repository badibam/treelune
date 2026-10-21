# Le site treelune.app

Le site fait découvrir l'app et donne envie de l'installer. Il montre ce qu'une capture seule ne dit pas : les pièces qui s'emboîtent, l'IA qui se sert des mêmes pièces que toi, la même app dans des univers très différents. Il ne recopie pas le Guide, qui vit dans l'app et que `scripts/check_guide.py` garde d'accord avec le code.

Public : les curieux un peu techniques (celui qui installe depuis F-Droid), et ceux qui veulent donner à une IA un accès à leur quotidien sans tout confier à un cloud.

## La page

1. Ouverture : la phrase du README (« Chaque app de suivi fait une chose, à sa façon. Treelune te donne les pièces, et tu construis celle qu'il te faut. »), puis l'IA en deuxième idée ; la capture de l'accueil et le sélecteur de thème.
2. Trois lignes sur les données : pas de compte ; l'IA que tu choisis, ta clé ou ton propre serveur ; l'accès depuis l'extérieur passe par ton réseau.
3. La carte annotée de la zone Italien : une capture, chaque élément touché ouvre son explication. Elle n'entre pas dans les outils, que la partie 5 montre.
4. En action : des vidéos courtes, sans son, en boucle, une à la fois dans le même cadre de téléphone, choisies par onglet. Pour commencer : l'IA crée un outil et on le valide, une saisie rapide, une vraie discussion, une routine (le « Bilan de la semaine » de Course), un client MCP sur l'ordinateur qui lit et écrit dans l'app. Ensuite : créer un outil à la main, ranger une zone, lire un graphique, réviser des Flashcards, une alerte, annuler une écriture, importer un fichier.
5. Les outils et les autres pièces : la liste part de la phrase `tagline` de chaque outil, comme `scripts/make_readme.py` ; une capture par outil. Les autres pièces : routines, variables, identités, conditions et alertes, pointeurs, connecteur MCP et accès externe, thèmes.
6. Les données en version longue, l'installation, et peut-être des pages à lire sur l'ordinateur (brancher une IA, l'accès externe, brancher un client MCP) : leur présence et leur contenu se décident sur la maquette.

Français et anglais, comme l'app, textes selon `docs/WRITING.md`.

## Les thèmes

- Le sélecteur ne vit que dans l'ouverture : il change la capture de l'accueil et le fond de ce bandeau (couleur, texture, nom et `tagline` du thème dans sa police).
- Chaque autre capture a son propre thème, fixé une fois, nommé en petit dessous ; deux captures voisines n'ont jamais le même. Une capture ne se prend qu'une fois : une capture par thème et par écran coûterait trop.
- Le cadre du site ne change jamais : le bleu nuit de l'icône pour les titres, les liens et les boutons, sur un blanc cassé à peine bleuté ; en sombre, le fond devient ce bleu nuit, selon le réglage que le navigateur transmet (`prefers-color-scheme`). L'arbre et le croissant en petit dans l'en-tête, en grand une fois en bas de page. Une police sobre ; les polices des thèmes restent dans le bandeau. Les captures sur un gris très clair, dans un cadre de téléphone fin et dessiné.

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

## La maquette

`docs/design/website-mockup.html` : une page HTML statique, sans PHP, pour juger l'enchaînement, les proportions et le dosage des thèmes. Les captures F-Droid existantes et des cadres vides annotés tiennent la place des vraies captures.
