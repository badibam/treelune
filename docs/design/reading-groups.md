# Le regroupement d'une Lecture

Une Lecture (`docs/BRICKS.md`) réduit toutes les entrées de sa sélection en une valeur : elle ne sait pas dire « par jour ». Les règles quotidiennes des Objectifs d'Arbre-Moi en ont besoin (« Décrochage 2 par jour », « 30 min de mouvement par jour », « au plus 2 cafés chaque jour »), jugées sur une semaine. Un compte sur la semaine (« ≥ 14 ») ne voit pas la régularité ; une saisie à la main ne montre pas de progrès pendant la semaine.

## Le principe

Une Lecture peut regrouper ses entrées, réduire chaque groupe, garder les groupes qui tiennent une condition, puis réduire les groupes gardés. Le `GROUP BY` et le `HAVING` de SQL.

```
Lecture = sélection + champ + réduction
          [+ regroupement : par quoi, valeur de chaque groupe, condition sur le groupe]
```

Sans regroupement, rien ne change.

## La forme stockée

```json
{
  "selection": {...},
  "reduction": "COUNT",
  "group": {
    "by": {"unit": "DAY"},
    "field": "data.duration",
    "reduction": "SUM",
    "keep": {"op": ">=", "right": {"constant": "PT30M"}}
  }
}
```

- `group.by` : une unité du calendrier, `{"unit": "DAY" | "WEEK" | "MONTH"}`, ou un champ, `{"field": "<chemin>"}` (le nom, un Choix simple, une Référence simple).
- `group.field` et `group.reduction` : la valeur de chaque groupe, comme une Lecture sans regroupement (`field` absent pour compter), les réductions permises par le type du champ (`Reduction.forField`).
- `group.keep` : facultatif, une Condition dont la gauche est la valeur du groupe (`ConditionCheck`, ses constantes écrites comme partout, en ISO face à l'IA).
- `field` en tête est absent : la réduction de tête porte sur les valeurs des groupes gardés.

## Ce que valent les groupes

- **Par unité du calendrier** : chaque jour, semaine ou mois de la période, coupé par le calendrier de l'utilisateur (`TimeResolver`, l'heure où commence le jour, le premier jour de la semaine ; jamais minuit, `scripts/check_day_bounds.py`). **Un groupe sans entrée existe** : son compte et sa somme valent 0, ses autres réductions n'ont pas de valeur et le groupe n'est pas gardé. « Au plus 2 cafés chaque jour » reste vrai un jour sans café.
- **Une période est requise** pour un regroupement par unité : celle de la sélection, ou celle que le contexte donne (la tentative d'un Objectif, `docs/BRICKS.md`, « Read criterion of a Goal »). Sans fin, elle s'arrête à la référence de la lecture. Sans début, la lecture est refusée, en le disant.
- **Une unité à moitié dans la période** (le premier jour d'une tentative ouverte à midi) compte comme un groupe entier ; la période d'un Objectif hebdomadaire tombe de toute façon sur les bornes de la semaine.
- **Par champ** : un groupe par valeur présente dans les entrées, jamais de groupe vide. Une entrée sans valeur pour ce champ n'entre dans aucun groupe.

## La réduction de tête

Sur les valeurs des groupes gardés : `COUNT` (combien de groupes), `SUM`, `AVERAGE` (« la moyenne par jour »), `MIN`, `MAX` ; `EARLIEST` et `LATEST` quand la valeur des groupes est un instant. `LAST` n'a pas de sens sur des groupes : refusé.

## Exemples

| Ce qu'on veut dire | `by` | Valeur du groupe | `keep` | Tête |
| --- | --- | --- | --- | --- |
| Décrochage 2 par jour | DAY | compte | ≥ 2 | COUNT (≥ 7 dans l'Objectif) |
| Mouvement 30 min par jour | DAY | somme de la durée | ≥ PT30M | COUNT |
| Au plus 2 cafés chaque jour | DAY | somme des cafés | ≤ 2 | COUNT |
| Moyenne d'eau par jour | DAY | compte | — | AVERAGE |
| Le pire jour de douleur | DAY | max | — | MAX |
| Semaines à 3 séances ce mois | WEEK | compte | ≥ 3 | COUNT |
| Personnes pas vues depuis 30 jours | champ « avec qui » | plus récente | < il y a 30 jours | COUNT |

## Où

- **Partout où une Lecture sert** : un critère lu d'Objectif, une Variable, une Condition, un terme d'étape de Tâche, `READING` pour l'IA. Le Graphique a sa propre agrégation (Vega-Lite) et ne change pas.
- **Le calcul** : en SQL, à côté de `EntryFilters` (le `WITH tool_data` filtré, puis `GROUP BY` sur la borne du jour calculée pour chaque entrée, la liste des unités jointe pour les groupes vides) ; à défaut, en Kotlin sur les entrées lues, si une période ne dépasse pas quelques milliers d'entrées. Le choix se mesure avant d'écrire.
- **Le sélecteur** (`SelectionPicker` et la ligne de la Lecture) : « Regrouper par » (aucun, jour, semaine, mois, un champ), puis la valeur de chaque groupe, puis « garder les groupes où… » (`ConditionPicker`), puis la réduction de tête, que le sélecteur dit en phrase (« le nombre de jours où la durée totale ≥ 30 min »).
- **L'IA** : le schéma de la Lecture, sa consigne et un exemple (`ai_prompt_chunks.xml`, `scripts/check_prompt_examples.py`), la constante de `keep` lue et écrite en ISO (`ModelValues`, `ConditionValues`).
- **Les docs** : `docs/BRICKS.md` (la ligne Reading, l'équation en tête).

## Hors de portée

- La régularité dans le temps : « 5 jours d'affilée », une série.
- L'heure dans la journée : « couché avant 23 h » (un filtre sur l'heure d'un instant).
- Un regroupement à deux niveaux (par personne, puis par semaine).

## Ce que les tests garderont

- Un jour sans entrée compte 0 et se garde sous « ≤ 2 ».
- Les jours sont coupés à l'heure où commence le jour de l'utilisateur, la semaine à son premier jour.
- Un regroupement par unité sans début de période est refusé en le disant.
- `LAST` en tête d'un regroupement est refusé.
- Un critère d'Objectif sans période lit celle de la tentative, regroupée par jour.
- La constante de `keep` passe de l'ISO aux millisecondes et retour pour l'IA.
