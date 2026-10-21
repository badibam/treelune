# Recette sur l'appareil

Ce que seul le téléphone juge : l'œil, l'oreille, le doigt, et ce qu'un vrai Android fait (le réveil d'une alarme, une notification, l'appareil photo, une app tuée, un autre téléphone). Une ligne dit quoi faire et quoi voir ; le pourquoi est dans le commit. Une vérification faite sort de la liste. Un commit n'y ajoute une ligne que pour ce qu'aucun test ne peut juger : ce qui casserait sans se voir, ou en perdant des données, devient un test dans le même commit ; ce qui se voit cassé à l'usage, sans rien perdre, n'entre pas. L'app est en portrait seul : « recréer l'écran », c'est basculer le thème sombre, ou activer « Ne pas conserver les activités » puis revenir à l'app.

## Mise à jour et démarrage

- Après un plantage (`adb shell am crash app.treelune.debug`, à vérifier qu'il passe par le gestionnaire de l'app) : au lancement suivant, l'écran « L'app s'est arrêtée » avant tout le reste ; « Voir le rapport » montre la pile d'appels, « Envoyer » ouvre le menu de partage avec le rapport entier ; « Continuer » démarre l'app, et l'écran ne revient pas au lancement d'après.
- Une sauvegarde faite après la migration 72, importée après avoir désinstallé puis réinstallé l'app (la clé du Keystore change) : l'écran du fournisseur dit que la clé ne s'ouvre pas et la montre vide ; un message à l'IA est refusé en le disant ; la clé ressaisie, tout repart.
- Installation neuve, app en « Optimisée » : après l'écran de bienvenue du Guide, un seul dialogue (« L'app peut être arrêtée en arrière-plan »), son bouton ouvre la demande d'Android ; acceptée, il ne revient plus ; « Plus tard », il ne revient pas au démarrage suivant, la carte reste en tête de Réglages.
- App quittée et téléphone verrouillé depuis plus d'une heure : un message de l'outil Messages prévu à une heure donnée arrive dans les 10 minutes qui suivent, sans ouvrir l'app ; de même après un redémarrage du téléphone, sans l'avoir ouverte depuis.
- Fermer l'app de force pendant que l'IA répond dans une discussion, la relancer : « Réponse de l'IA interrompue » dans la discussion, l'IA au repos, rien ne repart seul.
- La migration 82 (les images des messages et des entrées dans une seule table) sur la release et ses données : les photos des conversations passées toujours là.

## Validation

- Depuis Claude par le connecteur, écrire dans un outil protégé : la notification « … demande votre accord » arrive, Accepter laisse l'écriture se faire, Refuser ou 90 s sans réponse la laissent de côté et Claude le dit. Pendant l'attente, un autre appel de Claude passe-t-il ?

## Démo

- Sur l'OPPO A5 2020 (Android 10, ColorOS 7.1), dont la SQLite n'a pas `json_extract` : l'app désinstallée puis installée à neuf, la démo s'installe au premier démarrage sans toast d'erreur ; l'Objectif de la zone Italien montre ses tentatives validées, un Graphique de la démo se dessine, un chrono lancé s'affiche en cours sur sa tuile.
- Réglages › Démo : « Réinstaller » puis revenir en arrière aussitôt : la carte ne touche pas le bouton de conversation, sur un écran où il flotte ; un toast dit que c'est terminé.
- Le menu d'ajout d'une section (le + d'une section, et celui de « Hors groupe », branche guide-demo-pass) : « Ajouter dans « nom » », les tuiles icône et nom deux par rangée sous Noter, Planifier, Tirer parti, Relier et automatiser, Copier ; une seule par rangée avec « Une colonne » ; chaque tuile fait ce qu'elle dit, dans la section du + ; dans chaque thème, et à la plus grande taille de texte d'Android.
- Les recettes de Cuisine (branche guide-demo-pass) : chacune sous son titre, ses sous-titres Ingrédients et Étapes, la liste à puces, les étapes numérotées, le gras et l'italique, dans chaque thème ; la tuile Recettes montre les titres ; une note ouverte se modifie sans perdre sa mise en forme.
- Le graphique « Travail et sommeil » (Course › Analyse, en bas, branche guide-demo-pass) : les heures de travail par semaine en barres, la nuit moyenne en ligne dessous, les deux axes en durées lisibles ; la semaine de rush, haute en heures et basse en sommeil ; une session d'Heures ajoutée dans Travail le change.
- Les photos de la démo (branche guide-demo-pass) : dans Balcon › Plantes, les fiches Tomate, Basilic et Piment montrent chacune sa photo, nette, à l'endroit ; touchée, elle s'ouvre en grand ; « Supprimer la démo » puis l'app redémarrée, leurs fichiers partent au ménage (une ligne INFO au journal).

## Guide

- Les chapitres Les routines (six étapes, celle du message attend l'enregistrement) et Parler à l'IA (l'étape des cases d'accord), « Les outils en un coup d'œil », la partie Les outils et ses dix chapitres (textes mis en forme, tuiles de titre de partie, dans les trois thèmes).
- Le Guide réécrit dans sa langue ; les tutoriels de Prise en main (Organiser, Relier, Apparence) : chaque étape se valide par le bon geste, et seulement par lui ; les chapitres Tes données, Accès externe, Quand ça coince, et la partie Approfondissement, lus.

## Chat IA

- Le message de départ d'une automation ne propose pas le trombone.
- Session CHAT avec OpenAI : la première réponse passe (le mode JSON, `text.format`, est accepté par l'API Responses).
- Fermer l'app de force pendant une attente (validation, question, données volumineuses), la rouvrir : les boutons reviennent.
- Recréation pendant la composition : deux blocs, un pointeur ouvert sur le second avec un outil, ses cases et une période choisis. Après la recréation, la fenêtre et les choix sont là, et le pointeur arrive dans le second bloc.
- Une question de l'IA : « Ajouter une précision » ouvre un texte, envoyé avec la réponse, et l'IA en tient compte. « Répondre par un message » débloque le composeur, le formulaire reste répondable ; envoyer un message avec un pointeur ferme le formulaire, « Vous avez répondu à la question de l'IA par un message » apparaît et l'IA répond au message. Recréer l'écran entre-temps garde la précision.
- Images : « Photo » ouvre l'appareil photo sans demande d'autorisation, « Galerie » le sélecteur de photos ; la vignette arrive après le bloc actif, l'image entière au toucher, droite pour une photo prise en portrait ; avec Claude, la réponse parle de la photo, et une question au tour suivant sur un détail de la photo trouve sa réponse ; DeepSeek : `deepseek-flash` reçoit l'image, `deepseek-v4-pro` (sans images) : le bloc le dit, l'envoi est refusé ; rouvrir et enregistrer une config Claude montre « Lit les images : oui » ; supprimer la session efface ses fichiers (`files/attachments`) ; exporter une sauvegarde avec images, la réimporter, les vignettes reviennent ; un ancien `.json` s'importe encore.

## Accès externe (serveur MCP)

Avec le relais en place (son projet `relay`, en ligne sur `https://relay.badibam.fr` ; son hébergement tient une requête 120 s, `RelayLoop.WAIT_SECONDS` = 20 reste en dessous), son adresse et son secret dans Réglages › Accès externe.

- La tuile « Accès externe » ajoutée aux réglages rapides : un toucher ouvre, un autre ferme ; son état suit l'écran.
- Révoquer le client : l'appel suivant de claude.ai est refusé, il redemande l'autorisation.

## Accès externe par Tailscale

- Un membre d'un compte qui ne peut pas activer Funnel : les étapes à la main s'affichent (HTTPS et la page DNS, le bloc à copier et l'éditeur de la politique d'accès), et l'accès s'ouvre seul une fois le compte réglé par son administrateur.
- Passer du Wi-Fi à la 4G, accès ouvert : un appel passe encore dans la minute.
- « Déconnecter de Tailscale », accès fermé : confirmé, l'appareil `treelune` disparaît de la console ; l'ouverture suivante redemande la connexion.

## Automations

- Rattrapage réel : automation programmée, app fermée plusieurs jours. Voir quelle session est reprise et ce que montre l'écran d'historique ; une donnée de la date prévue est écrite avec un timestamp ISO explicite.
- Lancer une automation, couper le réseau pendant l'appel, puis Stop : la session se ferme tout de suite. Sans Stop, elle attend le réseau si l'appel n'était pas parti, ou s'arrête avec « Requête envoyée, réponse perdue » et un coût en « ≥ » sur sa carte.
- Routine planifiée qui demande plusieurs lectures et écritures, app fermée (glissée hors des récentes), écran éteint : à l'alarme, une notification « Routine en cours » à son nom apparaît, la routine va au bout (son historique), la notification disparaît. Pas de ligne « AutomationRun: the service could not start » au journal. Réseau coupé pendant qu'elle tourne : elle se ferme après 2 min, la notification part, et elle reprend à une alarme suivante une fois le réseau revenu.

## Outils et saisie

- Réglages nombre (bornes et pas d'une échelle, d'un nombre) : « 0 » s'affiche « 0 », un champ se vide entièrement, « 0.5 » se tape point compris. Le pas d'une échelle part à « 1 », son défaut ; il s'efface entièrement, et vide son libellé dit « Pas (par défaut : 1) ».
- Historique d'un Suivi, dans les trois thèmes, police d'Android agrandie au maximum : une carte par entrée, le nom et la valeur avec son unité (un chrono en cours le dit), la date en petit, les champs personnalisés un par ligne, modifier et supprimer (qui demande confirmation) ; un nom long revient à la ligne, rien n'est coupé. Messages, onglets « reçus » et « à venir » : les champs personnalisés d'un message sous son texte, un par ligne.
- Champ Image : ajouter, ordonner, retirer des photos dans une fiche ; une vignette touchée s'ouvre en grand, on glisse d'une image à l'autre ; en abrégé, la première et « +N ». Un PNG transparent et une capture d'écran gardés nets, transparence comprise ; une photo de 4 000 px réduite, une image de 200 px gardée telle quelle.

## Réglages et affichage

- Les messages de l'app (`Toasts`), dans chaque thème : un message long entier, lisible sur la page et distinct des cartes ; au-dessus d'une fenêtre ouverte (le refus d'un export dans sa fenêtre, le chat) ; parti au toucher ; « Import réussi » dit au redémarrage qui suit une restauration.
- Les rangées de la grille au cran de taille 3 dans les thèmes autres que Métro, puis avec la taille de texte d'Android au maximum dans chacun : une tuile en Minimal, Ligne et Compact (« Point facturation », le Carnet de Travail) montre son nom sur deux lignes et son type entiers, rien de coupé en bas ; au cran 0 et à la taille d'Android normale, les cases restent carrées comme avant (comparer avec une capture d'avant) ; le mode d'édition de la grille, ses cases à la nouvelle hauteur.
- La place à l'écran, en rétro à la plus grande taille, en une colonne, puis dans les deux autres thèmes : chaque écran (zone, outils, réglages, Guide, routine) à la même marge des bords, la plus petite, et une fenêtre flottante alignée sur elle ; les boutons d'un formulaire côte à côte s'ils tiennent, sinon chacun sur sa ligne en pleine largeur, le texte centré (Questionnaire en une colonne : « Remplir maintenant », « Avec l'IA ») ; les couleurs d'une icône et la grille d'icônes passent à la ligne sans s'écraser.
- Clavier ouvert dans un champ en bas d'une page longue (config d'un outil, Réglages) : la page défile jusqu'à son bas au-dessus du clavier ; le bouton de conversation et la bande du Guide disparaissent tant qu'il est ouvert et reviennent à sa fermeture ; dans le Guide en cours, l'éditeur n'est plus écrasé.
- Une fenêtre flottante longue (une note de quarante lignes) dans le thème cosy : elle tient au-dessus du clavier, ses boutons visibles, la ligne tapée en vue.
- Réglages › Système › Signaler un bug, sans plantage enregistré : le champ « Ce qui s'est passé », dont le texte apparaît aussitôt en tête du rapport ; le rapport sans section plantage, ses identifiants en `#1`, ses textes cités en `«text»` ; « Envoyer » vers une messagerie, le texte arrivé entier.
- Mode d'édition d'une grille, thème rétro, dans une zone et sur l'accueil : une tuile choisie reste pleine, les autres tuiles et le reste de l'écran tramés en damier, sans transparence ; les pointillés des cases visibles à travers les tuiles tramées, jusque sous une tuile de plusieurs cases, et toujours au défilement. Thème par défaut : les tuiles et le reste de l'écran pâlis comme avant.

## Variables

- Éditeur de formule : placer le curseur au milieu de la formule et toucher un nom ou un opérateur : il s'insère là, espacé, et le curseur se place juste après ; saisir au clavier dans n'importe quel champ de texte de l'app (accents, correction automatique) se comporte comme avant.

## Données structurées

- Filtrer « catégorie = fruit », ouvrir une fiche, lui changer sa catégorie : elle reste affichée jusqu'à ce qu'on la quitte. Recréer l'écran : filtre, tri et fiche ouverte restent ; quitter l'outil et revenir : tout repart à zéro.

## Objectif

- Notifications : toucher celle d'une tentative à valider, d'une invitation à remplir ou d'un message ouvre l'app sur la zone puis l'outil, à sa plus ancienne entrée qui attend ; un outil supprimé depuis ouvre l'app telle quelle.
- Icône des notifications : dans la barre d'état et le volet, celle de l'outil qui envoie (un message, un élément de liste à échéance, une tentative à valider, un questionnaire à remplir), en silhouette nette, avec le thème par défaut comme avec le rétro.

## Séance

- « Commencer » : « Prépare-toi », 5 s avec un accord au début et 3 bips ; une étape au temps bipe à 3, 2, 1 s et passe d'elle-même sur la quinte (et la vibration) ; la voix, activée, dit l'étape et sa consigne, la musique baissée un instant.
- Étapes de 30 s au temps, écran éteint, téléphone posé à plat 15 min : chaque bip et chaque changement à l'heure. Noter ce que la séance a pris de batterie.
- La notification montre l'étape et son temps qui défile ; « Fait » et « Pause » marchent écran verrouillé ; la toucher ouvre l'outil.
- « Pause » : le temps s'arrête, la notification dit « en pause », aucun bip ; « Reprendre » repart d'où il en était.
- Téléphone en silencieux ou en vibreur : aucun son ni voix, la vibration reste.
- Forcer l'arrêt de l'app pendant une séance, rouvrir l'outil : « Cette séance s'est arrêtée avec l'app » ; « Reprendre » relance l'étape, « Arrêter » la clôt.
- L'écran de la séance en cours et la tuile, dans les trois thèmes.

## Agenda (branche agenda)

- Réglages › Rappels : « 15 min avant » (Minute, −15, Même moment) ; « Rappels des journées entières » : « La veille, 18:00 » (Jour, −1, À une heure). Un événement dans 20 min : la notification part à l'heure, app fermée, écran éteint, téléphone posé à plat ; la toucher ouvre la fiche de l'événement. Deux rappels à 3 min d'écart : le second à l'heure, ou son retard noté : s'il y en a, le repli est `setAlarmClock` pour les seuls rappels, qu'Android montre comme un réveil (une horloge dans la barre d'état).
- Téléphone éteint pendant un rappel, rallumé avant la fin de l'événement : le rappel part ; rallumé après : rien.
- L'écran et la tuile, dans les trois thèmes.
- Installer la démo : chaque outil planifié créé fait tourner aussitôt le planificateur de son type (`CoreScheduler.runTool`) ; l'installation a pris ~50 s le 2026-10-20 (du toucher à « Demo installed »), à comparer avec une mesure d'avant la fusion de la branche agenda.

## Graphique

- Thème rétro : les graduations en pointillé d'un pixel sur deux, les axes pleins.

## Copier, limites, fuseau, import, sauvegarde automatique (branche data-text, 2026-10-14)

- La barre d'un texte sélectionné, dans les trois thèmes et les deux modes : un passage d'une note lue, d'une erreur, d'un titre de page, du Guide, puis d'un champ (au clavier ouvert, le curseur et le clavier restent) et d'un champ dans une fenêtre flottante. La barre du thème et jamais celle d'Android ; au-dessus de la sélection, dessous quand elle touche le haut ; Copier, Coller, Couper, Tout sélectionner dans la langue de l'app ; une appli de traduction installée ajoute son action ; tout tient à la ligne sur un écran étroit ; un toucher hors de la barre la ferme. Rétro : le mot pressé s'inverse ; cosy : la barre s'enfonce.

## Lecture seule pour l'IA (branche user-only)

- La case « Lecture seule pour l'IA » : sur un champ ajouté à un Journal, après « Toujours afficher » ; sur un critère saisi d'Objectif, une fois le type choisi, absente d'un critère lu ; « Validation fermée à l'IA » dans la section Temps de l'Objectif. Cochée, enregistrée, la config rouverte : toujours cochée ; changer la case ne demande aucune confirmation.

## Les textures (branches tool-texture et texture-background)

- Dans les trois thèmes, aux deux modes, une zone dont les tuiles portent chacune un motif, sans et avec couleur d'icône : chacune se reconnaît, aucune ne gêne la lecture de son nom ni de son résumé (un Graphique compris), et les quinze se voient à peu près autant ; puis Textures sur « Marquées ». Ce qui ne va pas se règle au banc des textures (`./run themes`).
- Une page texturée, dans les trois thèmes, aux deux modes, aux deux intensités : la tête de page comme sur toute page (sans bande dans le défaut et le rétro, la pilule du cosy unie) ; le titre, un titre de section et la phrase d'une liste vide se lisent sur le motif ; les cartes des entrées sont pleines, aucun motif dessous ; dans le cosy, les petits points du sable ne se voient pas sous le motif, et reviennent sur une page sans texture. Ce qui se lit mal se juge d'abord à la vue « page » du banc des textures (`./run themes`).
- Rétro : un motif tombe sur les pixels du thème, sans flou, aux crans de taille 0 et 3, sur la tuile comme sur le fond de la page (une bande de quelques pixels sans motif peut rester aux deux bords, la marge que le thème laisse hors de sa grille) ; en mode d'édition de la grille, une tuile `CHECKER` mise à l'écart se distingue encore du damier.

## Les sources de l'attente (branche waiting-sources)

- Envoyer un message, passer l'app en arrière-plan pendant la réponse, revenir sur la conversation ouverte : pas de point ensuite. Revenir sur un autre écran : le point est là.
- Une routine lancée à la main, réglée « Toujours » : à sa fin, une notification au nom de la routine avec le début du résumé ; la toucher ouvre l'exécution, le résumé et la ligne de faits en tête. Avant de la toucher : le point sur la tuile de la zone, la carte de la routine et sa ligne dans l'historique, la première phrase du résumé sous sa date ; ouverte, les trois points s'effacent.

## Les incarnations (branche incarnations)

- Réglages › Identités › Importer, avec un fichier d'identité au format 2 copié sur le téléphone (l'export de Stan Prokopenko une fois personae passé au format 2, ou l'exemple de `docs/IDENTITY_FILE.md`) : la fenêtre dit son nom, sa date de publication s'il en a une, ses langues, la langue de l'app présélectionnée ; importée, sa tuile paraît. Un fichier qui n'en est pas un (une sauvegarde de l'app, un export de personae au format 1) est refusé en disant le champ ou le format en cause.
- Config d'une zone : Identité, Stan Prokopenko ; la zone montre sa tuile sous l'en-tête, son bouton ouvre une conversation neuve, le bouton d'identité de la barre du haut à son icône. Envoyer « Comment tu dessines une main ? » : la réponse dans sa voix, en français ; le bouton d'identité ne change plus (un toucher ouvre son nom en lecture seule, la raison dessous, fermé par OK ; son nom touché ouvre sa page, le fil d'Ariane « Accueil › le titre du chat », le retour ou ce titre ramenant au chat). Dans le journal, la ligne « Prompt data built » donne la taille du L1 ; dans les statistiques de la session, les tokens du premier tour puis du second, mis en cache ou non : c'est la mesure qu'attend personae, qui produit les identités.

## Les bugs du téléphone du 2026-10-17

- Accès externe ouvert, par Tailscale puis par le relais : « Fermer l'accès » touché, l'écran dit aussitôt « Fermeture de l'accès… » avec la roue, sans bouton, jusqu'à « Fermé » ; la tuile des réglages rapides s'éteint aussitôt, et la touchée pendant la fermeture ne rouvre rien. Même chose depuis la notification.

## Le gras et l'italique d'un texte lu (branche text-emphasis)

- Une identité en Markdown (`**gras**`, `*italique*`, `***les deux***`, un gras dans un titre `##`), puis le Guide, dans les cinq thèmes et les deux modes : le gras et l'italique se voient sans casser la ligne ; défaut, suisse et encre en vrai italique, cosy penché par Android (la pente de la planche du 2026-10-17), rétro le gras à l'encre vive et l'italique de Cartouche, ses pixels entiers, ni coupé au début ni à la fin d'une ligne, ni collé à la lettre droite qui le suit ; un titre de section du suisse reste en capitales.

## La mise en forme des textes longs (branche text-formatting)

- Une note, puis un champ texte long de l'utilisateur, au clavier Android : après « - lait », Entrée commence « - » ; après « 2. Mélanger », « 3. » ; Entrée sur un élément vide retire sa marque et finit la liste ; Entrée au milieu d'un élément porte la fin dans le suivant ; la suggestion de mot du clavier en cours ne casse rien.
- Les deux boutons au bout de la ligne du label (note, entrée de Journal, champ de l'utilisateur, message d'une routine) : sur la ligne du curseur, puis sur plusieurs lignes sélectionnées ; touché deux fois, la marque repart ; le clavier reste ouvert, le curseur à sa place. Absents de la zone d'écriture du chat, qui garde la continuation.
- Gras et Italique dans la barre d'un mot sélectionné, dans chaque thème : les marques l'entourent, la sélection garde le mot ; touché à nouveau, elles partent ; les deux ensemble donnent `***mot***`. Absents d'un texte court et d'une sélection vide.
- Une note mise en forme (titre, liste, liste numérotée avec une étape insérée au milieu, gras, italique) dans chaque thème : sa carte, une entrée de Journal ouverte, un message, la consigne d'une étape de Séance (dite sans ses marques par la voix) ; abrégé sur la tuile des Notes, l'aperçu d'une carte de Journal, une cellule de Données structurées, sans marques ni retours à la ligne.
- Un message d'un outil Messages, son contenu mis en forme : sa notification sans les marques, une ligne par ligne du texte, les numéros d'une liste numérotée gardés.

## Les écrans du chat, des identités et des fournisseurs (2026-10-17, sur develop)

- Le composeur : le statut sur sa ligne au-dessus des blocs, à la hauteur de son texte ; T premier de la rangée, Envoyer seul plein à droite, dans les cinq thèmes ; Paramètres, Modifier, Configurer pleins en rétro et dans le thème par défaut, à juger.
- Les marges au cran de taille 0 puis 3, dans les quatre thèmes qui en ont : les espacements, la marge de bord et les écarts de la grille gardent leur longueur ; en rétro, seuls l'intérieur des cadres et les écarts de la grille des tuiles grandissent.

## Les bugs du téléphone du 2026-10-19

- Un questionnaire à remplir touché sur sa tuile, puis fermé ; l'app retirée des récents pendant qu'une routine est active ou que l'accès externe tourne, puis rouverte : la page de l'outil, sans questionnaire ouvert.
- Les boutons de la fenêtre d'une date au cran de taille 3, dans chaque thème sauf Métro : côte à côte s'ils tiennent, sinon l'un sous l'autre, l'action principale en haut (le rétro les empile sans les étendre à toute la largeur : à juger).
- Un outil à moitié rempli en Étendu, en Carré et en Complet : la tuile s'arrête sous sa dernière ligne ; plein, et un Graphique, elle a sa hauteur d'avant ; en mode d'édition de la grille, Étendu et Carré montrent toutes leurs cases ; un outil vide, sa ligne d'en-tête seule, dans les sept thèmes autres que Métro, en une colonne aussi.

## Flashcards (branche flashcards)

- La durée du calcul (« Flashcards: deck … read in N ms ») sur le lexique réel (sur la démo, de 26 à 152 ms) ; au-delà de 300 ms, le dire.

## L'historique des écritures (branche undo-history)

- Un gros import (le fichier de 64 659 lignes du 2026-10-01) : la durée de l'import, à comparer à celle d'avant (3 min 19 s), et la place prise par l'historique ensuite ; puis l'annuler, et sa durée.

## Les journées de l'app (2026-10-20, branche todo-cleanup)

- La migration 85 sur la release : un premier événement au mercredi 28 octobre, un second au lundi 9 novembre — leurs jours d'avant ? Leurs rappels déjà envoyés ne repartent pas ; une sauvegarde d'avant restaurée, de même.

## Les bugs du 2026-10-20 et les unités des durées (branche bugs)

- Les boutons à libellé à la plus grande taille de texte d'Android, dans les thèmes autres que le défaut : la validation d'une action de l'IA dans le chat, les formulaires d'une variable et d'une entrée du Journal, la duplication d'un outil, une fiche de Données structurées : côte à côte quand ils tiennent, l'un sous l'autre sinon, jamais un coupé ; l'entrée du Journal et une séance Flashcards laissent sous leurs boutons la place du bouton de conversation.

## L'export des entrées (branche post-release)

- Le fichier d'un export de Repas ouvert dans un tableur par son dialogue d'import (virgule, UTF-8) : la première ligne en libellés (« Nom », « Date »…), les accents justes, une ligne par entrée, l'aliment par son nom, un choix par son libellé (« Dîner »).
- Le fichier partagé joint à un mail arrive entier.
- Au chat, « Partager » de la carte d'un export ouvre le menu d'Android.

## Partager une partie de l'app (branche post-release)

- Le fichier écrit : la fenêtre dit son nom (celui de la zone, ou treelune_partage) et ses comptes, Partager ouvre le menu de partage avec le zip. Exporter, Tout : même chose, sous treelune_sauvegarde.
- Importer… sur une sauvegarde complète : rien coché, le choix « Reprendre une partie » / « Tout restaurer » en tête ; Tout restaurer cache les cases, dit ce qui est remplacé, demande confirmation, puis l'app redémarre sur la sauvegarde.
- Un fichier de partage reçu d'un autre téléphone (par le menu de partage, enregistré dans Téléchargements) s'importe de même, ses images d'entrées comprises.
