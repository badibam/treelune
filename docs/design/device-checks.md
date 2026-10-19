# Recette sur l'appareil

Ce que la suite de tests ne voit pas : les écrans, les migrations sur une vraie base, l'IA en vrai. Une ligne dit quoi faire et quoi voir ; le pourquoi est dans le commit. Une vérification faite sort de la liste. Un commit qui demande une vérification ajoute une ligne sous l'écran qu'il touche. L'app est en portrait seul : « recréer l'écran », c'est basculer le thème sombre, ou activer « Ne pas conserver les activités » puis revenir à l'app.

## Mise à jour et démarrage

- Après la migration 60 (tables `mcp_clients` et `mcp_tokens`) : l'app démarre, sans erreur de Room dans le journal.
- Après la migration 61 : Bilan quotidien, Journal de rêves et Histoire personnelle s'ouvrent, leur champ de texte supplémentaire en place, et l'IA les lit (TOOL_DATA, SCHEMA).
- Après la migration 62 : rien ne bouge à l'accueil ni dans les zones ; le journal dit combien de groupes perdus ont été vidés.
- Après la migration 63 : les zones s'ouvrent, leurs icônes neutres ; une sauvegarde faite avant s'importe de même.
- Importer une sauvegarde faite avant la mise à jour (base 52 ou plus ancienne) : chaque zone montre ses outils dans le même ordre, l'accueil ses zones de même ; les récurrences et les pointeurs se relisent. Juste après, l'écran des journaux filtré sur « Error » ne montre aucune ligne `MIGRATION`.
- Un pointeur qui filtrait un instant par « = » (avant la migration 52) est refusé en le disant : rien ne part en silence.
- Après un plantage (`adb shell am crash app.treelune.debug`, à vérifier qu'il passe par le gestionnaire de l'app ; `bug-report.md`) : au lancement suivant, l'écran « L'app s'est arrêtée » avant tout le reste ; « Voir le rapport » montre la pile d'appels, « Envoyer » ouvre le menu de partage avec le rapport entier ; « Continuer » démarre l'app, et l'écran ne revient pas au lancement d'après.
- Après la migration 64 : Réglages › IA › Limites montre « Taille des outils toujours envoyés avant confirmation » à 15 000.
- Treelune (`app.treelune`) s'installe à côté de l'ancienne Assistant : une sauvegarde exportée d'Assistant s'importe dans Treelune, tout en place ; l'accès externe se reconfigure et Claude s'y rebranche. L'icône dans le lanceur, ronde et carrée, et en monochrome avec les icônes à thème d'Android 13 et plus.
- Après la migration 72 (secrets scellés) : l'IA répond avec le fournisseur actif, sans ressaisir sa clé ; l'écran du fournisseur montre la clé masquée, en place ; l'accès par relais s'ouvre comme avant. Importer une sauvegarde faite avant : les clés marchent toujours.
- Une sauvegarde faite après la migration 72, importée après avoir désinstallé puis réinstallé l'app (la clé du Keystore change) : l'écran du fournisseur dit que la clé ne s'ouvre pas et la montre vide ; un message à l'IA est refusé en le disant ; la clé ressaisie, tout repart.
- Après la migration 73 (le compteur devient numérique) : l'outil de verres d'eau est un Suivi numérique, ses raccourcis « Verre » et « − Verre » (et le demi-verre, s'il existait) saisissent chacun sa valeur d'un toucher, en « verres » ; ses entrées passées s'affichent avec leur unité.
- Installation neuve, app en « Optimisée » : après l'écran de bienvenue du Guide, un seul dialogue (« L'app peut être arrêtée en arrière-plan »), son bouton ouvre la demande d'Android ; acceptée, il ne revient plus ; « Plus tard », il ne revient pas au démarrage suivant, la carte reste en tête de Réglages.
- Après la migration 65 : un questionnaire passé avec l'IA avant la mise à jour compte comme rempli sur la tuile (« dernière réponse ») comme dans l'écran ; le journal dit combien d'entrées sans statut sont devenues remplies.
- Demander à l'IA un message à une heure donnée dans un outil Messages : elle l'écrit avec le statut « pending », le message paraît parmi ceux à venir et part à son heure. Lui demander d'ajouter une tentative à un Objectif : refusé, l'IA active l'objectif à la place.
- App quittée et téléphone verrouillé depuis plus d'une heure : un message de l'outil Messages prévu à une heure donnée arrive dans les 10 minutes qui suivent, sans ouvrir l'app ; de même après un redémarrage du téléphone, sans l'avoir ouverte depuis.
- Fermer l'app de force pendant que l'IA répond dans une discussion, la relancer : « Réponse de l'IA interrompue » dans la discussion, l'IA au repos, rien ne repart seul.

- Après le passage à la SQLite de l'app (branche demo-install) : la mise à jour depuis la release installée, avec ses données, garde zones, entrées, conversations et fournisseurs ; l'écran des journaux filtré sur « Error » ne montre aucune ligne `MIGRATION`. Une sauvegarde s'exporte puis s'importe, tout en place.

- Après le passage à Compose 1.12 (BOM 2026.09.00) : parcourir l'accueil, une zone, un outil de chaque type, sa config, Réglages, le chat, dans les deux thèmes ; les fenêtres de dialogue, les listes qui défilent, le glisser-déposer et le clavier se comportent comme avant.


## Validation

- Protéger les données d'un outil : une entrée écrite par l'IA attend l'accord, la carte dit « Les données de « … » sont protégées ».
- Dans une conversation, cocher « Les données des outils » : toute écriture d'entrée attend l'accord, puis plus rien une fois décochée.
- Demander à l'IA de créer un groupe sur l'accueil, d'y ranger une zone, puis de le renommer (`UPDATE_APP_CONFIG`) : la zone suit le nouveau nom ; avec « Protéger l'accueil », l'accord est demandé.
- Une automation dont l'accès donne un outil en Lecture, à qui l'on demande d'y écrire : le refus se lit dans son historique d'exécution ; en Utilisation, l'écriture passe. Ajouter, changer, retirer une ligne de la carte d'accès, enregistrer, rouvrir : tout est gardé.
- Depuis Claude par le connecteur, écrire dans un outil protégé : la notification « … demande votre accord » arrive, Accepter laisse l'écriture se faire, Refuser ou 90 s sans réponse la laissent de côté et Claude le dit. Pendant l'attente, un autre appel de Claude passe-t-il ?
- Donner à un client du connecteur un accès limité (bouton « Accès » de sa ligne) : une lecture hors de la liste est refusée, et `app_context` dit son accès.
- Passer `docs/design/validation-nulls-ai-check.md` : le message à coller dans une session IA, et ce qui est attendu étape par étape.
- Demander à l'IA de créer une entrée en laissant un champ facultatif vide : l'entrée s'enregistre, sans ce champ. Lui faire appeler une opération d'outil avec un paramètre facultatif à null (`complete` une séance du Fractionné, sans durée) : acceptée.
- Créer une entrée dans une Liste, un Questionnaire (« Remplir maintenant » en passant une question), une fiche de Données structurées, un Journal, chacun avec un champ supplémentaire laissé vide : enregistrée, le champ absent.
- Enregistrer la config d'un outil, les réglages de l'app, une automation, un fournisseur d'IA, sans rien changer : accepté. Répondre à un module de communication de l'IA en laissant un champ facultatif vide : accepté.

## Démo

- Sur l'OPPO A5 2020 (Android 10, ColorOS 7.1), dont la SQLite n'a pas `json_extract` : l'app désinstallée puis installée à neuf, la démo s'installe au premier démarrage sans toast d'erreur ; l'Objectif de la zone Italien montre ses tentatives validées, un Graphique de la démo se dessine, un chrono lancé s'affiche en cours sur sa tuile.
- Deux `./run install` de suite, la démo seule à l'accueil : chaque fois la démo se réinstalle entière, sans toast d'erreur sur les groupes de zones. Réglages : retirer le dernier groupe de zones s'enregistre.
- Réglages › Démo : désactiver « Installer la démo à chaque mise à jour » et enregistrer ne supprime rien, la démo reste ; réinstaller l'APK la laisse telle quelle ; réactiver le réglage n'installe rien avant la mise à jour suivante.
- Réglages › Démo : « Réinstaller » puis revenir en arrière aussitôt : une carte en bas à gauche de chaque écran, sans rien déplacer et sans toucher le bouton de conversation, dit « Installation de la démo » et son étape jusqu'à la fin, puis un toast dit qu'elle est terminée ; la démo est entière. Revenir dans Réglages › Démo pendant l'installation : les boutons attendent, la roue et l'étape s'affichent.
- Ajouter une entrée dans un outil de la démo et un outil dans une de ses zones, puis Réglages › Démo › « Supprimer la démo » : la démo et ces deux ajouts disparaissent, le reste de l'app est intact ; « Réinstaller » la remet entière.
- Chaque suivi de la démo, sauf Repas, montre un raccourci d'ajout rapide nommé comme ses entrées : Verre ajoute 1, Pesée et Récolte ouvrent la saisie dans leur unité, Nuit lance un chrono.
- Pendant un import d'un gros fichier (Données structurées), lancer « Réinstaller » la démo ou exporter une sauvegarde : refusé, le message nomme l'import en cours ; la bande compte les lignes écrites par 500.

## Chat IA

- Messages système en langage courant, le détail replié sous « Détails » : mode avion (« Pas de réseau… »), une clé fausse (« …telle qu'elle est réglée… », le message du fournisseur dans Détails), la limite d'échanges atteinte, une fin à confirmer (« L'IA dit avoir terminé… »), des données refusées ; un résultat de lecture n'a pas de « Détails ».
- Demander à l'IA de répondre dans `post_text` sans rien faire (« réponds-moi dans post_text, sans action ») : son texte s'affiche après `pre_text`. Lui faire créer une entrée que son outil refuse (une valeur hors de ses bornes) avec un `post_text` : il ne s'affiche pas, et le résultat qu'elle reçoit dit qu'il n'a pas été montré.
- Paramètres de la conversation › Réflexion : la ligne du menu dit « Réflexion : comme réglée pour le fournisseur » ; décocher « Comme réglée… » part du réglage du fournisseur, choisir un effort, « Sans réflexion » (Claude) réduit les efforts proposés. La requête envoyée porte l'effort choisi (journal, ou le fournisseur). Fermer, rouvrir la conversation depuis l'historique : le réglage est gardé. Changer de modèle dans la config du fournisseur pour un qui n'a pas cet effort, puis écrire : l'appel est refusé avec « La réflexion réglée pour cette conversation ne convient pas… ». Les cases de validation cochées restent cochées en rouvrant les paramètres.
- Lui faire écrire dans un outil qui n'existe pas (un id inventé) : « L'IA a écrit une commande que l'app ne comprend pas », Détails « Aucun outil n'a l'id… », rien n'est fait. Lui faire ajouter à un outil un champ sans nom affiché : le refus nomme le champ manquant, sans « Failed to parse custom field ».
- Champs supplémentaires : « Ajoute une note dans Dev › Treelune » — l'IA remplit Catégorie et Portée sans lire le schéma (l'aperçu les nomme) ; une note créée sans eux, le résultat le dit (`extra_left_empty`) et l'IA le signale. Un outil changé de zone par l'IA : elle dit que son groupe est resté derrière.
- Couleurs d'icônes par l'IA : « Mets l'icône de la zone Santé en rose et celle du Poids en bleu » — CREATE/UPDATE_ZONE prend `icon_color`, l'outil la reçoit dans sa config ; « en gris » ou « en cyan » est refusé avec les huit noms.
- Zones par l'IA : « Crée une zone Voyages dans le groupe X, affichage minimal, avec les groupes d'outils A et B » — la zone arrive rangée et réglée ; un groupe de l'accueil inventé est refusé avec la liste des groupes, et l'IA se reprend. « Renomme le groupe d'outils A en C » passe par UPDATE_ZONE avec `renames`, et ses outils le suivent.
- Après le catalogue des commandes (`AICommands`) : « Combien de pesées cette semaine ? » passe par `TOOL_DATA` avec `period`, sans refus ; une commande à un paramètre inventé est refusée en le nommant, et l'IA la corrige au tour suivant ; le L1 (journal `Prompt data built`) garde sa taille d'avant, à quelques tokens près.
- Réglages d'une session de chat : l'interrupteur de validation, à droite de son libellé, bascule et la validation suit.
- Session CHAT avec DeepSeek : elle passe, et son coût s'affiche sans « ≥ ».
- « Importe ce fichier dans une nouvelle table » avec un CSV joint : l'IA crée la table, puis au tour suivant `IMPORT_PLAN` et `IMPORT_DATA` ; la carte de validation nomme le fichier, ses lignes et la table ; la table a ses colonnes et ses lignes.
- Le message de départ d'une automation ne propose pas le trombone.
- DeepSeek, sur une dizaine de réponses : une réponse dont le JSON est entouré de texte ou d'un bloc de code est exécutée sans `FORMAT_ERROR` ; un message système cite le texte écarté, et la réponse suivante ne le reprend pas.
- Session CHAT avec OpenAI : la première réponse passe (le mode JSON, `text.format`, est accepté par l'API Responses).
- « Coche Lait dans Courses » : l'IA appelle l'opération `check` et l'élément est coché ; dans une liste qui retire ce qui est coché, il disparaît.
- L'IA crée un Questionnaire sans `ai_message` : la création passe, avec le message par défaut.
- Après la mise à jour : une ancienne session affiche ses tokens et un coût en « ≥ » ; une nouvelle, un coût exact, le même dans la fiche de coût et sur la carte d'historique d'automation.
- Démarrer l'app en mode avion après l'avoir déjà utilisée en ligne : une session affiche quand même son coût (prix gardés sur le téléphone).
- Importer une sauvegarde faite avant la mise à jour : les conversations reviennent, leur coût en « ≥ ».
- « Quelle heure est-il ? », puis créer une entrée datée d'hier : la date est juste. Les résultats de données reçus par l'IA sont en ISO 8601.
- Après `CREATE_ZONE` puis `CREATE_TOOL`, l'IA enchaîne sans redemander la liste des zones ni des outils.
- Demander à l'IA une zone et un outil dedans en une fois : elle crée la zone, puis l'outil au tour suivant avec l'id reçu, sans id inventé dans la même réponse.
- Pointeur : le fil d'Ariane et les zones puis les outils s'affichent comme avant ; dans un outil, rien n'est listé en dessous ; passer d'un outil à celui d'une autre zone par le fil garde les cases et la période.
- Pointeur en chat, période « Depuis : date relative, il y a 2 jours, début » : « Soit : » montre la date obtenue, et après confirmation la phrase du pointeur dit cette date. Pointeur du message de départ d'une automation : « Par rapport à : l'heure prévue de l'exécution » s'affiche une fois, la période se relit « la veille », « le moment même ». Un filtre sur un champ DATE propose des jours, sans heure ni unité « heure ».
- Une IA qui enchaîne plus de 10 appels seule s'arrête et rend la main avec un message.
- Faire écrire par l'IA une valeur hors d'une échelle ou hors des options d'un choix : le refus lui revient.
- Faire créer et modifier des entrées de tracking par l'IA : `raw` est juste.
- Faire poser une récurrence sur un outil Messages par l'IA, et retirer la description d'une zone.
- Seuil de données à 5 000 : un pointeur sur un gros outil fait apparaître « Données volumineuses » ; « Envoyer » et « Refuser » font répondre l'IA avec ou sans les données. Même chose quand l'IA demande elle-même les données.
- Fermer l'app de force pendant une attente (validation, question, données volumineuses), la rouvrir : les boutons reviennent.
- Recréation pendant la composition : deux blocs, un pointeur ouvert sur le second avec un outil, ses cases et une période choisis. Après la recréation, la fenêtre et les choix sont là, et le pointeur arrive dans le second bloc.
- Sélecteur de pointeur dans le chat : descendre App › Zone › Outil puis remonter par le fil d'Ariane, ce qui n'a plus de sens au niveau atteint est effacé ; sur un outil, période, un filtre par valeur (nombre, choix, date) et deux champs cochés — la phrase du bas dit ce qui part, et l'IA reçoit exactement ça. Un filtre rempli puis la fenêtre validée sans « + » : il est gardé. Aucune case cochée avec un filtre : l'IA reçoit la requête sans les entrées et peut la lancer.
- Après la migration 45 : les anciennes conversations affichent leurs pointeurs avec les noms actuels. Renommer un outil pointé : le fil, sans quitter la discussion, l'aperçu de la session dans la liste et la carte du message de départ d'une automation montrent le nouveau nom ; supprimer un outil pointé : « Outil : supprimé ». Une automation pointant un outil renommé : son IA lit le nouveau nom avec l'id.
- Pointeur sur une zone avec « entrées » et une période : l'IA reçoit les entrées de chacun de ses outils sur cette période, chacune avec son schéma ; sans case cochée, elle reçoit les filtres de la période et peut lire outil par outil. Descendre dans un outil garde la période, remonter à la zone garde les entrées et la période.
- Filtre sur un champ texte (« contient » ou « est ») : sous la saisie, « Valeurs déjà saisies » propose les valeurs de l'outil, les plus fréquentes d'abord ; en toucher une remplit la valeur.
- Même sélecteur dans le message de départ d'une automation : période et filtre de date en valeurs relatives, recalculées à l'exécution suivante. Le résumé dit le côté de chaque borne : « entre le début de « 7 jours avant » et la fin de « le jour-même » », « Pesé le depuis le début de « la veille » ».
- Après la migration 46 : une ancienne conversation avec un pointeur à période s'affiche avec la même période ; une automation dont le départ pointe « la veille » lit encore la veille à son exécution suivante (le côté de chaque borne dans le résumé). Importer une sauvegarde d'avant : même chose.
- Demander à l'IA les entrées d'hier : elle écrit une `period` en dates relatives (`{"relative": …}` avec START et END), la lecture passe.
- Après la migration 44 : une ancienne conversation qui contenait des pointeurs les affiche encore, et les renvoyer dans une nouvelle session joint les mêmes données.
- Demander à l'IA une question à plusieurs volets (un choix, un texte, une date) : chaque champ se saisit avec son composant, Confirmer reste grisé tant qu'un champ obligatoire est vide, et la réponse réapparaît dans le fil champ par champ, la date affichée comme telle. Une confirmation sans champ : Confirmer et Annuler seuls. Recréer l'écran en cours de saisie : les valeurs restent.
- Demander à l'IA de créer une entrée puis de la modifier : elle modifie directement, avec l'id que la création lui a rendu, sans relire l'outil entre les deux.
- Faire créer puis modifier des entrées par l'IA, sans validation : sous chaque commande du message « actions exécutées », les entrées écrites s'affichent champ par champ (date lisible, échelle en jauge, choix par son libellé), la modification avec ses seuls champs changés. Supprimer ensuite l'outil : le fil dit que les valeurs ne peuvent plus s'afficher.
- Validation active, dans une nouvelle session, dire à l'IA d'ajouter une entrée à un outil existant sans rien lire avant : un message renvoie le schéma et dit l'écriture non faite, aucune carte de validation avant qu'elle la renvoie. Lui faire demander dans une même réponse le schéma et les entrées d'un outil : les deux lui arrivent.
- Validation active, faire créer deux entrées par l'IA puis en modifier une : la carte de validation montre chaque entrée champ par champ (date lisible, échelle en jauge, choix par son libellé), et pour la modification seulement les champs changés.
- Mode avion puis message CHAT : échec immédiat avec un message réseau, rien ne part (les NOTES disaient que l'appel partait quand même).
- Interrompre pendant que l'IA réfléchit, réseau coupé ou non : le composeur revient tout de suite, avec « Réponse de l'IA interrompue » et la note de coût inconnu ; le coût de la session s'affiche en « ≥ ».
- Une question de l'IA avec un oui/non et une échelle obligatoires : rien n'est choisi au départ, les deux libellés portent un astérisque, Confirmer s'active une fois les deux répondus ; « non » se répond d'un toucher, le minimum d'une échelle vide aussi.
- Une question de l'IA : « Ajouter une précision » ouvre un texte, envoyé avec la réponse, et l'IA en tient compte. « Répondre par un message » débloque le composeur, le formulaire reste répondable ; envoyer un message avec un pointeur ferme le formulaire, « Vous avez répondu à la question de l'IA par un message » apparaît et l'IA répond au message. Recréer l'écran entre-temps garde la précision.
- Rejeu ciblé : « crée un suivi de poids, puis ajoute une pesée » — la config passe par `units`, l'entrée porte `data.unit`.
- Composeur en blocs : écrire, ajouter un pointeur puis un texte (le bouton T, premier de la rangée) — chaque bloc arrive après le bloc actif et le devient, le nouveau texte prend le clavier ; déplacer un bloc par sa poignée dans la zone qui défile ; supprimer le bloc actif (celui d'avant s'allume), puis le dernier (un texte vide reste) ; tourner l'écran avec un dialogue de pointeur ouvert en modification ; le message envoyé et sa relecture gardent l'ordre des blocs, sans les textes vides.
- Images (`docs/design/message-images.md`) : « Photo » ouvre l'appareil photo sans demande d'autorisation, « Galerie » le sélecteur de photos ; la vignette arrive après le bloc actif, l'image entière au toucher, droite pour une photo prise en portrait ; avec Claude, la réponse parle de la photo, et une question au tour suivant sur un détail de la photo trouve sa réponse ; DeepSeek : `deepseek-flash` reçoit l'image, `deepseek-v4-pro` (sans images) : le bloc le dit, l'envoi est refusé ; rouvrir et enregistrer une config Claude montre « Lit les images : oui » ; supprimer la session efface ses fichiers (`files/attachments`) ; exporter une sauvegarde avec images, la réimporter, les vignettes reviennent ; un ancien `.json` s'importe encore.

## Accès externe (serveur MCP)

Avec le relais en place (son projet), son adresse et son secret dans Réglages › Accès externe.

- « Ouvrir l'accès » sans adresse ni secret : refusé, le message le dit. Avec : la notification « Accès externe ouvert » paraît, l'écran dit « Ouvert ».
- La tuile « Accès externe » ajoutée aux réglages rapides : un toucher ouvre, un autre ferme ; son état suit l'écran.
- Un secret faux : l'accès se ferme, l'écran dit que le relais ne reconnaît pas l'app.
- Ajouter le connecteur dans claude.ai avec l'adresse que l'écran donne (`…/mcp`) : la page montre un code à 4 chiffres ; le téléphone notifie la demande, sa fenêtre nomme le client et le domaine de retour ; le bon code autorise, la page revient à claude.ai, le connecteur est connecté et le client paraît dans la liste.
- Trois codes faux : la demande est refusée, la page le dit. « Refuser » : de même.
- Dans un chat claude.ai : le premier appel est `app_context` (ou un autre outil refusé, qui le renvoie à `app_context`) ; une lecture d'entrées avec une période passe ; une création d'entrée paraît dans l'app ; chaque réponse commence par la date et l'heure.
- Seuil de données à 5 000, dans un chat claude.ai : une lecture d'entrées plus grosse est refusée, et Claude la resserre de lui-même (champs, période, pages) ; une entrée seule au texte plus long (`limit` 1), ou la config d'un gros outil, arrive en tranches, et Claude lit les suivantes par `text_from` jusqu'à « la fin de cette lecture ».
- Ce que claude.ai fait des outils : les lectures marquées comme telles, l'autorisation outil par outil ; ce qu'il fait d'une session MCP d'une conversation à l'autre (instruit, pas exigé).
- 30 minutes sans appel : l'accès se ferme seul, la notification part. « Fermer » dans la notification : de même.
- Révoquer le client : l'appel suivant de claude.ai est refusé, il redemande l'autorisation.
- Après la migration 66 : le connecteur déjà autorisé continue sans reconnexion. Laisser l'accès ouvert plus d'une heure, téléphone endormi : le connecteur se renouvelle seul ; l'écran des journaux montre « token refresh_token … granted » à chaque renouvellement, et la raison d'un refus.

## Accès externe par Tailscale (`docs/design/funnel-access.md`)

Vu le 2026-10-09 : l'inscription par la page de connexion, « Activer Funnel » sur un compte où HTTPS et le droit `funnel` étaient retirés, l'accès ouvert seul ensuite, la publication de l'adresse attendue à l'écran, claude.ai branché sur l'adresse. Reste :

- Après la migration 68 : avec un relais réglé, Réglages › Accès externe montre « Relais » choisi, sa section affichée, et l'accès s'ouvre comme avant.
- En relais, une sauvegarde restaurée (secret illisible) : choisir Tailscale et toucher « Ouvrir » sans enregistrer : « Réglages enregistrés », puis l'étape Tailscale ; rouvrir l'écran, Tailscale est choisi. Revenir à Relais, toucher « Ouvrir » : l'écran dit que le secret a été saisi sur un autre téléphone.
- Un membre d'un compte qui ne peut pas activer Funnel : les étapes à la main s'affichent (HTTPS et la page DNS, le bloc à copier et l'éditeur de la politique d'accès), et l'accès s'ouvre seul une fois le compte réglé par son administrateur.
- Fermer, rouvrir : l'adresse revient en quelques secondes, sans connexion ni attente de certificat. 30 minutes sans appel : fermé seul, l'adresse ne répond plus. La tuile ferme l'accès aussi pendant une étape d'attente.
- Passer du Wi-Fi à la 4G, accès ouvert : un appel passe encore dans la minute.
- « Déconnecter de Tailscale », accès fermé : confirmé, l'appareil `treelune` disparaît de la console ; l'ouverture suivante redemande la connexion.
- Le journal de l'app montre une ligne par requête reçue (« POST /register -> 201 », « POST /mcp -> 200 »), une inscription avec le client créé, jamais un jeton.
- Le journal de l'app montre les messages « External access: » du nœud (URL de connexion, Funnel ouvert, adresse publiée), et `files/tailscale/tailscale.log` le journal technique, sans dépasser deux fichiers d'1 Mo.

## Automations
- Le message d'une routine : la page défile d'un bloc jusqu'à Enregistrer et Annuler, même avec un long message et des accès ; trois boutons Planning, Déclencheurs, Accès qui se résument ; le rattrapage sous le planning, dans sa fenêtre, gardé seulement à la confirmation ; les accès dans leur fenêtre, chaque nom sur toute la largeur, le niveau dessous ; Enregistrer écrit le message et les réglages (rouvrir la routine).
- Une automation lancée à la main, puis une planifiée, lisent et écrivent les outils de leur masque d'accès (plus de « Une automation qui n'existe plus n'a pas l'accès ») ; une automation au message vide ne s'enregistre pas et ne se lance pas, le refus dit.

- Rattrapage après la mise à jour : une automation programmée « sans limite » le reste, une fenêtre de 3 heures se relit « jusqu'à un délai » de 3 h. Dans l'éditeur : sans choix, l'enregistrement est refusé ; « jusqu'à un délai » demande la durée ; retirer la planification puis enregistrer retire aussi le rattrapage.
- Rattrapage réel : automation programmée, app fermée plusieurs jours. Voir quelle session est reprise et ce que montre l'écran d'historique ; une donnée de la date prévue est écrite avec un timestamp ISO explicite.
- Créer une automation planifiée : sa prochaine exécution s'affiche sur sa carte. La fenêtre de rattrapage se saisit dans l'éditeur.
- Seuil de données bas : le refus apparaît dans l'historique d'exécution, et l'IA resserre sa requête.
- Lancer une automation, couper le réseau pendant l'appel, puis Stop : la session se ferme tout de suite. Sans Stop, elle attend le réseau si l'appel n'était pas parti, ou s'arrête avec « Requête envoyée, réponse perdue » et un coût en « ≥ » sur sa carte.
- Routine planifiée qui demande plusieurs lectures et écritures, app fermée (glissée hors des récentes), écran éteint : à l'alarme, une notification « Routine en cours » à son nom apparaît, la routine va au bout (son historique), la notification disparaît. Pas de ligne « AutomationRun: the service could not start » au journal. Réseau coupé pendant qu'elle tourne : elle se ferme après 2 min, la notification part, et elle reprend à une alarme suivante une fois le réseau revenu.
- La tuile d'une automation dans sa zone : son nom lisible sur une ligne, et à côté deux petits boutons « On » / « Off », toucher le bouton choisi ne change rien, basculer ne fait pas clignoter l'écran ; les jours et les mois d'une planification sont des cases à cocher.

## Outils et saisie

- L'écran de config d'une Liste, d'un Suivi, d'un Journal : ses réglages propres dans un cadre au nom du type, sous « Paramètres généraux » ; « Afficher le nom des champs » dans le cadre « Champs supplémentaires », au-dessus de la liste « Champs ». Graphique, Objectif, Messages, Séance : leurs sections comme avant, sans cadre de plus.
- Liste réglée en « Éléments courts dans la tuile », dans les deux thèmes : 8 éléments en étendu, 24 en carré, une ligne chacun ; chaque case se coche sans toucher sa voisine (la case d'une ligne fait environ la moitié de celle d'avant). Réglage retiré : la tuile revient à 4 éléments sur deux lignes.
- Créer et modifier une entrée de chaque type : tracking de chaque sorte, note insérée à une position, journal, occurrence Messages, et une entrée avec champs personnalisés.
- Une plage de champ personnalisé dont le début dépasse la fin est refusée à l'écran.
- Champ DATE et DATETIME d'une entrée : une date personnalisée s'enregistre ; « Date relative, la veille, début » et « Maintenant » enregistrent la date qu'affiche « Soit : », qui revient en date personnalisée à la réouverture ; un champ facultatif se vide par sa croix, un champ obligatoire n'en a pas.
- Échelle : un champ de 1 à 10 affiche et enregistre « 7 », pas « 7.0 » ; une échelle de 0 à 5 par 0.5 s'arrête sur chaque demi-point et affiche « 3,5 » ; l'éditeur refuse une échelle de 1 à 10 par 2. Le curseur de note d'un tracking d'échelle se comporte comme avant.
- Sélecteurs de date et d'heure normaux dans l'entrée de journal, l'entrée de tracking, les champs DATE et DATETIME, le sélecteur de période. Un DATETIME déjà enregistré s'affiche comme une date.
- Journal, « Annuler » en modification : l'écran revient au texte stocké, et une entrée créée, rouverte puis annulée reste en place.
- Messages : créer puis modifier une récurrence, voir les messages partir ; mettre l'outil en pause, voir les envois s'arrêter.
- Messages, listes « à venir » et « reçus » : un indicateur pendant le chargement, jamais « rien de prévu » avant d'avoir lu.
- Messages, onglets « reçus » et « à venir » : ils basculent comme avant, l'onglet choisi souligné.
- Tuile Messages : un message non lu en gras, à la taille du texte courant, au-dessus des lus en petit.
- Messages, « Notif du matin » : après la mise à jour, les deux messages prévus en double ne le sont plus (un par jour). Changer plusieurs fois la récurrence de suite : jamais plus d'un message prévu par heure de la récurrence, et l'app ne rame pas.
- Lectures filtrées par les écrans : l'historique d'un suivi par période (jour, semaine, mois) montre les mêmes entrées qu'avant, et l'écran Messages range chaque message selon son état.
- Messages, récurrence illisible (écrire en base un `schedule` portant `"enabled": true`) : avertissement sur l'écran de l'outil, occurrences déjà prévues gardées et envoyées, erreur dans la config, enregistrement refusé sans rien écraser.
- Zone, écran généré : créer puis modifier (nom, description, icône, groupe, groupes d'outils ajoutés, réordonnés, supprimés) ; un nom vide est refusé avec le message du service ; effacer la description et enregistrer, elle disparaît.
- Écran de config généré, pour chaque type d'outil, en création puis en modification : tous les réglages s'affichent et s'enregistrent (icône, groupe, zone, champs personnalisés avec chaque type et ses réglages, options de choix avec couleur, planification de Messages et ses durées) ; un nom vide est refusé avec le message du service ; une recréation garde la saisie.
- Tracking : changer les bornes d'une échelle ou le type d'un champ personnalisé demande confirmation avec les nombres de valeurs retirées et d'entrées supprimées ; d'occurrence à numérique avec des entrées, la valeur à leur donner est demandée.
- Suivi numérique : taper « lb » dans une saisie l'enregistre en « lb », et la saisie suivante a « lb » dans sa liste d'unités (la première de la liste reste préremplie) ; même chose en cochant « ajouter aux raccourcis », le raccourci créé et « lb » gardé dans les unités ; un suivi sans unité reçoit la première depuis une saisie. La config d'un suivi numérique n'offre plus d'unité dans le bloc de la valeur ; celle d'un compteur et d'un champ nombre de l'utilisateur, si.
- Modifier une entrée (suivi, note, journal, message) et vider un de ses champs personnalisés, le dernier compris : après l'enregistrement, le champ est vide, pas revenu à son ancienne valeur.
- Valeur par défaut d'un champ personnalisé : dans la config, la case prend la forme du champ (options d'un choix, curseur d'une échelle) et suit les options ajoutées ; une valeur hors des options est refusée à l'enregistrement. Une nouvelle entrée (suivi, note, journal) part avec elle, effacée elle ne revient pas, une recréation ne la remet pas ; toucher un raccourci de suivi l'enregistre ; une entrée modifiée n'est pas touchée. L'IA la voit dans le schéma, et un module de communication dont un champ en porte une part prérempli.
- Suivi oui/non ou échelle : la fenêtre de saisie part vide. Un champ oui/non, échelle ou plage facultatif laissé vide s'enregistre sans valeur, et l'historique n'affiche rien pour lui.
- Réglages nombre (bornes et pas d'une échelle, d'un nombre) : « 0 » s'affiche « 0 », un champ se vide entièrement, « 0.5 » se tape point compris. Le pas d'une échelle part à « 1 », son défaut ; il s'efface entièrement, et vide son libellé dit « Pas (par défaut : 1) ».
- Tuiles en FULL, dans les trois thèmes : chaque élément aussi haut qu'en étendu (Notes, Suivi, Liste, Journal, Messages, Questionnaire, Séance, Données structurées, Objectif et ses lignes par sous-objectif) ; une note longue remplit sa case, en étendu, carré et FULL, sa dernière ligne finie par « … », titre ou non.
- Notes : activer « Titres » dans la config ; la fenêtre d'une note a un champ Titre facultatif, la carte et la tuile montrent le titre au-dessus du texte. Vider le titre d'une note l'efface. Désactiver « Titres » : plus aucun titre ni champ ; le réactiver : les titres reviennent.
- Config d'un outil : ses champs personnalisés viennent après ses réglages propres (valeur principale, unités, raccourcis).
- Glisser-déposer par la poignée : les notes (la position tient après rechargement, pas de retour visible à l'ancien ordre au lâcher), les champs personnalisés d'une config et les options d'un de ces champs (la liste imbriquée bouge seule, l'autre ne suit pas), un classement ; la page défile quand on tient un élément près du bord haut ou bas ; la saisie d'un champ de la config garde le focus pendant qu'on tape ; TalkBack propose « Monter » et « Descendre » sur la poignée.
- Listes de blocs dans une config (champs personnalisés, options d'un choix avec leur pastille, raccourcis d'un suivi, moments d'une récurrence) : fermées à l'ouverture de l'écran, une ligne de résumé chacune (« Sans nom » si vide) ; toucher la ligne ouvre et referme, plusieurs ouvertes à la fois ; un bloc ajouté s'ouvre ; un bloc déplacé ou une suppression au-dessus garde ouverts les bons blocs ; une recréation garde ce qui est ouvert.

- Liste : créer une liste, ajouter des éléments par le champ du bas ; cocher, voir l'élément descendre sous le trait ; décocher, le voir reprendre sa place ; glisser un élément parmi les non cochés, la place tient après rechargement ; ouvrir un élément, le renommer, remplir un champ personnalisé (visible sur sa ligne), le supprimer ; « Tout décocher » ; avec « Un élément coché est supprimé aussitôt » réglé, cocher supprime l'élément, depuis l'écran comme depuis la tuile ; l'ajout se fait en bas, sous le titre « Ajouter un élément », le champ « Contenu » puis les champs personnalisés à remplir sous le nom, à leur valeur par défaut, qui y reviennent après l'ajout ; un trait sépare la liste de ce formulaire, et un autre les cochés des autres ; une recréation garde la saisie en cours et l'élément ouvert.
- Liste et IA : demander à l'IA d'ajouter trois éléments puis d'en cocher un ; ils apparaissent, l'élément coché en bas.

- Champs personnalisés affichés : sur la liste des entrées d'un journal (un par ligne, le nom à la taille du titre, sous un trait qui les sépare du texte), sur une entrée ouverte (un bloc par champ), sur une carte de note et sur une ligne de liste (un par ligne, le nom en petit) ; un champ « Toujours afficher » vide y dit « Aucune valeur » ; un texte long prend toute la largeur. Décocher « Afficher le nom des champs » dans la config retire les noms partout ; une Liste neuve part sans les noms, les autres outils avec.
- Historique d'un Suivi, dans les trois thèmes, police d'Android agrandie au maximum : une carte par entrée, le nom et la valeur avec son unité (un chrono en cours le dit), la date en petit, les champs personnalisés un par ligne, modifier et supprimer (qui demande confirmation) ; un nom long revient à la ligne, rien n'est coupé. Messages, onglets « reçus » et « à venir » : les champs personnalisés d'un message sous son texte, un par ligne.

- Saisie des champs personnalisés (fenêtre d'un élément de liste, formulaire d'ajout, note, entrée de journal, envoi de Messages, entrée de suivi) : plus de titre « Champs personnalisés », le nom de chaque champ en petit au-dessus de sa saisie, un trait entre deux champs.
- Liste avec des éléments cochés : activer « Un élément coché est supprimé aussitôt », enregistrer : le dialogue dit combien d'entrées seront supprimées ; confirmer : les cochés ont disparu, les autres gardent leur ordre ; annuler : rien n'a bougé.

## Réglages et affichage
- La place à l'écran, en rétro à la plus grande taille, en une colonne, puis dans les deux autres thèmes : chaque écran (zone, outils, réglages, Guide, routine) à la même marge des bords, la plus petite, et une fenêtre flottante alignée sur elle ; les boutons d'un formulaire côte à côte s'ils tiennent, sinon chacun sur sa ligne en pleine largeur, le texte centré (Questionnaire en une colonne : « Remplir maintenant », « Avec l'IA ») ; les couleurs d'une icône et la grille d'icônes passent à la ligne sans s'écraser.
- Clavier ouvert dans un champ en bas d'une page longue (config d'un outil, Réglages) : la page défile jusqu'à son bas au-dessus du clavier ; le bouton de conversation et la bande du Guide disparaissent tant qu'il est ouvert et reviennent à sa fermeture ; dans le Guide en cours, l'éditeur n'est plus écrasé.
- Tuiles : une tuile de Journal à titre long montre « 12 j » à côté du titre, entier ; la tuile de Suivi en rétro montre sa date entière. Rétro : le point d'attente sur le livre du Guide se détache du cadre.
- Notes et blocs d'un message de routine : une bande de prise en haut de chaque carte, toute sa largeur prend le doigt ; le texte va d'un bord à l'autre ; la suppression d'un bloc au bout de sa bande. Liste : la poignée de côté plus étroite se prend toujours.
- Une fenêtre flottante longue (une note de quarante lignes) dans le thème cosy : elle tient au-dessus du clavier, ses boutons visibles, la ligne tapée en vue.

- Config en pages (Séance, Objectif, Graphique) : sur une sous-page, ni Enregistrer ni Annuler, la flèche de retour seule ; de retour à la page principale, les boutons reviennent et le brouillon est intact.
- Un Objectif dont le critère a une condition sans opérateur, ou une étape de Séance sans nom : sa ligne dit « À compléter ou corriger », et chaque ligne au-dessus jusqu'à la page principale ; le réglage rempli, les marques disparaissent. La ligne d'un critère montre sa condition en phrase (« Valeur saisie > 5 »).
- Écran Réglages (bouton de gauche de l'accueil, icône curseurs), dans les deux thèmes : quatre sections App, IA, Données, Système ; chaque tuile ouvre son écran, sa description sous le titre, et le retour ramène aux Réglages, puis à l'accueil.
- Outils toujours envoyés (`always-send.md`) : marquer un outil notes « toujours envoyer », ouvrir une discussion : l'IA cite ses notes sans les lire ; `app_context` du connecteur les montre. Abaisser le seuil sous leur taille : au message suivant, la carte « Outils toujours envoyés », sa phrase sur la session ; « Refuser », puis un autre message : pas de nouvelle carte, l'IA sait qu'ils ne sont pas envoyés et peut les lire ; une nouvelle discussion redemande ; « Envoyer » : ils partent jusqu'à la fin de la session.
- Réglages › Système › Signaler un bug, sans plantage enregistré : le champ « Ce qui s'est passé », dont le texte apparaît aussitôt en tête du rapport ; le rapport sans section plantage, ses identifiants en `#1`, ses textes cités en `«text»` ; « Envoyer » vers une messagerie, le texte arrivé entier.
- Historique depuis le chat : l'icône horloge dans l'en-tête d'une discussion, d'une automation en cours et dans le chat sans discussion ouvre l'historique ; reprendre une discussion l'ouvre dans le chat, le retour ramène au chat.
- Couleurs d'icônes (`icon-colors.md`), dans les deux thèmes et les deux modes : dans la config d'une zone et d'un outil, la rangée de choix sous l'icône, l'icône à côté de son sélecteur qui change aussitôt ; à l'enregistrement, la tuile et l'en-tête de la page dans la couleur ; « aucune » rend l'icône neutre. Thème par défaut : une icône neutre a sa pastille grise, les titres des tuiles alignés ; les marques (attente, chrono) au coin de la pastille. Les icônes, dans leur couleur, devant chaque zone et chaque outil du choix d'un pointeur ou d'un champ RÉFÉRENCE, de la duplication, et de la liste des zones dans la config d'un outil ou d'une automation (et devant la zone choisie dans le champ).
- « Une colonne » (Réglages › Interface), dans les trois thèmes, l'écran montrant le changement aussitôt : à l'accueil, dans une zone et dans les Réglages, chaque tuile sur toute la largeur, une LINE et l'en-tête d'une EXTENDED, SQUARE ou FULL en deux rangées (le titre au-dessus), deux ICON par rangée, dans l'ordre de la grille ; une FULL sans rangée vide sous elle. Ranger : haut et bas font passer la tuile au-dessus ou au-dessous de la rangée voisine, gauche et droite échangent deux ICON ; valider, désactiver le réglage : la grille dans le nouvel ordre, tassée. Après la migration 67, le réglage est désactivé et rien n'a bougé.
- Mode d'édition d'une grille, thème rétro, dans une zone et sur l'accueil : une tuile choisie reste pleine, les autres tuiles et le reste de l'écran tramés en damier, sans transparence ; les pointillés des cases visibles à travers les tuiles tramées, jusque sous une tuile de plusieurs cases, et toujours au défilement. Thème par défaut : les tuiles et le reste de l'écran pâlis comme avant.
- Choix de la zone dans la config d'un outil et dans une automation, dans les deux thèmes : les zones dans l'ordre de l'accueil, sous le titre de leur groupe puis « Sans groupe » ; un titre ne se choisit pas ; deux zones de même nom se choisissent chacune.
- Groupes de l'accueil (Réglages › Écran principal) : renommer un groupe qui a des zones, enregistrer — ses zones restent dedans sous le nouveau nom. Échanger deux noms : chaque zone garde sa section. Supprimer un groupe qui a des zones : refusé, en nommant ces zones. Supprimer puis rajouter le même nom : refusé de même s'il a des zones.
- Groupes d'outils d'une zone (modifier la zone) : renommer un groupe — ses outils, automations et variables le suivent ; supprimer un groupe utilisé : refusé, en nommant ce qui l'utilise.
- Changer un outil de zone dans sa config : le groupe se vide aussitôt, le sélecteur propose ceux de la zone choisie ; enregistré, l'outil est dans ce groupe-là ou hors groupe.
- Portrait seul : tourner le téléphone sur n'importe quel écran (accueil, zone, outil, chat, sélecteur de date et d'heure) ne change rien ; les sélecteurs de date et d'heure s'ouvrent sur le calendrier et le cadran.
- Icônes à la place des glyphes : les boutons d'action en icône (enregistrer, supprimer en corbeille, flèches de période, en-têtes) ont leur couleur de bouton et une taille lisible ; dans le chat, les enrichissements du composeur et d'un message, le résultat d'une commande et le compte à rebours de fermeture ; les boutons horaire et déclencheurs d'une automation ; le fournisseur actif ; l'avertissement d'une validation ; le chemin des sélecteurs de copie et de pointeur.
- Écrans de réglages générés (format, limites IA, validation, écran d'accueil) : chacun s'ouvre sur les valeurs stockées, s'enregistre et se retrouve à la réouverture ; fuseau et langue à « Aucun » suivent le téléphone ; un changement du fuseau ou du début de journée est suivi aussitôt par l'historique ; les groupes de zones ajoutés apparaissent sur l'accueil et dans l'écran de zone. L'écran de validation, qui était un bouchon, fonctionne.
- Fournisseurs d'IA, écran généré, pour Claude, OpenAI et DeepSeek : la clé s'affiche masquée et l'œil la montre ; les modèles se listent à l'ouverture pour une clé enregistrée, et après saisie d'une nouvelle clé ; effort obligatoire pour DeepSeek ; enregistrer, rouvrir, retrouver les valeurs ; une session de chat part avec le modèle choisi.
- Fuseau de l'app différent de celui du téléphone : l'historique range chaque entrée dans le jour affiché sur elle, le sélecteur de période et l'éditeur de planning montrent l'heure de l'app, « aujourd'hui » et « hier » du journal suivent. Un début de semaine changé est suivi par l'historique et les sélecteurs de période.
- Icônes : couleur du thème partout, et le sélecteur s'ouvre, cherche et parcourt une catégorie sans lenteur.
- Couleurs d'état données par le thème : l'écran des journaux montre chaque niveau en pastille puis en libellé (erreur en rouge, avertissement en orange, info en mauve, verbose et debug en gris) ; la pastille d'une carte d'exécution (terminée, interrompue, en erreur) garde sa couleur, le triangle d'une validation sensible prend l'orange doux de la palette.
- Copier un outil ou une automation : le sélecteur s'ouvre en carte, la zone puis l'élément choisis ressortent mis en avant, le chargement tourne au milieu.
- Réglages, « Interface utilisateur » : un écran de réglages avec « Sons de l'interface », activé ; le désactiver, enregistrer, rouvrir le retrouve désactivé. Le thème par défaut reste muet dans les deux cas.
- Boutons désactivés (enregistrer un formulaire incomplet, un bouton d'action grisé) : toujours sans effet au toucher ; le bouton actif, lui, répond comme avant.
- Espacements nommés, thème par défaut : rien ne bouge, sauf des écarts de 2 à 8 dp, à juger acceptables, là où une valeur rare a été arrondie : les lignes d'une validation et l'avertissement en retrait, les valeurs d'un champ affichées (choix ordonné, tags), les blocs d'une note sur sa tuile, les puces d'une condition et des variables, la carte des variables, le Graphique, une feuille des données structurées, une carte d'exécution, le chat flottant vide.
- Touche Retour : outil → zone → accueil → confirmation avant de fermer ; annule sur la création de zone, les réglages Claude/OpenAI et une entrée de journal en modification ; ferme les fenêtres sans effet de bord.

## Champs RÉFÉRENCE

- Champ RÉFÉRENCE restreint aux entrées d'un seul outil (`aliment`) : le choix s'ouvre dans cet outil, la recherche filtre les entrées, toucher une entrée l'ajoute au fil d'Ariane et « Confirmer » l'enregistre. Restreint à plusieurs outils : ils sont listés à l'app avec leur zone. Acceptant zones et outils : « Confirmer » n'est actif que sur une zone ou un outil atteint.
- Ajouter à un outil un champ de l'utilisateur de type Référence, limité aux entrées d'un autre outil : le choix liste ses entrées avec une recherche ; l'entrée enregistrée affiche le nom de la fiche, renommer la fiche change l'affichage, la supprimer affiche « supprimé » et l'entrée reste modifiable.
- Même champ sans restriction : choisir d'abord un outil puis son entrée. Avec les sortes Zone et Outil : leurs listes s'affichent.
- Retirer l'outil autorisé de la config : la confirmation compte les valeurs retirées.
- Faire lire ces entrées par l'IA : chaque référence arrive avec `name` ; lui faire écrire une référence vers un id inventé : refusée avec la raison.

## Variables

- Sélection (pointeur, lecture d'une variable) : le navigateur est ouvert tant qu'aucun outil n'est choisi ; un outil choisi, il se replie en « Outil (Zone) — Changer », qui le rouvre sur la zone ; au pointeur, une zone choisie montre déjà sa période tout en listant ses outils. Rouvrir une variable enregistrée montre son outil replié, sa période et ses filtres. Les filtres d'une lecture et des Données structurées n'offrent plus « Choisir les champs ».
- Filtres d'un pointeur : ajouter un filtre par valeur (nombre, choix, texte avec ses valeurs proposées, entre deux bornes), une période sur un champ date, « sans réponse » ; chacun se relit dans la liste, part à l'IA, et la requête de l'IA avec ses filtres s'affiche lisible sur sa carte. Un instant n'offre pas « = ».
- Terme d'une formule : passer de constante à variable puis à lecture et revenir ; une constante se tape comme un nombre ; une variable se choisit en descendant dans sa zone, son nom s'affiche ; enregistrer puis rouvrir garde chaque terme.
- Champ RÉFÉRENCE qui accepte les variables, et critère « variable » d'un Objectif : descendre dans une zone montre ses outils puis ses variables (« Variable » en petit), groupe par groupe sous le titre de chaque groupe, « Hors groupe » en dernier ; une zone sans groupe, une seule liste. Choisir une variable l'enregistre, son nom s'affiche, et le critère la lit à la fin de la tentative.
- Lecture d'une variable : sur un champ numérique, la liste des réductions (dernière, somme, moyenne, min, max) ; sur un champ texte ou « compter les entrées », une ligne « Réduction : … » sans liste.
- Choix d'un champ (filtres du pointeur, lecture d'une variable, champ d'un critère d'Objectif, import CSV, tri des Données structurées) : chaque champ s'affiche par son nom seul ; deux champs de même nom (un champ personnalisé « Note » et un champ « Note ») montrent leur chemin, et choisir l'un garde bien celui-là.
- Éditeur de formule : placer le curseur au milieu de la formule et toucher un nom ou un opérateur : il s'insère là, espacé, et le curseur se place juste après ; saisir au clavier dans n'importe quel champ de texte de l'app (accents, correction automatique) se comporte comme avant.
- La période d'une lecture sur l'écran d'une variable : « Par rapport à : l'instant lu », « Depuis » et « Jusqu'à » avec « Le moment même » et « Sans limite » ; une période enregistrée se relit telle qu'elle a été réglée.
- Faire créer par l'IA une variable « kcal du jour » (somme par entrée de Repas à travers la référence aliment) : elle apparaît dans `APP_STATE` d'une nouvelle session, `VARIABLES` la liste avec sa formule sous les noms actuels, `READING` rend sa valeur maintenant et la veille ; un repas sans aliment la fait échouer en le disant.
- Faire écrire à l'IA une formule avec un nom inconnu, ou deux variables qui se lisent l'une l'autre : refusées, le chemin de la boucle nommé.
- Dans une zone, « + » puis Variable : créer « objectif » (constante 2100 kcal), puis « kcal » (formule `mange`, terme lecture de Repas, formule par entrée, somme, du jour-même · début au moment même) et « reste » (`objectif - kcal`) : chaque groupe montre ses variables sur une ligne avec la valeur actuelle ; ajouter un repas met la ligne à jour ; toucher une ligne rouvre la variable, un nom inconnu tapé dans la formule s'affiche en erreur sous la saisie ; recréer l'écran en cours d'édition garde le brouillon.
- Supprimer « kcal » : « reste » affiche « pas de valeur — variable supprimée ».
- Après la migration 49 : le réglage de validation « Modifications des variables » est coché si « Modifications des zones » l'était ; coché, une variable que crée l'IA attend ta validation.
- Après la migration 48 : les anciennes sessions s'ouvrent ; le bouton de chat d'une carte d'automation ouvre une conversation dont la saisie porte son message de départ, pointeurs compris, à envoyer ou modifier.
- Après la migration 47 : l'app démarre, une sauvegarde exportée contient `variables` et se réimporte.

## Données structurées

- Une table neuve, vide : « Importer un fichier » est le bouton principal ; après un import, il redevient secondaire et reste là.
- Créer une table « Aliments » avec deux champs (kcal_100g, catégorie) : « + » ouvre une fiche vide, rien n'apparaît avant « Enregistrer » ; une fiche nommée « pomme » alors que « Pomme » existe est refusée en nommant l'existante.
- Le tableau montre le nom et les deux champs ; toucher un en-tête trie, retoucher inverse. Toucher une ligne ouvre la fiche ; glisser mène aux voisines dans l'ordre et le filtre du tableau ; la ligne repliée de l'en-tête dit la position (« 3 / 12 »).
- Filtrer « catégorie = fruit », ouvrir une fiche, lui changer sa catégorie : elle reste affichée jusqu'à ce qu'on la quitte. Recréer l'écran : filtre, tri et fiche ouverte restent ; quitter l'outil et revenir : tout repart à zéro.
- La tuile compte les fiches.
- « Importer un fichier » sur une table d'aliments avec un CSV (nom ; kcal en décimale virgule ; une date jour/mois ; une catégorie à quelques valeurs) : chaque colonne est proposée avec son type, son écriture et un exemple lu ; un fichier dont les dates ne tranchent pas jour/mois le signale ; importer crée les champs dans l'ordre, met à jour les fiches dont le nom existe, et le compte-rendu nomme les lignes refusées. Réimporter le même fichier : tout est mis à jour, rien n'est dupliqué. Un import avec une colonne nouvelle se termine (les appels imbriqués dans la transaction ne bloquent pas), et l'écran relit la table une fois fini.

## Objectif

- Notifications : toucher celle d'une tentative à valider, d'une invitation à remplir ou d'un message ouvre l'app sur la zone puis l'outil, à sa plus ancienne entrée qui attend ; un outil supprimé depuis ouvre l'app telle quelle.
- Icône des notifications : dans la barre d'état et le volet, celle de l'outil qui envoie (un message, un élément de liste à échéance, une tentative à valider, un questionnaire à remplir), en silhouette nette, avec le thème par défaut comme avec le rétro.
- Toucher la tuile d'un Questionnaire marqué d'un point ouvre le formulaire de l'invitation à remplir la plus ancienne.
- Attente : un Questionnaire avec une invitation à remplir montre un point à côté de son nom, et sa zone aussi à l'accueil ; la remplir fait disparaître le point sans quitter l'écran.
- Critères : un critère lu « Repas › kcal, somme ≤ 2100 » se juge sur la journée de la tentative sans toucher à la période (« Aucune choisie : celle de la tentative ») ; « ≤ » une variable à droite se juge aussi ; un critère saisi (Durée « ≥ 7 h », Choix « = bonne ») se remplit dans la tentative et se juge ; retirer la valeur saisie (bouton du groupe) ramène un critère lu. La ligne d'un critère dit sa valeur face à ce qu'elle compare, avec une jauge pour un nombre ou une durée.
- Objectif, Questionnaire et Messages : la récurrence de la config s'affiche en une ligne de résumé sous le titre propre à l'outil, s'édite par le bouton « Configurer la récurrence » et son dialogue ; « Aucune » la retire. Le résumé est le même que sur la carte et l'éditeur d'une automation.
- Créer un objectif ponctuel sans échéance avec trois critères (une variable « kcal » ≤ 2100, un champ « poids, dernière » ≤ 80, une saisie oui/non indispensable), au moins 2 : sa tentative s'ouvre dès l'enregistrement ; chaque critère montre sa valeur face à sa condition, la saisie oui/non se coche sur place ; « Valider » avant d'avoir saisi est refusé en nommant le critère ; après, la tentative est réussie ou échouée, « par vous ».
- Une tentative validée : la modifier par l'IA est refusé ; « Rouvrir » la remet à valider et garde la date de réouverture.
- Renommer un critère saisi : sa valeur reste ; le supprimer puis le recréer : la confirmation de la config compte la valeur retirée.
- Un objectif récurrent quotidien : une tentative par jour ; à la fin de la période, une notification et l'onglet « En cours (1) », la tentative à valider au-dessus de celle du jour ; sans validation, expirée après le délai. Arrêté, plus aucune ne s'ouvre.
- La section « Quand » d'un nouvel Objectif ne montre d'abord que Répétition, sans choix fait : enregistrer est refusé, la ligne marquée « À compléter ou corriger ». « Une fois » montre Début et Échéance ; « Récurrent », Récurrence (obligatoire) et Durée ; passer de l'un à l'autre retire les réglages de l'autre. Migration 79, sur la release et ses données : un objectif qui avait une récurrence est « Récurrent », sans Début ni Échéance ; un objectif ponctuel qui avait une Durée sans Échéance a pour Échéance son début plus cette durée, sa tentative en cours inchangée.
- Faire valider par l'IA : « par l'IA ».

## Questionnaire

- Un questionnaire de trois questions (échelle, texte, oui/non) : « Remplir maintenant » pose une question par écran ; quitter en route n'écrit rien ; terminer crée l'entrée remplie, datée de maintenant.
- Planifié chaque jour à une heure passée : au tick, une entrée « à remplir » datée de cette heure et une notification ; répondre à la première question puis quitter : la réponse reste, et « Remplir » reprend à la deuxième. Une absence de plusieurs jours : autant d'entrées, « Tout ignorer » les passe en ignorées.
- « Avec l'IA » sur une entrée à remplir : le chat s'ouvre, sa saisie porte le message et les pointeurs vers l'outil et l'entrée ; envoyé, l'IA pose les questions, écrit les réponses et marque l'entrée remplie.
- Toucher une entrée de l'historique : ses réponses en entier, modifiables.
- Onglets, Questionnaire et Objectif, dans les trois thèmes : deux onglets toujours là, l'écran ouvert sur le premier ; « À remplir (2) » ou « En cours (2) » quand deux attendent, sans nombre sinon ; les entrées en attente de la plus ancienne à la plus récente, la première étant celle que la tuile ouvre ; « Remplir maintenant » et « Avec l'IA » sous elles, présents sans rien à remplir ; l'onglet choisi tient en tournant le téléphone.

## Séance

- Créer une Séance : le formulaire du déroulé ajoute une étape, un bloc, un bloc dans un bloc (qui n'offre que des étapes) ; « Au temps » demande un minuteur ; « Sautée au dernier tour » n'apparaît que dans un bloc ; chaque ligne repliée montre son nom. L'IA crée la même config.
- « Commencer » : « Prépare-toi », 5 s avec un accord au début et 3 bips ; une étape au temps bipe à 3, 2, 1 s et passe d'elle-même sur la quinte (et la vibration) ; la voix, activée, dit l'étape et sa consigne, la musique baissée un instant.
- Étapes de 30 s au temps, écran éteint, téléphone posé à plat 15 min : chaque bip et chaque changement à l'heure. Noter ce que la séance a pris de batterie.
- La notification montre l'étape et son temps qui défile ; « Fait » et « Pause » marchent écran verrouillé ; la toucher ouvre l'outil.
- « Fait » trop tôt puis « Retour » : l'étape reprend avec son temps, celui écoulé depuis compris. « Retour » sur une étape passée d'elle-même : arrêtée à zéro, « Fait » ou « Recommencer l'étape » ; « +15 s » la relance avec 15 s.
- « Pause » : le temps s'arrête, la notification dit « en pause », aucun bip ; « Reprendre » repart d'où il en était.
- Téléphone en silencieux ou en vibreur : aucun son ni voix, la vibration reste.
- Forcer l'arrêt de l'app pendant une séance, rouvrir l'outil : « Cette séance s'est arrêtée avec l'app » ; « Reprendre » relance l'étape, « Arrêter » la clôt.
- « Commencer » une autre Séance pendant qu'une tourne : refusé, le message nomme l'outil.
- La dernière étape faite : l'accord de fin, l'entrée « Faite » avec sa durée et « n / n étapes » dans l'historique et la tuile ; « Arrêter » en route : « Arrêtée », les étapes non atteintes comptées.
- Planifiée chaque jour à une heure passée : au tick, une entrée prévue, une notification qui ouvre l'outil, le point d'attente sur la tuile ; « Commencer » la reprend, « Faite » la marque faite à son heure prévue ; une absence de plusieurs jours, « Tout ignorer ».
- L'IA marque faite une séance prévue, ajoute une séance faite hier sans être prévue, corrige la durée d'une séance, ignore une séance prévue ; lui demander de commencer une séance : elle ne le peut pas.
- Toucher une séance de l'historique : son heure, sa durée et ses étapes faites, sautées, non atteintes, modifiables ; passer une séance arrêtée à toutes ses étapes faites la rend « Faite » ; des comptes qui ne font plus le nombre d'étapes sont refusés, le message dit lequel.
- L'écran de la séance en cours et la tuile, dans les trois thèmes.
- La démo : Fractionné dans Course, groupe Entraînement, sous Sorties ; son historique, une séance par jeudi de fractionné, dont une « Arrêtée » et une avec une étape sautée ; le jeudi à 18 h 30, une séance prévue ; « Commencer » enchaîne ses 17 étapes, la voix les annonçant.

## Agenda (`docs/design/agenda-tool.md`, branche agenda)

- Créer un Agenda ; « + » : un événement jeudi 14 h 30, 45 min. Il apparaît sous « Jeudi … » dans « À venir », « 14:30 – 15:15 » sous son nom ; la tuile : « Dentiste · jeu. 23 14:30 », sa ligne dans le corps. Jour, Semaine, Mois : les flèches et le calendrier mènent au passé, l'événement passé y est.
- « Journée entière » sur 3 jours : la saisie passe à un jour et un nombre de jours ; l'événement apparaît à chacun de ses jours, en tête du jour, « Journée ».
- « Se répète » chaque mardi 18 h : les champs supplémentaires disparaissent ; des occurrences sur 90 jours apparaissent aussitôt l'enregistrement (une série créée par l'IA ou par la roue dentée aussi), chacune avec l'icône de répétition. Une occurrence : « Retirer » la fait passer sous « Retirées (1) », « Rétablir » la remet ; « Supprimer » n'y est pas.
- « Modifier la série » : changer la durée, enregistrer sans confirmation ; les occurrences restées à l'ancienne durée la prennent, celle réglée à la main garde la sienne. Changer le jour : la confirmation compte les occurrences à venir ; acceptée, elles reviennent sur le nouveau jour, le passé intact.
- Annuler un événement : « Annulé · Dentiste », dans l'écran et la tuile, sans rappel ; « Rétablir ».
- Réglages › Rappels : « 15 min avant » (Minute, −15, Même moment) ; « Rappels des journées entières » : « La veille, 18:00 » (Jour, −1, À une heure). Un événement dans 20 min : la notification part à l'heure, app fermée, écran éteint, téléphone posé à plat ; la toucher ouvre la fiche de l'événement. Deux rappels à 3 min d'écart : le second à l'heure, ou son retard noté (`docs/design/agenda-tool.md`, « Le réveil précis »).
- Téléphone éteint pendant un rappel, rallumé avant la fin de l'événement : le rappel part ; rallumé après : rien.
- Déplacer un événement déjà rappelé à plus tard : son rappel repart à la nouvelle heure.
- L'IA crée un événement (status planned), en annule un, retire une occurrence ; supprimer une occurrence est refusé et le message renvoie à agenda.remove ; changer le jour d'une série demande d'abord à la personne.
- L'écran et la tuile, dans les trois thèmes.
- Installer la démo : chaque outil planifié créé fait tourner aussitôt le planificateur de son type (`CoreScheduler.runTool`) ; noter la durée de l'installation, à comparer avec celle d'avant la fusion de la branche agenda.

## Graphique

- Créer un Graphique à la main : « Une vue », une couche « Une grille de pas », pas « Jour », période « Il y a 29 jours, début » → « Le moment même » ; une colonne `kcal` lisant une variable ; marque « Barres », x : « Pas », y : `kcal`. Enregistrer : trente barres, les jours sans valeur en trou hachuré ; toucher un trou : sa cause et les entrées à corriger, dont chacune ouvre son outil.
- Mettre le poids du même côté que les kcal : l'enregistrement est refusé et propose l'autre côté.
- Dans le formulaire, le choix d'une colonne d'un canal ne propose que celles de sa couche (après ses transformations) ; les colonnes d'un `fold` proposent celles d'avant lui.
- Ajouter une entrée au suivi que lit le graphique, revenir : le graphique l'a prise. Recréer l'écran : le graphique se redessine à l'identique.
- Thème rétro : les graduations en pointillé d'un pixel sur deux, les axes pleins.


## Copier, limites, fuseau, import, sauvegarde automatique (branche data-text, 2026-10-14)

- Conversation : un appui simple sur une bulle ne copie plus ; un appui long copie, avec une vibration. Message de l'IA à module : son texte puis ses questions ; en attente de validation : ses actions ; message système : résumé, lignes ✓/✗, détails. Les boutons et « Détails » dans la bulle répondent toujours à l'appui simple.
- La barre d'un texte sélectionné, dans les trois thèmes et les deux modes : un passage d'une note lue, d'une erreur, d'un titre de page, du Guide, puis d'un champ (au clavier ouvert, le curseur et le clavier restent) et d'un champ dans une fenêtre flottante. La barre du thème et jamais celle d'Android ; au-dessus de la sélection, dessous quand elle touche le haut ; Copier, Coller, Couper, Tout sélectionner dans la langue de l'app ; une appli de traduction installée ajoute son action ; tout tient à la ligne sur un écran étroit ; un toucher hors de la barre la ferme. Rétro : le mot pressé s'inverse ; cosy : la barre s'enfonce.
- Notes : appui long, « Copier » dans le menu. Journal, une entrée lue : « Copier », et un passage du contenu se sélectionne. Rapport de bug : « Copier ». Messages, Réglages › Journaux, la fiche d'une entrée (Données structurées, Questionnaire) : le texte se sélectionne ; dans une liste, l'appui long reste celui de la liste.
- Un nom (60) : coller un texte de 100 caractères le garde, le compteur « 100 / 60 » en erreur, l'enregistrement refusé avec la raison ; le compteur apparaît à 48. Une note de 3 000 caractères s'enregistre ; une consigne de Séance de 1 000 aussi.
- Réglages › Formats : le fuseau propose Europe/Lisbon avec son décalage, la recherche au-dessus de la liste la trouve en tapant « lisb » ; la langue propose « Português (Portugal) ». Le temps d'ouverture de la liste, en rétro surtout.
- Import d'un CSV dont une colonne s'appelle `created_at` : proposée « Écartée », la phrase dessous ; la mettre en « Un nouveau champ » : refusé dans ce qui manque ; la mettre sur la date : elle remplit la date.
- Sauvegarde automatique : choisir un dossier (`Documents/Treelune`), la carte dit « Aucune copie encore » puis, dans les dix minutes, « Dernière copie : … » et le fichier `treelune_auto_hour_….zip` paraît dans Fichiers ; rien de changé pendant une heure : pas de nouvelle copie ; une écriture, une heure plus tard : l'ancienne devient `day`. Relever dans Réglages › Journaux la ligne « Auto backup: … written in N ms » : au-delà de 50 000 ms app fermée, la copie est coupée (il faudra le service au premier plan). L'interrupteur sur Off : plus de copie. Installation neuve : un point sur le bouton Réglages de l'accueil et sur la tuile Données ; ouvrir Données : les deux points partent et ne reviennent pas. Désinstaller puis restaurer : « Dossier inaccessible » dans la carte, et les deux points reviennent jusqu'à la visite suivante ; rechoisir le dossier relance les copies. Le fichier `hour` se restaure par « Importer ».

## L'IA et les routines (branche ai-routines, `docs/design/ai-routines.md`)

- Créer une routine depuis sa zone : la page du message s'ouvre, vide ; l'enregistrer ; la démo réinstallée : ses trois routines ont leur message.
- En conversation, « crée une routine qui résume chaque lundi à 7 h les repas de la semaine passée » : la carte de validation dit « Création de la routine … » si la zone est protégée ; la routine paraît dans la zone sans la rouvrir, son message avec le texte et le pointeur sur le suivi des repas, la semaine passée en période ; son planning et son rattrapage dans leurs fenêtres. La lancer : le pointeur lit la bonne semaine.
- « Désactive-la », « renomme-la », « change son message » : chaque fois la zone suit ; « supprime-la » : elle part de la zone.
- Une routine à l'accès limité à une zone en `full`, dont le message demande de créer une routine : créée avec un accès dans le sien, refusée avec un accès vide ou plus large, la raison dans son historique.
- « Pourquoi ma routine a raté hier ? » : l'IA lit ses exécutions (heures en ISO, raison de fin, coût), puis une exécution, les erreurs seules, et répond depuis ce qu'elle a lu.
- Par le connecteur MCP : `automations` et `automation_config` répondent ; une création sur une zone hors du masque du client est refusée.

## Les bugs du téléphone du 2026-10-16 et le changement de config (`docs/design/config-change.md`)

Vu le 2026-10-16 sur la debug : le bouton « + » du Journal dans la page, la poignée d'une note limitée à son icône, les cartes d'une zone à la largeur de la grille en rétro, le coup d'œil sur les outils dans l'accueil du Guide, la sélection d'un texte du Guide, les bornes d'une échelle resserrées (comptes, nouveau champ), une séance arrêtée qui garde ses étapes et sa correction étape par étape. Reste :

- La carte des variables d'une zone avec un nom long : la valeur passe sous le nom, rien ne se coupe.
- Changer de thème depuis Réglages › Interface, revenir à l'accueil : pas de plantage (la marque de sauvegarde automatique).
- Copier une entrée de Journal et une note qui ont des champs ajoutés (une échelle, un choix de moins de 8 options, un classement, une référence) : les lignes « Nom : valeur » après le texte, une échelle en barre, un choix parmi ses options, une référence par son nom, un champ vide absent.
- Un choix à une réponse passé à plusieurs : enregistré sans rien demander, chaque entrée garde sa réponse. Plusieurs vers une : seules les entrées à plusieurs réponses perdent la valeur, comptées.
- Le type de valeur d'un Suivi changé : les entrées supprimées, comptées dans la fenêtre.
- Demander à l'IA de resserrer une échelle (conversation) : la carte de validation dit les comptes, même dans une zone non protégée ; refusée, rien ne change ; acceptée, les valeurs hors bornes partent. « Protéger les données passées » décoché : l'IA est refusée et doit renvoyer avec `confirm_migration`. Par le connecteur : la notification d'accord. Une routine qui le fait : passe si son accès le permet.
- Objectif : valider une tentative, changer un critère (seuil, critère retiré), rouvrir la tentative : elle est jugée selon ses anciens critères, la valeur saisie du critère retiré toujours là ; revalidée, son jugement aussi.
- Une séance notée « Faite » sans l'écran : ses étapes toutes faites, sans temps ; une séance finie avant ce changement se corrige toujours par ses comptes.

## Lecture seule pour l'IA (branche user-only, `docs/design/unified-fields.md`)

- La case « Lecture seule pour l'IA » : sur un champ ajouté à un Journal, après « Toujours afficher » ; sur un critère saisi d'Objectif, une fois le type choisi, absente d'un critère lu ; « Validation fermée à l'IA » dans la section Temps de l'Objectif. Cochée, enregistrée, la config rouverte : toujours cochée ; changer la case ne demande aucune confirmation.
- Le champ coché se remplit toujours à l'écran. Demander à l'IA, en conversation, de le remplir : refusé, elle dit que le champ lui est fermé, rien n'est écrit, pas même le reste de l'entrée. Lui demander de décocher la case : refusé ; de la cocher sur un autre champ : fait.
- Un Objectif fermé à l'IA : elle dit où en est la tentative, mais « valide-la » et « rouvre-la » sont refusés ; validée et rouverte à l'écran, sans changement.
- Par le connecteur, et par une routine qui écrit ce champ : refusé, l'erreur dans la réponse du connecteur, dans l'historique d'exécution de la routine.

## Les textures (`docs/design/tool-texture.md`, branches tool-texture et texture-background)

- Après la migration 75 : l'app démarre, rien ne change à l'écran ; Réglages › Interface montre Textures sur « Discrètes » ; une sauvegarde faite avant s'importe, ses zones sans texture.
- Config d'un outil, puis d'une zone : sous le bouton de l'icône, deux lignes, « Couleur de l'icône » et « Texture », chacune avec une petite tuile de la chose telle qu'elle est choisie et son nom. Toucher une ligne ouvre sa page, le chemin en haut, sans les boutons du bas ; chaque choix une petite tuile de la chose (la texture dans sa couleur, la couleur avec sa texture), son nom dessous, le choix en cours marqué d'une coche ; une zone sans icône n'offre pas « Son icône ».
- Toucher un choix le prend et ramène au formulaire, la ligne à jour ; la flèche et la touche retour du téléphone ramènent sans rien changer ; tourner l'écran sur une page de choix la garde ouverte. Annuler après un choix demande confirmation, Enregistrer le garde. Une identité (Réglages › Identités) garde sa grille de couleurs sur place, en petites tuiles.
- Une texture choisie se voit sur la tuile après l'enregistrement, et sur tout le fond de la page de la chose, bords compris, de la tête au bas de l'écran ; pas sur sa page de config, ni sur le formulaire d'une entrée ; la fiche ou l'historique ouverts dans un outil la gardent. Un outil d'un type qui ne la montrait pas en tête (Agenda, Calcul…) l'a aussi.
- Dans les trois thèmes, aux deux modes, une zone dont les tuiles portent chacune un motif, sans et avec couleur d'icône : chacune se reconnaît, aucune ne gêne la lecture de son nom ni de son résumé (un Graphique compris), et les quinze se voient à peu près autant ; puis Textures sur « Marquées ». Ce qui ne va pas se règle au banc des textures (`./run themes`).
- Une page texturée, dans les trois thèmes, aux deux modes, aux deux intensités : la tête de page comme sur toute page (sans bande dans le défaut et le rétro, la pilule du cosy unie) ; le titre, un titre de section et la phrase d'une liste vide se lisent sur le motif ; les cartes des entrées sont pleines, aucun motif dessous ; dans le cosy, les petits points du sable ne se voient pas sous le motif, et reviennent sur une page sans texture. Ce qui se lit mal se juge d'abord à la vue « page » du banc des textures (`./run themes`).
- Rétro : un motif tombe sur les pixels du thème, sans flou, aux crans de taille 0 et 3, sur la tuile comme sur le fond de la page (une bande de quelques pixels sans motif peut rester aux deux bords, la marge que le thème laisse hors de sa grille) ; en mode d'édition de la grille, une tuile `CHECKER` mise à l'écart se distingue encore du damier.
- Par l'IA : « Mets des vagues sur la zone Santé et l'icône en fond sur le Poids » — UPDATE_ZONE prend `texture`, l'outil la reçoit dans sa config ; « en papier » est refusé avec les quinze noms ; « retire l'icône de la zone Santé » quand elle porte `ICON` est refusé en disant pourquoi.

## Les sources de l'attente (branche waiting-sources, `docs/design/waiting-sources.md`)

- Après la migration 76 : aucun point nouveau sur le bouton flottant, les zones ni les routines ; Réglages et le Guide marqués comme avant, s'ils l'étaient.
- Écrire à l'IA, fermer la conversation avant sa réponse : le point paraît sur le bouton flottant quand elle répond ; ouvrir la conversation l'efface. Rester dans la conversation pendant la réponse : aucun point en la fermant.
- Envoyer un message, passer l'app en arrière-plan pendant la réponse, revenir sur la conversation ouverte : pas de point ensuite. Revenir sur un autre écran : le point est là.
- Une routine lancée à la main, réglée « Toujours » : à sa fin, une notification au nom de la routine avec le début du résumé ; la toucher ouvre l'exécution, le résumé et la ligne de faits en tête. Avant de la toucher : le point sur la tuile de la zone, la carte de la routine et sa ligne dans l'historique, la première phrase du résumé sous sa date ; ouverte, les trois points s'effacent.
- La même réglée « En cas de problème », qui finit bien : ni notification ni point. Réglée « Jamais » : rien, le résumé lisible dans l'exécution.
- Dans l'exécution, sous « Détails » : la confirmation demandée avec le résumé ; une IA qui confirme sans résumé reçoit le rappel une fois.
- La fenêtre de la routine (Configurer) montre « Me signaler la fin » ; changé et enregistré, il tient à la réouverture.

## Les incarnations (branche incarnations, `docs/design/incarnations.md`)

- Après la migration 77 : l'app démarre, rien ne change ; une sauvegarde faite avant s'importe, sans identité ; une sauvegarde faite après rend les identités, et l'identité des zones, des routines et des conversations.
- Réglages › Identités › Importer, avec un fichier d'identité au format 2 copié sur le téléphone (l'export de Stan Prokopenko une fois personae passé au format 2, ou l'exemple de `docs/IDENTITY_FILE.md`) : la fenêtre dit son nom, sa date de publication s'il en a une, ses langues, la langue de l'app présélectionnée ; importée, sa tuile paraît. Un fichier qui n'en est pas un (une sauvegarde de l'app, un export de personae au format 1) est refusé en disant le champ ou le format en cause.
- Par le connecteur (un client à l'accès « toute l'app ») : `import_identity` avec le texte d'un fichier au format 2 crée l'identité, ses textes « importé » ; la même encore, sans `replace`, est refusée en disant les retouches et langues perdues ; avec `replace: true`, remplacée. La protection du niveau de l'app active, la notification d'accord vient d'abord. Un client limité à des zones est refusé.
- Après la migration 78, une identité importée avant : ses textes disent « importé », une retouche les fait passer à « écrit dans l'app », et un nouvel import la nomme parmi les retouches perdues.
- L'écran de l'identité : Voix, Esprit, Portrait mis en forme (titres, listes, sans astérisques), leur taille et « importé » ; « Textes en » change de langue ; le Bagage, quatre ressources, chacune se lit et se replie. Un long texte se lit et se modifie sans ralentir.
- Modifier la voix, enregistrer : « écrit dans l'app » ; réimporter le fichier : la confirmation nomme `fr · voice` parmi les retouches perdues, et confirmée, la voix revient. Choisir une autre icône et une couleur, enregistrer : la tuile les montre.
- Nouvelle identité (un nom, `fr`) : son écran, rien d'écrit ; y ajouter une ressource : sa clé tirée du titre.
- Config d'une zone : Identité, Stan Prokopenko ; la zone montre sa tuile sous l'en-tête, son bouton ouvre une conversation neuve, le bouton d'identité de la barre du haut à son icône. Envoyer « Comment tu dessines une main ? » : la réponse dans sa voix, en français ; le bouton d'identité ne change plus (un toucher ouvre son nom en lecture seule, la raison dessous, fermé par OK ; son nom touché ouvre sa page, le fil d'Ariane « Accueil › le titre du chat », le retour ou ce titre ramenant au chat). Dans le journal, la ligne « Prompt data built » donne la taille du L1 ; dans les statistiques de la session, les tokens du premier tour puis du second, mis en cache ou non : c'est la mesure qu'attend personae, qui produit les identités.
- Dans cette conversation, « lis ton lexique » : la ressource arrive sans fenêtre d'accord, malgré ses 12 000 caractères et plus ; la redemander : « déjà dans cette session ». Lui demander une note : son texte dans sa voix, le nom de l'outil neutre.
- Une conversation ouverte par le bouton flottant : le bouton d'identité sur l'IA de l'app (le bonhomme) ; avant le premier message, la toucher et choisir Stan Prokopenko.
- Une routine créée dans la zone : Identité présélectionnée sur Stan Prokopenko ; lancée, son exécution parle dans sa voix. Demander à l'IA de créer une routine sans dire qui parle : elle donne `identity_id` (refusée sinon, en le disant).
- Supprimer Stan Prokopenko tant que la zone ou la routine le nomme : refusé, en les nommant. Les retirer, supprimer : la conversation passée se relit, son composeur remplacé par la raison. Supprimer un fournisseur d'IA qu'une routine utilise : refusé de même.
- Par le connecteur : IDENTITY et IDENTITY_RESOURCE au-delà du seuil arrivent en tranches, chacune disant où reprendre ; UPDATE_IDENTITY attend l'accord par notification quand le niveau de l'app est protégé.

## Les lectures envoyées une fois par session (branche read-once, `SessionReads`)

- Dans une conversation, faire lire à l'IA le schéma d'un outil, puis le redemander : « déjà inclus ». Lui faire ajouter un champ à la config de l'outil, puis écrire une entrée qui le remplit : le nouveau schéma arrive, précédé de « il remplace », et l'entrée passe.
- Même conversation : ajouter un champ à l'écran, revenir, demander une entrée : le schéma revient avant l'écriture, « il remplace », et l'entrée porte le champ.
- Une conversation avec une identité : « lis ton lexique », modifier la ressource à l'écran, « relis ton lexique » : le nouveau texte, « il remplace » ; la redemander : « déjà dans cette session ».
- Une conversation commencée avant cette version : un schéma déjà lu y repart une fois en entier, puis « déjà inclus ».

## Les bugs du téléphone du 2026-10-17

- La sauvegarde automatique, dossier choisi, données inchangées depuis plus d'une heure : la carte « Sauvegarde automatique » paraît une fois, puis plus avant une heure, et non plus à chaque minute ; dans le journal, une seule lecture de la base par heure. Une donnée changée : la copie s'écrit au tour de l'heure suivant.
- Accès externe ouvert, par Tailscale puis par le relais : « Fermer l'accès » touché, l'écran dit aussitôt « Fermeture de l'accès… » avec la roue, sans bouton, jusqu'à « Fermé » ; la tuile des réglages rapides s'éteint aussitôt, et la touchée pendant la fermeture ne rouvre rien. Même chose depuis la notification.
- Liste : un appui long sur un élément, coché ou non, dit « copié » ; collé ailleurs, son nom puis ses champs remplis, un par ligne. Un toucher l'ouvre toujours, sa poignée le déplace toujours.

## Le gras et l'italique d'un texte lu (branche text-emphasis)

- Une identité en Markdown (`**gras**`, `*italique*`, `***les deux***`, un gras dans un titre `##`), puis le Guide, dans les cinq thèmes et les deux modes : le gras et l'italique se voient sans casser la ligne ; défaut, suisse et encre en vrai italique, cosy penché par Android (la pente de la planche du 2026-10-17), rétro le gras à l'encre vive et l'italique de Cartouche, ses pixels entiers, ni coupé au début ni à la fin d'une ligne, ni collé à la lettre droite qui le suit ; un titre de section du suisse reste en capitales.
- Un passage sélectionné qui traverse un gras se copie sans ses marques.

## Les conditions typées à l'écriture (branche typed-conditions, `docs/design/typed-conditions.md`)

- Dans une conversation : « crée un Objectif sommeil moyen d'au moins 7 h sur Sommeil » ; la config de l'Objectif, rouverte à l'écran, montre 7 h, et la tentative se juge sans erreur. Redemander en exigeant « écris 25200000 » : refusé, le message dit d'écrire « PT7H », et l'IA corrige.
- Graphique « Repas par moment » : demander à l'IA un filtre « moment = déjeuner » : refusé, le message dit que `=` ne compare pas un choix et donne `in` ; puis un filtre sur « déjeunner » : refusé, le message dit d'ajouter l'option d'abord.
- À l'écran, une condition d'Objectif ou de Graphique enregistrée comme avant : rien ne change ; la config d'un Graphique dont un filtre vise un champ supprimé de son outil : l'enregistrement est refusé et nomme la condition.

## Les fournisseurs créés (branche ai-providers, `docs/design/ai-providers.md`)

- La mise à jour depuis la 0.7.0 avec ses données (migration 80) : Réglages › Fournisseurs IA ne montre que les fournisseurs configurés avant, sous leur nom d'avant, l'actif marqué « Par défaut » ; une routine qui nommait une variante jamais configurée dit « Sans fournisseur d'IA », éteinte ; les conversations passées se relisent, leurs coûts gardés.
- Liste vide (installation neuve) : la phrase sur ce qu'est un fournisseur et « Ajouter un fournisseur » ; le choix des quatre sortes, chacune sa ligne ; Autre serveur : le formulaire, nom prérempli « Autre serveur », « Prix » sur « Liste LiteLLM », « Réglés à la main » montre les trois cases ; enregistré, il est par défaut.
- Un deuxième fournisseur du même nom : refusé en le disant. Dupliquer : « … (copie) », la clé recopiée ; « Mettre par défaut » déplace la marque.
- Supprimer un fournisseur qu'une routine nomme : refusé, la routine nommée. Supprimer le défaut quand un autre reste : la fenêtre demande lequel le remplace ; le dernier supprimé : une nouvelle conversation dit d'en ajouter un.
- Une conversation nouvelle : son premier message part chez le défaut ; ses paramètres montrent ce fournisseur au-dessus de la réflexion ; en choisir un autre : le message suivant y part, la réflexion propre de la conversation retirée. Changer le défaut ne change pas une conversation commencée.
- Une routine nouvelle : le défaut présélectionné ; sans aucun fournisseur, elle se crée éteinte, et l'activer ou la lancer est refusé en disant d'en choisir un. Demander à l'IA de créer une routine : elle donne un `provider_id` de la liste, ou `null`.
- Un serveur à soi aux prix à 0 : la conversation montre un coût de 0, plus « coût inconnu » ; un prix d'entrée en cache vide alors que le serveur en rapporte : coût inconnu.
- Le Guide, « Brancher une IA » : l'étape « Ajoute un fournisseur » se valide à l'enregistrement d'un fournisseur nouveau, et seulement là.

## Les écrans du chat, des identités et des fournisseurs (2026-10-17, sur develop)

- Réglages › Identités : la liste s'ouvre en tuiles ; une identité ouverte, puis fermée, joue les sons d'entrée et de retour du thème et se nomme dans le fil d'Ariane ; sa couleur se choisit sur une page, la ligne la montrant ; supprimée, on revient à la liste sans son.
- Réglages › Fournisseurs d'IA : « Ajouter » ouvre le choix de la sorte, qui laisse la place à la config du nouveau (le retour mène à la liste) ; enregistrer, supprimer ou mettre par défaut ramène à la liste ; « Dupliquer » ouvre la copie par-dessus ; les sons à chaque passage.
- La barre du chat : identité, historique, statistiques, paramètres, stop, fermer, le titre sous les boutons ; le toucher renomme la conversation, ou la routine dans son exécution et sur son message ; une routine ouverte par la bulle porte son nom.
- Les paramètres d'une conversation, ouverts par l'engrenage en un seul dialogue (accord, fournisseur, réflexion), fermés par OK ; un fournisseur dont la clé ne s'ouvre pas sur ce téléphone, ou sans clé : la réflexion le dit, sans planter (release comprise) .
- Les statistiques d'une conversation qui a des appels : le coût total et ses tokens, le coût par modèle après un changement de fournisseur, le nombre d'appels, le dernier envoi qui grandit d'un message à l'autre, les écritures faites et refusées ; les montants jamais coupés.
- Le composeur : le statut sur sa ligne au-dessus des blocs, à la hauteur de son texte ; T premier de la rangée, Envoyer seul plein à droite, dans les cinq thèmes ; Paramètres, Modifier, Configurer pleins en rétro et dans le thème par défaut, à juger.
- Les marges au cran de taille 0 puis 3, dans les quatre thèmes qui en ont : les espacements, la marge de bord et les écarts de la grille gardent leur longueur ; en rétro, seuls l'intérieur des cadres et les écarts de la grille des tuiles grandissent.

## Les bugs du téléphone du 2026-10-19

- Une note touchée sur la tuile des Notes, puis fermée ; l'app retirée des récents pendant qu'une routine est active ou que l'accès externe tourne, puis rouverte : la page de l'outil, sans note ouverte. De même pour une entrée de Journal, un événement d'Agenda, un questionnaire touchés sur leur tuile.
- Les champs de l'utilisateur sous une note, un élément de Liste, un événement d'Agenda, un message, une entrée du Suivi : un champ par ligne, un texte long revenant à la ligne sur toute la largeur de la carte, le nom en tête ; de même avec les noms des champs masqués.
- Notes aux titres activés : une note créée le titre vide s'enregistre, sans titre sur sa carte ; un texte trop long refusé dit pourquoi, le dialogue restant ouvert.
- Notes : le bouton « Ajouter » du thème sous la dernière note, seul quand l'outil n'en a aucune, plus de carte « AD » ; la note ajoutée arrive à la fin, au-dessus du bouton ; « Ajouter au-dessus » dans le menu d'une note, inchangé.
- Les boutons d'une fenêtre (supprimer un outil, une confirmation, une date) au cran de taille 3, dans chaque thème : côte à côte s'ils tiennent, sinon l'un sous l'autre en pleine largeur, l'action principale en haut ; jamais un bouton écrasé ou absent.
- Un outil vide en Étendu, en Carré et en Complet : la tuile n'a que sa ligne d'en-tête, les tuiles du dessous remontent ; à moitié rempli, elle s'arrête sous sa dernière ligne ; plein, et un Graphique, elle a sa hauteur d'avant ; en mode d'édition de la grille, Étendu et Carré montrent toutes leurs cases ; dans les huit thèmes, en une colonne aussi.
- Un Agenda en Complet dont le prochain événement est dans plus de sept jours : il le montre, avec tous les suivants ; jamais moins qu'en Étendu.
- Effacer, à côté de Choisir une icône : une zone sans icône ensuite (sa tuile son nom seul, sa texture « icône » retirée avec), un outil revenu à l'icône de son type, une identité revenue à l'icône par défaut ; le bouton absent quand il n'y a rien à retirer ; les deux boutons l'un sous l'autre au cran de taille 3.
- Le thème encre et lavis : la marge de bord et les grands écarts resserrés (l'accueil, une zone, les Réglages) ; rien ne touche le bord de l'écran.
- Réglages › Interface, « Titre de l'accueil » : désactivé, l'accueil garde sa ligne de boutons (Réglages, Guide, Configurer), sans le nom de l'app ni sa marque, et les zones remontent ; vu dès le changement, avant l'enregistrement ; la migration 81 sur la release et ses données, le titre toujours là ; une sauvegarde d'avant restaurée, de même.
