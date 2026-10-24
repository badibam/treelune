# Tâches, API et médias

Cinq briques qui se construisent l'une après l'autre, chacune utile seule : des médias audio et vidéo, le pointeur vers une entrée, la valeur par défaut étendue, les API, les Tâches. Elles naissent d'un même cas, une séance guidée audio régénérée chaque nuit par un service extérieur d'après les retours notés à la fin de la précédente (dernière partie), mais aucune ne lui est propre.

## 1. Médias : image, audio, vidéo

La brique Image (`docs/DATA.md`, « IMAGE ») s'étend au son et à la vidéo : une seule brique, des médias, la même table de fichiers, les mêmes règles de propriété (un fichier appartient à une entrée ou à un outil, part avec lui, est supprimé quand on le retire), le même usage aux deux endroits. L'identifiant d'un média est la façon dont son fichier est rangé, pas une référence.

- Dans une entrée (le champ Audio d'une Fiche), un champ média tient ses propres fichiers : une entrée stocke des valeurs, comme pour ses autres champs ; seul un champ Référence y est une adresse.
- Dans une configuration (les médias d'une étape), chaque média est un Terme : un fichier à soi (une constante) ou le champ média d'une entrée suivi (une Lecture, partie 2). Les images d'une étape y passent aussi ; leurs valeurs actuelles sont des constantes.

- Une étape de Séquence porte une consigne écrite facultative, des images, et au plus un audio ou une vidéo. La vidéo se joue écran allumé, à l'écran de la séance. Une fin d'étape de plus : « à la fin du média ». L'audio se joue écran éteint, par le service qui fait déjà tourner une séance (`SequenceRunService`).
- Le sélecteur d'un Terme, le même pour tous les types, ne montre que ce qui convient au type attendu :
  - **Valeur fixe** : saisir (taper un nombre, un texte, une date ; pour un média, ajouter un fichier depuis l'appareil, plus tard l'enregistrer), ou **copier depuis un outil** (on va jusqu'au champ d'une entrée, sa valeur du moment est recopiée une fois ; un média copié devient le fichier de l'étape, par la règle actuelle des images).
  - **Lecture** : suivre un champ d'une entrée (partie 2), relu à chaque usage ; pour une étape, ce qu'il contient au moment de jouer.
  - **Variable**, là où une variable du type existe (aucune ne tient un média aujourd'hui).
- Copier et suivre passent par le même parcours jusqu'au champ et donnent des résultats opposés : les libellés disent l'effet.

- La copie d'un média étranger reste ce que fait une constante : une valeur possède son fichier. Elle est silencieuse aujourd'hui (l'IA qui écrit l'identifiant d'une photo de la conversation ne sait pas qu'elle a pris une copie) : le résultat de l'écriture le dit.

À trancher : les formats gardés, la taille au-delà de laquelle un fichier est refusé, la réduction (une vidéo réduite comme une photo l'est), ce que l'IA lit d'un média (sa durée et sa taille, comme une image ses dimensions).

Option non tranchée, posséder ou retenir : un endroit tient un fichier en le possédant (il part avec lui, est supprimé quand on le retire) ou en le retenant (il le nomme sans le posséder, et le fichier ne peut disparaître tant qu'il le nomme) ; un fichier vit tant qu'il a un propriétaire ou une rétention. Chaque endroit déclare sa manière : un choix, pas une exception. L'annulation retient déjà, sans le dire (le nettoyage au démarrage recalcule ce que l'historique des écritures nomme, `Images.kt`).

- En base : la table des médias (l'actuelle `images`, avec `kind` et `duration_ms`) garde au plus un propriétaire, qui ne l'emporte plus en partant (`ON DELETE SET NULL`) ; une table `media_retentions` (`media_id`, `reason`, l'entrée ou l'étape de l'historique qui retient, en cascade).
- Ce qui n'est plus que retenu se liste par raison, avec sa taille (« 14 fichiers, 230 Mo, gardés par l'historique des séances »), et se libère en supprimant ces rétentions.
- Premier usage possible : l'historique d'une séance qui garde, pour chaque étape jouée, d'où venait son média (une Référence qui descend jusqu'à un champ, « Fiche du 24 › Induction », elle aussi non tranchée) et retient le fichier joué, pour qu'une séance faite ne change plus.
- À mesurer : ce que coûte de faire écrire ses rétentions à l'historique des écritures.

## 2. Le pointeur vers une entrée, la Lecture d'un média

Une référence garde une adresse et suit sa source ; une copie prend une valeur et coupe le lien. Le Terme (`docs/BRICKS.md`) porte déjà les deux : une constante est une valeur, une Lecture une adresse relue à chaque usage. Suivre le média d'un outil est une Lecture.

- Une Référence désigne une chose de l'app (l'app, une zone, un outil, une variable, une entrée : `docs/DATA.md`, « REFERENCE »), choisie par le fil d'Ariane App › zone › outil › entrée ; elle dit laquelle, jamais ce qu'elle contient, et ne descend pas dans un champ. Une Lecture va chercher le contenu d'un champ, et ne vit que dans une configuration : une entrée est la trace du passé, et un champ qui suivrait une source la ferait changer après coup ; une entrée qui doit dire de quoi elle parle tient une Référence (une récolte de la démo désigne sa plante).
- L'entrée existe déjà comme chose (`{"kind": "ENTRY", "id"}`). Ce qui manque : une sélection d'entrées qui parte d'une entrée choisie, pas seulement d'un outil ou d'une zone (`docs/BRICKS.md`, « Entry selection »).
- La Lecture d'un champ média, sur une entrée précise, avec la réduction « sa valeur ». Aujourd'hui `Reduction.forType` n'en donne aucune à IMAGE (`core/reading/FieldReading.kt`).
- La référence vise le champ, pas le fichier : un audio remplacé dans la Fiche est suivi, comme sa suppression. Pointer l'identifiant du fichier ne suivrait que la suppression, et le fichier remplacé disparaît avec la règle de propriété.
- Un champ média tient une liste. Une Lecture n'en vise qu'un limité à un seul média (`max` 1).
- Une source supprimée laisse l'étape sans média : c'est le cas de la vérification « qui dépend de ça ? » avant de supprimer (`TODO.md`, « À concevoir »).

## 3. La valeur par défaut

- Permise sur une Référence. `docs/DATA.md` l'exclut aujourd'hui avec DATE, DATETIME et IMAGE : retrouver pourquoi avant de lever l'exclusion.
- Pré-remplie la première fois qu'on remplit une entrée née du système, dans les champs encore vides : l'écran « Terminer la séance » de Séquence, la réponse à une invitation de Questionnaire, les champs d'une tentative d'Objectif. Aujourd'hui ces trois formulaires partent des valeurs de l'entrée seules (`CustomFieldsInput` sans `newEntry`) ; la Liste, les Notes, le Journal, les Fiches, l'Agenda et le Suivi pré-remplissent déjà à la création. Une valeur par défaut est posée pour une raison : son absence là était un oubli.
- Une séance marquée faite sans l'écran (`complete`) ne passe par aucun formulaire et ne la reçoit pas.

À trancher : un champ montré à la fin d'une séance sans être à saisir (un texte lu, pas une réponse).

## 4. Les API

L'app confie un travail à l'extérieur et en reprend le résultat, toujours en appelant, jamais en étant appelée : un téléphone est un bon client et un mauvais serveur. Le connecteur externe reste la porte inverse, quand l'extérieur vient vers l'app.

- Déclarées dans les réglages, à côté des fournisseurs d'IA : un nom, une adresse, une clé (dans un en-tête ou un paramètre de l'adresse, gardée comme une clé de fournisseur, jamais copiée par une sauvegarde).
- Une API nomme ce qu'elle rend, sans connaître aucun outil : chaque valeur a un nom, un type et un chemin dans la réponse JSON (`temperature`, un nombre, à `current.temperature_2m`) ; un fichier, l'adresse où le télécharger. Où vont ces valeurs, ce sont les étapes d'une Tâche qui le disent.
- Réponse immédiate : la réponse est le résultat. Réponse différée : la réponse donne un identifiant (son chemin), puis l'app interroge une adresse de suivi jusqu'à ce que le chemin qui dit « prêt » le dise, de moins en moins souvent, avec un délai au-delà duquel l'échec est dit.
- Un fichier rendu entre en deux temps, comme une photo d'un formulaire (`files.prepare_image`) : téléchargé et mis de côté, puis propriété de l'entrée qui l'écrit.
- Hors portée : les réponses en plusieurs pages, OAuth, les transformations de valeurs. Ce qui en a besoin passe par un petit adaptateur hors de l'app.
- Le bouton **Essayer** fait un appel d'essai et montre la réponse brute à côté des valeurs que les chemins en tirent ; il n'écrit rien.
- Un appel (`apis.call`) passe par l'aiguillage comme toute opération. Immédiat, celui qui appelle reçoit la réponse : l'IA la lit comme le résultat d'une commande. Différé, seule une Tâche sait attendre ; l'IA qui en a besoin lance une Tâche. Il n'y a pas d'appel à la main hors d'une Tâche : c'est une Tâche d'une seule étape.

Exemples : une API météo (Open-Meteo, sans clé) pour la réponse immédiate ; une boîte aux lettres sur un hébergement, qui transmet le travail à un service et garde son résultat, pour la différée.

## 5. Les Tâches

Une Tâche est une suite d'instructions adressées à des destinataires, que l'app exécute elle-même, sans IA : utiliser un outil, lancer une routine, appeler une API. Sœur de la routine : la routine fait décider une IA, la Tâche exécute ce qui est écrit.

- Une étape : un destinataire, une opération, ses paramètres. Toutes les opérations que l'IA d'une routine peut appeler, opérations d'outil (`complete` de Séquence, l'envoi d'un message) comme écritures génériques (`tool_data.create`, la configuration d'un outil), avec la même vérification, l'historique et l'annulation, et les mêmes réglages d'accès qu'une routine.
- Le formulaire d'une étape est généré depuis la déclaration de l'opération, comme les formulaires de configuration. Chaque paramètre se remplit par un Terme : une valeur, une Lecture, une variable, et un genre nouveau, le résultat d'une étape précédente (« l'entrée créée à l'étape 2 », « `temperature` de l'étape 1 »).
- Une exécution dure : en attente sur un appel différé pendant des heures, elle survit à un redémarrage et reprend à l'étape suivante. Son historique montre chaque étape, et la réponse brute d'un appel, bornée en taille, pour comprendre un échec.
- Le traitement fixe d'un résultat est fait par les étapes suivantes. Quand il faut juger (interpréter, résumer, choisir), la Tâche lance une routine en lui passant le résultat : une routine reçoit alors une donnée au lancement, qui arrive dans son message comme une pièce jointe.
- La même enveloppe que la routine : une tuile parmi les outils de sa section, sa page, son activation, ses exécutions. Les déclencheurs sont une brique commune aux deux : à la main, par un planning (avec le rattrapage des exécutions manquées), plus tard par un événement (`ExecutionTrigger.EVENT`, prévu, pas fait), ce qu'attendent aussi les événements du cœur (`TODO.md`, « À concevoir »).

À trancher : une étape qui attend une validation de l'utilisateur avant de continuer (relire ce qu'une étape précédente a produit, le corriger ou demander qu'il soit refait), comme elle attend déjà une réponse différée — comment elle s'inscrit dans les flux : où et comment on la présente, ce que devient une correction, ce que fait un refus ; si l'IA peut lancer une Tâche (elle ne lance pas une routine aujourd'hui) ; les services du cœur dont les paramètres ne sont pas déclarés sous une forme dont un formulaire peut naître (à mesurer) ; le mot, que la Liste emploie déjà pour ses éléments à cocher.

## 6. Le cas de départ

Une séance guidée audio, régénérée chaque nuit d'après les retours notés à la fin de la précédente.

- Un outil Fiches de séances : par Fiche, le script et ses audios (un par partie de la séance, chaque champ d'un seul média).
- Une Séquence : une étape par partie, chacune suivant l'audio d'une Fiche (partie 2), une pause entre elles ; ses champs supplémentaires : une Référence à la Fiche jouée, dont la valeur par défaut est la Fiche du jour (partie 3), et les retours.
- Une Tâche chaque nuit : appeler l'API de la boîte aux lettres avec le contexte et les derniers retours et scripts, lus par des Lectures ; attendre ; le script relu et validé par l'utilisateur avant que la voix et le montage soient faits (partie 5, à trancher) ; créer la Fiche avec ce que l'API rend ; faire suivre à chaque étape le champ de cette Fiche, et lui donner la valeur par défaut de la Référence.
- Hors de l'app : la boîte aux lettres, qui garde le travail et son résultat et déclenche le service ; le service, qui écrit le script, le fait dire par une synthèse vocale et monte l'audio.

Premier usage, hors de l'app et à la main par le connecteur externe : le champ média (image, audio, vidéo ; saisie depuis l'appareil, affichage, lecture, ce que l'IA en lit), l'envoi d'un fichier par le connecteur en deux temps (une adresse à côté de `/mcp`, la même autorisation, un identifiant d'attente que l'écriture de l'entrée rend propriétaire), le pointeur vers une entrée et la Lecture d'un média, les médias d'une étape en Terme (« Suivre celui d'un outil » compris), la valeur par défaut ; « Copier depuis un outil » et l'enregistrement attendent.

Ordre de construction : les médias (un audio posé à la main dans une étape sert déjà) ; le pointeur vers une entrée, la Lecture d'un média et la valeur par défaut (le montage complet, à la main) ; les API et les Tâches (la boucle tourne seule).
