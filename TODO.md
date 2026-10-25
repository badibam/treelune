# TODO

Travail ouvert, une ligne par item ; un item fait sort, le commit en est le registre. Les pistes brutes arrivent dans `NOTES.md` et se trient ici.

## Release F-Droid


- La relecture de la merge request d'inclusion, https://gitlab.com/fdroid/fdroiddata/-/merge_requests/53578 (0.9.2 poussée le 2026-10-24, avec le site en `WebSite` : sa CI à regarder) : une demande du relecteur se corrige dans `scripts/fdroid/app.treelune.yml`, se recopie dans le clone `~/fdroid-test/fdroiddata-fork` (branche `app.treelune`, clé SSH réglée) et se pousse. Publiée, le README cesse de dire « It is not on F-Droid yet », et treelune.app s'annonce (r/fossdroid, Lemmy, forum F-Droid, Show HN, LinuxFr, Mastodon).

## Site treelune.app

- Les captures et les vidéos françaises du site : `./run site-shots fr`, ADBKeyBoard installé, puis `fr_media` retiré de `layout.json` et `./run site-deploy` (en ligne en anglais depuis le 2026-10-23). `/update pull php-heberge-leger`, le module abonné sans point de départ.
- `www.treelune.app` : son entrée du Multisite OVH (racine `treelune/public_html`), puis le certificat régénéré ; le 2026-10-22, le certificat ne couvre que `treelune.app` et `www` pointe sur la page de parking.

## Bugs du téléphone

- Un thème ou un mode choisi puis enregistré, que l'écran des réglages d'interface remet à l'ancien en se fermant sans enregistrer (vu le 2026-10-21 à 13:31 et 13:51) : les appuis, l'écriture et la pile de navigation sont maintenant au journal, à lire au prochain cas.
- Dans config de champ de type refere'ce : 1) si j'edite celui de recoltes je voos la partie de restriction à tel outil 2) si je cree un aitre champ refere'ce, je ne vois pas cette partie-là — UI — Bug
- Navigation clunky pour automation : paramtre via 2 ecrans, distinction paramètre et éditer discutavle. Bref à revoir. Aussi dans tuile : " pas de fournisseur n'a pas ma place, pourqupi pas une marque d'icone plutot ? — UI — Amélioration

## Bugs

- `GoalRecurringTest` échoue le jour du passage à l'heure d'hiver (vu le 2026-10-25, déjà avant la partie A) : il vieillit une tentative de 24 h exactes, alors que ce jour-là en dure 25 ; vieillir d'un jour du calendrier de l'app, pas d'une durée.

## À passer sur le téléphone

Codé, jamais vu sur l'appareil.

- Les titres coupés entre les mots (`HeadingBreak`) en Cosy, Encre et lavis, Métro, Papier découpé et Plan d'architecte : les noms de tuiles de la démo, un mot trop long pour sa ligne d'une demi-tuile (« Observations ») coupé à ses syllabes.
- La marque « en attente » sans « ! » (Métro, Suisse, Encre et lavis, Papier découpé, Plan d'architecte, Cosy en accent) : vide, se lit-elle encore comme « à lire », à sa taille de 18 à 22 dp, et la bulle du Cosy se détache-t-elle de ses boutons ?
- Le budget du jour : réglé dans Limites IA, la ligne « Aujourd'hui » sous les limites et en tête des coûts d'une conversation, en couleur d'avertissement au-delà du seuil ; une conversation qui le dépasse et le dit, la routine qui se termine, les deux notifications une seule fois dans la journée ; le nom d'un fournisseur sans prix ; l'événement choisi sous le seuil, émis une fois avec sa notification.
- Les événements : « Émettre aussi l'événement » dans la config d'un outil qui prévient (un rappel de l'Agenda qui fait partir une Tâche), à la fin d'une routine ; « Prévenir » décoché à l'Agenda, la Liste, le Questionnaire et la Séquence, l'événement émis sans notification ; la carte sous l'identité de la zone et son éditeur, « Nouvel événement… » du navigateur, une Tâche qui émet « séance prête » avec une Fiche et une autre qui l'écoute et lit la Fiche reçue, une routine qui l'écoute et reçoit la Fiche jointe.
- Les déclencheurs : une Tâche et une routine qui partent sur une entrée écrite (créée, modifiée, supprimée, avec un filtre), le formulaire « Part aussi quand… » des réglages d'une Tâche et du bouton éclair d'une routine, l'étape qui lit « L'entrée écrite » ; la routine qui reçoit l'entrée jointe dans son message ; une Tâche qui écrit là où elle écoute, arrêtée après 50 exécutions avec sa notification.
- La recette, `docs/design/device-checks.md` : ce que seul le téléphone juge, une ligne vue en sort.
- Les fichiers : un audio enregistré par l'app du téléphone (`RECORD_SOUND_ACTION`, et ce qu'elle rend, peut-être du 3GP, que l'app refuse aujourd'hui), une vidéo filmée, un texte choisi, leurs tuiles et leurs lecteurs ; « Garder l'original » d'une photo, montrée droite, puis réduite en entrant dans une conversation ; une étape de Séquence qui suit l'audio d'une Fiche, jouée écran éteint par le service (type `specialUse`), sa vidéo à l'écran, la pause et « Retour » ; Joindre dans une conversation, ses cinq gestes ; un envoi par le connecteur via le nœud Tailscale, `curl -X PUT` d'un audio de quelques dizaines de Mo, que la bibliothèque Go recompilée écrit sur disque.
- Le Guide : les étapes 4 et 5 de Brancher une IA, cochées par un message envoyé puis par un pointeur vers un outil (pas vers une zone) ; « Marquer comme lu » d'un chapitre d'Approfondissement ; dans les nouveaux chapitres, la couleur d'un Graphique qui suit une Condition, la date dans le nom d'un champ gardé (« Humeur (avant le 21 octobre) »), un choix multiple repassé en simple qui retire les listes de plusieurs réponses.
- Les usages de `TextType.SUBTITLE` (56 fichiers), maintenant sous `TITLE` et sur `HEADING` dans chaque thème, le rétro compris : un SUBTITLE placé sous un HEADING passe en STRONG, comme les intertitres du Guide.
- Les API et les Tâches : une API Open-Meteo essayée, puis appelée par une Tâche à la main et par son planning, sa valeur écrite dans un Suivi ; une API différée attendue app fermée, et une exécution coupée par un redémarrage dite interrompue ; un audio rendu par une API, téléchargé dans le champ d'une Fiche ; la notification d'un échec ; l'IA qui écrit une Tâche sous un masque, puis la lance ; le cas de départ monté en deux Tâches, le script relu dans la Fiche entre elles.
- Les passes des thèmes sur le téléphone : la tournée de `docs/THEMES.md` (« The pass on the phone ») pour chaque thème, puis ce que sa spec garde à juger sur l'appareil dans « What stays open » (`docs/themes/<id>.md`), le niveau des sons dans `docs/themes/sounds.md`.

## Décidé, à coder

- Les événements (`docs/design/events.md`) : le « quand ? » des outils actifs (une entrée écrite, en place pour les Tâches et les routines), une condition devenue vraie (les événements, l'émission là où l'app prévient et le budget du jour, en place).
- Un filtre qui compare un champ à une variable ou une lecture (« kcal > objectif_calorique ») : le terme lu une fois à la référence du contexte (`TermReader`, `docs/BRICKS.md`), puis le filtre SQL comme aujourd'hui ; `EntryFilters.parse` et `ConditionPicker` le prennent.

## À concevoir

- Une Lecture qui rend plusieurs valeurs (les trois derniers retours, joints) pour l'entrée d'une API : une Lecture réduit aujourd'hui à une seule valeur.
- Un contrôle « qui dépend de ça ? » avant de supprimer une chose (une condition, une variable, un pointeur qui la visent), écrit une fois, appelé par le service qui supprime et par l'annulation qui supprime une ligne.
- Des outils montrés sur la tuile d'une zone de l'accueil (le poids du jour sur « Santé ») : leur résumé ou un raccourci selon la place de la tuile — lesquels, dans quel ordre, un outil montré à deux endroits.
- Des rythmes que la planification (`ScheduleConfig`, commune à tous les outils et aux routines) ne sait pas dire : « tous les N jours à partir d'une date », « une semaine sur deux », plusieurs jours d'un mois (« le 1er et le 15 »).
- Une réponse coupée par la limite de longueur (`max_tokens` chez Claude et DeepSeek, un `status` incomplet chez OpenAI, `finish_reason` « length » chez un autre serveur) : refusée, son texte jeté et ses tokens mis à 0, le coût affiché la compte gratuite alors qu'elle est facturée — que garder du texte, et où le montrer.

## Dette de code

- Les Tâches hors de l'historique des écritures (une config changée ne s'annule pas, une Tâche supprimée avec sa zone ne revient pas par l'annulation) et hors de l'export d'une partie ; une étape déplacée garde les numéros d'étape que lisent les suivantes, l'enregistrement refusant alors en le disant.
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
- Une étape défaite puis rétablie qui refait partir ce qui écoute son écriture (`docs/design/events.md`) : une seconde Fiche créée par la Tâche de régénération, une fois les événements en place ; si ça gêne, ce que défaire et rétablir devraient faire de ce qu'une écriture a fait partir.
- Un `DATA_REFUSED` légitime dans une routine : un seuil par routine.
- Un Graphique, un Objectif ou des Flashcards affichés qui relisent tout à chaque écriture dans n'importe quel outil, à chaque changement d'outil ou de variable (`DataChangeEvent.mayChangeReadings`) : une tuile qui rame quand on écrit ailleurs les fera trier par ce qu'ils lisent, comme une tuile de variable (`tools_read`).
- Un calcul lourd qui fige l'app : lire, calculer sans bloquer, écrire.
- Un import lancé par erreur qu'on ne peut pas arrêter (`LongOperation`).
- Des réponses de l'IA perdues pendant la génération : streaming et keep-alive TCP.
- La mémoire d'un import grandit avec son fichier (276 Mo pour 5 Mo) : lire et écrire par tranches.
- Un événement toute la journée de l'Agenda qui se montre sur le mauvais jour : il est un instant, le début du jour de l'app à sa date (migration 85), qu'un changement de l'heure où commence le jour ou du fuseau (un voyage, le fuseau suivant le téléphone) fait glisser d'un jour, rien ne le replaçant ; le replacer à chaque changement du calendrier, ou stocker une date.

## Idée sans besoin

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
