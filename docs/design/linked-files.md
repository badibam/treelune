# Les fichiers liés

Conception du 2026-11-04. Une bibliothèque d'audios de séances, plusieurs Go dans `Documents/` du téléphone, beaucoup au-dessus de 50 Mo, certains en FLAC. Les copier dans l'app les mettrait en double sur le téléphone et dans chaque sauvegarde. Un champ Fichier peut désormais **lier** un fichier du téléphone au lieu de le copier : l'app le lit là où il est, sans le posséder.

## Décisions

- **Un réglage du champ Fichier**, `storage` : `copied` (par défaut, ce qui existe) ou `linked`. Tous les fichiers d'un champ sont de même nature. Un champ qui change de mode garde ses fichiers tels qu'ils sont, seuls les ajouts suivent le nouveau.
- **Un fichier lié vient toujours d'un dossier ouvert à l'app**, depuis l'écran comme depuis le connecteur : un seul droit, celui du dossier. En mode lié, la saisie ne propose que ce choix, par un navigateur des dossiers ouverts : ni photo, ni vidéo filmée, ni son enregistré, qui n'ont pas de place durable hors de l'app, ni le sélecteur de fichiers d'Android, qui laisserait prendre un fichier hors d'eux. Un champ en mode lié ne prend qu'un son ou une vidéo (`LinkedFiles.KINDS`) : une image ou un texte est petit, et tous leurs lecteurs (vignettes, orientation, réduction pour l'IA, lecture par lignes) lisent un fichier de l'app.
- **Des dossiers ouverts à l'app**, dans Réglages › Données : choisis par le sélecteur de dossiers d'Android, en lecture seule, retirables.
- **Un lien est l'adresse Android du fichier dans l'arbre de son dossier ouvert** (une URI `content://`, `DocumentsContract.buildDocumentUriUsingTree`), la même qu'il vienne de l'écran ou du connecteur.
- **Un fichier lié qui ne se lit plus est « introuvable »** : supprimé, renommé, déplacé, son droit retiré. L'app le dit sur la fiche, à la lecture et au lancement d'une séance, sans rien réparer. Ce n'est pas une erreur de l'app : c'est l'état du téléphone (WARN au journal, pas ERROR).
- **L'app ne touche jamais au fichier lié** : supprimer l'entrée, le champ ou l'outil supprime le lien, pas le fichier.
- **Une sauvegarde garde le lien, pas le fichier.** Restaurée sur le même téléphone, les liens marchent encore. Ailleurs, ils sont introuvables, et la restauration dit combien.

## La ligne de `files`

Un fichier lié a sa ligne dans `files`, comme un fichier copié : un seul propriétaire (l'entrée, ou l'outil), sa sorte, son format, son nom sur le téléphone, sa taille, sa durée. La règle « un fichier a exactement un propriétaire » tient : l'entrée possède le **lien**, pas les octets.

- Une colonne `uri` (texte) : l'adresse du fichier, nulle pour un fichier copié et pour un lien attendu. Une colonne `linked` dit si la ligne est un lien (`isLinked`), à ajouter dans l'unique migration du chantier (« Le lien attendu »).
- Le chemin d'un fichier copié découle toujours de son propriétaire, de son id et de son format (`Files.file`). Un lien n'a pas de chemin : `Files.file` refuse une ligne liée.
- La cascade emporte la ligne avec son propriétaire ; il n'y a rien à supprimer sur le disque.

## Lire un fichier : une source, pas un chemin

Aujourd'hui, tout lecteur reçoit un `java.io.File`. Il reçoit désormais une source : le fichier de l'app, ou une URI à ouvrir par le `ContentResolver`. Une fonction `Files.source(row)` la rend, et `Files.locate` et `Files.inspect` la portent à la place du `File`.

Les lecteurs à passer, relevés le 2026-11-04 :

- le son d'une étape de Séance (`SequenceAudio` : `MediaPlayer.setDataSource(context, uri)`) et sa vidéo (`SequenceScreen.StepVideo` : `VideoView.setVideoURI`) ;
- la tuile et la ligne d'un fichier (`FileViews` : le bouton audio, l'image d'une vidéo, un texte ouvert) et les images (`ImageViews`) ;
- la description lue au lien (`FileDescription.read`, par `MediaMetadataRetriever.setDataSource(context, uri)` pour une durée) ;
- la sauvegarde (`BackupService`).

« Introuvable » se décide à l'ouverture : le `ContentResolver` refuse (fichier absent, droit perdu). Un échec n'efface jamais l'adresse : il peut être passager, et un dossier autorisé à nouveau ou une sauvegarde restaurée sur le même téléphone s'en resservent. Seule la liste de « Que jouer ? » teste ses liens d'avance (« Le lien attendu »).

## Ajouter un lien

Un lien suit le chemin d'un fichier préparé (`files.prepare`, `FilePreparation`) : il est préparé, puis l'écriture de l'entrée le fait sien (`OwnedFiles`).

- **Préparé** : dans `attachments/pending/`, un `<id>.link` au lieu des octets, qui garde l'URI et la description lue (format, nom, taille, durée). `Files.pendingFile` le trouve, et le balayage de démarrage l'emporte s'il n'est jamais écrit, comme un fichier préparé.
- **Fait sien** : `OwnedFiles.place` écrit la ligne avec son `uri`, sans rien déplacer.
- **Copié vers un autre propriétaire** (une entrée dupliquée, un outil copié) : une ligne neuve, la même URI, aucun octet copié.
- **Refusé** : un lien dans un champ en mode copié, un fichier copié dans un champ en mode lié, chacun en le disant. Un format que `FileFormat` ne connaît pas, une URI que l'app ne peut pas lire.

### Depuis l'écran

En mode lié, la saisie ouvre un navigateur des dossiers ouverts, dans l'app : les dossiers, puis leurs sous-dossiers et leurs fichiers, filtrés sur les types du champ, un fil d'Ariane pour remonter. Un fichier touché est préparé comme lien (`files.link`). Il repose sur les mêmes lectures que le connecteur (`LinkedFiles.folders`, `LinkedFiles.list`). Sans dossier ouvert, il propose d'en ouvrir un.

### Depuis le connecteur et l'IA

Deux opérations du service `files`, et deux commandes de l'IA et du connecteur, `FOLDERS` et `LINK_FILE` :

- `folders` : sans `folder_id`, les dossiers ouverts, chacun avec son id et son nom ; avec, et `path` facultatif, ce que contient un dossier ou un de ses sous-dossiers, sur un niveau, chaque fichier avec son nom, sa sorte (`AUDIO`, `VIDEO`, `OTHER`) et sa taille. La durée n'y est pas : la lire ouvrirait chaque fichier.
- `link` (`folder_id`, `path`) : le fichier préparé comme lien, son id rendu avec sa description. Un chemin hors du dossier (`..`) ou absent est refusé en le disant. L'id s'écrit ensuite dans le champ comme celui d'un fichier envoyé.

L'IA dans l'app y a accès comme le connecteur, hors d'un masque d'accès : les dossiers du téléphone ne sont dans aucune zone (`AccessRules`, comme l'app).

## Les dossiers ouverts

- Rangés dans les réglages de l'app : pour chacun, un id, l'URI de l'arbre (`OpenDocumentTree`) et son nom.
- Un dossier s'ouvre par le sélecteur de dossiers, comme le dossier de l'app (`AppFolderPicker`), mais en lecture seule et à plusieurs.
- Retirer un dossier rend son droit (`releasePersistableUriPermission`). Avant, la confirmation compte les liens qui ne liront plus (`docs/design/dependents.md` : on prévient, on ne nettoie rien).
- Le droit d'un dossier couvre tout ce qu'il contient, sous-dossiers compris. Le lien d'un fichier qu'on y trouve est l'URI du document dans cet arbre (`DocumentsContract.buildDocumentUriUsingTree`).

## La sauvegarde

- Le zip ne prend pas les octets d'un fichier lié. La ligne y est, avec son `uri`.
- La vérification des fichiers manquants de la sauvegarde (`BackupService`, ceux d'une ligne sans fichier) ignore les lignes liées.
- La restauration garde les liens tels quels et ne cherche rien : le droit de lire un dossier appartient au téléphone et ne voyage pas avec la sauvegarde, et l'app redémarre aussitôt. Après le redémarrage, elle dit combien de liens ne sont pas trouvés, et d'autoriser leur dossier puis de lancer la recherche dans Réglages › Données.

## La partie exportée

Une partie exportée garde ses liens comme une sauvegarde : la ligne part, le fichier non. Importée, chaque lien attend comme un lien préparé (`LinkedFiles.pendAs`) que son entrée le prenne ; sur un autre téléphone, il est introuvable jusqu'à ce qu'on le relie.

## Relier un lien introuvable

Décidé le 2026-11-21, codé le 2026-11-22 (`LinkSearch`, `files.relink`). Le besoin : partager un montage (une bibliothèque jouée, sa Séance, ses Tâches) avec quelqu'un qui a les mêmes fichiers. On donne séparément le dossier des fichiers et la partie exportée ; l'autre copie le dossier sur son téléphone, l'ouvre à l'app, importe. Le même besoin vient d'une sauvegarde restaurée sur un autre téléphone et de ses propres fichiers déplacés.

- **Un fichier se reconnaît à son nom et à sa taille à l'octet**, déjà dans la description de chaque lien : aucun chemin gardé, aucune colonne, aucune migration. Pour un son ou une vidéo de plusieurs Mo, deux fichiers différents de même nom et de même taille ne se rencontrent pas en pratique. Celui qui reçoit range les fichiers où il veut ; seuls leurs noms restent.
- **La recherche** : tous les dossiers ouverts, à toute profondeur. Un fichier de même nom et de même taille se relie (le lien prend sa nouvelle URI). Même nom et autre taille : dit, pas pris. Deux candidats : dits, l'app ne choisit pas. Le parcours d'un grand dossier ouvert (tout le stockage) n'est pas mesuré ; il n'a lieu qu'à l'import, à la restauration et au bouton, jamais en fond.
- **À l'import, rien ne bloque** : ce qui se trouve est relié et dit ; ce qui manque est compté, avec « Chercher à nouveau » (les fichiers copiés entre-temps) et « Autoriser un dossier », qui l'ajoute aux dossiers autorisés et relance la recherche. « Importer » garde les liens manquants, introuvables et réparables ; aucune option ne les supprime.
- **Après, à tout moment** : Réglages › Données, un bouton « Chercher les fichiers introuvables », sans nombre : le toucher vérifie chaque lien et lance la même recherche, puis dit ce qui est relié, ce qui manque, ce qui est ambigu. Aucun compte à l'ouverture de l'écran, qui ouvrirait chaque lien (« pas de vérification à l'avance de tous les liens ») ; seuls l'import et la restauration, qui font ce passage, disent un nombre. Elle sert la restauration d'une sauvegarde et un dossier déplacé.
- **Le bilan**, le même à l'import et au bouton : « 5 reliés » ; « 75 pas trouvés », leurs noms repliés ; en entier, ce que l'app ne peut pas trancher (même nom et autre taille, deux candidats).
- **Relier ne relit pas le fichier** : seule l'adresse change, la description reste.

## Le lien attendu

Décidé le 2026-11-22. Le besoin : monter une bibliothèque complète depuis les fichiers du PC (des volumes numérotés, dans une Fiche), le téléphone n'en portant qu'une partie à la fois.

- **Un lien sans adresse** : `linked` vrai, `uri` nulle. Il se comporte comme un lien introuvable : dit sur la fiche, refusé au lancement, gardé par la sauvegarde et l'export. Une sauvegarde d'avant la colonne se lit « lié si elle a une adresse ».
- **`EXPECT_FILES`** (connecteur et IA) prend une liste de descriptions, `name`, `size_bytes`, `format` (un son ou une vidéo), `duration_ms` facultatif, et rend les ids dans le même ordre ; `CREATE_DATA` les écrit dans les fiches, comme des liens préparés. Le fichier d'attente d'un lien préparé (`LinkedFiles.pending`, `pendAs`) et l'import d'une partie (`PartImport`) acceptent donc un lien sans adresse.
- **Relier** lui donne son adresse et garde la description venue du PC.
- **« Que jouer ? »** (`LaunchDialog`) : une entrée dont le média manque reste à sa place, grisée, avec « Pas trouvé » sous son nom. Les lignes visibles sont testées avant l'affichage, l'attente plafonnée ; le reste en fond, un lien à la fois, à partir de la position affichée, jusqu'à la fermeture de la fenêtre. Rien n'est retenu d'une ouverture à l'autre. Un lien attendu est gris sans test.

## Hors de cette version

- Lier un dossier entier à un outil (une entrée par fichier) : les fiches se créent une à une, par le connecteur.

## Ouvert

- La durée d'un FLAC de 200 Mo lue par `MediaMetadataRetriever` sur une URI : le temps que ça prend.
- Le déplacement de la lecture (`seekTo`) dans un FLAC servi par une URI, pour la reprise d'une séance en pause.
