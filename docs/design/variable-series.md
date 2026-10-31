# Séries de variables

Conception du 2026-10-31, à implémenter puis élaguer.

## Le besoin

Une variable n'a qu'une valeur : celle de maintenant (ou de l'instant où on la lit). Ce qui compte des jours ne peut pas la lire : un Objectif qui veut « le jeûne de la nuit ≥ 16 h, 7 jours sur 7 » compte des entrées regroupées par jour, et une variable n'a pas d'entrées. Recopier sa valeur chaque jour dans un Suivi par une Tâche la fige au moment de la copie : un repas ajouté après coup, ou corrigé la veille, ne la corrige plus.

## Ce qu'est une série

Une variable peut déclarer un pas : elle a alors une valeur par pas, et se lit partout où on lit des entrées comme un outil qui en aurait une par pas.

- **La déclaration** : `series: {"unit": …}` dans sa définition, une unité de `PeriodType` (`HOUR`, `DAY`, `WEEK`, `MONTH`, `YEAR`), le pas coupé par le calendrier de l'utilisateur (l'heure où commence le jour, le premier jour de la semaine). Absente, la variable n'a que sa valeur du moment, comme aujourd'hui. À l'écran, dans la config de la variable : « Une valeur par : — / heure / jour / semaine / mois / année ».
- **La valeur d'un pas** : la variable calculée à la fin du pas, sa dernière milliseconde, comme toute fin de période dans l'app (`TimeResolver`) ; pour le pas qui contient l'instant lu, à cet instant. Un pas qui commence après l'instant lu n'a pas de valeur.
- **L'entrée d'un pas** : `timestamp`, l'instant où elle est calculée (dans son pas, donc rangée dans son pas par un regroupement), et `data.value`, la valeur, du type de la variable. Rien d'autre : ni nom, ni extra, ni état. Son id est fait de celui de la variable et de son instant.
- **Recalculée à chaque lecture**, jamais stockée : une correction des données qu'elle lit la corrige, quelle que soit sa date. Une formule modifiée réécrit tout son passé.
- **Un pas sans valeur** (un terme sans entrée, une valeur manquante) n'a pas d'entrée : il ne compte pas, une moyenne l'ignore, comme un jour où rien n'a été noté dans un outil.

Une série n'est juste dans le passé que si les termes de la variable se placent par rapport à l'instant lu (« du début du jour jusqu'à maintenant », « la veille ») : c'est à celui qui écrit la variable de le savoir, l'app ne le vérifie pas. Le Guide et le guide de conception de l'IA le disent.

## Comment on la lit

Une Sélection peut viser une variable qui a une série (`{"kind": "VARIABLE", "id"}`), dans une Lecture seulement : une période, des filtres sur `data.value` ou `timestamp`, puis le champ, la réduction et le regroupement de toute Lecture. Le reste de la brique ne change pas.

- **Sa période doit avoir un début** : une variable n'a pas de première entrée, « tout l'historique » n'a pas de sens. Une période sans début est refusée en le disant. Sans fin, elle s'arrête à l'instant lu. Dans un critère d'Objectif, la période vide vaut la tentative, qui a un début.
- **Ses filtres** sont ceux d'un outil, jugés par le même SQL (`EntryFilters`), les entrées fabriquées tenant lieu de la table (`WITH tool_data`), comme les déclencheurs jugent la copie d'une entrée.
- **À plusieurs instants** (un graphique, une ligne par pas) : chaque instant lit la série telle qu'elle est à cet instant, son dernier pas calculé à cet instant.
- **Ce qui la lit est relu quand elle change** : une condition, une tuile, un Repère qui lisent une série dépendent des outils que la variable lit (`tools_read`), comme pour un terme qui la lit directement.
- **Sous un masque**, lire la série, c'est lire la variable, et ce qu'elle lit.
- `{"variable": id}` comme terme lit toujours sa valeur à l'instant lu : la série ne sert qu'à qui la sélectionne. Un pointeur, la liste d'entrées d'une étape d'API, une Fiche par entrée (`per_entry`) ne visent pas une variable.

« Jeûne de 16 h », dans un Objectif :

```json
{"left": {"reading": {
   "selection": {"target": {"kind": "VARIABLE", "id": "<jeune>"},
                 "filters": [{"left": {"field": "data.value"}, "op": ">=", "right": {"constant": 57600000}}]},
   "reduction": "COUNT"}},
 "op": ">=", "right": {"constant": 7}}
```

La même variable en série par heure se lit regroupée par jour, la plus haute valeur de chaque jour gardée si elle atteint 16 h, les jours comptés : le pas est une résolution, les lecteurs regroupent avec ce qui existe.

## Ce qui reste ouvert

- Changer le pas d'une variable change le sens de ce qui la lit (un compte de jours devient un compte d'heures) : le dire au changement, par le contrôle « qui dépend de ça ? » du TODO.
- Le calcul : un appel à la variable par pas, chaque terme relisant ses entrées. Une lecture unique des entrées de toute la période, puis chaque pas calculé en mémoire, si une mesure le demande.

## À vérifier sur le téléphone

- Jeûne en série par jour, « Jeûne de 16 h » et « Jeûne de 18 h » de Semaine du Tronc relus ; un repas ajouté le matin pour la veille, le compte qui suit.
