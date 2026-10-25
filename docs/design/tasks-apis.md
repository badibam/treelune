# Tâches et API

Ce qui reste de la conception « Tâches, API et fichiers » : sa partie A (les fichiers image, audio, vidéo et texte, la désignation jusqu'au champ, la valeur par défaut étendue, l'envoi d'un fichier par le connecteur) est en place, et la doc durable la décrit (`docs/BRICKS.md`, « Designating, reading, fixing » ; `docs/DATA.md`, « Kept files » et « FILE » ; `docs/EXTERNAL_ACCESS.md`, « Sending a file »). Restent les API et les Tâches, avec lesquelles le cas de départ, une séance guidée audio régénérée chaque nuit d'après les retours notés à la fin de la précédente, tourne seul.

## 1. Les API

L'app confie un travail à l'extérieur et en reprend le résultat, toujours en appelant, jamais en étant appelée : un téléphone est un bon client et un mauvais serveur. Le connecteur externe reste la porte inverse, quand l'extérieur vient vers l'app.

- Déclarées dans les réglages, à côté des fournisseurs d'IA : un nom, une adresse, une clé (dans un en-tête ou un paramètre de l'adresse, gardée comme une clé de fournisseur, jamais copiée par une sauvegarde).
- Une API nomme ce qu'elle rend, sans connaître aucun outil : chaque valeur a un nom, un type et un chemin dans la réponse JSON (`temperature`, un nombre, à `current.temperature_2m`) ; un fichier, l'adresse où le télécharger. Où vont ces valeurs, ce sont les étapes d'une Tâche qui le disent.
- Réponse immédiate : la réponse est le résultat. Réponse différée : la réponse donne un identifiant (son chemin), puis l'app interroge une adresse de suivi jusqu'à ce que le chemin qui dit « prêt » le dise, de moins en moins souvent, avec un délai au-delà duquel l'échec est dit.
- Un fichier rendu entre en deux temps, comme une photo d'un formulaire (`files.prepare_image`) : téléchargé et mis de côté, puis propriété de l'entrée qui l'écrit.
- Hors portée : les réponses en plusieurs pages, OAuth, les transformations de valeurs. Ce qui en a besoin passe par un petit adaptateur hors de l'app.
- Le bouton **Essayer** fait un appel d'essai et montre la réponse brute à côté des valeurs que les chemins en tirent ; il n'écrit rien.
- Un appel (`apis.call`) passe par l'aiguillage comme toute opération. Immédiat, celui qui appelle reçoit la réponse : l'IA la lit comme le résultat d'une commande. Différé, seule une Tâche sait attendre ; l'IA qui en a besoin lance une Tâche. Il n'y a pas d'appel à la main hors d'une Tâche : c'est une Tâche d'une seule étape.

Exemples : une API météo (Open-Meteo, sans clé) pour la réponse immédiate ; une boîte aux lettres sur un hébergement, qui transmet le travail à un service et garde son résultat, pour la différée.

## 2. Les Tâches

Une Tâche est une suite d'instructions adressées à des destinataires, que l'app exécute elle-même, sans IA : utiliser un outil, lancer une routine, appeler une API. Sœur de la routine : la routine fait décider une IA, la Tâche exécute ce qui est écrit.

- Une étape : un destinataire, une opération, ses paramètres. Toutes les opérations que l'IA d'une routine peut appeler, opérations d'outil (`complete` de Séquence, l'envoi d'un message) comme écritures génériques (`tool_data.create`, la configuration d'un outil), avec la même vérification, l'historique et l'annulation, et les mêmes réglages d'accès qu'une routine.
- Le formulaire d'une étape est généré depuis la déclaration de l'opération, comme les formulaires de configuration. Chaque paramètre se remplit par un Terme : une valeur, une Lecture, une variable, et un genre nouveau, le résultat d'une étape précédente (« l'entrée créée à l'étape 2 », « `temperature` de l'étape 1 »).
- Une exécution dure : en attente sur un appel différé pendant des heures, elle survit à un redémarrage et reprend à l'étape suivante. Son historique montre chaque étape, et la réponse brute d'un appel, bornée en taille, pour comprendre un échec.
- Le traitement fixe d'un résultat est fait par les étapes suivantes. Quand il faut juger (interpréter, résumer, choisir), la Tâche lance une routine en lui passant le résultat : une routine reçoit alors une donnée au lancement, qui arrive dans son message comme une pièce jointe.
- La même enveloppe que la routine : une tuile parmi les outils de sa section, sa page, son activation, ses exécutions. Les déclencheurs sont une brique commune aux deux : à la main, par un planning (avec le rattrapage des exécutions manquées), plus tard par un événement (`ExecutionTrigger.EVENT`, prévu, pas fait), ce qu'attendent aussi les événements du cœur (`TODO.md`, « À concevoir »).

À trancher : une étape qui attend une validation de l'utilisateur avant de continuer (relire ce qu'une étape précédente a produit, le corriger ou demander qu'il soit refait), comme elle attend déjà une réponse différée — comment elle s'inscrit dans les flux : où et comment on la présente, ce que devient une correction, ce que fait un refus ; si l'IA peut lancer une Tâche (elle ne lance pas une routine aujourd'hui) ; les services du cœur dont les paramètres ne sont pas déclarés sous une forme dont un formulaire peut naître (à mesurer) ; le mot, que la Liste emploie déjà pour ses éléments à cocher.

## 3. Le cas de départ

Une séance guidée audio, régénérée chaque nuit d'après les retours notés à la fin de la précédente.

- Un outil Fiches de séances : par Fiche, le script et ses audios (un par partie de la séance, chaque champ d'un seul fichier).
- Une Séquence : une étape par partie, chacune suivant l'audio d'une Fiche par une Lecture (en place), une pause entre elles ; ses champs supplémentaires : une Référence à la Fiche jouée, dont la valeur par défaut est la Fiche du jour (en place), et les retours.
- Une Tâche chaque nuit : appeler l'API de la boîte aux lettres avec le contexte et les derniers retours et scripts, lus par des Lectures ; attendre ; le script relu et validé par l'utilisateur avant que la voix et le montage soient faits (partie 2, à trancher) ; créer la Fiche avec ce que l'API rend ; faire suivre à chaque étape le champ de cette Fiche, et lui donner la valeur par défaut de la Référence.
- Hors de l'app : la boîte aux lettres, qui garde le travail et son résultat et déclenche le service ; le service, qui écrit le script, le fait dire par une synthèse vocale et monte l'audio.

## Découpage

- **A**, en place : les fichiers, la désignation jusqu'au champ, la valeur par défaut, l'envoi d'un fichier par le connecteur. Le montage du cas de départ se fait à la main par le connecteur externe : une IA extérieure dépose les audios dans une Fiche et fait suivre la Séquence.
- **B** : les API (partie 1) et les Tâches (partie 2), avec lesquelles la boucle tourne seule.
