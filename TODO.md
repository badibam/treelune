# TODO

Travail ouvert. Un item disparaît d'ici dès qu'il est fait — le commit en est le registre. Une ligne par item : ce qu'il faut faire, et ce qui le débloque. Les deux premières sections suivent l'ordre de travail : la release, puis ce qui la suit ; les autres suivent l'état de l'item : les bugs, ce qu'attend le téléphone, ce qu'attend du code, ce qu'attend une décision, ce qu'attend un déclencheur. Les pistes brutes arrivent dans `NOTES.md` et se trient ici.

## Release F-Droid

- La recette F-Droid (`scripts/fdroid/app.treelune.yml`) essayée dans l'image de build de F-Droid : la démarche, les deux commandes et les inconnues sont dans `docs/design/fdroid-compliance.md`, « La recette, essayée chez nous ».
- La reproductibilité : construire la release deux fois depuis deux clones du tag, à deux chemins différents, et comparer les APK.
- La fiche fastlane (`docs/design/fdroid-compliance.md`) : en-US `title`, `short_description`, `full_description`, `changelogs/<versionCode>.txt`, `images/icon.png`, `images/featureGraphic.png` ; les captures d'écran, à faire sur le téléphone, servent aussi au README (`fastlane/metadata/android/en-US/images/phoneScreenshots/`, leur place marquée dans `README.md` et `README.fr.md`), l'icône de l'app en tête.
- Les anti-features : `NonFreeNet` (Anthropic, OpenAI, DeepSeek, le serveur de connexion de Tailscale) ; à trancher pour la liste de prix LiteLLM que `ModelPriceManager` lit sur GitHub.
- La version soumise, taguée sur un arbre propre, ses notes de version écrites et validées avant de publier ; la mise à jour depuis la 0.6.0 installée passée sur le téléphone avec ses données (une base neuve ne passe aucune migration, une mise à jour les passe toutes, nouvelles comprises).
- La signature : si F-Droid signe l'app de sa propre clé, un APK installé depuis GitHub ne peut plus se mettre à jour par F-Droid ; un build reproductible lui fait publier l'APK signé de la nôtre (`Binaries`, `AllowedAPKSigningKeys` dans la recette).

À régler avant de soumettre :

- Ce que la campagne 1 a trouvé dans l'app (`docs/design/bench-campaign-1.md`, « Ce qui ne vient pas des modèles ») : `completed` absent du schéma de réponse forcé, qu'une automation demande pourtant ; une lecture de variable au début d'un jour qui vaut 0, que le prompt ne signale pas ; une écriture dans un outil inexistant qui reçoit une erreur de `SchemaNotation` ; « Failed to parse custom field » brut de `ToolInstanceService`.
- L'importeur propose en champ nouveau une colonne qui nomme un champ que `tool_data` n'écrit pas (`state.*`, `created_at`) : il doit demander au service ce qui s'écrit, et écarter le reste en le disant.
- Les chapitres de Prise en main du Guide (`docs/design/user-journey.md`, « Prise en main ») : des ébauches, à écrire ensemble en séance dédiée, avec les premiers sujets d'Approfondissement, partie cachée tant qu'elle est vide — Organiser couvre aussi les groupes d'outils d'une zone ; puis élaguer la spec, ses garanties devenant des tests.

## Juste après la release

- Une réponse coupée par la limite de longueur (`OpenAICompatibleExtensions.kt`, `finish_reason` « length ») garde son texte et ses tokens, aujourd'hui jetés, ce qui montre gratuit un appel facturé ; à trancher : le texte montré dans la session avec l'erreur, ou seulement gardé en base. Puis rejouer les scénarios coupés du banc en supprimant leur `verdict.json`.
- L'export des entrées d'un outil (`docs/design/export.md`) : le dossier choisi dans les réglages, l'opération `exports.csv`, « Exporter… » sur l'écran d'un outil, l'action `EXPORT_DATA` de l'IA.
- Copier une note dans le presse-papier.
- Copier un message de l'IA ne copie que son texte (`ChatComponents`, `preText`) : ses commandes manquent.
- Les boutons du haut d'un outil changent de hauteur selon le nombre de lignes de son titre.
- Le titre de l'écran de config d'un outil, écrit par chaque écran : le rendre commun, le nom de l'outil et son type.
- La phrase de date et d'heure envoyée à l'IA (`ai_prompt_current_datetime`) ne dit pas qu'elle est écrite par le système, et non par l'utilisateur.
- Les boutons copier / coller sur un texte sélectionné sont ceux d'Android, hors du thème (la barre d'outils du texte, `TextToolbar` en Compose).

## Bugs

- Réglages › Interface : un thème choisi, vu, enregistré, et l'ancien revient ; une seconde fois, ça tient (2026-10-09, pas reproduit, peut-être dans la release). Au prochain cas, lire dans le journal les lignes « Theme applied » : laquelle a remis l'ancien (STORED, PREVIEW, LEFT_UNSAVED).
- Une automation IA lancée par l'alarme du planificateur (`SchedulerAlarm`), app fermée : Android ne garde l'app éveillée que le temps du tick (50 s au plus), un tour d'IA peut être coupé. Un service au premier plan, du type de `McpAccessService`, allumé quand un tour commence et éteint quand l'IA n'a plus rien à faire ou attend une validation, sa notification nommant l'automation ; lire d'abord où la machine à états dit « tour en cours » et « en attente ».

### Bloc 1 — la place à l'écran : des règles pour toute l'app

Décider une règle par problème, puis passer tous les écrans, dans le cas le moins favorable (thème rétro, taille d'interface la plus grande, mode une colonne).

- Ce qui ne passe pas à la ligne quand la place manque : les couleurs d'icône restent à 5 par ligne ; les deux boutons du Questionnaire en une colonne (« Remplir maintenant », « Avec l'IA ») restent côte à côte. Une façon unique de faire, et les autres cas analogues.
- Les marges en mode une colonne : trop grandes sur les bords, en rétro au moins. Les fenêtres flottantes ont déjà pris presque toute la largeur (`FloatingDialog`, une case de marge en rétro, 2026-10-11) : à juger avec le reste.
- La place du texte face aux poignées : dans une liste qu'on réordonne (Notes), la colonne de la poignée prend trop ; dans les accès d'une automation, poignée d'un côté et suppression de l'autre, il reste très peu pour le texte.
- « il y a… » face au titre : dans une tuile de Journal, il prend toute la place et coupe le titre ; dans le Suivi en rétro, il est coupé alors qu'il a la place de deux lignes. Options à discuter.

### Bloc 2 — le Guide et les textes

- Guide, Automatiser : une étape pour écrire le message de l'automation (une automation au message vide ne s'enregistre plus et ne se lance plus).
- Guide, Automatiser 1/4 : « avec ses pièces jointes » est faux, on ne peut pas joindre de fichier au message d'une automation.
- Guide : présenter et bien distinguer la discussion récurrente, une automation dont on se sert comme départ de conversation (bouton bulle), de l'automation qu'on lance (bouton lecture).
- Guide, Brancher une IA 3/5 : à reprendre ; et y dire, ou ailleurs, Fournisseur › Limites pour économiser.
- Guide, clavier ouvert : la barre du bas reste, et l'éditeur est écrasé ; la replier tant que le clavier est là.

### Petites décisions

- Accueil : le titre de l'app dans l'en-tête, entre les boutons.
- Rétro : le point de notification (l'icône du Guide, par exemple) ne se voit pas au coin d'un cadre ; le grossir ?
- Les accès d'une automation, sur l'écran de son message : la liste ne s'étend pas, le bas se tasse dès qu'on en ajoute un et le bouton s'écrase ; et tout ce texte sur le même écran est de trop. Avis à donner : un bouton qui ouvre un écran à part.

## À passer sur le téléphone

Codé, jamais vu sur l'appareil. Chaque item renvoie à ses lignes de `docs/design/device-checks.md` (la recette, classée par écran) ; passé, sa spec s'élague, ses garanties devenant des tests.

- Les outils manquants (sélection du cœur, RÉFÉRENCE, lecture du cœur, variables, Données structurées et import, Objectif, Questionnaire, Graphique) : puis élaguer de `docs/design/missing-tools.md` ce qui est codé.
- Le thème rétro (`docs/design/retro-theme.md`), vu le 2026-10-01 (écran d'une zone, palette pâle, marges reprises) : le parcourir écran par écran, les graduations en pointillé de ses graphiques et le damier du mode d'édition de la grille compris, puis ce que la spec garde dans « Ce qui reste ouvert ».
- Le thème cosy (`docs/design/cosy-theme.md`, maquettes liées dedans) : l'essayer, puis ce que la spec garde dans « Ce qui reste ouvert », le banc des palettes d'abord.
- Ce qui n'est pas passé le 2026-10-02 : la conversion du réglage d'apparence en v58 (une famille devient une teinte), les titres de section du thème par défaut, la tuile sans icône centrée.
- Les couleurs des icônes (`docs/design/icon-colors.md`).
- Les images dans un message (`docs/design/message-images.md`) : la ligne « Images ».
- L'outil Séance (`docs/design/sequence-tool.md`) : sa section.
- Le formulaire de config en pages (`docs/design/settings-pages.md`) : les boutons sur la page racine seule, les marques d'un problème, la phrase d'une condition (Réglages et affichage).
- L'état de départ d'une entrée (`docs/design/entry-start-state.md`) : migration 65, un message demandé à l'IA, une tentative refusée.
- Les outils toujours envoyés (`docs/design/always-send.md`) : migration 64, Réglages.
- Le rapport de bug (`docs/design/bug-report.md`) : démarrage, Réglages.
- La validation et les accès de l'IA (`docs/design/validation.md`) : les trois niveaux et les cases de session (migration 69), `UPDATE_APP_CONFIG`, le masque des automations (migration 70), la validation du connecteur par notification et son masque par client (migration 71) — section Validation.
- Le serveur MCP (`docs/design/mcp-server.md`, `core/mcp`, migration 60) ; le relais (projet `relay`) est en ligne sur `https://relay.badibam.fr` depuis le 2026-10-05, Claude s'y est branché comme connecteur depuis le téléphone ; l'hébergement tient une requête 120 s, `RelayLoop.WAIT_SECONDS` = 20 reste en dessous : la section « Accès externe ».
- L'accès externe par Tailscale Funnel (`docs/design/funnel-access.md`) : sa section (migration 68), puis élaguer aussi `funnel-poc.md`.
- Ce qui a changé le 2026-10-11, vu en debug seulement ou pas du tout : la migration 73 (le compteur devient numérique) sur la release et ses données, l'avertissement d'arrière-plan sur une installation neuve, les automations et leurs accès, une fenêtre flottante longue dans le cosy (Mise à jour et démarrage, Automations, Réglages et affichage).
- Les réglages de réflexion de chaque fournisseur, en envoyant une requête : Claude (Sonnet 5.5 sans réflexion par `between_tools`, Opus 5.5 sans « Sans réflexion », Haiku 4.5 sans effort), OpenAI (un modèle à `none`, un modèle qu'aucun fait ne couvre), DeepSeek (effort obligatoire, sans réflexion) ; le changement de modèle qui vide les deux réglages, l'enregistrement d'une combinaison refusée.

## Conçu, à coder

Une spec existe dans `docs/design/`.

- Les conditions typées à l'écriture (`docs/design/typed-conditions.md`) : le type de chaque côté d'une condition connu sans lire de données, les constantes de l'IA traduites à l'entrée, la vérification au service, et le refus d'un changement dont dépend une condition ailleurs.
- L'historique des écritures et leur annulation (`docs/design/undo-history.md`) : un pas par opération demandée de l'extérieur, la ligne entière avant et après, écrite par une seule classe que garde un script, 30 jours, le service `history` ouvert à l'IA, son écran.
- Modèles libres (`docs/design/local-models.md`) : le banc est en place et rodé ; la campagne 1 a tourné le 2026-10-04 (190 scénarios en 5 h 15, 1,35 $ comptés hors réponses coupées, tableau dans `bench-results/2026-10-04_1326/summary.md`), ses échecs relus dans `docs/design/bench-campaign-1.md` ; les niveaux de prompt se décident après les corrections de l'app qu'elle a trouvées (Bugs) et une campagne rejouée.

## À concevoir

Une idée sans spec : elle demande une décision avant du code.

### IA et automations

- Le connecteur MCP : sans session, rien n'oblige le client à lire le schéma d'un outil avant d'y écrire (la consigne seule, `ai_chunk_validation_strategy`) ; l'imposer demande de retenir, par connexion, les schémas envoyés.
- Le prochain envoi se calcule de deux façons : à la volée pour une automation (`AutomationScheduler`), stocké en base et remis à jour à chaque envoi pour Messages — en garder une.

### Outils et champs

- Des modèles d'outils prédéfinis et paramétrables (« Série de questions » suivie d'un « Avis de l'IA »…).
- Un type de Suivi « étiquette », qui ne note qu'une étiquette.

### Interface

- Une texture au choix pour l'affichage de chaque outil.
- Les onglets de Messages, à remplacer par un classement par thèmes.

### Events du cœur

- Un système d'events du cœur, intégré au planificateur, avec une section par zone : un lien entre outils qui passe par le cœur (par une automation, par exemple). Déclencheurs d'une exécution : `SCHEDULE`, `MANUAL`, `EVENT` — revoir ceux des automations (ce qui est un déclencheur y est traité de façon inégale). Un event peut naître du planning d'un outil, et nomme l'outil qui l'a déclenché pour qu'on y aille en un geste ; des events système, sur le même modèle. Les constantes sont à penser avec. L'Alerte et l'automation directe le croisent.

### Documentation

- Traduire la doc en anglais.

## Dette de code

Ce qui marche, mais contredit l'architecture ou une règle du projet.

- `CommandExecutor` lit la base directement (`AppDatabase.getDatabase`, les messages d'une session), hors du coordinateur.
- `AutomationScheduler.getNextExecutionForAutomation()` est appelé depuis l'écran (`AutomationCard`, `AutomationScreen`), sans passer par le service.
- `CommandExecutor` remplace un nom ou un `schema_id` manquant par `"unknown"` : un repli que la règle du projet interdit.
- `ToolConfigActions` (`core/ui/components`), utilisé par l'IA, porte un nom qui ne dit pas ce qu'il fait.
- Des conversions JSON → valeurs refaites sur place au lieu de `JsonUtils` : au moins `toValueMap` (`MessagesScreen`), `jsonToValue` (`StateSavers`), `toFieldConfigValue` (`FieldDefinition`) — compter le reste avant de les ramener.
- `SessionCostDisplay` formate coûts et tokens par ses propres fonctions, à côté de `AIFormatUtils`, qui fait la même chose pour les exécutions.
- `shared.xml` (~1 900 lignes) à découper en plusieurs fichiers.

## Bugs du téléphone

- Guide : aussi tour d'horozon des outils pour donner aperçu des usages et conseil d'utiliser les outils pour ce pour quoi ils ont ete prévus
- Saivegarde auto des données avec roulement 1h 1j 1s 1m
- Dans config de champ de type refere'ce : clarifier dans l'ui les 2 réglages... C'est confusant là. Par ailleirs est-ce clair dans le prompt ?
- Il faudrait piur sélectionner/copier davantage de textes dans l'app. Soit via fonction comme chat ia, via clic, soit via selection android, selon les xas. On passe en revue.
- Fenêtre dédition de aitomation, dès que trop grande, les boutons du bas disparaissent
- Tri par valeur de champ au choix dans liste notes etc
- Guide : remarque : tous les tutos de la 2eme série doivent mentionner tous les réglages, critère : 1 bouton = 1 mention. C'est à concevoir proprement
- Appui long sir message système : ne copie pas / pas tout le message ?
- Limite trop courte pour sequence dans seance, on va repasser sur tiutes les limites de texte au sein de l'app
- Quand clavier ouvert : il faidraot toujours avoir au moins la taille du clavier en + en bas de page, de façon à pouvoir affocher jusqu au bas de page.
- Pb fudeau horaire : pas d'option qui convienne par exemple pour le portugal
- Réglage de thinking et effort en cours de conv

## En attente d'un déclencheur

- Un appel à l'IA relancé sans raison : les effets d'une phase (`AIEventProcessor.handleStateChange`, l'appel en `CALLING_AI`) se rejouent à chaque nouvel état, pas seulement à chaque changement de phase ; un événement qui ne change qu'un champ pendant l'appel en relance un, la requête déjà partie pouvant être facturée. Deux réponses identiques à une même demande le 2026-10-09 ; les gardes de phase (`AIStateMachine.expectedPhases`) en limitent l'effet — à mesurer dans le journal (« ignored: it belongs to ») au prochain doublon.
- Faire monter dans le module de sagesse `android` le pilotage du téléphone par adb pour la recette (captures réduites, toucher par le texte de l'écran, frappe mot par mot ; `tmp/phone_ui.sh`, gardé sur cette machine seulement) — au prochain `/update push android` fait pour autre chose.
- Le correctif de la route `cert/` sous Android est proposé à Tailscale (ticket #21719, PR #21720) : fusionné, monter à la version qui le porte, retirer la correction de `scripts/vendor_go.py` et son contrôle de `./run test` ; refusé, la garder et noter pourquoi dans `docs/design/funnel-poc.md`.
- Des écrans encore ouverts à l'intérieur d'un lieu, hors de la pile des lieux (`Place`, `docs/UI.md`) : la config d'un fournisseur d'IA, les pages d'un formulaire de config (`SettingsForm`), les écrans internes d'un outil (fiche, historique), l'historique des conversations — à passer en lieux quand un tutoriel, une notification ou un pointeur doit les viser.
- Revenir à une zone la relit (ses outils, ses variables) : ce que l'écran garde par `remember` ne survit pas à son passage sous un autre lieu — si le rechargement se voit.
- Un pointeur touché dans un message (l'étiquette d'une zone ou d'un outil, aujourd'hui un simple texte) ouvre sa cible, empilée au-dessus de la conversation pour que le retour y ramène ; de même pour une cible nommée par l'IA dans sa réponse — la pile des lieux sait l'ouvrir (`Navigator.open`, `docs/UI.md`) — quand on le voudra.
- Le sens « out » : l'app qui confie une tâche lourde à une IA extérieure (Claude Code, par exemple) et en reprend le résultat, comme une commande différée — une fois le serveur MCP en place, par lequel la tâche lira et écrira, et vérifié ce que chaque service permet pour lancer une tâche par une requête.
- Le graphique Calories de Panorama (74 lignes) se lit en 0,75 s à l'ouverture de l'outil, 0,9 à 1,2 s à celle de la zone (2026-10-01, version debug, une fiche lue une fois par calcul) ; sa mise en page prend 15 à 19 ms. Restent 181 appels aux services : par jour, deux lectures des repas (60) et la lecture de l'outil qui donne leurs champs (`ToolFields.filterable`, `tools.get`) à chacune — si ça gêne encore.
- L'ouverture d'une zone bloque encore l'écran ~1 s la première fois après le démarrage (version debug, 2026-10-01 : 6 graphiques, 18 tuiles) — à décomposer si ça gêne.
- Choisir une entrée précise comme cible du pointeur dans son sélecteur (conçu dans `docs/design/pointer.md` : étiquette par type d'outil, liste, relecture de l'entrée) — si le besoin apparaît : un pointeur peut désigner une entrée (le Questionnaire en pose un), mais seule l'app le fait. La cible APP attend que l'IA sache lire les réglages de l'app.
- Les exécutions d'une automation décrites comme des entrées (champs déclarés, schéma généré, dates en ISO par ce schéma) — le jour où l'IA les lit ; ce ne sont pas des réglages.
- Défilement saccadé à la réouverture d'une longue session CHAT — à mesurer (recompositions de la liste, défilements automatiques successifs).
- Une lecture du connecteur MCP au-delà du seuil de données de la conversation est coupée (`AppMcpBackend.call`, `withinChars`), sans qu'on puisse l'autoriser en entier comme dans la conversation : une demande d'accord par notification, comme pour les écritures (`McpApprovals`) — si la coupure gêne à l'usage.
- Seuil de taille des données par automation — quand une automation légitime montre un `DATA_REFUSED` dans son historique d'exécution ; la valeur globale deviendra la valeur par défaut.
- Marquer les lignes que les migrations 13→14 et 14→15 n'ont pas su transformer, et le dire une fois au démarrage (jamais les supprimer) — si des lignes `MIGRATION` apparaissent en « Error » dans l'écran des journaux.
- Un filtre qui compare un champ à une variable ou à une lecture (« kcal > objectif_calorique ») : `EntryFilters.parse` le refuse, `ConditionPicker` n'offre qu'une valeur écrite — quand un pointeur ou une variable en a besoin ; le terme se lit alors une fois à la référence du contexte (`TermReader`).
- Le nombre d'entrées en attente sur la tuile, au lieu du point (`WaitingMark`, décidé « pour le moment » le 2026-09-29) — si le point ne suffit pas.
- D'autres sources de l'attente (`docs/BRICKS.md`) : un message de l'IA arrivé dans une session pendant qu'on était ailleurs, signalé par exemple sur le bouton de conversation — à concevoir.
- Joindre un fichier ou une image au message de départ d'une automation : son composeur n'a pas toujours de session, à laquelle un fichier joint et une image appartiennent.
- La brique de planning ne sait pas dire « tous les N jours / N semaines à partir d'une date », ni plusieurs jours d'un mois (« le 1er et le 15 ») — au premier rythme qu'on ne peut pas saisir.
- Une chose répétée dont on veut savoir qu'elle est faite (un médicament chaque jour) demande deux gestes : lire le message, cocher le suivi — un bouton « Fait » sur le message qui écrit dans un suivi (avec les automations directes), ou un type Rappels avec un champ planning par entrée ; quand ce double geste pèse.
- Un outil Agenda, pour des événements qui ont une heure et passent d'eux-mêmes (un rendez-vous), distinct d'un élément de liste à échéance qu'on coche — à concevoir au premier événement qu'on voudra noter dans l'app.
- Outil jamais livré : l'Alerte, laissée pour plus tard (outil ou event du cœur, sans doute une lecture et une condition).
- L'automation directe : des commandes que l'app exécute elle-même, sans IA. Premier usage connu, le relevé (une écriture qui accepte une valeur lue à l'exécution : un Suivi qui garde un chiffre de Calcul, `docs/design/missing-tools.md`) ; à concevoir avec les events du cœur (« À concevoir › Events du cœur ») et l'Alerte, qu'elle croise, et à trancher : la date de l'entrée écrite, le doublon d'une exécution relancée.
- L'IA crée et modifie des automations — aujourd'hui seul l'utilisateur le peut. À faire comme pour les variables (`docs/design/missing-tools.md`) : elles apparaissent sous leur zone dans l'instantané `APP_STATE` et dans une commande qui les liste à la demande (id, nom, description), avec des opérations d'un service dédié, leur planning écrit par le schéma `schedule_config`. L'historique des exécutions est à trancher avec : une table à part comme les entrées des outils, ou ses champs filtrés par commande, au risque d'un schéma moins clair pour l'IA.
- Le Suivi (tableau de l'historique) et Messages (cartes des envois) n'affichent pas les champs personnalisés de leurs entrées, qu'on ne voit qu'en modification — à trancher : où, et dans quelle disposition (`CustomFieldsDisplay`, `docs/DATA.md`).
- Des outils sur l'accueil : une tuile de zone qui montre les résumés de certains de ses outils, ou des raccourcis vers eux selon la place qu'elle a — à discuter : lesquels, dans quel ordre, un outil montré à deux endroits.
- Les incarnations (`docs/design/incarnations.md`, identités de personae) — au premier export publié par personae.
- Supprimer un fournisseur d'IA ne regarde ni les automations ni les sessions qui le nomment (`AIProviderConfigService.deleteProviderConfig`) : refuser tant qu'une automation l'utilise, en la nommant ; une session passée reste lisible mais ne peut plus continuer, message à l'appui — avec les identités incarnées, qui demandent le même contrôle, écrit une fois pour les deux.
- Un calcul lourd en trois temps (lire, calculer longtemps sans bloquer les autres opérations, écrire), le résultat rendu à l'écran qui l'a lancé et arrêté si cet écran se ferme — au premier calcul qui fige l'app. Les écritures longues ont leur place unique (`LongOperation`) ; un calcul n'a rien à y faire, son résultat ne servant qu'à son écran.
- Arrêter une opération longue en cours (un bouton dans la bande de `LongOperation`) — quand un import lancé par erreur gênera ; l'import ne regarde aujourd'hui sa demande d'arrêt qu'au début.
- Streaming des réponses IA (Claude et OpenAI), avec le TCP keep-alive — quand des messages « requête envoyée, réponse perdue » s'accumulent dans les sessions : le réseau coupe les connexions restées silencieuses pendant la génération.
- Le journal de l'app ne garde que ses ~12 000 dernières lignes, 24 minutes d'usage le 2026-10-01 (VERBOSE 7 562 dont 3 782 « Service result » du coordinateur, DEBUG 2 439) : les lignes utiles, migrations comprises, en sortent avant d'être lues. Réduire ce que VERBOSE et DEBUG écrivent, ou ne garder qu'eux en rotation courte.
- La mémoire d'un import grandit avec son fichier : le texte entier, toutes ses cellules (`CsvReader.read`), le plan de toutes les lignes et leurs fiches sont tenus ensemble — l'écriture va au service par tranches de 500 lignes, mais toutes les fiches sont construites avant ; 64 659 lignes (5 Mo) ont atteint 276 Mo de tas Java pour une limite de 256 Mo le 2026-10-01, en 3 min 19 s. À lire au fil de l'eau et construire chaque tranche au moment de l'écrire, dans la même transaction (le contrôle des doublons sur les seules clés), puis l'écran d'une table qui charge toutes ses fiches — au premier import qui plante, ou à une limite de mémoire plus basse.
- Le réglage « réservé à l'utilisateur » (`unified-fields.md`, Ouvert) couvre deux cas à séparer : une valeur saisie (champ de l'utilisateur, champ saisi d'un critère d'Objectif), écrite par `tool_data`, prend un réglage de champ `user_only` que le service vérifie par l'origine (`currentOrigin()`) et que le schéma de l'IA marque non modifiable ; le verdict d'un Objectif, calculé par `goal.validate`, n'est pas un champ et se réserve par un réglage de l'Objectif vérifié dans `goal.validate` et `goal.reopen` — la phrase de `missing-tools.md` qui l'envoie au réglage de champ est à corriger. Laissé de côté le 2026-10-01.
