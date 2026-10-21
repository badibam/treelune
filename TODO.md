# TODO

Travail ouvert, une ligne par item ; un item fait sort, le commit en est le registre. Les pistes brutes arrivent dans `NOTES.md` et se trient ici.

## Release F-Droid

- La fiche fastlane : le changelog de la 0.9.0, les captures d'écran faites sur le téléphone (elles servent aussi aux README, place marquée).
- La 0.9.0 taguée sur un arbre propre, ses notes de version validées, la mise à jour depuis la dernière version publiée passée sur le téléphone avec ses données.
- La recette F-Droid sur `v0.8.1` essayée dans le conteneur (`docs/design/fdroid-compliance.md`), son APK comparé à celui de GitHub (`apksigcopier compare`) — podman à installer.

## Bugs du téléphone


## Bugs

- Les dates sous l'axe d'un Graphique se chevauchent quand la place manque (« 31 août14 sept.28 sept. » au cran 3 du plan d'architecte, la tuile des récoltes) : le nombre de graduations à régler sur la largeur de leurs libellés.
- La démo nomme chaque repas par son moment, que sa valeur dit aussi : la tuile de Repas montre « Dîner Dîner ».
- La fin d'un événement de l'Agenda est son début plus sa durée en temps écoulé : un événement de plusieurs jours qui traverse un changement d'heure finit une heure avant ou après — l'ajouter sur le calendrier (`TimeResolver.after`, comme l'Objectif), la grille des jours et les séries lisant cette fin.
- Une session de routine restée en `AWAITING_SESSION_CLOSURE` à la fermeture de l'app est close au démarrage (`AIStateRepository.initializeFromDb`) sans passer par `RunEnd` : son résumé n'est pas gardé, et elle attend quel que soit son réglage « Me signaler la fin ».
- Le dialogue « la démo n'est pas installée » du Guide : le fermer sans choisir (retour, toucher à côté) saute les étapes de la démo, comme son bouton.
- Au cran 3 dans certains thèmes, les marques sur les icônes deviennent trop grandes proportionnellement aux icônes et les recouvrent presque entièrement. La proportion devrait rester la même.

## À passer sur le téléphone

Codé, jamais vu sur l'appareil.

- La recette, `docs/design/device-checks.md` : ce que seul le téléphone juge, une ligne vue en sort.
- Les passes des thèmes sur le téléphone : ce que la spec de chaque thème garde dans « Ce qui reste ouvert » (`docs/design/<id>-theme.md`), sa passe de `docs/THEMES.md` (« The pass on the phone ») en tête.

## Décidé, à coder

- Un filtre qui compare un champ à une variable ou une lecture (« kcal > objectif_calorique ») : le terme lu une fois à la référence du contexte (`TermReader`, `docs/BRICKS.md`), puis le filtre SQL comme aujourd'hui ; `EntryFilters.parse` et `ConditionPicker` le prennent.

## À concevoir

- Le Guide : les sujets d'Approfondissement suivants (formules et variables, conditions et filtres, champs et leur migration…), ce qui marque « fait » un chapitre sans étape à faire (ouvert, ou lu jusqu'au bout), et l'étape « demandez-lui son avis » de Brancher une IA, qui se lit au lieu d'attendre l'envoi : un message à l'IA ne passe pas par le dispatcher (`AIOrchestrator.sendMessage` le range lui-même).
- Un contrôle « qui dépend de ça ? » avant de supprimer une chose (une condition, une variable, un pointeur qui la visent), écrit une fois, appelé par le service qui supprime et par l'annulation qui supprime une ligne.
- Un modèle libre chez soi : un Ollama sur l'ordinateur, servi en https par `tailscale serve`, branché comme Autre serveur depuis le téléphone avec un modèle de la classe 30B, prix à 0 ; mesurer le temps d'une réponse (la lecture d'un prompt de ~29k tokens sur la machine). Passé, une page du Guide « Brancher un modèle libre » d'après ce qui a été fait.
- Les events du cœur : quel premier usage — un événement de l'Agenda qui commence, une entrée écrite, une condition devenue vraie (l'Alerte) ? L'automation directe attend la même brique : sans IA, elle exécute elle-même ses commandes ; son premier usage, le relevé, garde une valeur telle qu'elle était (une variable `imc` écrite chaque semaine dans un Suivi), là où une variable se relit à tout jour passé avec les données d'aujourd'hui.
- Des outils montrés sur la tuile d'une zone de l'accueil (le poids du jour sur « Santé ») : leur résumé ou un raccourci selon la place de la tuile — lesquels, dans quel ordre, un outil montré à deux endroits.
- Des rythmes que la planification (`ScheduleConfig`, commune à tous les outils et aux routines) ne sait pas dire : « tous les N jours à partir d'une date », « une semaine sur deux », plusieurs jours d'un mois (« le 1er et le 15 »).
- Une réponse coupée par la limite de longueur (`max_tokens` chez Claude et DeepSeek, un `status` incomplet chez OpenAI, `finish_reason` « length » chez un autre serveur) : refusée, son texte jeté et ses tokens mis à 0, le coût affiché la compte gratuite alors qu'elle est facturée — que garder du texte, et où le montrer.

## Dette de code

- Les mots de passe de `~/.config/treelune/signing.env` en clair : au trousseau (`secret-tool`).
- `CommandExecutor` lit la base directement, hors du coordinateur.
- `AutomationScheduler.getNextExecutionForAutomation()` appelé depuis l'écran, sans le service.
- Des conversions JSON refaites sur place au lieu de `JsonUtils` (`toValueMap`, `jsonToValue`, `toFieldConfigValue`…).
- `shared.xml` (~1 900 lignes) à découper.
- Les garanties sorties de la recette, `docs/design/device-check-tests.md` : chacune un test, ou sortie si un test la tient déjà.
- `RetroPaletteTest` et `CosyPaletteTest` à parcourir par pas de 15° de teinte, comme `SwissPaletteTest`.
- `TextType.SUBTITLE` sans rang dans le contrat des thèmes : lui en donner un, aligner les thèmes et ses 71 usages.
- `scripts/check_chat_space.py` ne voit que l'écran d'usage d'un outil, pas ceux qu'il ouvre dans son lieu (la saisie du Journal, une séance Flashcards, corrigées à la main le 2026-10-20) : les lui faire lire.
- Le journal ne garde que ~24 min d'usage (12 000 lignes le 2026-10-01, dont 3 782 « Service result » en VERBOSE) : les lignes utiles à la recette en sortent avant d'être lues ; réduire VERBOSE et DEBUG, « Service result » d'abord.
- Des écrans encore ouverts à l'intérieur d'un lieu, hors de la pile (`Place`, `docs/UI.md`) : la config d'un fournisseur d'IA, les pages d'un formulaire de config, les écrans internes d'un outil (fiche, historique), l'historique des conversations — à passer en lieux, au plus tard quand un tutoriel, une notification ou un pointeur doit les viser.

## Sagesse

Ce qui doit monter dans un module de sagesse (`/update push`).

- Le pilotage du téléphone par adb pour la recette (`scripts/phone_ui.sh` : captures réduites, toucher par le texte de l'écran, frappe mot par mot), dans `android`.

## Bloqué par autre chose

- Le correctif Tailscale de la route `cert/` (PR #21720) : fusionné, monter de version et retirer la correction de `scripts/vendor_go.py` ; refusé, noter pourquoi dans `docs/EXTERNAL_ACCESS.md`.
- L'import d'une incarnation depuis personae (la dernière section de la recette, l'identité de Stan Prokopenko) : quand personae exportera au format 2 du fichier d'identité (`docs/IDENTITY_FILE.md`), le format 1 n'étant plus lu.

## À surveiller

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
