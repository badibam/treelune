# Recette sur l'appareil

Ce que seul le téléphone juge : l'œil, l'oreille, le doigt, et ce qu'un vrai Android fait (le réveil d'une alarme, une notification, l'appareil photo, une app tuée, un autre téléphone). Une ligne dit quoi faire et quoi voir ; le pourquoi est dans le commit. Une vérification faite sort de la liste. Un commit n'y ajoute une ligne que pour ce qu'aucun test ne peut juger : ce qui casserait sans se voir, ou en perdant des données, devient un test dans le même commit ; ce qui se voit cassé à l'usage, sans rien perdre, n'entre pas, ni le pire cas d'un écran (un thème, la plus grande taille, une colonne), que tient la passe des thèmes de `docs/THEMES.md`. L'app est en portrait seul : « recréer l'écran », c'est basculer le thème sombre, ou activer « Ne pas conserver les activités » puis revenir à l'app.

## Mise à jour et démarrage

- Après un plantage (`adb shell am crash app.treelune.debug`, à vérifier qu'il passe par le gestionnaire de l'app) : au lancement suivant, l'écran « L'app s'est arrêtée » avant tout le reste ; « Voir le rapport » montre la pile d'appels, « Envoyer » ouvre le menu de partage avec le rapport entier ; « Continuer » démarre l'app, et l'écran ne revient pas au lancement d'après.
- Une sauvegarde faite après la migration 72, importée après avoir désinstallé puis réinstallé l'app (la clé du Keystore change) : l'écran du fournisseur dit que la clé ne s'ouvre pas et la montre vide ; un message à l'IA est refusé en le disant ; la clé ressaisie, tout repart.
- Installation neuve, app en « Optimisée » : après l'écran de bienvenue du Guide, un seul dialogue (« L'app peut être arrêtée en arrière-plan »), son bouton ouvre la demande d'Android ; acceptée, il ne revient plus ; « Plus tard », il ne revient pas au démarrage suivant, la carte reste en tête de Réglages.
- App quittée et téléphone verrouillé depuis plus d'une heure : un message de l'outil Messages prévu à une heure donnée arrive dans les 10 minutes qui suivent, sans ouvrir l'app ; de même après un redémarrage du téléphone, sans l'avoir ouverte depuis.
- Fermer l'app de force pendant que l'IA répond dans une discussion, la relancer : « Réponse de l'IA interrompue » dans la discussion, l'IA au repos, rien ne repart seul.

## Validation

- Depuis Claude par le connecteur, écrire dans un outil protégé : la notification « … demande votre accord » arrive, Accepter laisse l'écriture se faire, Refuser ou 90 s sans réponse la laissent de côté et Claude le dit. Pendant l'attente, un autre appel de Claude passe-t-il ?

## Démo

- Sur l'OPPO A5 2020 (Android 10, ColorOS 7.1), dont la SQLite n'a pas `json_extract` : l'app désinstallée puis installée à neuf, la démo s'installe au premier démarrage sans toast d'erreur ; l'Objectif de la zone Italien montre ses tentatives validées, un Graphique de la démo se dessine, un chrono lancé s'affiche en cours sur sa tuile.

## Chat IA

- Session CHAT avec OpenAI : la première réponse passe (le mode JSON, `text.format`, est accepté par l'API Responses).
- Fermer l'app de force pendant une attente (validation, question, données volumineuses), la rouvrir : les boutons reviennent.
- Recréation pendant la composition : deux blocs, un pointeur ouvert sur le second avec un outil, ses cases et une période choisis. Après la recréation, la fenêtre et les choix sont là, et le pointeur arrive dans le second bloc. De même pendant une question de l'IA : la précision ajoutée est gardée.
- Images : « Photo » ouvre l'appareil photo sans demande d'autorisation, la photo prise en portrait arrive droite ; supprimer la session efface ses fichiers (`files/attachments`) ; exporter une sauvegarde avec images, la réimporter, les vignettes reviennent ; un ancien `.json` s'importe encore.

## Accès externe (serveur MCP)

Avec le relais en place (son projet `relay`, en ligne sur `https://relay.badibam.fr` ; son hébergement tient une requête 120 s, `RelayLoop.WAIT_SECONDS` = 20 reste en dessous), son adresse et son secret dans Réglages › Accès externe.

- La tuile « Accès externe » ajoutée aux réglages rapides : un toucher ouvre, un autre ferme ; son état suit l'écran.
- Révoquer le client : l'appel suivant de claude.ai est refusé, il redemande l'autorisation.
- Accès externe ouvert, par Tailscale puis par le relais : « Fermer l'accès » touché, l'écran dit aussitôt « Fermeture de l'accès… » avec la roue, sans bouton, jusqu'à « Fermé » ; la tuile des réglages rapides s'éteint aussitôt, et la touchée pendant la fermeture ne rouvre rien. Même chose depuis la notification.

## Accès externe par Tailscale

- Un membre d'un compte qui ne peut pas activer Funnel : les étapes à la main s'affichent (HTTPS et la page DNS, le bloc à copier et l'éditeur de la politique d'accès), et l'accès s'ouvre seul une fois le compte réglé par son administrateur.
- Passer du Wi-Fi à la 4G, accès ouvert : un appel passe encore dans la minute.
- « Déconnecter de Tailscale », accès fermé : confirmé, l'appareil `treelune` disparaît de la console ; l'ouverture suivante redemande la connexion.

## Automations

- Rattrapage réel : automation programmée, app fermée plusieurs jours. Voir quelle session est reprise et ce que montre l'écran d'historique ; une donnée de la date prévue est écrite avec un timestamp ISO explicite.
- Lancer une automation, couper le réseau pendant l'appel, puis Stop : la session se ferme tout de suite. Sans Stop, elle attend le réseau si l'appel n'était pas parti, ou s'arrête avec « Requête envoyée, réponse perdue » et un coût en « ≥ » sur sa carte.
- Routine planifiée qui demande plusieurs lectures et écritures, app fermée (glissée hors des récentes), écran éteint : à l'alarme, une notification « Routine en cours » à son nom apparaît, la routine va au bout (son historique), la notification disparaît. Pas de ligne « AutomationRun: the service could not start » au journal. Réseau coupé pendant qu'elle tourne : elle se ferme après 2 min, la notification part, et elle reprend à une alarme suivante une fois le réseau revenu.
- Un questionnaire à remplir touché sur sa tuile, puis fermé ; l'app retirée des récents pendant qu'une routine est active ou que l'accès externe tourne, puis rouverte : la page de l'outil, sans questionnaire ouvert.

## Notifications

- Notifications : toucher celle d'une tentative à valider, d'une invitation à remplir ou d'un message ouvre l'app sur la zone puis l'outil, à sa plus ancienne entrée qui attend ; un outil supprimé depuis ouvre l'app telle quelle.
- Icône des notifications : dans la barre d'état et le volet, celle de l'outil qui envoie (un message, un élément de liste à échéance, une tentative à valider, un questionnaire à remplir), en silhouette nette, avec le thème par défaut comme avec le rétro.
- Une routine lancée à la main, réglée « Toujours » : à sa fin, une notification au nom de la routine avec le début du résumé ; la toucher ouvre l'exécution, le résumé et la ligne de faits en tête. Avant de la toucher : le point sur la tuile de la zone, la carte de la routine et sa ligne dans l'historique, la première phrase du résumé sous sa date ; ouverte, les trois points s'effacent.
- Un message d'un outil Messages, son contenu mis en forme : sa notification sans les marques, une ligne par ligne du texte, les numéros d'une liste numérotée gardés.

## Séance

- « Commencer » : « Prépare-toi », 5 s avec un accord au début et 3 bips ; une étape au temps bipe à 3, 2, 1 s et passe d'elle-même sur la quinte (et la vibration) ; la voix, activée, dit l'étape et sa consigne, la musique baissée un instant.
- Étapes de 30 s au temps, écran éteint, téléphone posé à plat 15 min : chaque bip et chaque changement à l'heure. Noter ce que la séance a pris de batterie.
- La notification montre l'étape et son temps qui défile ; « Fait » et « Pause » marchent écran verrouillé ; la toucher ouvre l'outil.
- « Pause » : le temps s'arrête, la notification dit « en pause », aucun bip ; « Reprendre » repart d'où il en était.
- Téléphone en silencieux ou en vibreur : aucun son ni voix, la vibration reste.
- Forcer l'arrêt de l'app pendant une séance, rouvrir l'outil : « Cette séance s'est arrêtée avec l'app » ; « Reprendre » relance l'étape, « Arrêter » la clôt.
- Les variables parmi les outils : une zone de la démo réinstallée, ses variables en tuiles à leur place (Cuisine : Objectif calorique et Calories du jour côte à côte en Compact) ; une variable mise à jour d'avant garde sa valeur, son nom comme libellé, en Ligne sous les outils de sa section ; Disposer la déplace parmi les outils ; son icône, sa couleur, sa texture et son mode changés sur sa page ; une variable passée dans un autre groupe change de grille.
- L'accueil dans chaque thème, ses zones dans chacun des quatre modes (une sans icône en ICON) : à l'identique d'avant, la tuile d'une zone passant par la tuile commune (`UI.LookCard`).
- Une routine lancée : la marque « en cours » sur son icône et sur la tuile de sa zone à l'accueil, partie à la fin de l'exécution.
- Les routines parmi les outils : une mise à jour d'avant les montre en Étendu sous les outils et variables de leur section, « Routine » sous leur nom ; leurs quatre boutons, l'interrupteur et l'état marchent sur la tuile ; en Carré, leurs trois dernières exécutions avec leur résumé, chacune s'ouvre ; en Ligne et Compact, un toucher ouvre leur page, qui a l'état, l'interrupteur et les boutons (Réglages à la place d'Historique) ; un appui long ouvre leurs réglages, leur apparence comprise ; en file d'attente, Annuler à la place de Lancer.
- Des images sur une étape (la config de la Séance, une photo prise et une de la galerie) : pendant la séance, la première en grand sous la consigne, les autres petites dessous, chacune ouverte en grand au toucher ; la première petite au bout de sa ligne dans le déroulé ; l'outil dupliqué garde ses images, une image retirée de l'étape disparaît.
- Une séance finie écran éteint (dernière étape au temps) : la notification devient « Séance finie : touche pour la terminer », le point sur l'outil et la zone ; la toucher ouvre la page de fin, tes champs dessous ; « Terminer la séance » retire la notification et le point, la séance « Faite » dans l'historique ; « Commencer » refusé tant qu'elle attend. Pareil après « Arrêter », la séance « Arrêtée ».

## Agenda

- Réglages › Rappels : « 15 min avant » (Minute, −15, Même moment) ; « Rappels des journées entières » : « La veille, 18:00 » (Jour, −1, À une heure). Un événement dans 20 min : la notification part à l'heure, app fermée, écran éteint, téléphone posé à plat ; la toucher ouvre la fiche de l'événement. Deux rappels à 3 min d'écart : le second à l'heure, ou son retard noté : s'il y en a, le repli est `setAlarmClock` pour les seuls rappels, qu'Android montre comme un réveil (une horloge dans la barre d'état).
- Téléphone éteint pendant un rappel, rallumé avant la fin de l'événement : le rappel part ; rallumé après : rien.

## Sauvegarde et partage

- Le fichier partagé joint à un mail arrive entier.
- Importer… sur une sauvegarde complète : rien coché, le choix « Reprendre une partie » / « Tout restaurer » en tête ; Tout restaurer cache les cases, dit ce qui est remplacé, demande confirmation, puis l'app redémarre sur la sauvegarde.
- Un fichier de partage reçu d'un autre téléphone (par le menu de partage, enregistré dans Téléchargements) s'importe de même, ses images d'entrées comprises.

## Les incarnations

- Réglages › Identités › Importer, avec un fichier d'identité au format 2 copié sur le téléphone (l'export de Stan Prokopenko une fois personae passé au format 2, ou l'exemple de `docs/IDENTITY_FILE.md`) : la fenêtre dit son nom, sa date de publication s'il en a une, ses langues, la langue de l'app présélectionnée ; importée, sa tuile paraît. Un fichier qui n'en est pas un (une sauvegarde de l'app, un export de personae au format 1) est refusé en disant le champ ou le format en cause.
- Config d'une zone : Identité, Stan Prokopenko ; la zone montre sa tuile sous l'en-tête, son bouton ouvre une conversation neuve, le bouton d'identité de la barre du haut à son icône. Envoyer « Comment tu dessines une main ? » : la réponse dans sa voix, en français ; le bouton d'identité ne change plus (un toucher ouvre son nom en lecture seule, la raison dessous, fermé par OK ; son nom touché ouvre sa page, le fil d'Ariane « Accueil › le titre du chat », le retour ou ce titre ramenant au chat). Dans le journal, la ligne « Prompt data built » donne la taille du L1 ; dans les statistiques de la session, les tokens du premier tour puis du second, mis en cache ou non : c'est la mesure qu'attend personae, qui produit les identités.
