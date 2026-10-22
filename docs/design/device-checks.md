# Recette sur l'appareil

Ce que seul le téléphone juge : l'œil, l'oreille, le doigt, et ce qu'un vrai Android fait (le réveil d'une alarme, une notification, l'appareil photo, une app tuée, un autre téléphone). Une ligne dit quoi faire et quoi voir ; le pourquoi est dans le commit. Une vérification faite sort de la liste. Un commit n'y ajoute une ligne que pour ce qu'aucun test ne peut juger : ce qui casserait sans se voir, ou en perdant des données, devient un test dans le même commit ; ce qui se voit cassé à l'usage, sans rien perdre, n'entre pas, ni le pire cas d'un écran (un thème, la plus grande taille, une colonne), que tient la passe des thèmes de `docs/THEMES.md`. L'app est en portrait seul : « recréer l'écran », c'est basculer le thème sombre, ou activer « Ne pas conserver les activités » puis revenir à l'app.

## Mise à jour et démarrage

- Après un redémarrage du téléphone, sans ouvrir l'app : un message de l'outil Messages prévu à une heure donnée arrive dans les 10 minutes qui suivent.

## Validation

- Depuis Claude par le connecteur, écrire dans un outil protégé : la notification « … demande votre accord » arrive, Accepter laisse l'écriture se faire, Refuser ou 90 s sans réponse la laissent de côté et Claude le dit. Pendant l'attente, un autre appel de Claude passe-t-il ?

## Démo

- Sur l'OPPO A5 2020 (Android 10, ColorOS 7.1), dont la SQLite n'a pas `json_extract` : l'app désinstallée puis installée à neuf, la démo s'installe au premier démarrage sans toast d'erreur ; l'Objectif de la zone Italien montre ses tentatives validées, un Graphique de la démo se dessine, un chrono lancé s'affiche en cours sur sa tuile.
- Pendant l'import d'un gros fichier dans une table (Collection) : la bande compte les lignes écrites, par 500.

## Chat IA

- Fermer l'app de force pendant une attente de données volumineuses, la rouvrir : les boutons reviennent.
- Un ancien `.json` de sauvegarde s'importe encore.

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
- Routine planifiée en cours, réseau coupé : elle se ferme après 2 min, la notification « Routine en cours » part, et elle reprend à une alarme suivante une fois le réseau revenu.
- Un questionnaire à remplir touché sur sa tuile, puis fermé ; l'app retirée des récents pendant qu'une routine est active ou que l'accès externe tourne, puis rouverte : la page de l'outil, sans questionnaire ouvert.

## Notifications

- Notifications : toucher celle d'une tentative à valider, d'une invitation à remplir ou d'un message ouvre l'app sur la zone puis l'outil, à sa plus ancienne entrée qui attend ; un outil supprimé depuis ouvre l'app telle quelle.
- Icône des notifications : dans la barre d'état et le volet, celle de l'outil qui envoie (un message, un élément de liste à échéance, une tentative à valider, un questionnaire à remplir), en silhouette nette, avec le thème par défaut comme avec le rétro.

## Outils

- Messages, récurrence illisible (écrire en base un `schedule` portant `"enabled": true`) : un avertissement sur l'écran de l'outil, une erreur dans sa config.
- « Importer un fichier » sur une table d'aliments : l'écran relit la table une fois l'import fini.
- Un objectif récurrent quotidien, sa période finie : l'onglet « En cours (1) », la tentative à valider au-dessus de celle du jour.
- Un questionnaire planifié, la première réponse donnée puis l'écran quitté : « Remplir » reprend à la deuxième question.

## Séance

- « Commencer » : l'accord au début, les 3 bips du décompte, la quinte au changement d'étape ; la voix, activée, dit l'étape et sa consigne, la musique baissée un instant.
- Étapes de 30 s au temps, écran éteint, téléphone posé à plat 15 min : chaque bip et chaque changement à l'heure. Noter ce que la séance a pris de batterie.
- « Fait » et « Pause » de la notification marchent écran verrouillé ; la toucher ouvre l'outil.
- Téléphone en silencieux ou en vibreur : aucun son ni voix, la vibration reste.
- Les variables parmi les outils : une zone de la démo réinstallée, ses variables en tuiles à leur place (Cuisine : Objectif calorique et Calories du jour côte à côte en Compact) ; une variable mise à jour d'avant garde sa valeur, son nom comme libellé, en Ligne sous les outils de sa section ; Disposer la déplace parmi les outils ; son icône, sa couleur, sa texture et son mode changés sur sa page ; une variable passée dans un autre groupe change de grille.
- L'accueil dans chaque thème, ses zones dans chacun des quatre modes (une sans icône en ICON) : à l'identique d'avant, la tuile d'une zone passant par la tuile commune (`UI.LookCard`).
- Une routine lancée : la marque « en cours » sur son icône et sur la tuile de sa zone à l'accueil, partie à la fin de l'exécution.
- Les routines parmi les outils : une mise à jour d'avant les montre en Étendu sous les outils et variables de leur section, « Routine » sous leur nom ; leurs quatre boutons, l'interrupteur et l'état marchent sur la tuile ; en Carré, leurs trois dernières exécutions avec leur résumé, chacune s'ouvre ; en Ligne et Compact, un toucher ouvre leur page, qui a l'état, l'interrupteur et les boutons (Réglages à la place d'Historique) ; un appui long ouvre leurs réglages, leur apparence comprise ; en file d'attente, Annuler à la place de Lancer.
- Des images sur une étape (la config de la Séance, une photo prise et une de la galerie) : pendant la séance, la première en grand sous la consigne, les autres petites dessous, chacune ouverte en grand au toucher ; la première petite au bout de sa ligne dans le déroulé ; l'outil dupliqué garde ses images, une image retirée de l'étape disparaît.
- Une séance finie écran éteint (dernière étape au temps) : la notification devient « Séance finie : touche pour la terminer », le point sur l'outil et la zone ; la toucher ouvre la page de fin, tes champs dessous ; « Terminer la séance » retire la notification et le point, la séance « Faite » dans l'historique ; « Commencer » refusé tant qu'elle attend. Pareil après « Arrêter », la séance « Arrêtée ».

## Agenda

- Téléphone éteint pendant un rappel, rallumé avant la fin de l'événement : le rappel part ; rallumé après : rien.

## Sauvegarde et partage

- Le fichier partagé joint à un mail arrive entier.
- Un fichier de partage reçu d'un autre téléphone (par le menu de partage, enregistré dans Téléchargements) s'importe de même, ses images d'entrées comprises.
- Un export d'entrées : son fichier paraît dans le dossier choisi dans Réglages › Données.
- Importer… sur un fichier de partage qui porte des entrées : elles arrivent cochées.

## Les incarnations

- Config d'une zone : Identité, Stan Prokopenko ; la zone montre sa tuile sous l'en-tête, son bouton ouvre une conversation neuve, le bouton d'identité de la barre du haut à son icône. Envoyer « Comment tu dessines une main ? » : la réponse dans sa voix, en français ; le bouton d'identité ne change plus (un toucher ouvre son nom en lecture seule, la raison dessous, fermé par OK ; son nom touché ouvre sa page, le fil d'Ariane « Accueil › le titre du chat », le retour ou ce titre ramenant au chat). Dans le journal, la ligne « Prompt data built » donne la taille du L1 ; dans les statistiques de la session, les tokens du premier tour puis du second, mis en cache ou non : c'est la mesure qu'attend personae, qui produit les identités.
- Une autre icône et une couleur choisies pour une incarnation, enregistrées : sa tuile les montre.
- Une conversation dont l'incarnation est supprimée : son composeur remplacé par la raison.
