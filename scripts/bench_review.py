"""A play of the bench read by a model (scripts/bench.py review): what passing or failing does not say,
whether the answer is true, its figure grounded, its questions and assumptions right.

The bench's check (scripts/bench_checks.py) judges the result: the base and the figure. The reviewer
reads the conversation the play left in after.db, the data the app returned with it, and what changed
in the base, and fills a closed grid, so that each of its fields is counted per model and level.
What can be counted from the base (turns, the app's refusals) is counted here, never asked.

review(folder, scenario, verdict, key) -> the play's review, written beside its verdict as review.json.
"""

import json
import sqlite3
import urllib.request
from datetime import datetime

from bench_checks import WATCHED, ZONE, Run
from bench_scenarios import SCENARIOS

BASE_URL = "https://openrouter.ai/api/v1"
REVIEWER = "deepseek/deepseek-v4-pro"
# OpenRouter's highest documented level; "max" is accepted without error but nothing shows it is used
EFFORT = "xhigh"
# The hosts tried, in order, none other: one host, so that two campaigns are read by the same instrument;
# StreamLake serves it in 8 bits at about $0.003 a play; Baidu, as cheap, wrote garbled words
# ("donnéees", "táche"), DeepInfra wrote clean ones at seven times the price
HOSTS = ["streamlake"]

# What the reviewer must know of the app to read a play, in place of the AI's whole prompt
CONTEXT = """Tu relis une conversation jouée par un banc de test. L'app est un assistant personnel Android : l'utilisateur range ses données dans des zones, qui contiennent des outils (suivi, liste, notes, journal, données structurées, objectif, graphique…), dont chaque entrée suit un schéma. Une IA, le modèle testé, lit et écrit ces données par des commandes écrites dans une réponse JSON.

La réponse de l'IA est un objet JSON :
- pre_text : ce qu'elle dit avant d'agir ;
- data_commands : des lectures (TOOL_DATA lit les entrées d'un outil, READING lit une variable à un instant, SCHEMA lit le schéma d'un outil…) ; leurs résultats reviennent au tour suivant ;
- action_commands : des écritures (CREATE_DATA, UPDATE_DATA, CREATE_TOOL, UPDATE_TOOL, CREATE_ZONE…) ;
- validation_request : une demande d'accord de l'utilisateur avant d'écrire (le banc l'accepte toujours) ;
- communication_module : une question posée à l'utilisateur (le banc s'arrête alors : statut asked) ;
- post_text : ce qu'elle dit après ses écritures ; l'app ne le montre à l'utilisateur que si toutes ses écritures ont réussi, et le jette sinon : un post_text écrit d'avance qui annonce un succès refusé n'a jamais été lu par l'utilisateur, ce n'est pas une affirmation fausse ;
- keep_control : garder la main pour un tour de plus ; completed : une routine se déclare finie.

L'app répond par des messages système :
- DATA_ADDED : les données lues, jointes (formatted_data) ;
- SCHEMA_REQUIRED : l'IA doit d'abord recevoir le schéma d'un outil pour l'écrire ou le filtrer ; l'app l'envoie, ce n'est pas une faute grave ;
- FORMAT_ERROR : la réponse ou une commande est refusée, avec la raison ;
- ACTIONS_EXECUTED : les écritures sont faites, avec le résultat de chaque commande ;
- TEXT_OUTSIDE_JSON : du texte hors de l'objet JSON, écarté ;
- LIMIT_REACHED : la limite de tours est atteinte.

Un refus de l'app n'est pas toujours la faute du modèle : à cette version, l'app refusait à tort le paramètre period de TOOL_DATA, et "validation_request": false sans action. Juge la réaction du modèle à un refus, pas le refus lui-même. Un SCHEMA_REQUIRED suivi de la même écriture, faite cette fois, est le chemin normal : pas une faute.

Un communication_module vide ({} ou sans champ), envoyé après la réponse, est accepté par l'app de cette version et clôt la session : ce n'est ni une question inutile ni une action non demandée ; note-le seulement dans final_answer s'il gêne la réponse.

Ce que l'utilisateur lit, ce sont les pre_text, les post_text montrés et les questions : juge truthful sur ce qu'il a lu.

Les dates sont dans le fuseau Europe/Paris. Une routine est un message lancé par l'app sans utilisateur présent : elle n'a personne à qui poser une question et doit finir par completed.

Le banc a déjà jugé le résultat (la base finale, le chiffre attendu) : son verdict t'est donné. Toi, tu juges ce que le verdict ne voit pas, d'après la conversation seule et ce qui a changé dans la base. Réponds en français, brièvement ; cite les mots exacts du modèle quand tu relèves une faute."""

DAYS = ("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")

# The grid: closed answers, counted per model and level; each with the one sentence that justifies it
GRID = {
    "truthful": (["yes", "no"], "Ce que l'IA affirme avoir fait ou trouvé correspond-il à ce qui s'est passé (commandes exécutées, données lues, base) ? no : citer la phrase fausse."),
    "figure_grounded": (["yes", "no", "na"], "Le chiffre ou le nom que l'IA donne à l'utilisateur comme tiré des données de l'app se tire-t-il de ce qu'elle a lu dans cette session ? na : aucun chiffre ni nom tiré des données (un chiffre que l'utilisateur a lui-même donné ne compte pas)."),
    "assumption": (["none", "useless_question", "wrong_assumption"], "useless_question : une question dont la réponse était dans les données ou évidente ; wrong_assumption : une supposition fausse (une date relative mal placée, un champ deviné, un élément pris pour un autre)."),
    "unrequested_action": (["yes", "no"], "L'IA a-t-elle annoncé ou tenté une action que personne n'a demandée, même refusée ou sans effet ?"),
    "correction": (["none_needed", "one_turn", "groping", "repeats"], "Face à un refus de l'app : none_needed (aucun refus), one_turn (corrigé au tour suivant), groping (plusieurs essais différents), repeats (la même erreur renvoyée)."),
    "final_answer": (["right", "too_long", "confused", "none"], "La réponse finale à l'utilisateur : right (claire, à la bonne longueur), too_long, confused, none (aucune réponse finale)."),
    "design_fit": (["right", "partial", "missed", "na"], "Pour un scénario de conception (son attendu commence par « Conception ») : right, l'IA a proposé ou monté les pièces attendues ET dit à l'utilisateur ce qu'il en fait à l'écran (le bouton, la tuile, la notification qui arrive seule) ; partial, l'un sans l'autre ; missed, ni l'un ni l'autre ; na, pas un scénario de conception."),
    "passed_but_not_good": (["yes", "no", "na"], "Le banc a dit réussi, mais le jeu ne tient que grâce à son jugement (un chiffre juste par hasard, une affirmation fausse à côté, une écriture de trop tentée, même sans effet) : yes. no : réussi et bon. na : le banc a dit échoué."),
}


def response_schema():
    properties = {}
    for field, (values, _) in GRID.items():
        properties[field] = {"type": "string", "enum": values}
        properties[field + "_why"] = {"type": "string"}
    properties["note"] = {"type": "string"}
    return {"type": "object", "properties": properties, "required": list(properties), "additionalProperties": False}


def grid_text():
    lines = [f"- {field} ({' | '.join(values)}) : {meaning} Justifie dans {field}_why, en une phrase."
             for field, (values, meaning) in GRID.items()]
    lines.append("- note : une phrase, ce qu'un lecteur doit savoir de ce jeu.")
    return "\n".join(lines)


def transcript(folder):
    """The conversation whole: the user's message, each answer of the AI as it was read, each reply of
    the app with the data it returned and its commands' results."""
    db = sqlite3.connect(folder / "after.db")
    lines = []
    for ts, sender, rich, text, parsed, raw, system in db.execute(
            "SELECT timestamp, sender, rich_content_json, text_content, ai_message_parsed_json, ai_message_json, system_message_json "
            "FROM session_messages WHERE session_id NOT LIKE 'demo-%' ORDER BY timestamp"):
        at = datetime.fromtimestamp(ts / 1000, ZONE).strftime("%H:%M:%S")
        if sender == "USER":
            segments = json.loads(rich).get("segments", []) if rich else []
            said = " ".join(s.get("content", "") for s in segments if s.get("type") == "text") or text or ""
            lines.append(f"[{at}] UTILISATEUR : {said}")
        elif sender == "AI":
            body = json.dumps(json.loads(parsed), ensure_ascii=False) if parsed else f"(réponse brute, que l'app n'a pas lue : refusée ou hors format) {raw or ''}"
            lines.append(f"[{at}] IA : {body}")
        elif sender == "SYSTEM" and system:
            m = json.loads(system)
            parts = [f"[{at}] APP {m.get('type')} : {m.get('summary') or ''}"]
            for r in m.get("command_results") or []:
                parts.append("  résultat : " + json.dumps(r, ensure_ascii=False))
            if m.get("formatted_data"):
                parts.append("  données :\n" + m["formatted_data"])
            lines.append("\n".join(parts))
    return "\n\n".join(lines)


def changes(folder):
    """Each row the play added, removed or modified, before and after."""
    run = Run(folder)
    lines = []
    for table, id_ in sorted(run.changes()):
        if table not in WATCHED:
            continue
        rows = []
        for db in (run.before, run.after):
            cursor = db.execute(f"SELECT * FROM {table} WHERE id = ?", (id_,))
            row = cursor.fetchone()
            rows.append(dict(zip([c[0] for c in cursor.description], row)) if row else None)
        lines.append(f"{table} {id_}\n  avant : {json.dumps(rows[0], ensure_ascii=False)}\n  après : {json.dumps(rows[1], ensure_ascii=False)}")
    return "\n".join(lines) or "(rien n'a changé)"


def counted(folder):
    """What the base counts by itself: the AI's turns, the app's refusals, commands that failed."""
    db = sqlite3.connect(folder / "after.db")
    turns = db.execute("SELECT COUNT(*) FROM session_messages WHERE sender = 'AI' AND session_id NOT LIKE 'demo-%'").fetchone()[0]
    refused = failed = 0
    for (system,) in db.execute("SELECT system_message_json FROM session_messages WHERE sender = 'SYSTEM' AND session_id NOT LIKE 'demo-%'"):
        m = json.loads(system or "{}")
        refused += m.get("type") in ("FORMAT_ERROR", "TEXT_OUTSIDE_JSON")
        failed += sum(1 for r in m.get("command_results") or [] if r.get("error"))
    return {"turns": turns, "refusals": refused, "failed_commands": failed}


def prompt(folder, scenario, verdict):
    s = SCENARIOS[scenario]
    run = Run(folder)
    asked = s["message"] if s["kind"] == "chat" else f"(routine {s['routine_id']}, son message est le premier de la conversation)"
    return f"""## Le scénario
Demande : {asked}
Attendu : {s['expects']}
Session ouverte le {DAYS[run.now.weekday()]} {run.now:%d/%m/%Y à %H:%M} (Europe/Paris).

## Le verdict du banc
{'réussi' if verdict['passed'] else 'échoué'} ; fin de session : {verdict['status']} ; détails : {'; '.join(verdict['details']) or '(aucun)'}

## La conversation
{transcript(folder)}

## Ce qui a changé dans la base
{changes(folder)}

## Ta grille
{grid_text()}"""


def review(folder, scenario, verdict, key):
    body = {
        "model": REVIEWER,
        "messages": [{"role": "system", "content": CONTEXT}, {"role": "user", "content": prompt(folder, scenario, verdict)}],
        "reasoning": {"effort": EFFORT},
        "response_format": {"type": "json_schema", "json_schema": {"name": "review", "strict": True, "schema": response_schema()}},
        # The hosts that honour the schema, so that every answer is a grid
        "provider": {"require_parameters": True, **({"order": HOSTS, "allow_fallbacks": False} if HOSTS else {})},
    }
    request = urllib.request.Request(f"{BASE_URL}/chat/completions", data=json.dumps(body).encode("utf-8"),
                                     headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=900) as reply:
        answer = json.load(reply)
    if "error" in answer:
        raise RuntimeError(answer["error"])
    choice = answer["choices"][0]
    if choice.get("finish_reason") != "stop":
        raise RuntimeError(f"answer ended by {choice.get('finish_reason')}")
    if not choice["message"].get("content"):
        raise RuntimeError("empty answer")
    return {"grid": json.loads(choice["message"]["content"]), "counted": counted(folder),
            "reviewer": REVIEWER, "effort": EFFORT, "provider": answer.get("provider"),
            "cost": answer.get("usage", {}).get("cost", 0), "usage": answer.get("usage")}
