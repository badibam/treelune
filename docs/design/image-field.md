# Champ Image

Une ou plusieurs images dans un champ d'une entrée : les photos d'une fiche de plante, un schéma dans une note. Venu des bugs du téléphone. Conçu le 2026-10-19. Repose sur ce qu'ont posé les images d'un message (`docs/design/message-images.md`).

## Le champ

- Un type de champ Image, ajouté à n'importe quel outil comme les autres types.
- Sa valeur est toujours une liste ordonnée d'ids d'images, éventuellement d'une seule : une seule forme stockée, un seul code de saisie et d'affichage. Un réglage du champ fixe le maximum (illimité par défaut, 1 pour une seule photo) ; passer d'une à plusieurs ne convertit rien.
- Pas de légende par image : la fiche a ses autres champs.
- Un filtre sur ce champ ne connaît que `present` et `absent`.

## Saisie et affichage

- Saisie : un bouton d'ajout (appareil photo ou galerie du téléphone, comme dans le composeur), et pour chaque vignette, retirer et changer de place.
- Dans la fiche : les images en rangée de vignettes ; une vignette touchée s'ouvre en grand, on glisse d'une image à l'autre.
- En abrégé (tuile, cellule d'un tableau, carte d'une note) : la première image en vignette, le nombre des autres (`+3`).

## La préparation

- Celle des images d'un message : tournée d'après l'orientation de l'appareil, toutes ses métadonnées retirées (position GPS comprise), réduite si son grand côté dépasse 1 568 px, jamais agrandie.
- Le format est gardé quand il compte, pour les images d'un message aussi : une photo JPEG reste en JPEG (qualité 85) ; un PNG reste en PNG, sans perte, transparence gardée ; un autre format (WebP, HEIC) devient JPEG s'il est opaque, PNG s'il a de la transparence. Une image animée : sa première image seule, ce que l'app dit à l'ajout.

## Le stockage

- Une seule table d'images pour les messages et les entrées : chaque ligne a exactement un propriétaire, une session ou une entrée (deux clés étrangères, une seule remplie, suppression en cascade), et note son format. La table `attached_images` devient cette table par une migration.
- Le fichier : `files/attachments/<id>.jpg` ou `<id>.png`, l'extension d'après le format ; le reste comme aujourd'hui (écrit en `.part` puis renommé, le fichier avant la ligne, la ligne avant le fichier).
- À l'écriture d'une entrée, le service compare les ids du champ avant et après : une image retirée perd sa ligne.
- Une entrée disparaît par beaucoup de chemins (l'entrée, son outil, sa zone, un champ retiré par un changement de config) : sa ligne d'image part avec elle, son fichier au ménage du démarrage suivant. Pour une image d'entrée c'est un cas prévu, écrit au journal en INFO ; un fichier de message orphelin reste un WARN.
- La sauvegarde les emporte comme les images d'un message.
- L'historique des écritures (`docs/design/undo-history.md`, à coder) restaure une entrée supprimée : il faudra alors que le fichier de ses images survive autant que le pas qui la garde (30 jours), et que le ménage le sache.

## L'IA

- En lisant une entrée, l'IA voit la description du champ : le nombre d'images, leurs ids, leurs dimensions — jamais les images, dont chacune coûterait ~1 600 tokens à chaque tour.
- Une lecture exprès lui donne les images d'une entrée : elles entrent dans la conversation comme si l'utilisateur les avait jointes.
- Elle peut ranger dans un champ Image une image de la conversation, par son id : l'image est copiée, sa copie appartenant à l'entrée.
- Un modèle qui ne lit pas les images : la lecture est refusée en le disant, comme un envoi (`reads_images`).

## Plus tard

- La galerie comme affichage d'un outil : la grille des premières images de ses entrées.
- Les images d'une entrée lues par le connecteur MCP.
- L'import d'un CSV ne remplit pas un champ Image : sa colonne est écartée, en le disant.
- La face d'une carte de Flashcards montrant une image (`docs/design/flashcards.md`).

## À passer sur le téléphone

- Ajouter, ordonner, retirer des photos dans une fiche ; l'ouverture en grand et le glissé.
- Un PNG transparent et une capture d'écran : gardés nets, transparence comprise ; une photo de 4 000 px réduite, une image de 200 px gardée telle quelle.
- Une entrée supprimée, puis l'app redémarrée : son fichier parti, une ligne INFO au journal.
- L'IA qui regarde la photo d'une fiche, et qui range une photo envoyée dans la conversation.
- Une sauvegarde et sa restauration, avec des images d'entrées et de messages.
