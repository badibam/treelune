# Événements

Ce qui fait partir une routine, une Tâche ou un outil actif autrement qu'à une heure : une entrée écrite, une condition devenue vraie, un événement émis. Conçu pendant les Tâches (`docs/CORE.md`, « Tasks »), pour qu'elles reçoivent la forme de déclenchement qui l'accueille.

## 1. Le « quand ? »

En place pour les Tâches et les routines : la sorte 2, une entrée écrite (`docs/CORE.md`, « Triggers »), stockée en liste à côté du planning, qui garde sa forme et son rattrapage. Restent les outils actifs (Questionnaire, Messages, Séquence, Objectif), et les sortes 3 et 4.

Partout où l'app a aujourd'hui un planning (`ScheduleConfig`), ce planning devient une liste de déclencheurs : routine, Tâche, invitations d'un Questionnaire, envois d'un Message, séances prévues d'une Séquence, tentatives d'un Objectif. Les séries de l'Agenda gardent leur planning seul : une série montre ses rendez-vous 90 jours à l'avance, ce qu'un événement ne permet pas. Le bouton « Exécuter », à la main, reste à côté, hors de la liste.

Une seule brique, son modèle et son sélecteur, la même à chacun de ces endroits ; c'est là que vit tout le vocabulaire de ce qui fait partir. Quatre sortes, qui se mélangent dans la liste (« chaque dimanche à 3 h, et aussi après chaque séance ») :

1. **Une heure** : le planning d'aujourd'hui.
2. **Une entrée écrite** dans un outil : créée, modifiée ou supprimée, avec un filtre facultatif sur l'entrée (`EntryFilters`). Part à chaque écriture qui passe le filtre : « une séance de Yoga passe à done », « un poids noté au-dessus de 80 ».
3. **Une condition qui devient vraie** : une Condition jugée une fois (`ConditionJudge`), « moyenne 7 j > 80 ». Part au passage de faux à vrai, puis plus rien tant qu'elle ne redevient pas fausse.
   - **Le dernier jugement** est gardé chez ce qui écoute, pas sur la Condition : une ligne par Tâche ou routine et par texte stocké de la condition. Réordonner la liste ne le perd pas ; une condition modifiée est une ligne neuve. Le premier jugement d'une ligne est noté sans rien faire partir : un déclencheur ajouté sur une condition déjà vraie attend qu'elle redevienne fausse puis vraie.
   - **Rejugée** après une écriture dans un outil qu'elle lit, par une Lecture ou par une variable (comme `tools_read`), dans la chaîne de celui qui a écrit ; et toutes au tic du planificateur, en chaîne neuve, sans chercher celles que le temps fait bouger. Une variable ou une Lecture modifiée attend le tic.
   - **Ni vrai ni faux** : sans réponse (une valeur manque), le verdict reste tel quel, inconnu n'est pas faux. En échec (une variable lue supprimée), le verdict reste aussi, l'échec est noté sur la ligne et montré sous le déclencheur, une notification le dit une fois, à son début.
   - **Hors des sauvegardes** : après une restauration, chaque ligne repart de son premier jugement, rien ne part.
4. **Un événement** émis (partie 2).

La différence entre 2 et 3 : tu notes 81, puis 82. « Un poids noté au-dessus de 80 » part deux fois ; « le dernier poids passe à > 80 » une fois.

## 2. Les événements

En place (`docs/CORE.md`, « Events ») : la carte de la zone, l'étape « émettre », le déclencheur, les valeurs reçues. Restent l'émission par l'IA, un bouton, l'IA extérieure, et les endroits où l'app prévient (partie 3).

Un événement est une chose de l'app, dans une zone, comme une variable : un nom (« séance prête ») et les valeurs qu'il porte, déclarées comme les valeurs d'une API (un nom, un type : `fiche`, une entrée de Fiches de séances ; `duree`, une durée), aucune s'il n'en porte pas. Il sert à ce qu'aucune écriture ne dit (une Tâche a fini de fabriquer l'audio), et à ce que plusieurs choses écoutent sous un même nom ; une écriture, elle, s'écoute directement par la sorte 2.

Qui émet :
- **une étape de Tâche** « émettre », chaque valeur remplie par un Terme (« l'entrée créée à l'étape 3 », une constante) ; une valeur déclarée manquante est refusée à l'écriture de la Tâche, pas à son exécution ;
- **les endroits où l'app prévient** (partie 3) ;
- plus tard l'IA, un bouton, l'IA extérieure par le connecteur.

Le masque d'accès s'y applique sans rien de nouveau : émettre un événement demande le niveau « usage » sur sa zone, l'écouter (le mettre dans un « quand ? ») de pouvoir lire sa zone. Une IA ne peut pas donner à ce qu'elle crée ou change un « quand ? » ni un masque plus large que le sien (`AccessRules.widening`, comme pour les routines aujourd'hui). Ce que porte une émission est vérifié chez celui qui émet : une IA n'émet pas « séance prête » avec une Fiche qu'elle ne peut pas lire, sinon elle choisirait la cible d'une Tâche plus puissante qu'elle. Ce qui part agit ensuite avec ses propres droits, quel que soit l'émetteur.

## 3. Les endroits où l'app prévient

En place (`docs/CORE.md`, « Events », `EventPoints`) : « émettre » aux sept endroits, la routine et la Tâche à la fin d'une exécution qui s'est bien passée. « Prévenir : non » partout (réglage `notify` de l'Agenda, de la Liste, du Questionnaire, de la Séquence et de l'Objectif). Le seuil du budget émet, sans réglage « prévenir » : sans seuil, rien ne prévient.

Chacun prend deux réglages indépendants : **prévenir** (oui, non) et **émettre** (aucun événement, ou un événement choisi par le sélecteur des choses de l'app, toutes zones). Ce qui est émis porte l'entrée concernée.

| Où | Le moment | Exemple |
|---|---|---|
| Agenda | un rappel avant un rendez-vous | 10 min avant chaque Yoga, émettre « yoga bientôt » ; une Tâche ouvre la séance de Séquence |
| Liste | l'échéance d'un élément non coché passée | « Payer le loyer » en retard ; une routine propose une nouvelle date |
| Objectif | une tentative à valider | « Dormir 7 h » de la semaine ; une routine propose le verdict |
| Questionnaire | une invitation à remplir | l'humeur du soir ; une routine prépare les questions du jour |
| Séquence | une séance prévue arrive | la séance du matin ; une Tâche fabrique la Fiche du jour si elle manque |
| Messages | un message part | « lundi » sans prévenir : une horloge à événements, qu'une Tâche planifiée fait aussi |
| Routine | une exécution se termine (`notify_end`) | « bilan prêt » ; une Tâche en copie le résultat dans le Journal |
| Budget | le seuil d'alerte franchi (partie 5) | « budget bas » ; une Tâche met en pause une routine chère |

Le moment de l'Agenda et celui de la Liste ne sont pas des écritures (leurs marques `reminded_until` et `due_notified` sont internes, rien ne s'y appuie) : c'est ici, et seulement ici, qu'ils deviennent écoutables.

## 4. Ce que reçoit ce qui part

Une exécution reçoit une seule chose, jamais une liste : l'entrée écrite (sorte 2), rien (sorte 1), les deux valeurs lues de la condition au moment où elle est devenue vraie (sorte 3 : `left`, `right`, « 82,4 » et « 80 »), les valeurs de l'événement (sorte 4). La notification de fin d'une exécution partie sur une condition la nomme avec sa valeur (« Poids moyen 7 j > 80 : 82,4 »). L'entrée est relue au départ, telle qu'elle est ; supprimée, elle est dite supprimée.

- Une Tâche les lit par un Terme de plus, du même genre que « le résultat de l'étape 1 » : « l'entrée reçue », « `fiche` de l'événement », un champ de celles-ci.
- Une routine les reçoit dans son message de départ comme une pièce jointe (la donnée qu'une Tâche passe à une routine, `docs/CORE.md`, « Tasks »), avec une ligne qui dit ce qui l'a fait partir.

Un import, la restauration d'une sauvegarde et l'installation de la démo ne font rien partir : l'app recharge des données, personne ne les note. Une condition, elle, est rejugée au tic suivant : ce qu'un import dans un outil déjà lu rend vrai part, assumé (un outil importé est neuf, rien ne le lit encore).

Défaire et rétablir une étape (`history.undo`, `history.redo`) sont des écritures faites par toi, en chaîne neuve : défaire une création fait partir ce qui écoute une suppression, défaire une modification ce qui écoute une modification, et une condition se rejuge. Sinon ce qui recopie ou résume diverge sans bruit (une durée recopiée dans un Suivi garderait la valeur défaite). Ce qu'une écriture a fait partir n'est pas défait avec elle, comme une notification envoyée ou un appel payé : une séance défaite puis rétablie refait partir la Tâche qui l'écoute.

## 5. Les garde-fous

Deux, tous deux dans l'écran des limites de l'IA, à côté des appels par exécution (`AILimitsConfig`).

**Le plafond par chaîne**, en place entre Tâches et routines (`docs/CORE.md`, « Chains ») et pour les entrées écrites qu'une Tâche écoute ; reste à y faire entrer les conditions et les émissions, chacune continuant la chaîne de l'exécution qui l'a faite. Une exécution appartient à la chaîne de ce qui l'a fait partir : celui qui a écrit l'entrée, émis l'événement, ou dont l'écriture a rendu la condition vraie. Une heure (un planning, une étape « attendre jusqu'à », le temps qui fait basculer une condition) ou une personne (un geste à l'écran, un message envoyé dans une conversation) commencent une chaîne neuve ; une écriture de l'IA extérieure par le connecteur aussi, que l'app ne rappelle jamais et par laquelle aucune boucle ne passe (une écriture protégée fait partir ce qui l'écoute une fois acceptée, quand elle a lieu). Le plafond compte toutes les exécutions d'une chaîne, pas les maillons d'affilée : deux Tâches qui se renvoient chacune deux entrées doublent à chaque tour. Atteint, ce que la dernière exécution écrit ou émet ne fait plus rien partir ; son historique le dit, une notification aussi ; les autres chaînes ne sont pas touchées, et la chaîne arrêtée ne reprend pas.

- Dans la même chaîne : une Tâche écrit dans Journal, une autre l'écoute ; une routine émet, une Tâche l'écoute ; une Tâche note un poids qui rend une condition vraie ; une Tâche attend 2 h la réponse d'une API puis émet (l'attente d'une réponse n'est pas une heure choisie) ; l'IA d'une conversation crée une entrée écoutée (la conversation est la première exécution).
- Une chaîne neuve : la routine du dimanche, chaque dimanche ; le rappel d'un rendez-vous qu'une Tâche a créé ; une condition que les jours font basculer ; une entrée qu'une Tâche a créée et que tu modifies ; ce qu'une Tâche émet après « attendre jusqu'à demain 7 h », le cycle quotidien voulu.
- Arrêtés : une Tâche qui écrit où elle écoute ; deux routines qui se relancent sans conclure (Rédaction et Critique : 25 allers-retours) ; deux Tâches qui doublent leurs écritures. Bloqué à tort : quelque chose qui écrit plus de 50 entrées d'un coup dans un outil écouté, le plafond se relève alors.

**Le budget du jour**, en place (`docs/AI.md`, « Daily budget ») ; un appel au coût inconnu compte pour 0, et la page du budget nomme les fournisseurs dont le modèle n'a pas de prix. En argent, facultatif : tous les appels d'IA de l'app, conversations comprises (l'IA extérieure du connecteur ne coûte rien à l'app), sur le jour de l'app (`TimeResolver`), comptés par `SessionCost`. Atteint, plus rien ne démarre : une exécution qui devait partir est notée manquée dans son historique, sans report au lendemain ; une conversation n'envoie pas, avec « budget du jour atteint » et un bouton vers l'écran des limites ; une IA qui enchaîne seule s'arrête avant son appel suivant. Le dépassement est au plus celui d'un appel en cours, dont le coût n'est connu qu'à sa réponse. Un seuil d'alerte facultatif, en pourcentage, prévient une fois par jour et peut émettre (partie 3). L'écran du coût des conversations rappelle « Aujourd'hui : 0,42 $ sur 2,00 $ », à la couleur d'avertissement du thème au-delà du seuil.

## Écarté

- Un délai minimum entre deux départs : il ralentit une boucle sans l'arrêter, et les départs arrivés pendant le délai, jetés ou regroupés en liste, faisaient plus de cas tordus qu'il n'en réglait. Les rafales qu'il visait viennent des imports, qui ne déclenchent rien ; une routine chère qui part trop souvent bute sur le budget.
- Un « quand ? » qui ne propose que des événements, les écritures et les conditions transformées en événements ailleurs : la règle « ceci arrive → émettre » voudrait aussi lancer une Tâche, et ferait double emploi avec le « quand ? ». Une Tâche d'une étape « émettre » la remplace.
- Une condition pour les rendez-vous de l'Agenda (« prochain Yoga ≤ dans 10 min ») : en retard du tic, aveugle au second de deux rendez-vous proches, sans dire lequel commence.
- Interdire qu'une même chose repasse dans une chaîne : bloque le va-et-vient voulu entre deux routines et une Tâche qui aiguille.

## À trancher

- La sorte « à une date des données » (10 min avant chaque entrée d'un outil, le lendemain d'une échéance), commune avec les rappels de l'Agenda : tant que les endroits où l'app prévient suffisent, pas faite.
- L'IA qui émet un événement, et si elle lance une Tâche (elle ne lance pas une routine aujourd'hui).
- Supprimer un événement écouté ou une chose qu'un « quand ? » vise : le contrôle « qui dépend de ça ? » de `TODO.md`.

## Découpage

1. Le « quand ? » des outils actifs, avec les entrées écrites (fait pour les Tâches et les routines).
2. Les événements et l'étape « émettre » ; ce que reçoit ce qui part (fait).
3. Les endroits où l'app prévient (fait).
4. La condition qui devient vraie, pour les Tâches et les routines. L'Alerte n'est pas un outil : une Tâche sans étape qui écoute une condition, prévenue à la fin, a déjà sa tuile, ses exécutions à lire, sa notification et son émission.
5. Le budget du jour (fait).
