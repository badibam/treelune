# Tâches, API et fichiers

Cinq briques qui se construisent l'une après l'autre, chacune utile seule : les fichiers (image, audio, vidéo, texte), la désignation qui descend jusqu'à un champ, la valeur par défaut étendue, les API, les Tâches. Elles naissent d'un même cas, une séance guidée audio régénérée chaque nuit par un service extérieur d'après les retours notés à la fin de la précédente (dernière partie), mais aucune ne lui est propre.

## Les notions

À reporter dans la doc durable (`docs/BRICKS.md`, `docs/DATA.md`) une fois le code en place.

- **Désigner** : dire de quoi il s'agit, sans rien lire. Une désignation est une chose de l'app (l'app, une zone, un outil, une variable, une entrée), et facultativement un champ de cette chose : `{"kind": "ENTRY", "id", "field"?}`. Sans `field`, c'est la forme d'aujourd'hui.
- **Une Référence** est une désignation gardée comme valeur : « Plante : le plant de tomates cerises », « Fiche jouée : Séance du 24 ». Elle dit laquelle, jamais ce qu'elle contient.
- **Une Lecture** est une désignation plus une manière d'en tirer une valeur (la dernière, la somme, sa valeur…), relue à chaque usage. Son résultat est la valeur du moment ; il sert sur le moment et n'est écrit nulle part, sauf si une opération l'écrit (le relevé d'une variable dans un Suivi).
- **Une valeur fixe** est posée une fois : saisie, ou copiée depuis le champ d'une entrée. Une fois copiée, rien ne la distingue d'une valeur saisie ; elle ne bouge plus.
- **Le pointeur d'un message** est une désignation que l'IA lit au moment de l'envoi ; il ne copie rien.
- **Où vit quoi** : une configuration décrit ce qui se fera, elle tient des valeurs fixes et des Lectures. Une entrée et un message sont la trace de ce qui s'est passé : ils tiennent des valeurs, dont des Références, jamais une Lecture, sans quoi le passé changerait après coup.
- **L'identifiant d'un fichier** est la manière dont ce fichier est rangé (trop gros pour vivre dans la ligne, il vit à part et la valeur garde son numéro), pas une désignation : un identifiant écrit dans une valeur est un fichier possédé.
- **Un seul navigateur** mène aux choses, le fil d'Ariane App › zone › outil › entrée (`ThingBrowser`), avec un dernier cran facultatif, le champ, où ne paraissent que les champs de la sorte attendue. Il s'arrête où l'usage le demande : une chose pour une Référence ou un pointeur, un champ pour une copie ou une Lecture.

## 1. Fichiers : image, audio, vidéo, texte

La brique Image (`docs/DATA.md`, « IMAGE ») devient la brique Fichier : une table commune, une sorte par fichier (IMAGE, AUDIO, VIDEO, TEXT), son format, ce qui va avec sa sorte (des dimensions, une durée, un nombre de lignes). La sorte et le format sont établis une fois, quand le fichier est mis de côté, et refusés s'ils ne conviennent pas ; ensuite on lit la ligne, et le chemin du fichier en découle (son identifiant, l'extension de son format). Les pièces jointes texte des conversations (`attached_files`, leur contenu aujourd'hui dans la base) y entrent dans le même chantier, leur contenu sorti dans un fichier, pour ne transformer la table qu'une fois.

- Ce qui varie selon la sorte : comment on l'ajoute, comment on la montre ou la joue, comment on la réduit, ce que l'IA en lit (une image ses dimensions et sa taille, un audio sa durée et sa taille).
- Chaque endroit qui tient des fichiers déclare les sortes qu'il accepte et leur nombre maximal (le champ Image a déjà son `max`). Une étape de Séquence a deux réglages : ses images (IMAGE, sans limite) et son son ou sa vidéo (AUDIO ou VIDEO, un au plus) ; un champ de Fiche « Induction » : AUDIO, un au plus.
- Une entrée et un message possèdent leurs fichiers. Une configuration possède ses valeurs fixes et suit, par une Lecture, le champ d'une entrée qui possède le sien.
- Une étape de Séquence porte une consigne écrite facultative, ses images, et son son ou sa vidéo, chaque fichier un Terme. Les images actuelles des étapes deviennent des valeurs fixes. Une fin d'étape de plus : « à la fin du média ». L'audio se joue écran éteint, par le service qui fait déjà tourner une séance (`SequenceRunService`) ; la vidéo écran allumé, à l'écran de la séance.
- Le sélecteur d'un Terme, le même pour tous les types, ne montre que ce qui convient au type attendu :
  - **Valeur fixe** : saisir (taper un nombre, un texte, une date ; pour un fichier, l'ajouter depuis l'appareil, plus tard l'enregistrer), ou **copier depuis un outil** (le navigateur jusqu'au champ d'une entrée, sa valeur du moment recopiée une fois ; un fichier copié devient celui de l'endroit).
  - **Lecture** : suivre un champ d'une entrée, relu à chaque usage ; pour une étape, ce qu'il contient au moment de jouer.
  - **Variable**, là où une variable du type existe (aucune ne tient un fichier aujourd'hui).
- Copier et suivre passent par le même parcours jusqu'au champ et donnent des résultats opposés : les libellés disent l'effet.
- La zone de saisie d'une conversation a deux gestes : **Pointer** (désigner, l'IA lit au moment de l'envoi) et **Joindre** (le sélecteur de valeur fixe : depuis l'appareil, ou copier depuis un outil ; la conversation possède ce qu'elle joint). Il n'y a pas de Lecture dans un message.
- La copie d'un fichier qui appartient à un autre reste ce que fait une valeur fixe : elle possède son fichier. Elle est silencieuse aujourd'hui (l'IA qui écrit l'identifiant d'une photo de la conversation ne sait pas qu'elle a pris une copie) : le résultat de l'écriture le dit.

À trancher : les formats gardés, la taille au-delà de laquelle un fichier est refusé, la réduction (une vidéo réduite comme une photo l'est).

Option non tranchée, posséder ou retenir : un endroit tient un fichier en le possédant (il part avec lui, est supprimé quand on le retire) ou en le retenant (il le nomme sans le posséder, et le fichier ne peut disparaître tant qu'il le nomme) ; un fichier vit tant qu'il a un propriétaire ou une rétention. Chaque endroit déclare sa manière : un choix, pas une exception. Seul ce qui désigne un fichier sans en être l'endroit retient : un historique. L'annulation retient déjà, sans le dire (le nettoyage au démarrage recalcule ce que l'historique des écritures nomme, `Images.kt`). Un fichier n'est jamais modifié sur place (le remplacer en crée un autre), si bien que rien dans l'app n'altère ce qui est retenu.

- En base : la table des fichiers garde au plus un propriétaire, qui ne l'emporte plus en partant (`ON DELETE SET NULL`) ; une table `file_retentions` (`file_id`, `reason`, l'entrée ou l'étape de l'historique qui retient, en cascade).
- Ce qui n'est plus que retenu se liste par raison, avec sa taille (« 14 fichiers, 230 Mo, gardés par l'historique des séances »), et se libère en supprimant ces rétentions ; l'historique garde alors la désignation et dit que le fichier a été supprimé.
- Premier usage possible : l'historique d'une séance qui garde, pour chaque étape jouée, d'où venait son fichier (« Fiche du 24 › Induction ») et retient le fichier joué, pour qu'une séance faite ne change plus, sans copie par séance.
- À mesurer : ce que coûte de faire écrire ses rétentions à l'historique des écritures.

## 2. La désignation jusqu'au champ, la Lecture d'un fichier

- La brique Chose (`docs/BRICKS.md`) gagne le champ facultatif ; la Référence, la Lecture, le pointeur et la copie s'en servent. Les valeurs existantes, sans champ, restent valides.
- Une Lecture part aujourd'hui d'une sélection d'entrées qui part d'un outil ou d'une zone (`docs/BRICKS.md`, « Entry selection ») : elle part aussi d'une entrée précise, l'entrée étant déjà une chose (`{"kind": "ENTRY", "id"}`). Le pointeur d'un message, construit sur la même sélection, vise alors une entrée.
- La Lecture d'un champ fichier, avec la réduction « sa valeur ». Aujourd'hui `Reduction.forType` n'en donne aucune à IMAGE (`core/reading/FieldReading.kt`). Elle ne vise qu'un champ d'un seul fichier (`max` 1).
- La Lecture vise le champ, pas le fichier : un audio remplacé dans la Fiche est suivi, comme sa suppression.
- Une source supprimée laisse l'étape sans fichier : c'est le cas de la vérification « qui dépend de ça ? » avant de supprimer (`TODO.md`, « À concevoir »).

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

- Un outil Fiches de séances : par Fiche, le script et ses audios (un par partie de la séance, chaque champ d'un seul fichier).
- Une Séquence : une étape par partie, chacune suivant l'audio d'une Fiche par une Lecture (partie 2), une pause entre elles ; ses champs supplémentaires : une Référence à la Fiche jouée, dont la valeur par défaut est la Fiche du jour (partie 3), et les retours.
- Une Tâche chaque nuit : appeler l'API de la boîte aux lettres avec le contexte et les derniers retours et scripts, lus par des Lectures ; attendre ; le script relu et validé par l'utilisateur avant que la voix et le montage soient faits (partie 5, à trancher) ; créer la Fiche avec ce que l'API rend ; faire suivre à chaque étape le champ de cette Fiche, et lui donner la valeur par défaut de la Référence.
- Hors de l'app : la boîte aux lettres, qui garde le travail et son résultat et déclenche le service ; le service, qui écrit le script, le fait dire par une synthèse vocale et monte l'audio.

## Découpage

- **A, l'usage à la main par le connecteur externe** : les parties 1 à 3 et l'envoi d'un fichier par le connecteur. La brique Fichier (image, audio, vidéo, et les pièces jointes texte déplacées ; saisie depuis l'appareil, affichage, lecture, ce que l'IA en lit ; dans une étape, le son ou la vidéo en Terme, « à la fin du média », la lecture écran éteint), la désignation jusqu'au champ, la sélection qui part d'une entrée et la Lecture d'un fichier (« Suivre » compris), la valeur par défaut. L'envoi d'un fichier par le connecteur, en deux temps : une adresse à côté de `/mcp`, la même autorisation, un identifiant d'attente que l'écriture de l'entrée rend propriétaire. Le montage de la partie 6 se fait alors à la main, une IA extérieure déposant les audios dans une Fiche et faisant suivre la Séquence.
- **B, le reste** : « Copier depuis un outil », Joindre par copie, l'enregistrement, l'option posséder ou retenir (partie 1) ; les API (partie 4) ; les Tâches (partie 5), avec lesquelles la boucle tourne seule.
