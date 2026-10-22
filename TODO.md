# TODO

Travail ouvert, une ligne par item ; un item fait sort, le commit en est le registre. Les pistes brutes arrivent dans `NOTES.md` et se trient ici.

## Release F-Droid

- La relecture de la merge request d'inclusion, https://gitlab.com/fdroid/fdroiddata/-/merge_requests/53578 (CI verte sur la 0.9.1, le 2026-10-21) : une demande du relecteur se corrige dans `scripts/fdroid/app.treelune.yml`, se recopie dans le clone `~/fdroid-test/fdroiddata-fork` (branche `app.treelune`, clé SSH réglée) et se pousse. Publiée, le README cesse de dire « It is not on F-Droid yet ».

## Site treelune.app

- Les captures et les vidéos du site (48 manquent, `./run site-shots` sur le téléphone vidé, qui n'a que la démo), puis leur commit et le premier `./run site-deploy`, qui crée `treelune/` sur le compte (absent le 2026-10-22) ; `/update pull php-heberge-leger`, le module abonné sans point de départ.
- `www.treelune.app` : son entrée du Multisite OVH (racine `treelune/public_html`), puis le certificat régénéré ; le 2026-10-22, le certificat ne couvre que `treelune.app` et `www` pointe sur la page de parking.

## Bugs du téléphone

- Un thème ou un mode choisi puis enregistré, que l'écran des réglages d'interface remet à l'ancien en se fermant sans enregistrer (vu le 2026-10-21 à 13:31 et 13:51) : les appuis, l'écriture et la pile de navigation sont maintenant au journal, à lire au prochain cas.

## Bugs


## À passer sur le téléphone

Codé, jamais vu sur l'appareil.

- Les titres coupés entre les mots (`HeadingBreak`) en Cosy, Encre et lavis, Métro, Papier découpé et Plan d'architecte : les noms de tuiles de la démo, un mot trop long pour sa ligne d'une demi-tuile (« Observations ») coupé à ses syllabes.
- La recette, `docs/design/device-checks.md` : ce que seul le téléphone juge, une ligne vue en sort.
- Le Guide : les étapes 4 et 5 de Brancher une IA, cochées par un message envoyé puis par un pointeur vers un outil (pas vers une zone) ; « Marquer comme lu » d'un chapitre d'Approfondissement ; dans les nouveaux chapitres, la couleur d'un Graphique qui suit une Condition, la date dans le nom d'un champ gardé (« Humeur (avant le 21 octobre) »), un choix multiple repassé en simple qui retire les listes de plusieurs réponses.
- Les usages de `TextType.SUBTITLE` (56 fichiers), maintenant sous `TITLE` et sur `HEADING` dans chaque thème, le rétro compris : un SUBTITLE placé sous un HEADING passe en STRONG, comme les intertitres du Guide.
- Les passes des thèmes sur le téléphone : la tournée de `docs/THEMES.md` (« The pass on the phone ») pour chaque thème, puis ce que sa spec garde à juger sur l'appareil dans « What stays open » (`docs/themes/<id>.md`), le niveau des sons dans `docs/themes/sounds.md`.

## Décidé, à coder

- Un filtre qui compare un champ à une variable ou une lecture (« kcal > objectif_calorique ») : le terme lu une fois à la référence du contexte (`TermReader`, `docs/BRICKS.md`), puis le filtre SQL comme aujourd'hui ; `EntryFilters.parse` et `ConditionPicker` le prennent.

## À concevoir

- Un contrôle « qui dépend de ça ? » avant de supprimer une chose (une condition, une variable, un pointeur qui la visent), écrit une fois, appelé par le service qui supprime et par l'annulation qui supprime une ligne.
- Les events du cœur : quel premier usage — un événement de l'Agenda qui commence, une entrée écrite, une condition devenue vraie (l'Alerte) ? L'automation directe attend la même brique : sans IA, elle exécute elle-même ses commandes ; son premier usage, le relevé, garde une valeur telle qu'elle était (une variable `imc` écrite chaque semaine dans un Suivi), là où une variable se relit à tout jour passé avec les données d'aujourd'hui.
- Des outils montrés sur la tuile d'une zone de l'accueil (le poids du jour sur « Santé ») : leur résumé ou un raccourci selon la place de la tuile — lesquels, dans quel ordre, un outil montré à deux endroits.
- Des rythmes que la planification (`ScheduleConfig`, commune à tous les outils et aux routines) ne sait pas dire : « tous les N jours à partir d'une date », « une semaine sur deux », plusieurs jours d'un mois (« le 1er et le 15 »).
- Une réponse coupée par la limite de longueur (`max_tokens` chez Claude et DeepSeek, un `status` incomplet chez OpenAI, `finish_reason` « length » chez un autre serveur) : refusée, son texte jeté et ses tokens mis à 0, le coût affiché la compte gratuite alors qu'elle est facturée — que garder du texte, et où le montrer.

## Dette de code

- Des écrans encore ouverts à l'intérieur d'un lieu, hors de la pile (`Place`, `docs/UI.md`) : la config d'un fournisseur d'IA, les pages d'un formulaire de config, les écrans internes d'un outil (fiche, historique), l'historique des conversations — à passer en lieux, au plus tard quand un tutoriel, une notification ou un pointeur doit les viser.

## Sagesse

Ce qui doit monter dans un module de sagesse (`/update push`).

- Rien de personnel dans un fichier commité : une passe sur le téléphone de l'utilisateur lit ses vraies données, et en recopier un nom (un événement, une personne, une note) dans la recette ou le TODO le publie au prochain push. On décrit le cas (« deux événements toute la journée ») sans le nommer ; vu le 2026-10-21, deux noms de l'Agenda retirés de 348 commits non poussés. Pour le module universel.
- Le pilotage du téléphone par adb pour la recette (`scripts/phone_ui.sh` : captures réduites, toucher par le texte de l'écran, frappe mot par mot), dans `android`.

## Bloqué par autre chose

- Le correctif Tailscale de la route `cert/` (PR #21720) : fusionné, monter de version et retirer la correction de `scripts/vendor_go.py` ; refusé, noter pourquoi dans `docs/EXTERNAL_ACCESS.md`.
- L'import d'une incarnation depuis personae (la dernière section de la recette, l'identité de Stan Prokopenko) : quand personae exportera au format 2 du fichier d'identité (`docs/IDENTITY_FILE.md`), le format 1 n'étant plus lu.

## À surveiller

- Gemini essayé par le type « Autre serveur » (son point d'accès compatible OpenAI) : un type de fournisseur à lui seulement pour ce qui y manque, noté.
- Des durées à relever sur le téléphone : l'installation de la démo (~50 s le 2026-10-20, chaque outil planifié faisant tourner son planificateur, `CoreScheduler.runTool`) ; le calcul d'un paquet de Flashcards sur le lexique réel (« Flashcards: deck … read in N ms », de 26 à 152 ms sur la démo, à dire au-delà de 300 ms) ; un gros import (64 659 lignes, 3 min 19 s avant l'historique des écritures), la place prise par l'historique ensuite, et son annulation.
- Une zone lente à s'afficher, à l'ouverture comme au retour, qui relit tout (~1 s en debug le 2026-10-01, le graphique Calories de Panorama de 0,75 à 1,2 s).
- Le bouton de liste d'un texte long touché pendant une dictée : Gboard réécrit son texte encore souligné une seconde fois au curseur (mesuré le 2026-10-20), le bouton rendant le champ sans la place de ce texte (`composition` nulle) ; la garder, décalée par `MarkupEditing.apply` comme le curseur.
- Une copie automatique dont la rotation n'a rien fait (le 2026-10-20 à 21h21, juste après le choix du dossier) : la ligne « Auto backup: copies found … » dit maintenant ce qu'elle a trouvé et prévu.
- Un `DATA_REFUSED` légitime dans une routine : un seuil par routine.
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
- Une entrée précise comme cible d'un pointeur : `{"kind": "ENTRY", "id"}`, lue par `tool_data.get_single`, nommée par sa date puis son nom ou le début de son premier texte (qu'un type d'outil peut remplacer : « 26/09 08:12 — Pesée : 72,4 kg »), choisie dans la liste des entrées d'un outil, 30 par 30.
- Des valeurs de champs de l'utilisateur recopiées dans chaque occurrence d'une série d'Agenda.
- Un pointeur touché dans un message ouvre sa cible.
- Le sens « out » : l'app confie une tâche lourde à une IA extérieure et en reprend le résultat.

## Écarté

- Obliger le client MCP à lire le schéma avant d'écrire : chaque écriture est validée, un refus dit quoi corriger.
- Un seul calcul du prochain envoi : Messages stocke des envois que l'utilisateur voit, une routine n'a qu'une heure.
- Un Suivi « étiquette » : le type « choix » le couvre.
- Le nombre d'entrées en attente sur la tuile, au lieu du point : le résumé des tuiles donne déjà ce compte.
- Un fichier joint au message de départ d'une routine : un pointeur vers un outil qui garde le contenu fait mieux, relu à chaque exécution.
