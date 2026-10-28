"""The bench's scenarios (docs/AI.md, « Free models and the bench »), on the demo in French.

A chat scenario is the message the user sends; a routine scenario is a demo routine run
now. What each must leave is checked by scripts/bench_checks.py, under the same name; "expects"
says it in words, for the reviewer that reads a play (scripts/bench_review.py).
"""

SCENARIOS = {
    # Entry
    "entry_water": {"kind": "chat", "message": "J'ai bu 2 verres d'eau",
                    "expects": "Eau : +2 au compte du jour."},
    "entry_run": {"kind": "chat", "message": "Footing de 7,5 km ce matin en 42 minutes, ressenti 4",
                    "expects": "Sorties : 7,5 km, durée 42 min, type footing, ressenti 4, daté de ce matin."},
    "entry_sleep": {"kind": "chat", "message": "Cette nuit j'ai dormi 6 h 50, qualité moyenne",
                    "expects": "Sommeil : 6 h 50 datée de la nuit passée, qualité au milieu de l'échelle."},
    "entry_meal": {"kind": "chat", "message": "Ce midi : 150 g de patate douce et 120 g de poulet",
                    "expects": "Repas : deux entrées au déjeuner, chacune liée à la bonne fiche d'Aliments (Patate douce, Poulet) avec sa quantité."},
    "entry_shopping": {"kind": "chat", "message": "Ajoute du parmesan aux courses et coche le lait",
                    "expects": "Courses : un élément parmesan ajouté, l'élément « lait » existant coché."},
    "entry_ambiguous": {"kind": "chat", "message": "Note : les tomates cerises commencent à rougir",
                    "expects": "Une entrée dans Observations ou dans le Carnet du balcon, l'un ou l'autre."},
    # Reading
    "read_late_tasks": {"kind": "chat", "message": "Quelles tâches sont en retard ?",
                    "expects": "Rien d'écrit ; la réponse nomme toutes les tâches non cochées dont l'échéance est passée."},
    "read_km_week": {"kind": "chat", "message": "Combien de km j'ai couru cette semaine ?",
                    "expects": "Rien d'écrit ; la réponse donne la somme des Sorties depuis lundi."},
    "read_weight_7d": {"kind": "chat", "message": "Mon poids moyen sur les 7 derniers jours ?",
                    "expects": "Rien d'écrit ; la réponse donne la moyenne des pesées des 7 derniers jours, calculée ou lue dans poids_moyen_7j_demo."},
    "read_billable_hours": {"kind": "chat", "message": "Combien d'heures facturables pour Studio Brume ce mois-ci ?",
                    "expects": "Rien d'écrit ; la réponse donne les heures de Studio Brume marquées facturables depuis le 1er du mois, le chronomètre en cours compté ou non."},
    "read_kcal_yesterday": {"kind": "chat", "message": "Combien de calories j'ai mangé hier ?",
                    "expects": "Rien d'écrit ; la réponse donne la somme quantité × kcal ÷ 100 des repas d'hier."},
    # Automation
    "auto_morning": {"kind": "routine", "routine_id": "demo-auto-morning",
                    "expects": "Rien d'écrit ; la réponse cite la tâche « Relancer la Librairie » ; la session finit par completed."},
    "auto_menu": {"kind": "routine", "routine_id": "demo-auto-weekly-menu",
                    "expects": "Au moins 3 articles ajoutés à Courses, aucun en double d'un article présent non coché ; la session finit par completed."},
    "auto_review": {"kind": "routine", "routine_id": "demo-auto-weekly-review",
                    "expects": "Une entrée au Carnet d'entraînement portant les km de la semaine passée ; la session finit par completed."},
    # Configuration
    "config_km_target": {"kind": "chat", "message": "Monte mon objectif kilométrique à 25",
                    "expects": "La variable objectif_km_demo vaut 25, les critères de l'Objectif intacts."},
    "config_coffee": {"kind": "chat", "message": "Crée un suivi \"Café\" dans Cuisine, une tasse par toucher",
                    "expects": "Un Suivi numérique nommé Café dans la zone Cuisine, avec un raccourci qui saisit 1."},
    "config_elevation": {"kind": "chat", "message": "Ajoute un champ Dénivelé, en mètres, aux Sorties",
                    "expects": "Un champ numérique Dénivelé en m ajouté aux Sorties ; les autres champs et les entrées intacts."},
    "config_trail": {"kind": "chat", "message": "Ajoute \"trail\" aux types de sortie",
                    "expects": "Une option « trail » de plus au choix Type des Sorties ; les autres options et les entrées qui les portent intactes."},
    "config_reading_zone": {"kind": "chat", "message": "Crée une zone Lecture avec un suivi des pages lues et un graphique des pages par semaine",
                    "expects": "Une zone Lecture, un suivi numérique des pages lues, un graphique en barres par semaine qui le lit."},
    # Design: a need said in the user's words, whose answer hides a gesture on screen. Proposed or
    # built, either is right; what counts is the setup and telling what the user does with it.
    "design_weekly_talk": {"kind": "chat", "message": "J'aimerais faire le point avec toi sur ma semaine chaque dimanche, tous les deux",
                    "expects": "Conception : une routine sans planning (ou proposée telle), dont le bouton de conversation de la tuile ouvre une conversation avec son message ; l'IA dit ce bouton. Une routine planifiée seule, qui tourne sans l'utilisateur, ne répond pas au besoin."},
    "design_km_glance": {"kind": "chat", "message": "Je veux voir d'un coup d'œil mes km de la semaine sur la zone Course",
                    "expects": "Conception : la variable km_semaine_demo existe déjà ; sa tuile, dans la zone Course, montre sa valeur ; l'IA le dit, ou règle son affichage, sans créer de doublon."},
    "design_weight_alert": {"kind": "chat", "message": "Préviens-moi si mon poids dépasse 75",
                    "expects": "Conception : une condition sur le poids (dernière pesée ou poids_moyen_7j_demo > 75) qui envoie une notification quand elle devient vraie ; l'IA dit que la notification arrivera d'elle-même."},
    "design_next_session": {"kind": "chat", "message": "Quand je note une sortie, prépare-moi la suivante",
                    "expects": "Conception : un déclencheur sur une entrée écrite dans Sorties, qui fait partir une routine (ou une Tâche qui en lance une) préparant la sortie suivante ; l'IA dit que noter une sortie suffit à la faire partir."},
    "design_coffee_gesture": {"kind": "chat", "message": "Je veux noter mon café en un seul geste",
                    "expects": "Conception : un Suivi Café avec un raccourci qui note une tasse ; l'IA dit qu'un toucher sur le bouton du raccourci, sur la tuile, note la tasse."},
    "design_weather_button": {"kind": "chat", "message": "Je voudrais pouvoir lancer moi-même la mise à jour de la météo quand j'en ai besoin",
                    "expects": "Conception : la Tâche météo existe déjà ; son bouton sur sa tuile la lance à la main ; l'IA le dit, sans créer de doublon."},
}
