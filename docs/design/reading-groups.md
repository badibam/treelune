# Le regroupement d'une Lecture

Une Lecture (`docs/BRICKS.md`) réduit toutes les entrées de sa sélection en une valeur : elle ne sait pas dire « par jour ». Les règles quotidiennes des Objectifs d'Arbre-Moi en ont besoin (« Décrochage 2 par jour », « 30 min de mouvement par jour », « au plus 2 cafés chaque jour »), jugées sur une semaine. Un compte sur la semaine (« ≥ 14 ») ne voit pas la régularité ; une saisie à la main ne montre pas de progrès pendant la semaine.

## Le principe

Une Lecture peut regrouper ses entrées par la valeur d'un champ, réduire chaque groupe, garder les groupes qui tiennent une condition, puis réduire les groupes gardés. Le `GROUP BY` et le `HAVING` de SQL.

```
Lecture = sélection + champ + réduction
          [+ regroupement : par quel champ (arrondi à une unité si c'est une date), condition sur le groupe, réduction des groupes]
```

Sans regroupement, rien ne change.

## La forme stockée

```json
{
  "selection": {...},
  "field": "data.duration",
  "reduction": "SUM",
  "group": {
    "field": "timestamp",
    "unit": "DAY",
    "keep": {"op": ">=", "right": {"constant": "PT30M"}},
    "reduction": "COUNT"
  }
}
```

- `field` et `reduction` en tête gardent leur sens : ce qu'on tire des entrées, ici de chaque groupe (`field` absent pour compter, les réductions permises par le type du champ, `Reduction.forField`). Ajouter ou retirer un regroupement ne déplace rien.
- `group.field` : le champ dont la valeur fait le groupe. `timestamp` (la date de l'entrée), un autre instant ou une date, un Choix, une Référence, un texte.
- `group.unit` : facultatif, permis seulement sur un instant ou une date, `DAY` | `WEEK` | `MONTH` ; la valeur est arrondie à l'unité qui la contient. « Par jour » s'écrit `{"field": "timestamp", "unit": "DAY"}`.
- `group.keep` : facultatif, une Condition dont la gauche est la valeur du groupe (`ConditionCheck`) et la droite un terme : une constante (écrite comme partout, en ISO face à l'IA), une Variable ou une Lecture, lu une fois à la référence du contexte (`TermReader`), puis comparé à chaque groupe.
- `group.reduction` : sur les valeurs des groupes gardés.
- Le type du résultat se connaît sans lire les données (`TermFields`) : celui de la valeur d'un groupe (`field` et `reduction`), puis celui que `group.reduction` en donne.

## Ce que valent les groupes

- **Un groupe vide n'existe que là où les valeurs s'énumèrent d'avance** : son compte et sa somme valent 0, ses autres réductions n'ont pas de valeur et il n'est pas gardé. « Au plus 2 cafés chaque jour » reste vrai un jour sans café.
  - Par `timestamp` arrondi à une unité : chaque unité de la période, coupée par le calendrier de l'utilisateur (`TimeResolver`, l'heure où commence le jour, le premier jour de la semaine ; jamais minuit, `scripts/check_day_bounds.py`).
  - Par un Choix : chacune de ses options (« les catégories où je n'ai rien dépensé ce mois »).
  - Ailleurs (un autre instant ou une date, une Référence, un texte) : un groupe par valeur présente dans les entrées, jamais de groupe vide. Une entrée sans valeur pour ce champ n'entre dans aucun groupe.
- **La période fait foi** : ses unités à venir sont des groupes, vides s'ils n'ont pas d'entrée, comme ses unités coupées. Un Agenda veut ses jours libres à venir ; un journal lu « jusqu'ici » finit sa période à maintenant. Les pièges : « cette semaine » sur un journal (les jours à venir valent 0, gardés sous « ≤ 2 ») ; « il y a 7 jours → maintenant », 8 jours dont 2 incomplets (la moyenne divisée par 8) au lieu de « début du jour il y a 7 jours → début d'aujourd'hui ».
- **Une période est requise** pour un regroupement par `timestamp` arrondi : celle de la sélection, ou celle que le contexte donne (la tentative d'un Objectif, du début à maintenant, `docs/BRICKS.md`, « Read criterion of a Goal »). Sans fin, elle s'arrête à la référence de la lecture. Sans début, la lecture est refusée, en le disant.
- **Une unité à moitié dans la période** compte comme un groupe entier : le premier jour d'une tentative lancée à 14 h, le jour en cours, provisoire.
- **Un champ à plusieurs valeurs** (Choix multiple, Référence multiple) : l'entrée va dans chacun de ses groupes, comme le `flatten` du Graphique. La somme sur tous les groupes compte alors deux fois une entrée qui est dans deux.

## La réduction des groupes

`COUNT` (combien de groupes), `SUM`, `AVERAGE` (« la moyenne par jour »), `MIN`, `MAX` ; `EARLIEST` et `LATEST` quand la valeur des groupes est un instant. `LAST` n'a pas de sens sur des groupes : refusé.

## Exemples

| Ce qu'on veut dire | `group.field` | Valeur du groupe | `keep` | `group.reduction` |
| --- | --- | --- | --- | --- |
| Décrochage 2 par jour | `timestamp`, DAY | compte | ≥ 2 | COUNT (≥ 7 dans l'Objectif) |
| Mouvement 30 min par jour | `timestamp`, DAY | somme de la durée | ≥ PT30M | COUNT |
| Au plus 2 cafés chaque jour | `timestamp`, DAY | somme des cafés | ≤ 2 | COUNT |
| Jours sous mon objectif calorique | `timestamp`, DAY | somme des kcal | ≤ variable `objectif_calorique` | COUNT |
| Moyenne d'eau par jour | `timestamp`, DAY | compte | — | AVERAGE |
| Le pire jour de douleur | `timestamp`, DAY | max | — | MAX |
| Semaines à 3 séances ce mois | `timestamp`, WEEK | compte | ≥ 3 | COUNT |
| Mois de péremption chargés | date de péremption, MONTH | compte | ≥ 5 | COUNT |
| Catégories sans dépense ce mois | Choix « catégorie » | somme du montant | = 0 | COUNT |
| Personnes pas vues depuis 30 jours | Référence multiple « avec qui » | plus récente | < il y a 30 jours | COUNT |

## Où

- **Partout où une Lecture sert** : un critère lu d'Objectif, une Variable, une Condition, un terme d'étape de Tâche, `READING` pour l'IA. Le Graphique a sa propre agrégation (Vega-Lite) et ne change pas.
- **Le calcul** : en SQL, à côté de `EntryFilters` (le `WITH tool_data` filtré, puis `GROUP BY` sur la valeur du champ, arrondie à la borne de l'unité calculée pour chaque entrée, la liste des unités ou des options jointe pour les groupes vides) ; à défaut, en Kotlin sur les entrées lues, si une période ne dépasse pas quelques milliers d'entrées. Le choix se mesure avant d'écrire.
- **Le sélecteur** (`SelectionPicker` et la ligne de la Lecture) : « Regrouper par » (aucun, un champ, son unité si c'est une date), puis « garder les groupes où… » (`ConditionPicker`, un terme à droite), puis la réduction des groupes ; la phrase suit l'ordre du calcul (« la somme de la durée, par jour, gardée si ≥ 30 min, comptée ») et dit les unités à venir ou incomplètes de la période (« 7 jours, dont 4 à venir »), sans rien refuser.
- **L'IA** : le schéma de la Lecture, sa consigne et un exemple (`ai_prompt_chunks.xml`, `scripts/check_prompt_examples.py`), la constante de `keep` lue et écrite en ISO (`ModelValues`, `ConditionValues`). La consigne nomme en une ligne les pièges de la période (finir à maintenant pour laisser les jours à venir, commencer au début d'un jour) et le double compte d'un champ à plusieurs valeurs.
- **Les docs** : `docs/BRICKS.md` (la ligne Reading, l'équation en tête).

## Hors de portée

- La régularité dans le temps : « 5 jours d'affilée », une série.
- L'heure dans la journée : « couché avant 23 h » (un filtre sur l'heure d'un instant).
- Un regroupement à deux niveaux (par personne, puis par semaine).
- La droite de `keep` lue à l'instant de chaque groupe (« les jours où j'ai dépassé l'objectif de ce jour-là ») : une Tâche chaque jour, à l'heure où il commence, crée une entrée de Bilan dont les champs sont les kcal d'hier (une Lecture) et la variable lue à cet instant ; la Lecture compte les entrées où `kcal > objectif`. Ses kcal sont figées au passage de la Tâche.

## Ce que les tests garderont

- Un jour sans entrée compte 0 et se garde sous « ≤ 2 ».
- Les jours d'une période qui finit après la référence sont des groupes, vides sans entrée.
- Les jours sont coupés à l'heure où commence le jour de l'utilisateur, la semaine à son premier jour.
- Un regroupement par `timestamp` arrondi sans début de période est refusé en le disant.
- `unit` sur un champ qui n'est ni un instant ni une date est refusé.
- Une option d'un Choix sans entrée est un groupe vide.
- Une entrée à deux personnes compte dans les deux groupes.
- `LAST` en réduction des groupes est refusé.
- Un critère d'Objectif sans période lit celle de la tentative, regroupée par jour.
- Une droite de `keep` qui nomme une Variable la lit une fois, à la référence.
- La constante de `keep` passe de l'ISO aux millisecondes et retour pour l'IA.
