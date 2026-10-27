# La Référence multiple

Une Référence désigne aujourd'hui une seule chose : sa valeur est un objet `{kind, id, field?}` (`FieldValueSchema`). Un usage qui revient en demande plusieurs : un moment raconté dans un journal, partagé avec trois personnes qui ont chacune leur fiche, d'où l'on tire ensuite, personne par personne, la dernière fois qu'on s'est vus. Un choix multiple des noms y suffirait sans lien vers les fiches ; plusieurs champs Référence (« Personne 1, 2, 3 ») sont lourds et bornés.

## Le principe

Une option `multiple` du champ Référence, comme celle du Choix (`Choice`, `ChoiceShape.MULTIPLE`), pas un type de plus. Elle se range dans la config du champ à côté de `target`, pas dedans : un changement de `target` se lit comme un rétrécissement des cibles (`ReferenceTargetNarrowed`). Sa valeur devient une liste de ces objets, sans doublon (`uniqueItems`), dans l'ordre où on les a ajoutés, sans nombre maximal. Une Référence sans l'option ne change pas.

Le Choix multiple a tracé le chemin : on reprend ses solutions partout où elles s'appliquent.

## Ce qui reprend le Choix multiple

- **La config** : `multiple` parmi les réglages du champ (`FieldTypeSettings`, `FieldConfigValidator`).
- **Le schéma et la validation** : un tableau d'objets Référence (`FieldValueSchema`) ; l'existence de chaque chose désignée vérifiée à l'écriture, comme celle de la seule aujourd'hui (`FieldValueValidator`, le service).
- **La saisie et l'affichage** : plusieurs choses choisies et montrées, chacune avec son nom (`FieldInputRenderer`, `FieldValueDisplay`, `EntryText`). La saisie montre les choses choisies en puces, chacune avec sa croix, et un « + » qui ouvre le sélecteur d'aujourd'hui pour en ajouter une.
- **La valeur par défaut** (`default_value`) : une liste, du type du champ (`valueOfDefined`).
- **Les filtres** : « contient l'une de » sur une Référence multiple, comme `in` sur un Choix multiple (`EntryFilters`) ; `absent`, `present` inchangés.
- **Les graphiques** : une entrée qui désigne trois fiches compte pour chacune, par le `flatten` qui étale aujourd'hui un Choix multiple en une ligne par option (`ChartSpec`, `ChartCheck`, `ChartScales`).
- **L'IA** : le schéma lui dit la forme de la valeur ; à vérifier, les commandes qui nomment une Référence par son nom (`references.names`) la rendent pour chaque élément.
- **L'export** : la liste des noms, séparés comme les options d'un Choix multiple (`EntriesCsv`).
- **Le passage de simple à multiple et retour** : la comparaison des configs le traite comme le changement de forme d'un Choix (`FieldConfigComparator`, puis `MigrationStrategy.CONVERT`) : une référence devient une liste d'une ; repassée en simple, une liste d'une redevient sa référence, une plus longue est retirée, et le compte est dit avant d'enregistrer (`EntryMigration`).

## Ce qui reste comme la Référence simple

- **Le tri** (`EntrySort`) : une Référence n'est pas triable aujourd'hui, la multiple non plus.
- **Une chose désignée supprimée** : comme pour une Référence simple, rien ne la retire ; l'élément se montre « supprimé » (`ReferenceName.Deleted`). Le contrôle « qui dépend de ça ? » reste à part (`TODO.md`, « À concevoir »).

## Ce qui refuse la Référence multiple

Là où le sens veut une seule chose, la config qui y mène une Référence multiple est refusée à l'écriture, avec sa raison, comme un mauvais type de champ l'est aujourd'hui :

- **les valeurs d'un événement** qui portent une entrée (`EventValues`, `EventPoints`) ;
- **les termes d'une Tâche** qui lisent une Référence, son entrée reçue ou créée (`TaskRunner`, `TaskStepEditor`, `TermPicker`) ;
- **une Lecture** sur un champ Référence et ses réductions (`FieldReading`) : « la dernière » de trois n'a pas de sens ; le compte reste permis ;
- **une Condition** qui compare une Référence (`ConditionJudge`) ;
- **une variable** qui lit une Référence (`VariableService`) ;
- **le suivi d'un fichier** par une Lecture (`ConfigTerms`) ;
- **la source d'une carte** de Flashcards (`FlashcardsToolType`), écrite par l'app seule ;
- **les déclencheurs** dont le filtre porterait sur l'égalité d'une Référence (`Triggers`) : `contient l'une de` y est permis comme dans tout filtre.

La liste vient des 27 fichiers qui manipulent `FieldType.REFERENCE` au 2026-10-27 ; chacun est à relire, ceux qui ne sont ni ici ni plus haut compris (`EntrySort`, `ToolDataService`, `ToolInstanceService`, `AppSettings`, `ChartView`, `FieldDefinition`).
