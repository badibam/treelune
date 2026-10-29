# TODO

Travail ouvert, une ligne par item ; un item fait sort, le commit en est le registre. Les pistes brutes arrivent dans `NOTES.md` et se trient ici.

## Release F-Droid


- La relecture de la merge request d'inclusion, https://gitlab.com/fdroid/fdroiddata/-/merge_requests/53578 (0.9.2 poussée le 2026-10-24, avec le site en `WebSite` : sa CI à regarder) : une demande du relecteur se corrige dans `scripts/fdroid/app.treelune.yml`, se recopie dans le clone `~/fdroid-test/fdroiddata-fork` (branche `app.treelune`, clé SSH réglée) et se pousse. Publiée, le README cesse de dire « It is not on F-Droid yet », et treelune.app s'annonce (r/fossdroid, Lemmy, forum F-Droid, Show HN, LinuxFr, Mastodon).

## Site treelune.app

- Les captures et les vidéos françaises du site : `./run site-shots fr`, ADBKeyBoard installé, puis `fr_media` retiré de `layout.json` et `./run site-deploy` (en ligne en anglais depuis le 2026-10-23). `/update pull php-heberge-leger`, le module abonné sans point de départ.
- Les cartes Tâches, API et Événements du site, commitées avant leur release : `./run site-deploy` avec la version qui les porte, pas avant, la 0.9.2 téléchargée ne les ayant pas. Les captures françaises, si elles partent avant, partent d'un arbre sans ces cartes.
- `www.treelune.app` : son entrée du Multisite OVH (racine `treelune/public_html`), puis le certificat régénéré ; le 2026-10-22, le certificat ne couvre que `treelune.app` et `www` pointe sur la page de parking.

## Bugs

- `TASK_CONFIG` refuse de rendre la Tâche « Météo du matin » de la démo : « Le résultat n'a pas pu être mis dans la forme que tu lis : NUMERIC field 'latitude' declares no decimals » (vu au banc le 2026-10-28) ; un champ numérique d'une entrée d'API sans `decimals` doit se lire comme un nombre, ou se déclarer avec.
- Une date (`timestamp`) donnée par l'IA à une entrée de Fiches, qui n'en a pas : refusée par un message Java brut (« Value … of type java.lang.String cannot be converted to long », vu le 2026-10-28 dans une routine) au lieu d'une phrase qui dit que l'outil n'a pas de date.
- Une Tâche qui passe l'entrée reçue à une routine (`run_routine`, `data` ← `{"received": {}}`) ne lui donne que son id (vu le 2026-10-28) : la routine doit la relire. La joindre en entier, comme le fait un déclencheur qui lance une routine directement (`docs/CORE.md`, « Triggers »).
- `GoalRecurringTest` échoue le jour du passage à l'heure d'hiver (vu le 2026-10-25, déjà avant la partie A) : il vieillit une tentative de 24 h exactes, alors que ce jour-là en dure 25 ; vieillir d'un jour du calendrier de l'app, pas d'une durée.

## À passer sur le téléphone

Codé, jamais vu sur l'appareil.

- Les corrections de la passe du 2026-10-27 : une Tâche, « Part aussi quand… » puis « Une entrée est écrite », sans fermeture de l'app, et sa boucle arrêtée après 50 exécutions avec sa notification ; la config d'une Tâche à étapes quittée sans rien toucher, sans question ; le Guide, « Suivant » à l'étape 6 des Routines dans la conversation et à l'étape 5 sur l'exécution ouverte, la fin d'un chapitre sans vide sous son texte ; l'export, la démo absente et la phrase qui le dit, une zone cochée qui prend l'API de ses Tâches, les liens bloquants écrits sous leur compte ; un fichier qui porte la démo, laissée de côté à l'import ; la carte d'une exécution de routine partie sur une entrée, un événement, une condition ; la tuile d'une Tâche à déclencheur ; « Copie de la routine … » dans l'historique ; une routine coupée par le budget, sa notification qui le dit ; un outil, une Tâche, une routine changés de zone, le fil d'Ariane qui suit ; l'événement d'une étape « Émettre » choisi dans le navigateur ; les lignes du navigateur sous un chevron ; « Me signaler la fin » sans « Aucun », l'étoile des choix qui ont un défaut ; « Toutes les … semaines » vidé puis retapé ; « Élément 1 » ; un minuteur de 8 s noté 0:08 ; « Semaine d'entraînement » coupé à ses syllabes en Cosy ; une routine sans fournisseur sur sa tuile, son état en couleur d'avertissement ; la marque « en attente » du Plan d'architecte, le triangle à l'encre, vide.
- Une partie avec ses Tâches, sur une zone à soi : une API seule partagée, sa clé absente chez qui l'importe ; une API différente, gardée puis remplacée, sa clé restée.
- L'expiration d'un Questionnaire et d'une Séquence : « Expire » réglé « Au déclenchement suivant », deux invitations (ou deux séances prévues) et la première « Expiré(e) » dans l'historique ; « Au bout d'un délai » d'un jour, une invitation de la veille expirée au tic suivant ; une séance commencée, pas terminée, qui attend toujours.
- La marque « en attente » du Cosy en accent : la bulle se détache-t-elle de ses boutons ?
- Le budget du jour : le nom d'un fournisseur sans prix.
- Les événements : « Émettre aussi l'événement » dans la config d'un outil qui prévient (un rappel de l'Agenda qui fait partir une Tâche), à la fin d'une routine ; « Prévenir » décoché à l'Agenda, la Liste, le Questionnaire et la Séquence, l'événement émis sans notification ; une Tâche qui émet « séance prête » avec une Fiche et une autre qui lit la Fiche reçue, une routine qui l'écoute et reçoit la Fiche jointe.
- Les outils actifs « maintenant » : une Tâche qui écoute l'outil Yoga et invite au Questionnaire « Ressenti » (`invite`), une autre qui prévoit une séance de Séquence (`plan`), leur notification et leur événement ; l'Objectif sans planning, « Lancer maintenant » sur sa page, sa confirmation quand une tentative est en cours, l'ancienne passée « à valider » ; un Objectif à planning lancé la veille de son horaire, coupé par celui-ci ; la tuile « À lancer ».
- L'IA qui émet et qui lance une routine : l'accord demandé quand la zone est en « valider » ; une routine sous un masque refusée d'émettre une entrée d'une zone qu'elle ne lit pas.
- La condition devenue vraie : « inconnue » sur sa ligne ; partie au tic quand le temps la fait basculer (une moyenne sur 7 jours) ; son échec dit une fois, l'outil lu supprimé ; une routine qui l'écoute, sa ligne dans le message.
- Les déclencheurs : une entrée modifiée, supprimée, avec un filtre ; l'étape qui lit « L'entrée écrite ».
- La planification à cycle : rouverte le lendemain, le même choix coché ; « Tous les 4 jours » à une heure déjà passée, aujourd'hui absent des choix ; repassée à 1, plus de choix ; « le 1er et le 15 » d'un mensuel, son + et sa croix ; une série d'Agenda une semaine sur deux, ses rendez-vous.
- Les variantes de la Séance : le bip 3-2-1 avant la fin d'un Minuteur ; une Séance Son ou vidéo sur une Fiche à champ audio, la sélection filtrée et triée par deux clés, « Que jouer ? » ouvert sur « Jouée en dernier », l'audio écran éteint, sans « Prolonger » ; « Fait » sur une séance prévue, qui pose la même question ; une Séance à étapes passée en Minuteur, son historique intact ; « 2:45 sur 2:00 » dans la correction d'une étape prolongée.
- La Référence multiple : un champ « Personnes » d'un journal coché « Sélection multiple », trois fiches ajoutées par « + », une retirée par sa croix, la même refusée deux fois ; la liste sur la tuile, dans l'historique et dans le texte d'une entrée ; repassé en simple, l'avertissement qui compte les entrées à plusieurs fiches ; un filtre « contient l'une de » ; un graphique « dernière fois par personne » par un `flatten`, les noms des fiches sur l'axe, une fiche supprimée dite « supprimée ».
- Les champs d'un raccourci de Suivi : « Saumon » qui désigne la fiche du saumon dans un champ Référence, son toucher qui écrit l'entrée avec la fiche, sa fenêtre de saisie ouverte remplie ; le formulaire du raccourci qui propose les champs de l'outil, un champ ajouté proposé une fois enregistré ; une saisie libre gardée comme raccourci avec ses champs remplis ; un champ supprimé de l'outil, sa valeur partie du raccourci sans question.
- La recette, `docs/design/device-checks.md` : ce que seul le téléphone juge, une ligne vue en sort.
- Les fichiers : un audio enregistré par l'app du téléphone (`RECORD_SOUND_ACTION`, et ce qu'elle rend, peut-être du 3GP, que l'app refuse aujourd'hui), une vidéo filmée, un texte choisi, leurs tuiles et leurs lecteurs ; « Garder l'original » d'une photo, montrée droite, puis réduite en entrant dans une conversation ; une étape de Séquence qui suit l'audio d'une Fiche, jouée écran éteint par le service (type `specialUse`), sa vidéo à l'écran, la pause et « Retour » ; Joindre dans une conversation, ses cinq gestes ; un envoi par le connecteur via le nœud Tailscale, `curl -X PUT` d'un audio de quelques dizaines de Mo, que la bibliothèque Go recompilée écrit sur disque.
- Le Guide : les étapes 4 et 5 de Brancher une IA, cochées par un message envoyé puis par un pointeur vers un outil (pas vers une zone) ; dans les nouveaux chapitres, la couleur d'un Graphique qui suit une Condition, la date dans le nom d'un champ gardé (« Humeur (avant le 21 octobre) »), un choix multiple repassé en simple qui retire les listes de plusieurs réponses.
- Les usages de `TextType.SUBTITLE` (56 fichiers), maintenant sous `TITLE` et sur `HEADING` dans chaque thème, le rétro compris : un SUBTITLE placé sous un HEADING passe en STRONG, comme les intertitres du Guide.
- Les API et les Tâches : une API Open-Meteo essayée, puis appelée par une Tâche à la main et par son planning, sa valeur écrite dans un Suivi ; une API différée attendue app fermée, et une exécution coupée par un redémarrage dite interrompue ; un audio rendu par une API, téléchargé dans le champ d'une Fiche ; la notification d'un échec ; l'IA qui écrit une Tâche sous un masque, puis la lance ; le cas de départ monté en deux Tâches, le script relu dans la Fiche entre elles.
- Les API écrites par l'IA : une API déclarée par le connecteur, « Clé à saisir » sur sa carte et son appel refusé avant de partir ; la clé saisie, puis l'API modifiée par l'IA (une entrée ajoutée), la clé restée ; son adresse changée, la clé partie ; une Séance Minuteur créée par le connecteur avec « PT15M ».
- Les passes des thèmes sur le téléphone : la tournée de `docs/THEMES.md` (« The pass on the phone ») pour chaque thème, puis ce que sa spec garde à juger sur l'appareil dans « What stays open » (`docs/themes/<id>.md`), le niveau des sons dans `docs/themes/sounds.md`.

## Décidé, à coder

- Un rappel ponctuel, pour l'IA : un élément de Liste avec échéance (une chose à faire) ou un rendez-vous d'Agenda, jamais Messages, réservé à ce qui revient à un rythme. L'usage de Messages commence par « une fois ou à un rythme régulier » (le retirer, ajouter le rappel ponctuel à son « Pas fait pour »), et l'exemple de `CREATE_DATA` envoie un verre d'eau ponctuel par Messages (vu le 2026-10-29, l'IA a proposé Messages pour un rappel unique).
- Le regroupement d'une Lecture (« 2 par jour », « 30 min par jour »), la version complète : `docs/design/reading-groups.md`.
- Un filtre qui compare un champ à une variable ou une lecture (« kcal > objectif_calorique ») : le terme lu une fois à la référence du contexte (`TermReader`, `docs/BRICKS.md`), puis le filtre SQL comme aujourd'hui ; `EntryFilters.parse` et `ConditionPicker` le prennent.

## À concevoir

- Des montages types pour l'IA (une discussion qui revient, une alerte sur un seuil, un bilan fait seul…) : un catalogue lu à la demande par une commande, le texte de départ n'en gardant qu'une ligne par montage (besoin → nom) ; chaque montage dit le besoin, les pièces et leur liaison, ses noms (commandes, réglages, types d'étape) vérifiés par un script comme le Guide ; la matière tirée du montage réel de l'utilisateur, l'effet mesuré au banc par des scénarios de conception.

- Les zones d'une partie importée : chacune arrive seule sur sa ligne, en colonne 0 (`Grid.arrive`, comme toute tuile nouvelle), deux zones côte à côte dans le fichier empilées avec un vide à droite ; garder leur disposition entre elles.
- Une Lecture qui rend plusieurs valeurs (les trois derniers retours, joints) pour l'entrée d'une API : une Lecture réduit aujourd'hui à une seule valeur.
- Un contrôle « qui dépend de ça ? » avant de supprimer une chose (une condition, une variable, un pointeur qui la visent, la condition d'un déclencheur qui lit un outil ou une variable), écrit une fois, appelé par le service qui supprime et par l'annulation qui supprime une ligne ; le modèle existe pour les événements (`EventUses` : le refus nomme ce qui écoute ou émet). Une fiche désignée par un raccourci de Suivi (`SettingNode.FieldValues`) en est aussi : supprimée, le raccourci la garde, lue « supprimée ».
- Des outils montrés sur la tuile d'une zone de l'accueil (le poids du jour sur « Santé ») : leur résumé ou un raccourci selon la place de la tuile — lesquels, dans quel ordre, un outil montré à deux endroits.
- Une réponse coupée par la limite de longueur (`max_tokens` chez Claude et DeepSeek, un `status` incomplet chez OpenAI, `finish_reason` « length » chez un autre serveur) : refusée, son texte jeté et ses tokens mis à 0, le coût affiché la compte gratuite alors qu'elle est facturée — que garder du texte, et où le montrer.
- Un type d'outil Schéma : des briques et des flèches entre elles (une organisation, une boucle, qui nourrit qui), écrit par la personne ou par l'IA. Une Note n'y suffit pas : l'app n'a aucune police à chasse fixe, un schéma en texte s'y décale. Sa forme (un texte qui se dessine, Mermaid ou autre, ou des briques posées à la main), et s'il peut viser des zones, des outils ou des groupes pour y mener.

## Dette de code

- Une étape de Tâche déplacée garde les numéros d'étape que lisent les suivantes, l'enregistrement refusant alors en le disant.
- Des écrans encore ouverts à l'intérieur d'un lieu, hors de la pile (`Place`, `docs/UI.md`) : la config d'un fournisseur d'IA, les pages d'un formulaire de config, les écrans internes d'un outil (fiche, historique), l'historique des conversations — à passer en lieux, au plus tard quand un tutoriel, une notification ou un pointeur doit les viser.

## Sagesse

Ce qui doit monter dans un module de sagesse (`/update push`).

- Rien de personnel dans un fichier commité : une passe sur le téléphone de l'utilisateur lit ses vraies données, et en recopier un nom (un événement, une personne, une note) dans la recette ou le TODO le publie au prochain push. On décrit le cas (« deux événements toute la journée ») sans le nommer ; vu le 2026-10-21, deux noms de l'Agenda retirés de 348 commits non poussés. Pour le module universel.
- Le pilotage du téléphone par adb pour la recette (`scripts/phone_ui.sh` : captures réduites, toucher par le texte de l'écran, frappe mot par mot), dans `android`.

## Bloqué par autre chose

- Le correctif Tailscale de la route `cert/` (PR #21720) : fusionné, monter de version et retirer la correction de `scripts/vendor_go.py` ; refusé, noter pourquoi dans `docs/EXTERNAL_ACCESS.md`.
- L'import d'une incarnation depuis personae (la dernière section de la recette, l'identité de Stan Prokopenko) : quand personae exportera au format 2 du fichier d'identité (`docs/IDENTITY_FILE.md`), le format 1 n'étant plus lu.

## À surveiller

- Un appel d'API long fait par le planificateur pendant le réveil de l'alarme (50 s au plus), sans service au premier plan comme celui des routines : une Tâche planifiée dont l'appel dépasse peut être coupée par Android, son exécution dite interrompue au réveil suivant.
- L'adresse Tailscale lente à revenir à l'ouverture de l'accès (5 min 14 s le 2026-10-25 au matin, après la nuit) : son nom public reste chez les serveurs DNS de `ts.net` après la fermeture, au moins 10 h 31 mesurées ce jour-là (nœud hors ligne de 06:59 à 17:30, réouverture instantanée après 8 min comme après 10 h 31), puis Tailscale le retire et le republie en minutes à l'ouverture suivante. Le seuil exact n'est pas mesuré. Piste si ça gêne : réveiller brièvement le nœud, accès fermé, avant ce seuil (WorkManager, 15 min au plus souvent) — à condition qu'une connexion brève remette le compteur à zéro chez Tailscale, non vérifié. Écartés : le relais et le PC en porte d'entrée (trop compliqués pour l'utilisateur), l'accès ouvert en permanence. Tailscale perd parfois le nom d'un nœud en ligne de lui-même (tickets #21746, #21852) : hors de notre main.
- Gemini essayé par le type « Autre serveur » (son point d'accès compatible OpenAI) : un type de fournisseur à lui seulement pour ce qui y manque, noté.
- Des durées à relever sur le téléphone : l'installation de la démo (~50 s le 2026-10-20, chaque outil planifié faisant tourner son planificateur, `CoreScheduler.runTool`) ; le calcul d'un paquet de Flashcards sur le lexique réel (« Flashcards: deck … read in N ms », de 26 à 152 ms sur la démo, à dire au-delà de 300 ms) ; un gros import (64 659 lignes, 3 min 19 s avant l'historique des écritures), la place prise par l'historique ensuite, et son annulation.
- Une zone lente à s'afficher, à l'ouverture comme au retour, qui relit tout (~1 s en debug le 2026-10-01, le graphique Calories de Panorama de 0,75 à 1,2 s).
- Le bouton de liste d'un texte long touché pendant une dictée : Gboard réécrit son texte encore souligné une seconde fois au curseur (mesuré le 2026-10-20), le bouton rendant le champ sans la place de ce texte (`composition` nulle) ; la garder, décalée par `MarkupEditing.apply` comme le curseur.
- Une copie automatique dont la rotation n'a rien fait (le 2026-10-20 à 21h21, juste après le choix du dossier) : la ligne « Auto backup: copies found … » dit maintenant ce qu'elle a trouvé et prévu.
- Une étape défaite puis rétablie qui refait partir ce qui écoute son écriture (`docs/CORE.md`, « Triggers ») : une seconde Fiche créée par la Tâche de régénération, une fois les événements en place ; si ça gêne, ce que défaire et rétablir devraient faire de ce qu'une écriture a fait partir.
- Un `DATA_REFUSED` légitime dans une routine : un seuil par routine.
- Un Graphique, un Objectif ou des Flashcards affichés qui relisent tout à chaque écriture dans n'importe quel outil, à chaque changement d'outil ou de variable (`DataChangeEvent.mayChangeReadings`) : une tuile qui rame quand on écrit ailleurs les fera trier par ce qu'ils lisent, comme une tuile de variable (`tools_read`).
- Un calcul lourd qui fige l'app : lire, calculer sans bloquer, écrire.
- Un import lancé par erreur qu'on ne peut pas arrêter (`LongOperation`).
- Des réponses de l'IA perdues pendant la génération : streaming et keep-alive TCP.
- La mémoire d'un import grandit avec son fichier (276 Mo pour 5 Mo) : lire et écrire par tranches.
- Un événement toute la journée de l'Agenda qui se montre sur le mauvais jour : il est un instant, le début du jour de l'app à sa date (migration 85), qu'un changement de l'heure où commence le jour ou du fuseau (un voyage, le fuseau suivant le téléphone) fait glisser d'un jour, rien ne le replaçant ; le replacer à chaque changement du calendrier, ou stocker une date.

## Idée sans besoin

- L'occurrence d'un questionnaire ouvert par sa notification ou sa tuile, dite quand plusieurs attendent (deux en retard, on croit répondre à la dernière) : sur l'écran de saisie (« Prévu il y a 2 jours · 1 sur 2 à remplir »), ou un écran intermédiaire, la liste « À remplir », quand plusieurs attendent.
- Le champ Image : la galerie comme affichage d'un outil (la grille des premières images de ses entrées), les images d'une entrée lues par le connecteur MCP.
- Le relais de l'accès externe (le projet `relay`) servi à d'autres : il n'a pas de licence aujourd'hui.
- Flashcards : les statistiques (cartes acquises, prévision des jours à venir), la liste des cartes et leur état, la réponse tapée, le jugement d'une réponse libre par l'IA, une face en image, les révisions d'une ligne disparue de la source.
- Le texte d'une image d'un message (un ticket) transcrit par un modèle qui lit les images, réglé à part, pour un modèle qui ne les lit pas : modifiable dans son bloc, il part à la place de l'image.
- Une tentative passée d'un Objectif, enregistrée à la main ou importée (refusée aujourd'hui) : la réponse d'Objectif poserait son état (la période où tombe la date, une tentative déjà là, le verdict, la définition à copier).
- Un champ obligatoire dans `extra` : une saisie rapide ouvrirait alors la fenêtre d'édition préremplie.
- Les quasi-doublons d'un CHOICE ouvert (« Travail » / « travail » / « boulot ») : aujourd'hui chacun devient une option.
- Corriger une proposition de l'IA avant de la valider, avec le composant de saisie : l'IA apprendrait ce qui a été changé.
- Des modèles d'outils prédéfinis.
- Lire un long texte du connecteur MCP par section (le sommaire des titres `##`, puis une section par son titre), en plus des tranches.
- Un modèle sous la classe 30B, ou dans l'app sur le téléphone (la campagne 1 du banc : `docs/AI.md`) : un prompt par portée de tâche plutôt que par taille de modèle, une session bornée à une zone ou un outil, le forçage par schéma seul, des exemples choisis selon la demande, un aiguillage vers un mini-prompt, l'appel de fonctions natif, des modèles mélangés par session, un fine-tuning léger en dernier recours — aucune piste mesurée.
- Des valeurs de champs de l'utilisateur recopiées dans chaque occurrence d'une série d'Agenda.
- Retenir un fichier sans le posséder (un historique qui le nomme, le fichier gardé tant qu'il le nomme) : une table `file_retentions` (`file_id`, `reason`, ce qui retient), le propriétaire d'un fichier qui ne l'emporte plus en partant (`ON DELETE SET NULL`), ce qui n'est plus que retenu listé par raison avec sa taille et libéré ; premier usage, une séance passée qui rejoue le média de chaque étape. Aujourd'hui un fichier n'a qu'un propriétaire, et une séance note d'où venait chaque média en texte.
- Un pointeur touché dans un message ouvre sa cible.

## Écarté

- Obliger le client MCP à lire le schéma avant d'écrire : chaque écriture est validée, un refus dit quoi corriger.
- Un seul calcul du prochain envoi : Messages stocke des envois que l'utilisateur voit, une routine n'a qu'une heure.
- Un Suivi « étiquette » : le type « choix » le couvre.
- Le nombre d'entrées en attente sur la tuile, au lieu du point : le résumé des tuiles donne déjà ce compte.
- Un fichier joint au message de départ d'une routine : un pointeur vers un outil qui garde le contenu fait mieux, relu à chaque exécution.
- Un bouton qui émet un événement : une Tâche qui émet, lancée à la main, est ce bouton ; des valeurs à saisir au moment d'appuyer passent par une entrée écrite, qu'une Tâche écoute.
- Un déclencheur « à une date des données » (3 jours avant une péremption, un anniversaire) : une condition sur un comptage (« entrées dont la date approche > 0 ») le couvre. Ses limites, à rouvrir sur un cas qui gêne : un second élément ne repart pas tant que le premier attend, ce qui revient chaque année ne se dit pas en filtre, la précision est celle du tic.
- Un délai minimum entre deux départs d'un déclencheur : il ralentit une boucle sans l'arrêter, et les départs arrivés pendant, jetés ou regroupés, font plus de cas tordus qu'ils n'en règlent ; le plafond par chaîne arrête les boucles, le budget du jour les routines chères.
- Un « quand ? » qui n'écoute que des événements, écritures et conditions transformées en événements ailleurs : ça doublerait le « quand ? » ; une Tâche d'une étape « émettre » fait la règle « ceci arrive → émettre ».
- Une condition pour les rendez-vous de l'Agenda (« prochain Yoga ≤ dans 10 min ») : en retard du tic, aveugle au second de deux rendez-vous proches ; le rappel de l'Agenda émet.
- Interdire qu'une même chose repasse dans une chaîne : bloque le va-et-vient voulu entre deux routines et une Tâche qui aiguille.
