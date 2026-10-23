<?php
/**
 * The site's words, in each language, read by page.php through getSiteText().
 *
 * French is written first, English is its translation (docs/WRITING.md). Both languages hold the
 * same keys: a key missing from one fails the page rather than showing the other language.
 * What the app says itself (a tool type's name and tagline, a theme's name, a demo tool's name)
 * comes from generated.json and is not repeated here. "{android}" is the lowest Android version,
 * read from the app's build.
 */

return [
    'fr' => [
        'meta' => [
            'title' => 'Treelune — construis ton propre suivi',
            'description' => "Treelune, une app Android libre : des pièces qui s'emboîtent pour construire le suivi qu'il te faut, et une IA de ton choix qui s'en sert comme toi.",
            'other_language' => 'English',
        ],
        'nav' => [
            'themes' => 'Thèmes',
            'data' => 'Tes données',
            'discover' => 'Découvrir',
            'action' => 'En action',
            'tools' => 'Outils',
            'more' => 'Et aussi',
            'install' => 'Installer',
        ],
        'band' => [
            'pitch' => "Chaque app de suivi fait une chose, à sa façon. Treelune te donne les pièces, et tu construis celle qu'il te faut.",
            'ai' => "Une IA de ton choix se sert des mêmes pièces que toi. Elle peut par exemple créer un outil, noter une entrée ou préparer le bilan de la semaine.",
            'install' => 'Installer',
            'more' => 'Voir la suite',
            'picker_intro' => "L'app se décline en plusieurs thèmes :",
            'picker' => 'Thème',
            'previous' => 'Thème précédent',
            'next' => 'Thème suivant',
            'mode' => 'Clair ou sombre',
            'light' => 'Clair',
            'dark' => 'Sombre',
            'shot_alt' => "L'écran d'accueil de l'app dans le thème %s",
        ],
        'privacy' => [
            'title' => 'Tes données restent chez toi',
            'no_account' => [
                'title' => 'Pas de compte',
                'text' => 'Les données restent sur le téléphone, sans compte ni publicité.',
            ],
            'your_ai' => [
                'title' => "L'IA que tu choisis",
                'text' => "Elle s'utilise avec ta propre clé, ou sur ton propre serveur. L'app marche aussi sans IA.",
            ],
            'outside_ai' => [
                'title' => 'Ouverte à une IA extérieure',
                'text' => "Avec l'accès externe, une IA qui tourne ailleurs, comme Claude, ChatGPT ou un modèle libre sur ton ordinateur avec LM Studio, peut lire et écrire dans l'app. La connexion arrive sur le téléphone, sans passer par un serveur de Treelune.",
            ],
        ],
        'zone' => [
            'title' => "Des outils qui s'emboîtent au sein d'une zone",
            'lead' => "Dans la démo, la zone Italien prépare un voyage à Rome, avec une soirée d'italien chaque soir. Ce qui est noté dans un outil sert aussi aux autres.",
            'routine' => 'Routine',
            'shot_alt' => "La zone Italien de la démo, ses outils en tuiles",
            'demo-italian-evening' => "Un Questionnaire posé chaque soir à 20 h, pour noter ce qui a été fait et les mots appris. Ses réponses nourrissent l'Objectif, le Graphique et la Leçon du dimanche.",
            'demo-italian-goal' => "Un Objectif pour la semaine : 5 soirées remplies et 40 mots appris. Rien n'y est noté à la main, il compte les soirées et additionne les mots du Questionnaire.",
            'demo-italian-chart' => "Un Graphique des mots appris, semaine après semaine. Il se dessine à partir des soirées du Questionnaire, sans rien saisir de plus.",
            'demo-italian-lexicon' => "Le vocabulaire, avec une fiche par mot. Un mot ajouté ici entre de lui-même dans les Flashcards Ripasso.",
            'demo-italian-deck' => "Des Flashcards tirées du Lessico, sans recopier un seul mot. Chaque mot revient juste avant d'être oublié.",
            'demo-italian-phrases' => "Des Notes à relire dans le train pour Rome. La Leçon du dimanche y ajoute chaque semaine trois phrases sur ce qui a été le plus dur.",
            'demo-italian-suitcase' => "Une Liste à cocher avant le départ.",
            'demo-auto-italian-lesson' => "Chaque dimanche à 10 h, l'IA de l'app, dans le rôle d'une professeure d'italien, lit les soirées de la semaine et l'Objectif. Elle dit ce qui avance et ce qui coince, puis ajoute aux Phrases utiles trois phrases pour Rome.",
        ],
        'action' => [
            'title' => 'En action',
            'lead' => "Quelques secondes de l'app pendant qu'on s'en sert.",
            'ai_creates_tool' => [
                'name' => "L'IA crée un outil",
                'text' => "« Je voudrais suivre mon pouls au repos chaque matin. » L'IA propose un Suivi déjà réglé. Une fois validé, sa tuile apparaît dans la zone.",
            ],
            'quick_entry' => [
                'name' => 'Une saisie rapide',
                'text' => "Une sortie de 7,4 km se note depuis la tuile, sans ouvrir l'outil. L'Objectif de la semaine avance aussitôt.",
            ],
            'discussion' => [
                'name' => 'Une vraie discussion',
                'text' => "« Est-ce que je dors moins quand je travaille beaucoup ? » L'IA lit les deux outils et répond, chiffres à l'appui.",
            ],
            'routine' => [
                'name' => 'Une routine',
                'text' => "Chaque dimanche, l'IA de l'app fait seule le bilan de la semaine d'entraînement. Une notification arrive quand il est prêt.",
            ],
            'outside_ai' => [
                'name' => 'Avec une IA extérieure',
                'text' => "Par l'accès externe, une IA qui tourne ailleurs lit les données de l'app et y écrit. Pour un outil protégé, une notification demande ton accord.",
            ],
        ],
        'tools' => [
            'title' => 'Les outils',
            'lead' => "Chaque type d'outil se règle à ton idée, et les entrées d'un outil peuvent servir à un autre.",
            'shot_alt' => "Un outil %s dans l'app",
            'pieces_title' => 'Et ce qui les relie',
        ],
        'pieces' => [
            'routines' => ['name' => 'Routines', 'text' => "L'IA de l'app travaille seule à l'heure prévue, par exemple pour un bilan, un menu ou une leçon."],
            'variables' => ['name' => 'Variables', 'text' => "Un chiffre se calcule une fois et sert partout, comme les calories du jour ou les km de la semaine."],
            'conditions' => ['name' => 'Conditions et alertes', 'text' => "Une notification arrive quand une condition devient vraie, par exemple un poids au-dessus d'un seuil."],
            'identities' => ['name' => 'Identités', 'text' => "L'IA de l'app peut incarner un personnage, par exemple une professeure d'italien ou un coach."],
            'pointers' => ['name' => 'Pointeurs', 'text' => "Dans un message, un outil ou une zone se désigne directement, pour que l'IA sache de quoi il s'agit."],
            'external_access' => ['name' => 'Accès externe', 'text' => "Une IA qui tourne ailleurs, comme Claude, ChatGPT ou un modèle libre sur ton ordinateur avec LM Studio, peut lire et écrire dans l'app, le temps que tu ouvres l'accès."],
            'themes' => ['name' => 'Thèmes', 'text' => "La même app se décline en plusieurs thèmes, presque tous avec leurs propres sons."],
        ],
        'install' => [
            'title' => 'Installer Treelune',
            'lead' => 'Une application Android libre, sans compte ni publicité.',
            'android' => 'Android {android} ou plus récent',
            'arm64' => 'Un téléphone 64 bits',
            'fdroid' => 'F-Droid',
            'apk' => 'APK sur GitHub',
            'young' => "Treelune est encore jeune, et n'a été essayée que sur quelques téléphones. Pour qu'un problème soit corrigé, signale-le depuis Réglages › Signaler un bug.",
        ],
        'footer' => [
            'licence' => "Treelune, sous licence GPL-3.0. Ce site n'a aucun traceur.",
            'source' => 'Code source',
            'bug' => 'Signaler un bug',
        ],
        'common' => [
            'theme' => 'Thème : %s',
            'previous' => 'Précédent',
            'next' => 'Suivant',
            'enlarge' => 'Agrandir',
            'close' => 'Fermer',
            'stand_in' => 'À prendre : %s',
        ],
    ],

    'en' => [
        'meta' => [
            'title' => 'Treelune — build your own tracker',
            'description' => 'Treelune, a free Android app: pieces that fit together to build the tracker you need, and an AI of your choice that uses them as you do.',
            'other_language' => 'Français',
        ],
        'nav' => [
            'themes' => 'Themes',
            'data' => 'Your data',
            'discover' => 'Discover',
            'action' => 'In action',
            'tools' => 'Tools',
            'more' => 'And more',
            'install' => 'Install',
        ],
        'band' => [
            'pitch' => 'Every tracking app does one thing, its own way. Treelune gives you the pieces, and you build the one you need.',
            'ai' => 'An AI of your choice uses the same pieces as you. It can, for example, create a tool, note an entry or prepare the week’s review.',
            'install' => 'Install',
            'more' => 'See what follows',
            'picker_intro' => 'The app comes in several themes:',
            'picker' => 'Theme',
            'previous' => 'Previous theme',
            'next' => 'Next theme',
            'mode' => 'Light or dark',
            'light' => 'Light',
            'dark' => 'Dark',
            'shot_alt' => 'The app’s home screen in the %s theme',
        ],
        'privacy' => [
            'title' => 'Your data stays with you',
            'no_account' => [
                'title' => 'No account',
                'text' => 'The data stays on the phone, with no account and no ads.',
            ],
            'your_ai' => [
                'title' => 'The AI you choose',
                'text' => 'It runs with your own key, or on your own server. The app also works without AI.',
            ],
            'outside_ai' => [
                'title' => 'Open to an outside AI',
                'text' => 'With the external access, an AI running elsewhere, such as Claude, ChatGPT or a free model on your computer with LM Studio, can read and write in the app. The connection reaches the phone without going through a Treelune server.',
            ],
        ],
        'zone' => [
            'title' => 'Tools that fit together within a zone',
            'lead' => 'In the demo, the Italian zone prepares a trip to Rome, with an Italian evening every night. What is noted in one tool serves the others too.',
            'routine' => 'Routine',
            'shot_alt' => 'The demo’s Italian zone, its tools as tiles',
            'demo-italian-evening' => 'A Questionnaire asked every evening at 8 pm, to note what was done and the words learnt. Its answers feed the Goal, the Chart and the Sunday lesson.',
            'demo-italian-goal' => 'A Goal for the week: 5 evenings filled and 40 words learnt. Nothing is noted in it by hand, it counts the evenings and adds up the words from the Questionnaire.',
            'demo-italian-chart' => 'A Chart of the words learnt, week after week. It is drawn from the Questionnaire’s evenings, with nothing more to enter.',
            'demo-italian-lexicon' => 'The vocabulary, with one record per word. A word added here goes into the Ripasso Flashcards by itself.',
            'demo-italian-deck' => 'Flashcards drawn from the Lessico, without copying a single word. Each word comes back just before it is forgotten.',
            'demo-italian-phrases' => 'Notes to read again on the train to Rome. Every week, the Sunday lesson adds three phrases about what was hardest.',
            'demo-italian-suitcase' => 'A List to tick before leaving.',
            'demo-auto-italian-lesson' => 'Every Sunday at 10 am, the app’s AI, as an Italian teacher, reads the week’s evenings and the Goal. It says what moves forward and what gets stuck, then adds three phrases for Rome to the Useful phrases.',
        ],
        'action' => [
            'title' => 'In action',
            'lead' => 'A few seconds of the app while it is being used.',
            'ai_creates_tool' => [
                'name' => 'The AI creates a tool',
                'text' => '“I’d like to track my resting heart rate each morning.” The AI offers a Tracking already set up. Once approved, its tile appears in the zone.',
            ],
            'quick_entry' => [
                'name' => 'A quick entry',
                'text' => 'A 7.4 km run is noted from the tile, without opening the tool. The week’s Goal moves forward at once.',
            ],
            'discussion' => [
                'name' => 'A real conversation',
                'text' => '“Do I sleep less when I work a lot?” The AI reads both tools and answers, figures in hand.',
            ],
            'routine' => [
                'name' => 'A routine',
                'text' => 'Every Sunday, the app’s AI reviews the training week on its own. A notification comes when it is ready.',
            ],
            'outside_ai' => [
                'name' => 'With an outside AI',
                'text' => 'Through the external access, an AI running elsewhere reads the app’s data and writes in it. For a protected tool, a notification asks for your consent.',
            ],
        ],
        'tools' => [
            'title' => 'The tools',
            'lead' => 'Each tool type is set up your way, and one tool’s entries can serve another.',
            'shot_alt' => 'A %s tool in the app',
            'pieces_title' => 'And what ties them together',
        ],
        'pieces' => [
            'routines' => ['name' => 'Routines', 'text' => 'The app’s AI works on its own at the set time, for example for a review, a menu or a lesson.'],
            'variables' => ['name' => 'Variables', 'text' => 'A figure is computed once and used everywhere, such as the day’s calories or the week’s km.'],
            'conditions' => ['name' => 'Conditions and alerts', 'text' => 'A notification comes when a condition becomes true, for example a weight above a threshold.'],
            'identities' => ['name' => 'Identities', 'text' => 'The app’s AI can play a character, for example an Italian teacher or a coach.'],
            'pointers' => ['name' => 'Pointers', 'text' => 'In a message, a tool or a zone is pointed to directly, so that the AI knows what it is about.'],
            'external_access' => ['name' => 'External access', 'text' => 'An AI running elsewhere, such as Claude, ChatGPT or a free model on your computer with LM Studio, can read and write in the app, for as long as you open the access.'],
            'themes' => ['name' => 'Themes', 'text' => 'The same app comes in several themes, nearly all with their own sounds.'],
        ],
        'install' => [
            'title' => 'Install Treelune',
            'lead' => 'A free and open source Android app, with no account and no ads.',
            'android' => 'Android {android} or later',
            'arm64' => 'A 64-bit phone',
            'fdroid' => 'F-Droid',
            'apk' => 'APK on GitHub',
            'young' => 'Treelune is still young, and has been tried on only a few phones. To get a problem fixed, report it from Settings › Report a bug.',
        ],
        'footer' => [
            'licence' => 'Treelune, under the GPL-3.0 licence. This site has no tracker.',
            'source' => 'Source code',
            'bug' => 'Report a bug',
        ],
        'common' => [
            'theme' => 'Theme: %s',
            'previous' => 'Previous',
            'next' => 'Next',
            'enlarge' => 'Enlarge',
            'close' => 'Close',
            'stand_in' => 'To take: %s',
        ],
    ],
];
