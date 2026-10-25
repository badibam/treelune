# AI system - technical documentation

## 1. General architecture

### Unified pattern
All AI interactions use the same data structure `SessionMessage`, with 5 variants according to the fields filled. Unified sessions allow reusing the UI components, the persistence logic and the prompt transformation.

### Session types
- **CHAT**: real-time conversation, absolute queries, communication modules
- **SEED**: automation template (user message + enrichments), never run
- **AUTOMATION**: autonomous run, relative queries, copies the SEED messages at the start

### Event-driven architecture (V2)
The AI orchestrator works as a state machine driven by events. A single source of truth with atomic synchronisation memory + DB.

**Main components**:
- **AIOrchestrator**: singleton public facade exposing an observable `currentState`
- **AIStateMachine**: pure state machine (transitions without side effects)
- **AIEventProcessor**: event loop with side effects (DB, network, commands)
- **AIStateRepository**: atomic management of the state, memory + DB sync
- **AIMessageRepository**: message persistence with an observable cache
- **AISessionScheduler**: scheduling, interruption, inactivity computation

**AIOrchestrator public API**:
- `currentState: StateFlow<AIState>`: observable state (phase, sessionId, counters, timestamps, waitingContext)
- `observeMessages(sessionId): Flow<List<SessionMessage>>`: observable messages
- `sendMessage(richMessage)`: send a user message
- `requestChatSession()`: create/activate a CHAT session
- `executeAutomation(automationId)`: start an automation
- `stopActiveSession()`: stop with CANCELLED
- `resumeActiveSession()`: resume the session
- `resumeWithValidation(approved)`: answer a validation
- `resumeWithResponse(response, note)`: answer a communication module, with the precision added

### Phase and AIState

**Phase**: 13 execution phases
- `IDLE`: no active session
- `EXECUTING_ENRICHMENTS`: processing the user's enrichments
- `CALLING_AI`: calling the AI provider
- `PARSING_AI_RESPONSE`: parsing the JSON response
- `WAITING_VALIDATION`: waiting for the user's validation (CHAT)
- `WAITING_COMMUNICATION_RESPONSE`: waiting for the answer to a communication module (CHAT)
- `WAITING_DATA_CONFIRMATION`: waiting for the user about data beyond the size threshold (CHAT)
- `EXECUTING_DATA_QUERIES`: running data commands
- `EXECUTING_ACTIONS`: running action commands
- `WAITING_NETWORK_RETRY`: waiting for a network retry (AUTOMATION)
- `RETRYING_AFTER_FORMAT_ERROR`: retry after a format error
- `RETRYING_AFTER_ACTION_FAILURE`: retry after failed actions
- `COMPLETED`: session over

**AIState**: the system's complete state
```kotlin
data class AIState(
    val sessionId: String?,
    val phase: Phase,
    val sessionType: SessionType?,
    val totalRoundtrips: Int,
    val lastEventTime: Long,
    val lastUserInteractionTime: Long,
    val waitingContext: WaitingContext?
)
```

**WaitingContext**: typed waiting contexts, held in the state only, never stored
- `Validation(validationContext)`: waiting for the actions' validation
- `Communication(communicationModule, aiMessageId)`: waiting for a communication answer

### AIEvent
Events triggering transitions: `SessionActivationRequested`, `UserMessageSent`, `EnrichmentsExecuted`, `AIResponseReceived`, `AIResponseParsed`, `ValidationReceived`, `DataConfirmationRequested`, `DataConfirmationReceived`, `CommunicationResponseReceived`, `DataQueriesExecuted`, `ActionsExecuted`, `NetworkErrorOccurred`, `ParseErrorOccurred`, `ActionFailureOccurred`, `NetworkRetryScheduled`, `RetryScheduled`, `NetworkAvailable`, `SystemErrorOccurred`, `SessionCompleted`, `SchedulerHeartbeat`.

## 2. Types and structures

### Result types
- **OperationResult**: services with `.success: Boolean`
- **CommandResult**: Coordinator with `.status: CommandStatus` (SUCCESS, FAILED, CANCELLED, CACHED)
- **Reads sent once per session** (`SessionReads`): a schema, an identity, a resource of its baggage. Each read runs; its result keeps its target (`entries:<id>`, `tracking_config`, `identity_resource:<id>:<language>:<key>`) and the fingerprint of its text. A text identical to the last one received for the same target becomes CACHED, without data; a changed text goes out whole again, preceded by the sentence saying it replaces the previous read (`ai_read_replaces`). A schema of a tool type, its config's or a tool's entries', comes after the first level of that type's entry (`EntryDescription`, target `entry:<tooltype>`): what its name, timestamp, data, extra and state are in the type, which neither schema says; itself a read sent once, whichever schema brings it first, in a SCHEMA command's results or a SCHEMA_REQUIRED message. Without a session (an outside AI), once per batch of commands.

### AISessionEntity (DB)
```kotlin
data class AISessionEntity(
    val id: String,
    val name: String,
    val type: SessionType,
    val validateApp: Boolean, val validateZones: Boolean, val validateData: Boolean, // SessionValidation
    val phase: String, // Current phase (serialized)
    val totalRoundtrips: Int,
    val lastEventTime: Long,
    val lastUserInteractionTime: Long,
    val automationId: String?,
    val scheduledExecutionTime: Long?,
    val providerId: String,
    val providerSessionId: String,
    val createdAt: Long,
    val lastActivity: Long,
    val isActive: Boolean,
    val endReason: SessionEndReason?,
    val tokensUsed: String? // JSON tokens breakdown
)
```

### SessionMessage (unified structure)
```kotlin
data class SessionMessage(
    val id: String,
    val timestamp: Long,
    val sender: MessageSender, // USER, AI, SYSTEM
    val richContent: RichMessage?, // The user's enriched messages
    val textContent: String?, // Simple messages (module answers)
    val aiMessage: AIMessage?, // AI structure parsed for the UI
    val aiMessageJson: String?, // Original JSON for the prompts' history
    val systemMessage: SystemMessage?, // System messages with results
    val executionMetadata: ExecutionMetadata?, // Automations only
    val excludeFromPrompt: Boolean = false // Excluded from the prompt (UI messages only)
)
```

**Storage pattern**: separate messages USER → SYSTEM → AI → SYSTEM. The provider adjusts to its constraints.

**PostText**: the "it's done" text, shown as a separate message (`sender=AI`, `textContent=postText`, `excludeFromPrompt=true`, the AI reading it again in its answer's JSON): after the actions if they all succeed; right after `preText` when the answer has no actions, nothing being then to wait for. An action fails: it is not shown, and the results' summary tells the AI so (`ai_posttext_not_shown`).

### RichMessage and AIMessage
```kotlin
data class RichMessage(
    val segments: List<MessageSegment> // Text | EnrichmentBlock(type, config): nothing else is stored
)

data class AIMessage(
    val preText: String, // Required
    val validationRequest: Boolean?, // true = validation required
    val dataCommands: List<DataCommand>?, // OR actions (exclusive)
    val actionCommands: List<DataCommand>?,
    val postText: String?,
    val keepControl: Boolean?, // true = keeps control after the actions succeed
    val communicationModule: CommunicationModule?,
    val completed: Boolean? // true = work finished (AUTOMATION only)
)
```

**AIMessage patterns**: Actions (preText + validationRequest? + actionCommands + postText?), Queries (preText + dataCommands + postText?), Communication (preText + communicationModule + postText?).

**Parsing fallback**: if the JSON parsing fails, an AIMessage is created with the prefix `"ai_response_invalid_format"` + the raw text in preText.

### SystemMessage
```kotlin
data class SystemMessage(
    val type: SystemMessageType, // DATA_ADDED, ACTIONS_EXECUTED, LIMIT_REACHED, FORMAT_ERROR, NETWORK_ERROR, SESSION_TIMEOUT
    val commandResults: List<CommandResult>,
    val summary: String,        // the sentence the user reads, in plain language
    val formattedData: String?  // what the AI reads besides: the data read, or the text written for it
)

enum class SystemMessageType {
    DATA_ADDED, // Query results → sent to the prompt
    ACTIONS_EXECUTED, // Action results → sent to the prompt
    LIMIT_REACHED, // Limit reached → sent to the prompt
    FORMAT_ERROR, // Format error in the AI's answer → sent to the prompt for correction
    SCHEMA_REQUIRED, // Schemas of the entries a query or a write expects → sent to the prompt, commands not run
    NETWORK_ERROR, // Network/HTTP errors → filtered from the prompt, visible in the UI (audit + transparency)
    PROVIDER_ERROR, // Provider not configured/invalid → filtered from the prompt, visible in the UI (audit + transparency)
    SESSION_TIMEOUT, // Session watchdog timeout → filtered from the prompt, visible in the UI (audit + transparency)
    TEXT_OUTSIDE_JSON, // Text written around the answer's JSON, set aside → quoted to the user (summary), the AI receives only the mention (formattedData)
    COMPLETED_CONFIRMATION, // The AI says it has finished → the request for confirmation it receives (formattedData)
    AUTOMATION_NO_COMMANDS, // An automation answers without a command → the reminder it receives (formattedData)
    APP_ERROR // An error of the app stops the turn → its cause (formattedData), filtered from the prompt
}
```

**summary and formattedData**: `summary` is what the user reads, always in plain language; the AI reads `summary`, one line per command result, then `formattedData` (`toPromptText`). What is written only for the AI (an instruction, the detail of a format error, the raw message of a provider or an exception) goes into `formattedData`, which the chat folds under "Details" (`SystemMessage.details`) — except for the types where it is data: DATA_ADDED, ACTIONS_EXECUTED, DATA_AWAITING_CONFIRMATION, SCHEMA_REQUIRED.

**formattedData of DATA_ADDED**: complete JSON data formatted for the prompt. Concatenation of `PromptCommandResult` with titles.

### Commands and PromptData
```kotlin
data class DataCommand(
    val id: String, // Deterministic hash
    val type: String, // TOOL_DATA, CREATE_DATA, etc.
    val params: Map<String, Any?>, // a null asks to empty the field, at any level
    val isRelative: Boolean = false
)

data class ExecutableCommand(
    val resource: String, // "zones", "tool_data"
    val operation: String, // "get", "batch_create"
    val params: Map<String, Any?>
)

data class PromptData(
    val level1Content: String, // System documentation (with limits)
    val level2Content: String, // User data (always_send tools)
    val sessionMessages: List<SessionMessage>
)
```

### AIResponse
```kotlin
data class AIResponse(
    val success: Boolean,
    val content: String,
    val errorMessage: String? = null,
    val tokensUsed: Int = 0,
    val cacheWriteTokens: Int = 0, // Cache write/creation tokens (generic, all providers)
    val cacheReadTokens: Int = 0, // Cache read tokens (generic, all providers)
    val inputTokens: Int = 0 // Uncached input tokens
)
```

## 3. AI configuration

### AILimitsConfig
The global configuration of the AI's limits, stored in the settings category `ai_limits` and set in the AI limits screen.

```kotlin
data class AILimitsConfig(
    val chatMaxAutonomousRoundtrips: Int = 10,
    val automationMaxAutonomousRoundtrips: Int = 20,
    val chatMaxDataChars: Int = 15_000,
    val automationMaxDataChars: Int = 100_000,
    val alwaysSendMaxChars: Int = 15_000,
    val chainMaxRuns: Int = 50
)
```

The data size thresholds are described with the waiting for confirmation, section "Validation and communication".

**Limit of calls**:
- **AutonomousRoundtrips**: the number of calls to the AI in a row. In CHAT, the limit reached hands control back to the user (back to IDLE, the session stays open); in AUTOMATION, it closes the session (`LIMIT_REACHED`).

**Counter**: `totalRoundtrips` counts the calls to the AI since the user's last intervention — a message, an answer to a question or a validation, a cancellation, an interruption. Each one resets it: the limit bounds what the AI does alone, never the length of a conversation. An AUTOMATION, with nobody to intervene, therefore counts its whole session.

**Rationale**: each call is billed, and nothing else stops an AI that calls itself in a loop — in CHAT as in AUTOMATION. No separate limits on format errors or action failures: the AI must correct itself, and the roundtrip limit is enough as a net.

**Runs per chain** (`chainMaxRuns`): the runs of Tasks and routines one chain holds, each started by the one before (docs/CORE.md, « Chains »). The roundtrip limit bounds one run; a loop from run to run, each with its counter at zero, is bounded by this one.

**Storage**: the defaults of `AILimitsConfig` are the only ones. A new install or a reset writes them through `toSettingsJson()`; `fromSettingsJson()` requires every key and fails if one is missing.

**API**: `AppConfigService.getAILimits()`, `AppConfigManager.getAILimits()` (volatile cache).

**Level 1 inclusion**: limits documented in the L1 prompt.

## 4. Components and responsibilities

### Repositories
- **AIStateRepository**: atomic management of AIState (memory + DB sync), Entity ↔ Domain conversion, initialisation from the DB
- **AIMessageRepository**: synchronous message persistence, observable cache per session, Entity ↔ Domain conversion

### Processing
- **AIEventProcessor**: event loop with side effects (executeEnrichments, callAI, parseAIResponse, executeDataQueries, executeActions)
- **AISessionScheduler**: session queue, inactivity computation, timeout detection, eviction/resumption of sessions

### Coordination
- **AIOrchestrator**: singleton public facade, delegating to the specialised components
- **AIClient**: interface to the external providers
- **PromptManager**: generation of the L1-L2 prompts
- **EnrichmentProcessor**: generates commands from the UI's enrichments
- **CommandTransformer**: transforms DataCommand → ExecutableCommand
- **CommandExecutor**: single point of execution + generation of the SystemMessage
- **ValidationResolver**: what waits for the user's agreement, by levels (app, zone, tool), session and the AI's request

### Command processing pipeline
```
User: EnrichmentBlock → EnrichmentProcessor → CommandTransformer → CommandExecutor
AI: AIMessage → CommandTransformer → CommandExecutor
```

**Command catalogue**: each AI command is declared once in `AICommands`: its type, query or action, its parameters (JSON type, required or not), the text that describes it to the model (`ai_command_*` in `ai_prompt_chunks.xml`) and whether it writes into the entries. `AICommandProcessor` refuses, naming them, an unknown type and a parameter the command does not take, missing or of another type; the forced response schema, the list of verbalised actions and the L1 are drawn from it, the latter assembling the texts in the catalogue's order. `AICommandsTest` holds each text to its declaration.

**Shared notions**: a piece of the L1 marked `shared` says what the app is (environment, fields, schemas, values written by the app, dates, tool types) and holds for any AI that works in it; the others say how the built-in AI talks to it (JSON envelope, session, examples in that envelope, schema required in the session). `PromptChunks.buildAppNotions` assembles the former, for an outside AI.

**External access (MCP server)**: an outside AI calls the same commands through the app's MCP server (`core/mcp`; how a request reaches the phone and who may send it: `docs/EXTERNAL_ACCESS.md`). One tool per command of the catalogue, named by its type in lower case, described by its text; `app_context` returns the shared notions, L2 and L3, and the context token every other tool requires. A command goes through `AICommandProcessor` and `CommandExecutor` like those of the built-in AI, as source `EXTERNAL`, without a session: no schema required before a write; a write a protection guards waits for the user's agreement, given from a notification with two buttons (`McpApprovals`, 90 s at most), the server passing to the backend the client that calls (`McpCaller`). Each client has its access mask (`mcp_clients.access_json`, `McpClientAccess`, empty by default), set in the "External access" screen: its commands run under its `AccessScope`, and the app's context (`app_context`) tells it so. The server answers through a relay (`RelayLoop`), authorises its clients through OAuth (`OAuthServer`, a code typed on the phone) and runs only while the access is open (`McpAccess`, `McpAccessService`).

**Identities** (service `identities`, the file `docs/IDENTITY_FILE.md`): an object of the app that an import fills or the user writes by hand — an `id`, a name, an icon and its colour, a language, and per language a voice, a mind, a portrait and a baggage of resources; the whole file is kept, all its languages. Each piece imported is marked so (`imported`), a retouch removing the mark, and a new import of the same `id` replaces everything after naming the retouches and languages lost; exported from its screen as the same file (`identities.export`, `IdentityFile.write`, into the app's folder), every language holding a text and the one it speaks one at least, dated as its author's version while nothing changed since its import (`updated_at` still its `imported_at`) and of the day otherwise; edited as raw text, read formatted (`MarkupText`). The AI of the app operates (the JSON, the commands, the validations, the forms), the identity writes what the user reads (`pre_text`, `post_text`, a module's questions, the text of the entries it writes); the structure of the app stays neutral (names, descriptions, a routine's message). A zone names one, shown at its top with a button that opens a new conversation with it; the chat's identity button chooses one before the first message, where it is fixed — who speaks, not a version: an identity changed counts from the next turn. A routine created by the AI gives `identity_id`, `null` for the ordinary AI, refused when absent. Under a mask (a routine, a connector's client) an identity is read freely and never written.

A session, a routine or a zone can name an identity (`identity_id`). A session that has one receives, right after the AI's role in the L1, who writes what the user reads, its voice, its mind and its portrait in its language and its listed baggage (`IdentityPrompt`), read again from the database at each call; a deleted identity stops the session, saying so. IDENTITIES, IDENTITY and IDENTITY_RESOURCE read it once per session and outside the conversation's data threshold (`PromptCommandResult.sizeExempt`), in slices through the connector (`McpReads`); CREATE_IDENTITY, UPDATE_IDENTITY (a whole piece) and DELETE_IDENTITY go at the app's level. An identity or a provider is not deleted while a zone or a routine names it (`InUse`).

**History of writes** (`docs/DATA.md`, service `history`): HISTORY lists the steps, of a tool, of a zone or of a period; UNDO_STEP and REDO_STEP undo and redo a step by its id, the user's included. Each AI command is a step that keeps its conversation (`StepSession`, set by `CommandExecutor`). An undo asks for agreement at the level of each row it puts back, as the write of that row would (`ValidationResolver.stepReason`); under a mask, the history of a tool or a zone is read at their level, an undo at the app's level.

**A tool type's operations**: `TOOL_OPERATION` (`tool_instance_id`, `operation`, `params`) becomes `{tooltype}.{operation}` in `AICommandProcessor`, the type read from the tool. The parameters pass into milliseconds, then are checked against the schema generated from their declaration (`ToolOperations.schema`); an unknown operation is refused with the list of those the type declares. The AI reads these operations after the schema of a tool's entries (`CommandExecutor.operationsForModel`): the L1 prompt describes only the command, never the catalogue.

## 5. Session control

### Exclusive active session
A single active session at a time (CHAT or AUTOMATION). `AISessionScheduler` manages the queue and the activation.

**Activation rules**:
- **CHAT**: immediate interruption if another session is active (even an AUTOMATION)
- **AUTOMATION MANUAL**: queued if the slot is taken, FIFO activation
- **AUTOMATION SCHEDULED**: created on demand by `tick()` if the slot is free + the queue empty

**Scheduling**: double heartbeat (1 min coroutine with the app open + exact 10 min alarm with the app closed, `SchedulerAlarm`) + event-driven (CRUD of automations, end of a session).

**Inactivity**: computed through `AIState.calculateInactivity()`, the active phases (CALLING_AI, EXECUTING_*) never time out, the waiting phases use `lastUserInteractionTime`.

**SUSPENSION (system)**:
- `endReason = SUSPENDED`, frees the slot
- Session evicted to make room for a CHAT
- Resumes automatically when the slot is free (scheduler)

## 6. Automations

### Concept
AUTOMATION sessions created from a SEED template (user message + enrichments). At each trigger: SEED messages copied → new AUTOMATION session → activation.

`automations.create` writes the routine, its SEED session and its message in a single transaction, or nothing; the message is given in blocks (`AutomationMessage`: text and pointers), empty when the screen writes it afterwards. `ai_sessions.create_session` refuses a SEED session alone.

### The AI and the routines
The AI reads, creates, changes and deletes the routines, and reads their runs (`AUTOMATIONS`, `AUTOMATION_CONFIG`, `AUTOMATION_RUNS`, `AUTOMATION_RUN`, `CREATE_AUTOMATION`, `UPDATE_AUTOMATION`, `DELETE_AUTOMATION`); it does not start a routine. Its config is written in the form it is read in (`AutomationSchemas`, schema `automation_config`): `zone_id`, `settings`, `access`, `message` in blocks; the instants and durations pass into ISO there through that schema (`ModelValues`), those of the runs through the schema `automation_runs`. A run is read again as its AI received it (`PromptManager.runHistory`), the data attached at the start replaced by the mention of their pointers. The routines count at their zone's validation level; under a mask, they are written with the zone at `full`, and the mask an AI gives a routine, or leaves it, is never wider than its own (`AccessRules.widening`). `APP_STATE` and the level 3 snapshot carry them in one line each (`AutomationSchemas.listed`), without their message.

### The AI and the Tasks
The AI lists the Tasks (`TASKS`, one line each: how many triggers start it, its steps by their kind), reads one whole (`TASK_CONFIG`, schema `task_config`, `TaskSchemas`), writes them (`CREATE_TASK`, `UPDATE_TASK`, `DELETE_TASK`) and runs one now (`RUN_TASK`, which answers the run's id, not its outcome). Its settings' instants and durations pass in ISO 8601 through their schema; a step's constant does too, as the field it fills says (a tool's field, an API's input), never by the look of the value. Under a mask, it writes only steps it could take itself — never one that runs a routine, which the screens alone start — and runs a Task of a zone it may use (docs/CORE.md, « Tasks »). Its writes ask for approval as a write in the Task's zone, a run too. A routine run by a Task receives what it is passed joined to the end of its message (`execute_manual`'s `data`).

### Triggering
**ExecutionTrigger** distinguishes the origin:
- **MANUAL**: the user clicks Execute → `executeAutomation(id)` → queued if the slot is taken
- **SCHEDULED**: tick() computes the next run → created only if the slot is free + the queue empty
- **ENTRY**: an entry written that one of its triggers hears (docs/CORE.md, « Triggers ») → `executeAutomation` with the entry attached → queued if the slot is taken

**tick() triggers**:
- Periodic: AIOrchestrator coroutine (1 min, app open) + `SchedulerAlarm` (10 min, app closed)
- Event-driven: CRUD of automations (create/update/enable/disable)
- End of a session: scheduler triggered after the slot is freed

### Pull-based architecture
```kotlin
tick() {
  if (slotTaken) return
  if (queueNotEmpty) processQueue()
  else {
    nextSession = AutomationScheduler.getNextSession() // Dynamic computation
    if (nextSession) executeAutomation(id)
  }
}
```

**AutomationScheduler**: a pure computation helper. Finds incomplete sessions (endReason null/NETWORK_ERROR/SUSPENDED) OR the next run from the history.

### Missed runs

When the app did not run at the planned time, the `catch_up` settings of a scheduled automation (declared in `AutomationSettings.catchUpNodes`, present with the schedule and only with it; `AutomationService` refuses the rest):

- **`limit`**: `limited` or `unlimited`, chosen explicitly, without a default value. `limited` brings **`window`**, a DURATION in milliseconds: beyond that delay, the occurrence is skipped — a log line, no session (the history is made of sessions; an empty "skipped" session would be one more form to handle everywhere).
- **`dismiss_older_instances`**: among the occurrences due, start only the most recent. Not deduced from the window.

An automation's settings (its look, provider, group, activation, schedule, catching up) are declared in `AutomationSettings`; `AutomationService` checks every write against the generated schema, and `Automation.fromResult` is the only reader of its results. Its look is the Look brick's (`AutomationSettings.LOOK`: an icon, `bot` by default, from ICON to SQUARE, EXTENDED by default): a routine is a tile of its group's section among the tools and variables (`grid_x`, `grid_y`, `SectionGrid`), « Routine » under its name (`AutomationTile`): its state and its switch from LINE on, its four buttons (run or cancel while queued, a conversation with its message, its history, its message to edit) on a line of their own from EXTENDED on, its last three runs with their summary in SQUARE. A touch opens its page (`AutomationScreen`), which holds the same at its top whatever the mode, its settings in the history's place; a long touch opens its settings. The actions are one class for both (`AutomationActions`).

A routine's message screen (`AutomationEditor`, the SEED mode of `AIScreen`) scrolls as one block: the message, then one button per setting that sums it up and opens its window — the schedule with the catching up below, the triggers, the access —, then Save, which writes the message and the settings together.

The search for the next occurrence starts at the earliest at `now − window` (`AutomationScheduler.searchStart`). The most recent one due is found by bisection on the search's start (`lastDueOccurrence`): the calculator answers only "the first after this instant", and that answer never decreases as the instant grows.

**Time resolution**: an AUTOMATION session resolves its relative dates, `{"relative": "NOW"}` included, on its `scheduledExecutionTime`, not on the clock — otherwise all the catch-up runs read the same day. The instant is chosen by `AIEventProcessor.periodReference()` and goes through `UserCommandProcessor`/`AICommandProcessor` down to `CommandTransformer`. `resolveRelativePeriod` requires it, without a default value. The prompt carries both dates (cf. §8): the data read is anchored on the planned date, what the AI does stays in the present.

**Earlier data**: a scheduled automation saved before these settings is read as "without limit, the most recent only" (`LegacyCatchUp`) — a rule applied by migration 22→23 and by the import of a backup that does not carry the field.

### AUTOMATION specifics vs CHAT

**Completed flag**: the AI signals the end with `completed: true`. The app does not take it at its word: it goes back to `PREPARING_CONTINUATION` with `COMPLETION_CONFIRMATION_REQUIRED` and sends the AI back to work once. If the next answer says `completed: true` again without holding a command, the session ends with `endReason=COMPLETED`. Any answer holding a command lowers the flag: an AI that goes on working starts again from zero.

**Summary of the run**: the request for confirmation also asks for `summary`, what the AI did during the run and why, for the user. A confirmation without it is sent back once (`SUMMARY_REQUIRED`, `AIState.summaryReminded`), then the session closes anyway. At the end of a run for good (a reason outside `SessionEndReason.resumable`), `RunEnd` keeps the summary of the answer that confirmed (column `summary`), counts the writes made and refused or failed, and judges whether there is a problem (an end other than `COMPLETED`, no summary, a write missed); according to the routine's setting (`notify_end`: `always`, `on_problem`, `never`), the run waits for the user to read it (`seen_at` empty) and a notification opens it, or it is seen at once. Opening the run's screen reads it; it shows at the top the summary and the line of facts.

**Automatic continuation**: after the actions succeed, an AUTOMATION continues automatically (no keepControl required).

**Keeping alive**: while an AUTOMATION is active (phase neither `IDLE` nor `CLOSED`), `AutomationRun`, which follows the state from `AIOrchestrator.initialize`, holds the foreground service `AutomationRunService` and a partial wake lock: the scheduler's alarm keeps the app awake only for its tick (50 s at most). Its notification names the routine. It goes out 2 s after the end of the session, a routine that follows at once taking it up again: Android refuses to restart it from the background outside the alarm's moment. A routine started by the app's internal heartbeat once the app has been left does not have that right: the refusal is written to the log, the turn goes on without protection.

**Network**: a new try every 30 s, phase `WAITING_NETWORK_RETRY` (the watchdog does not count it). After 2 min of waiting in all (`AIStateMachine.AUTOMATION_NETWORK_WAIT_LIMIT`), the session closes as `NETWORK_ERROR`, so as not to keep the phone awake while the network is missing; the scheduler resumes it 5 min later at the earliest (`SessionSlotPolicy.NETWORK_RESUME_PAUSE`) and starts no other occurrence of the same automation meanwhile.

**Communication modules**: nobody answers during a run. An AUTOMATION's prompt does not present the modules and says so (`ai_chunk_automation_completion`); a module received is a FORMAT_ERROR (`ai_error_communication_module_in_automation`) that asks the AI to decide alone and to say in its summary what it assumed. A `validationRequest` is ignored there.

### Stopping an AUTOMATION
**UI buttons**:
- **STOP**: `stopActiveSession()` → `endReason=CANCELLED` (will not resume); the AI call in progress is cut, without waiting for its answer

**Automatic stop**:
- **completed=true**: the AI finishes its work → AWAITING_SESSION_CLOSURE (5s) → COMPLETED
- **Roundtrip limit**: maxAutonomousRoundtrips exceeded → AWAITING_SESSION_CLOSURE (5s) → LIMIT_REACHED
- **Provider/system error**: provider configuration error or system error → AWAITING_SESSION_CLOSURE (5s) → ERROR
- **Watchdog**: real inactivity without network waiting → AWAITING_SESSION_CLOSURE (5s) → TIMEOUT

**CHAT**: NEVER closes automatically, always goes back to IDLE (except network/provider/system errors). Roundtrip limit = Int.MAX_VALUE.

### Resuming sessions
Automatic detection of orphan sessions by AutomationScheduler:
- **endReason null**: crash/interruption → an AUTOMATION resumes transparently, except during actions; a CHAT cut in the middle of a turn is interrupted (below)
- **NETWORK_ERROR**: 2 min without network → resumed after a 5 min pause
- **SUSPENDED**: system eviction → resumed when the slot is free

**Transparency**: no system message, the AI does not know it resumes (goes on naturally).

**Actions cut by the app closing**: a session restored in `EXECUTING_ACTIONS` never replays its actions, which a resumption would do twice (a creation, an import). A message, sent to the AI too, says that those finished before took place and the others did not; a CHAT goes back to `IDLE` through `INTERRUPTED`, an AUTOMATION closes as `INTERRUPTED`, which the AutomationScheduler does not resume. A CHAT restored in the middle of a turn (`ROUND_PHASES`: AI call, reads, retries…) is interrupted ("AI answer interrupted") and waits for the user: a turn that brought the app down would bring it down again at every start (`settleRoundCutByAppClosing`); an AUTOMATION resumes. An AUTOMATION restored in `AWAITING_SESSION_CLOSURE` closes at once with the end it had reached, written from the countdown on (`COMPLETED` or `LIMIT_REACHED`), through `RunEnd` like any end; a run waits for the user only once closed (`is_active` false).

**Unexpected error in a turn**: an exception that leaves the state loop or one of its jobs (AI call, actions, retry) stops the turn without killing the app (`stopRoundOnError`): a CHAT goes back to rest, its session open, the error said in its messages outside the prompt; an AUTOMATION ends in error.

### SessionEndReason
The reason a session stopped (audit + resumption logic). An AUTOMATION resumes without a reason or for a reason of `SessionEndReason.resumable` (`SUSPENDED`, `NETWORK_ERROR`); any other reason closes its occurrence, which the AutomationScheduler counts as done and does not start again — the two queries of `AIDao` are the exact opposite of each other:
- **COMPLETED**: the AI has finished (completed=true)
- **LIMIT_REACHED**: the maxAutonomousRoundtrips limit reached
- **ERROR**: provider/system error
- **TIMEOUT**: inactivity watchdog without network waiting
- **CANCELLED**: user STOP (does not resume)
- **INTERRUPTED**: app closed during the actions (does not resume, an action would be done twice)
- **SUSPENDED**: system eviction (resumes later)
- **NETWORK_ERROR**: 2 min without network (resumes after its pause)
- **null**: crash/interruption (resumes)

## 7. Event loop and autonomous loops

### Event-driven architecture

**AIEventProcessor** processes events sequentially with side effects:
1. Listens to events through a channel
2. For each event: `AIStateMachine.transition()` → new state
3. `AIStateRepository.updateState()` → atomic memory + DB sync
4. Side effects according to the event (DB calls, network, coordinator)

An event that closes a step of the turn (answer received, answer read, continuation ready, retry) is taken only in that step's phase (`AIStateMachine.expectedPhases`): late or doubled, it is ignored with a warning in the log, instead of starting a call again.

**Autonomous loops**: handled by the `totalRoundtrips` counter in `AIState` and the `AILimitsConfig` limits. The state machine increments the counter automatically.

### Main logical flow

```
Event UserMessageSent:
  → transition EXECUTING_ENRICHMENTS
  → side effect: executeEnrichments()
  → emit EnrichmentsExecuted

Event EnrichmentsExecuted:
  → transition CALLING_AI
  → side effect: callAI()
  → emit AIResponseReceived

Event AIResponseReceived:
  → transition PARSING_AI_RESPONSE
  → side effect: parseAIResponse()
  → emit AIResponseParsed

Event AIResponseParsed:
  → decision tree (completed? validation? communication? data? actions?)
  → transition to the appropriate phase

Event DataQueriesExecuted:
  → transition CALLING_AI, emit a new round

Event SchemaRequired (writes on a tool whose entries' schema, as it stands, the AI has not received in the session; the creation of a tool of a type whose config schema it has not):
  → emitted by parseAIResponse, before validation: nothing is run nor submitted to the user
  → SCHEMA_REQUIRED message with the missing schemas, each after the first level of its type's entry, transition CALLING_AI
  → an unreadable schema (a tool or a tool type that does not exist) refuses the actions as a wrong command, in the service's words: ParseErrorOccurred

Event DataConfirmationRequested (CHAT, data beyond the threshold):
  → transition WAITING_DATA_CONFIRMATION

Event DataConfirmationReceived:
  → sent or refused, transition CALLING_AI, the call counter starts again at 1

Event ActionsExecuted:
  → if allSuccess + (keepControl OR AUTOMATION): transition CALLING_AI
  → otherwise (CHAT without keepControl): transition IDLE
  → if actions failed: emits ActionFailureOccurred

Event ActionFailureOccurred:
  → transition RETRYING_AFTER_ACTION_FAILURE (no limit, the AI must adapt its strategy)

Event ParseErrorOccurred:
  → transition RETRYING_AFTER_FORMAT_ERROR (no limit, the AI must correct itself)

Event NetworkErrorOccurred:
  → if CHAT: transition IDLE (the session stays active, the user must retry by hand)
  → if AUTOMATION: transition WAITING_NETWORK_RETRY (automatic retry with a delay)
```

### Validation and communication

**Validation**:
- `ValidationResolver` decides by levels, session and the AI's request ("Validation of the AI's actions")
- If required: `WaitingContext.Validation` created with `ValidationContext` + `cancelMessageId`
- Phase `WAITING_VALIDATION`
- Fallback SYSTEM message created BEFORE the suspension
- The UI observes `aiState.waitingContext` and shows it inline in the last AI message
- The user answers: `resumeWithValidation(approved)` → `ValidationReceived` event
- If refused: keeps the fallback message, transition COMPLETED
- If approved: deletes the fallback message, runs the actions

**Communication**:
- Phase `WAITING_COMMUNICATION_RESPONSE`
- `WaitingContext.Communication` created with the module + cancelMessageId
- Fallback AI message created BEFORE the suspension (excludeFromPrompt=true)
- The UI observes `aiState.waitingContext` and shows it inline in the last AI message
- The user answers: `resumeWithResponse(response)` → `CommunicationResponseReceived` event
- Stores the answer, deletes the fallback, sends it back to the AI

**Data size threshold**:
- The data retrieved for the AI, by the user's pointers or by its own queries, is measured in characters of the text it would receive, against `chat_max_data_chars` or `automation_max_data_chars` (`ai_limits`, set in the AI limits screen).
- CHAT beyond: the data message is stored outside the prompt (`DATA_AWAITING_CONFIRMATION`), phase `WAITING_DATA_CONFIRMATION`. The waiting context is read again from this message, so the waiting survives a restart. "Send" turns it into `DATA_ADDED` and puts it in the prompt; "Refuse" turns it into `DATA_REFUSED`, without data, with a summary that asks the AI to narrow down.
- Tools always sent beyond their own threshold: the same waiting and the same card, before the call to the AI, the choice holding for the session (`ALWAYS_SEND_AWAITING_CONFIRMATION`, then `ALWAYS_SEND_ACCEPTED` or `ALWAYS_SEND_REFUSED`, outside the prompt).
- AUTOMATION beyond: the data is not stored; a `DATA_REFUSED` message goes to the AI with the size, the threshold and the queries concerned, and stays visible in the run's history.
- Action results: they go without asking anything, the action being done; beyond the same threshold, `withinChars` cuts them where the room ends (summary and whole results first, in order), with the number of characters not sent.
- Reads of the MCP connector, against its own threshold, `max_data_chars` of the external access settings (200,000 by default, `McpAccess.maxDataChars`, `McpReads`): a read's size weighs on the outside AI's memory and subscription, never on a key of the app's paid by the call, hence a threshold separate from the chat's. The reads are whole or refused, never cut. A read of entries beyond it is refused, the refusal saying its size and how to ask for less; any other read, and a single entry (`limit` 1), goes in slices: each read tool of the connector takes `text_from`, where the slice starts, the app filling up to the threshold and cutting at the end of a paragraph, a line or a word; the slice says where it stands in the whole and the `text_from` of the next one.
- The data of the "always send" tools (level 2) is not measured: it is a configuration choice of the user.

## 8. Network and error handling

**NetworkUtils**: `isNetworkAvailable(context)` to check connectivity (core/utils).

**HTTP timeout** (providers): connection 15 s, read 10 min, write 2 min. Without streaming, nothing arrives before the end of the generation: the read delay covers a whole long answer.

**Call in progress**: `callAI` runs in its own job, outside the loop that processes the state changes. `SessionCompleted` (STOP included) and `AIRoundInterrupted` (Interrupt, CHAT) cancel it before the transition, which closes the HTTP connection (`OkHttpClient.awaitReply()`): nothing of the answer is kept. Interrupt goes through `INTERRUPTED`, the time to write the interruption message, then back to `IDLE`.

**Actions in progress**: `executeActions` also runs in its own job; Stop and Interrupt cancel it, the operation in progress is abandoned (a write in one transaction writes nothing) and a message, sent to the AI, says that the actions finished before took place and the others did not.

**Call gone without an answer**: cut after the request was sent (Stop, Interrupt) or lost (`LOST`), the call may have been billed and its usage is unknown. Its system message says so and carries `usage_unknown`. A call cut before sending cost nothing and is not marked; `RequestSent`, set in the coroutine's context around the call, tells the difference.

**Cost**: each AI message keeps its tokens, the config's model and the four prices per token of its call, as at the moment of the call (`CallPricing`); beyond the threshold of a LiteLLM tier (`…_above_<N>k_tokens`), they are the tier's. Nothing is stored on the session: `SessionCost.of()` adds up its messages on reading (`AIDao.getCallUsages`). A call is of unknown cost if it carries `usage_unknown`, or if a category in which it has tokens has no price (`null`, never 0); the total counts only the known and then shows as "≥". The prices come from the LiteLLM list, kept on the phone (`ModelPriceManager`): read again at the first need, downloaded again in the background after a day, and at once when a model is missing from a copy more than an hour old.

**Nature of the failure**: `AIResponse.failure` (`AIFailure`), set by the provider where the failure happens — never deduced from the message's text. `NETWORK` (nothing reached the provider) = retry; `LOST` (request gone whole, answer never received: connection cut or read delay exceeded) = stop as for a refusal, since the provider may have billed it — `OkHttpClient.awaitReply()` tells the difference, and marks the body "one-shot" so that OkHttp never sends again by itself a request already gone; `REFUSED` (429, 529, credit exhausted) and `CONFIG` (key, model, request) = `ProviderErrorOccurred`, the session stops. A provider therefore no longer words its error messages to fall on the right side of a string test.

**AUTOMATION**:
- Network check before the call → offline = phase `WAITING_NETWORK_RETRY`
- 30 s delay + endless retry, reserved for `NETWORK` failures: as long as nothing goes out, nothing is billed
- The watchdog does not time out during a network retry
- Retry until the network is available OR the user STOPs

**CHAT**:
- Network check before the call → offline = toast + `NETWORK_ERROR` event
- No automatic retry
- The session stays active

## 9. Enrichments

### Kinds of enrichments
- **POINTER** - reference data (zones or instances)
- **FILE** - a text file attached to the message (`FileEnrichment`: `{"file_id", "name", "line_count", "whole"}`), kept by the service `files` with its session (a TEXT file, `docs/DATA.md`, « Kept files »). It comes through « Join » (below), read once as UTF-8 (refused otherwise); the dialog (`FileEnrichmentDialog`) shows name, size, lines and beginning, and "Include whole in the context", checked. It goes as a FILE read, whole or its first 20 lines (FILE reads an entry's text too, under the mask that reads the entry), held to the data size threshold like every enrichment. Cancelled or removed from the composer before sending, the file is deleted; outside a session (an automation's starting message), nothing is joined.

### Images
**Join**, one button of the composer, only in a session, keeps with it what the user joins (`files.join`): a photo (`TakePicture`, into a cache file passed through the `FileProvider`, without the `CAMERA` permission, then `files.attach_image`), a video (`CaptureVideo`) or a sound (the phone's recorder, `RECORD_SOUND_ACTION`) taken now, a file of the phone of any kind (`OpenDocument`), or the files of a tool's field copied (`EntryFieldPicker`, an original image reduced on its way). The pointer keeps its own button. An image (`MessageSegment.Image(image_id)`, `{"type": "image", "image_id"}`) is a block apart, not an enrichment: it never becomes text. A sound or a video (`MessageSegment.Media(file_id)`, `{"type": "media", "file_id"}`) is a block apart too, which the AI reads as a line of text, its kind, id, name and duration (ISO 8601), the id by which it files it in an entry; a text goes through the FILE enrichment's dialog.
- **Kept reduced** (`Images`): turned according to the EXIF, 1,568 px on the long side, without metadata, in its format when it matters: a JPEG photo stays JPEG (quality 85), a PNG stays lossless PNG, another format becomes PNG if it has transparency, JPEG otherwise; an animated image keeps its first frame, which the composer says. One file per image, `files/attachments/<id>.jpg` or `.png`, written as `.part` then renamed, described by a row of the table `files` (`FileEntity`, owned by the session; service `files`: `attach_image`, `delete`), shared with the entries' files (`docs/DATA.md`, « Kept files »). The file is written before the row and deleted after it: an orphan can only be a file, which the startup sweep deletes while logging it (INFO for a `.part`, WARN for a message's file without a row). Deleting a session deletes its files.
- **To the model**: `PromptManager` prepares a message with images in pieces (`SessionMessage.promptParts`: `PromptPart.Text`, `PromptPart.Image`), each image in its place; the file is read when the request is built, under its format's type — an `image` block in base64 at Claude, `input_image` as `data:` at OpenAI, `image_url` as `data:` at the compatible servers. Sent again at each turn like the rest of the history.
- **The model that does not read images** (`ImageInput`): what its list said (`capabilities.image_input` at Anthropic, `input_modalities` at DeepSeek, `architecture.input_modalities` at OpenRouter), kept in the config under `reads_images` on saving, otherwise an `input` fact of provider-facts (`ProviderFacts.readsImages`). A model that does not read them or of which nothing is known is refused: the block says so, the composer refuses the sending, and `AIClient` refuses before any request a history with images, with more than 100 images, or whose file is missing.
- **On screen**: a thumbnail in the composer and in the message, the whole image on touch.

### A block's text
A block stores only its type and its config; its text is written at each reading of the message (`EnrichmentText`), which reads again in two reads all the zones and all the tools. A pointer therefore names its target as it is called today, and a deleted target reads "deleted"; a reading failure shows as such, never as a deletion.
- **On screen** (`display`, through `rememberDisplayText`): "Tool: Weight (Tracking), data, filtered period".
- **For the AI** (`prompt`, in `PromptManager` at each sending): the same text with the target's id, and for a mention of narrowed entries the query that reads them. A user message goes to the AI as text; an automation therefore reads the names again at each run.

### EnrichmentProcessor
```kotlin
class EnrichmentProcessor {
    fun generateSummary(type: EnrichmentType, config: String): String
    fun generateCommands(
        type: EnrichmentType,
        config: String,
        isRelative: Boolean,
        dayStartHour: Int,
        weekStartDay: String
    ): List<DataCommand>
}
```

**Periods**: CHAT (isRelative=false) → fixed dates, AUTOMATION (isRelative=true) → relative dates `{"relative": {"unit", "offset", "edge"}}` (`TimePoint`).

**Flow**: EnrichmentProcessor → DataCommand → CommandTransformer → CommandExecutor → SystemMessage.

### CommandTransformer
**Transformations**: APP_CONFIG → app_config.get and UPDATE_APP_CONFIG → app_config.set, for the categories of `AppSettings.OPEN_TO_AI` alone (`main_screen`), which the service holds according to the origin; SCHEMA → schemas.get, TOOL_CONFIG → tools.get, TOOL_DATA → tool_data.get (its `period` as filters on timestamp, the dates and durations of its `filters` put in stored form according to the field's type, `FilterValues`), ZONE_CONFIG → zones.get, ZONES → zones.list, TOOL_INSTANCES → tools.list, VARIABLES → variables.list or list_all, READING → readings.read of a variable (`at` in ISO or in relative dates, resolved on the reference), ICONS → icons.overview (without a parameter) or icons.search (`categories` and/or `query`), FILE → files.read (`start_line`, `lines`), IMAGES → files.show_images (`ids`, and the session the executor adds: the entries' images copied into the conversation as if the user had attached them, refused for a model that does not read images; `PromptManager` sends them after the message that names them, each preceded by `[image <id>]`, like attached images), IMPORT_PLAN → imports.detect of an attached file. The action IMPORT_DATA goes to imports.apply, under the tool's data validation, its card naming the file and the tool. The action EXPORT_DATA goes to exports.csv (its `period` and `filters` as TOOL_DATA's, put in stored form, its relative dates resolved when it runs, an action having no reference), without validation: nothing of the app changes and nothing is written over; a routine's mask reads the tool. The AI gets the file's name and its number of entries, never its content; the chat's card shares the file. The actions CREATE_VARIABLE, UPDATE_VARIABLE, DELETE_VARIABLE go to the service `variables`, under their own validation setting ("Changes to variables", `validate_variable_changes`); the definition is written with the names, and the service checks it as the screen does.

### User vs AI commands
**User**: source EnrichmentBlocks, types POINTER/FILE only, aim contextual data, never actions.
**AI**: source AIMessage.dataCommands + actionCommands, types queries + real actions, aim asking for data + running actions.

## 10. Prompt architecture

**The AI's language**: what the app writes for the AI — the prompt, the schemas, an entry's first level, a tool type's operations — is in English whatever the app's language (`AiLanguage`); the texts stay translated in the string files. A schema's labels, which the user sees on screen, come in the app's language beside the English ("Shortcuts (Raccourcis)"), so that the AI names a setting with the screen's words; its descriptions stay in English alone. The prompt's chunk `vocabulary`, filled only when the app speaks another language, gives the app's words as its screens show them, each beside the English the prompt uses: the tool types, the field types, the app's notions and its settings screens, read from the texts the app shows (`PromptChunks.buildVocabulary`).

**What a description says**: what the thing is, at its own level, never what not to do; a mistake of the AI is corrected by making right the description of what it misread. Every value a settings declaration puts in a schema carries one: a setting, a group, a list and one of its elements, each option of a variant (written by the notation on its line, `if end = timed: …`); a section, the screen's alone, has none (`SettingDescriptionsTest` names each value without). The parts every entry has take their meaning in the type at the entry's first level (`EntryDescription`), not in the entries schema. What the AI cannot draw from the descriptions, a type's ergonomic tips (a tracking of one thing gets a shortcut named after it), goes in its `ai_detail`, the only text of a type read before the first turn.

### 2 levels of context
**Level 1: DOC** - generated by PromptChunks with configurable degrees of importance. Includes the AI's role, the API documentation, **dynamic AI limits** according to the SessionType, the legend of the schemas' notation, the definition of a field and the schemas of the AI's answer and of a zone, written in that notation (`SchemaNotation`, see `docs/DATA.md`). For AUTOMATION: documentation of the required `completed: true` flag + automatic continuation after the actions succeed.
**Level 2: USER DATA** - data of the tool instances with `always_send: true`, read with their config (`include_config`) to find the marked tools; their data schemas enter the session once like any schema received (outside a session, `app_context` gives them along). Beyond `always_send_max_chars`, a CHAT asks once per session, an AUTOMATION and an outside AI receive their list.

**APP_STATE**: the home screen's groups, the zones, the tool instances with each one's extra fields (name, label, type) and the variables (zones.list, tools.list_all, variables.list_all), sent as a matter of course with the first message.
**Enrichments**: stored as a SessionMessage sender=SYSTEM, included in the history.
**RichComposer UI**: the message is a sequence of typed blocks, one per segment (text, pointer, text file, image, sound or video), in the order they go. A new block is inserted after the active block and becomes active; the blocks are reordered by their handle (`UI.ReorderableColumn`); the list is never empty, and an empty text does not go. The rules live in `ComposerBlocks`, tested by `ComposerBlocksTest`.

### The L1 is a contract, checked on what changes

`ai_prompt_chunks.xml` is the only description of the command API the AI receives. Nothing compiles or tests it: a divergence from the code produces no error, just a parameter ignored silently and a wrong result on the AI's side.

Rule: **each example of a request present in the L1 must be executable as is and produce what it announces.**

The net has two meshes, and they do not catch the same thing. `scripts/check_prompt_examples.py`, run at each `./run test`, confronts the prompt's JSON examples with the code, and `FieldTypeSchemasTest` and `CommunicationModulesTest` validate its field definitions and its modules against the generated schemas (the prompt is an input of the test task, which runs it again): they say that the prompt promises nothing the code does not hold. What the AI does with the prompt shows in a real CHAT session. After a change to the L1 or to the pipeline that serves it (`AICommandProcessor`, `CommandTransformer`, `CommandExecutor`), an ad hoc test covers that change and nothing else: a few steps written for the occasion, in a zone created for it, with what to read there. There is no fixed test of the whole prompt, which would cost an hour and calls to check again what has not moved.

### PromptManager.buildPromptData()
```kotlin
suspend fun buildPromptData(sessionId: String): PromptData {
    // L1-L2 generated again at each call (never cached in the DB)
    val level1Content = PromptChunks.buildLevel1StaticDoc(context, sessionType, config)
    // L2: USER DATA (always_send tools)

    // Filtering the messages excluded from the prompt:
    // - NETWORK_ERROR and SESSION_TIMEOUT (audit only)
    // - excludeFromPrompt=true (UI-only messages, like a success postText)
    val sessionMessages = loadMessages(sessionId)
        .filter { message ->
            val type = message.systemMessage?.type
            val isSystemError = type == SystemMessageType.NETWORK_ERROR || type == SystemMessageType.SESSION_TIMEOUT
            !isSystemError && !message.excludeFromPrompt
        }

    return PromptData(level1Content, level2Content, sessionMessages)
}
```

**Assembly**: the final prompt is assembled by the provider (messages merged, cache_control applied).

### Storage policy
**Stored**: SessionMessage (USER/AI/SYSTEM), RichMessage, SystemMessage with the results of queries/actions (formattedData + commandResults), aiMessageJson.
**Not stored (generated again)**: the L1-L2 levels, DataCommand/ExecutableCommand (temporary), the final assembled prompt.

### Dual resolution mode
**CHAT** (isRelative=false): absolute periods (Period with fixed timestamps).
**AUTOMATION** (isRelative=true): relative dates (`TimePoint`), resolved at each run by `TimeResolver` on the planned time.

## 11. Provider abstraction

Two levels: a **kind** is the code that talks to an API (`AIProviderKind`: Claude, OpenAI, DeepSeek, Other server, listed by `AIProviderKinds`); a **provider** is what the user creates of a kind, with a unique name and its config (table `ai_provider_configs`: `id`, `kind`, `name`, `config_json`, `is_default`). One creates as many as one wants, duplicates them; the list shows only them.

### AIProviderKind signature
```kotlin
interface AIProviderKind {
    val kind: String                                   // "claude", "openai", "deepseek", "compatible"
    fun kindName(context: Context): String             // the name of a new provider of this kind
    fun kindDescription(context: Context): String      // one line, at the choice of the kind
    fun getConfigSettings(context: Context): List<SettingNode>
    fun getConfigHelp(context: Context): String
    fun modelListingSettings(): List<String>          // by default ["api_key"]
    suspend fun listModels(config: JSONObject): ProviderModels
    fun configError(config: JSONObject, context: Context): String?  // by default null
    fun ownPrices(config: JSONObject, context: Context): ManualPrices?  // by default null: the LiteLLM list
    suspend fun query(promptData: PromptData, config: String): AIResponse
}
```

**Config**: declared with the fields (`getConfigSettings`); the API key is a `secret` setting, entered masked. The schema (`AIProviderSettings.schema`), against which `AIProviderConfigService` checks every write, and the screen (`AIProviderConfigScreen`) are generated from it; the model is chosen among those `listModels` lists, once the settings `modelListingSettings` names are filled (the key, or the address for a compatible server); changing one of them erases the list. `configError` holds what the schema cannot say (the form of an address): the service refuses the config on saving. The code reads a config through `AIProviderSettings.read`.

**Provider responsibilities**: parse the config, transform promptData (API-specific structure), merge messages (alternation constraints if applicable), call the HTTP API, parse the answer (content, tokens, errors).

### Extensions pattern
**Example ClaudeExtensions.kt**:
```kotlin
internal fun PromptData.toClaudeJson(config: JSONObject, datetimeText: String): JsonObject
internal fun JsonElement.toClaudeAIResponse(): AIResponse
```

**Advantages**: testable separately, concise, complex logic isolated. The dated message (clock and strings) is built by the caller and passed as text, which keeps these functions pure: `ClaudeExtensionsTest` and `OpenAIExtensionsTest` cover them without network.

### Merging messages (general pattern)
The provider merges consecutive USER/SYSTEM messages to respect the API's constraints.

**Example**: DB (1.USER "Question" → 2.SYSTEM enrichments → 3.AI answer → 4.SYSTEM queries → 5.USER "Other") transformed into API (1.USER ["Question", "enrichments"] → 2.ASSISTANT answer → 3.USER ["queries", "Other"]). An empty message is left out, and an empty AI message does not split the user turn around it.

### ClaudeProvider - cache control (specific)
**4 breakpoints**, the API's maximum: one per system block (L1, L2, L3), and the last block of the history's last message. The dated message comes after, outside a breakpoint, since it changes at each call.
**Automatic prefix checking**: earlier messages (without cache_control) cached automatically (~20 blocks before the last breakpoint).

**Structure**: system array with L1/L2/L3 + cache_control, messages array with USER/SYSTEM merged. The last message is forced into array format to support cache_control on its last block.

### DeepSeek - Anthropic-compatible endpoint
DeepSeek goes through `ClaudeProviderCore` on its `/anthropic` endpoint (`MessagesApi.DEEPSEEK`: address, list of models, effort levels). Particularities:
- Effort (`output_config.effort`) required in the config, among `low`/`high`/`max`: reasoning is active by default at DeepSeek, the other values being only aliases.
- Model substitution: a `claude-*` name is served by `deepseek-flash` without an error, and the answer's `model` field reveals it. Any answer whose `model` differs from the model asked for is rejected (definitive error). Measured on 2026-09-19; an unknown ID receives an explicit error.
- No `[1m]` suffix on the model ID. The API accepts it and answers with the model without the suffix (measured on 2026-09-19); its effect on the context's size is not measured. Claude Code's docs describe it as a setting of Claude Code's own context window.
- `cache_control` ignored: DeepSeek's context cache is automatic. It takes up from one call to the next the whole identical beginning, conversation included, the final dated message and the removed reasoning changing nothing (measured on 2026-09-25); what changes before the history, like a rebuilt L3, stops it there.
- The answer starts with a `thinking` block: the parsing reads the `text` block.

### Other server - Chat Completions at a settable address
`OpenAICompatibleProviderCore` serves any server that speaks `/chat/completions`: Ollama, llama.cpp, vLLM, LM Studio, OpenRouter. The OpenAI provider stays on `/v1/responses`, which these servers do not all serve. Kind `compatible`, named "Other server" on screen. Why it exists: « Free models and the bench », below.
- Base address (`base_url`, the one before `/chat/completions`, often ending in `/v1`) **in https only**: the prompt holds the user's data. Refused on saving (`configError`), on listing the models and on calling. No cleartext traffic exception is declared in the app.
- Optional key, sent as `Bearer` when it exists. The models are listed through `GET /models` as soon as the address is filled.
- Output forcing (`output_forcing`), a required choice: `none`, `json` (`response_format: json_object`), `schema` (`json_schema` holding the schema of the AI's answer as the model reads it, `SchemaModelView`). A server that refuses the level returns an error, shown as is.
- L1, L2 and L3 go as **a single** system message: some chat templates refuse a second system message.
- An answer cut by the limit (`finish_reason: length`) is refused. `prompt_tokens` and `completion_tokens` are required, `cached_tokens` optional.
- Cost: a "Prices" choice (`prices`), the LiteLLM list as elsewhere (`list`, a model it does not know has an unknown cost) or set by hand (`manual`): input, cached input, output, in dollars per million tokens (`price_input`, `price_cached_input`, `price_output`), read by `ownPrices`. An empty price is unknown: a call that has tokens of its kind has an unknown cost (`SessionCost`), 0 says free. No cache writing in Chat Completions, no tiers.

### Free models and the bench
The aim: the app's AI running on a free model, on a computer or a server of one's own, for privacy and to depend on no proprietary provider; a home server goes through "Other server", served in https by `tailscale serve` or a reverse proxy. The bench measures what a model does with the app's real prompt, on every use, configuration included.

- **Played by the app itself** (`./run bench`, `scripts/bench.py`): an instrumentation test that knows no scenario (`BenchScenario`) empties the base, sets the provider from its arguments, installs the demo, sends the message or starts the routine, waits for the session at rest, and copies the base; `scripts/bench_checks.py` judges each scenario on the final state of the test zone ("an entry of 300 in Calories, dated today"), a different but valid path succeeding. The scenarios (`scripts/bench_scenarios.py`, on the demo in French): about twenty, in four families after the uses — entering, reading, automation, configuration — each from simple to tricky.
- **Campaign 1** (2026-10-04, on the emulator through OpenRouter, one run per cell, 19 scenarios; read again by a judge on 2026-10-17), successes without forcing / at the exact schema:

| Model (class of machine) | Without forcing | At the schema |
|---|---|---|
| `ministral-3b-2512` (phone) | 2/19 | 8/19 |
| `qwen3.6-35b-a3b` (laptop, 32 GB) | 8/19 | 9/19 |
| `gemma-4-31b-it` (24 GB GPU) | 18/19 | 16/19 |
| `gpt-oss-120b` (server, 64-128 GB) | 14/19 | 14/19 |
| `deepseek-v4-flash` (hosted only) | 19/19 | 4/19 |

  qwen and ministral had answers cut by the output limit (16,000 tokens), which the app does not keep. Not measured: the variance (one run per cell), the speed on a machine of one's own.
- **Decided on 2026-10-17**: a single prompt for every model, no levels; the bench stays in `scripts/`, without a campaign planned.

### Configuration
Providers managed by `AIProviderConfigService` (`ai_provider_config.kinds`, `list`, `get`, `create`, `update`, `delete`, `set_default`, `get_default`), kinds found by `AIProviderKinds`, `AIClient` goes through the coordinator (no direct DB access).
- **Name** required, prefilled with the kind's, unique ignoring case (unique index on `name`); a duplicate is refused saying so, never renamed.
- **The default**: the first provider created becomes it; deleting the default while others remain asks which becomes it (`new_default_id`), the last one deleted leaves no default. The deletion is refused while a routine names it (`InUse.provider`), the refusal listing them.
- **Who calls what** (`ProviderVerifier`, before building the prompt): a session calls its own provider (`ai_sessions.provider_id`). A conversation has none at its creation; its first call takes the default and keeps it (`ai_sessions.set_provider`), and its parameters change it, its own reasoning going with it. A routine's run takes the routine's provider at its start. Without a provider, a conversation is refused saying to add one; a provider deleted since refuses the conversation, which can be read again (`ai_session_provider_gone`). No fallback to another.
- **A routine** can have no provider (`automations.provider_id` null): it is then neither enabled nor started, a refusal to back it, and its tile says so. `CREATE_AUTOMATION` requires the key `provider_id`, `null` for none; the form prefills the default; the demo sets its routines with the default, without a provider if there is none.
- **The AI** reads the providers in the app's state (L3, `ai_providers`: `id`, `name`, `kind`, `is_default`), never their config.
- **Migration 80** (`ProvidersAtV80`, shared with a backup's import): the eight fixed identifiers of before (`claude_standard`, `compatible_economic`…) that were configured become providers of their kind, under their id and their name, the active one becoming the default; a variant never configured disappears, and a routine that named it is left without a provider, switched off.

**A conversation's reasoning**: a conversation can ask for its own reasoning (`SessionReasoning`: reasoning off or not, effort), set in its parameters (`ai_sessions.update_reasoning`, columns `reasoning_*` of `ai_sessions`, migration 74) among what the model of its provider offers (`AIClient.modelReasoning`, the provider's list). Without it, the provider's config applies. It replaces the two settings of the config on sending (`SessionReasoning.applyTo`), for the rest of the conversation. Before each call, `AIClient.reasoningRefusal` submits it to `AIProviderKind.configError`: a model that no longer takes it (provider or model changed since) refuses the call, with a message, never a silent replacement. The routines always follow their provider's config.

## 12. Communication modules

### Structure
A module is a list of fields declared by the AI: `{"fields": [...]}`, each field a field definition as in `extra_fields`, which the AI names itself (the answer comes back under that name) and which says whether it is `required`. Without a field, the module asks for a confirmation; the question is the `pre_text`.

**Validation**: `CommunicationModules` declares the module with the fields (`declarationNodes`, generated schema `communication_module`); `check` confronts it with that schema then with `FieldConfigValidator` (key in snake_case, distinct keys, the type's settings). A module refused becomes a FORMAT_ERROR that says why; nothing is ignored silently. `CommunicationModule` keeps the declaration as written, and its `fields` are read only once the module is checked.

**Answer**: the card draws the fields with `SettingsForm`; Confirm is possible only when the answer holds to the schema generated from the fields (`checkAnswer`). The answer goes to the AI as an object of values, dates and durations in ISO 8601 (`answerForModel`), a confirmation as `confirmed`; the thread shows it again through the fields' display components (`CommunicationAnswer`).

**Two other ways out**, under the form: "Add a precision" opens a free text, sent after the answer as a system message (`ai_module_note_prefix`); "Answer with a message" unlocks the composer, the form staying answerable. It is the sending of the message that replaces the module: `sendMessage` first writes the system message `ai_module_replaced_by_message`, and `UserMessageSent` leaves the waiting as from IDLE.

### User answer flow
```kotlin
// UI pattern
val aiState by AIOrchestrator.currentState.collectAsState()

// Inline display in ChatMessageBubble (last AI message only)
if (isLastAIMessage && aiState.waitingContext is WaitingContext.Communication) {
    val ctx = aiState.waitingContext as WaitingContext.Communication
    CommunicationModuleCard(
        module = ctx.communicationModule,
        onResponse = { response, note ->
            AIOrchestrator.resumeWithResponse(response, note)
        },
        onCancel = { AIOrchestrator.cancelCommunication() },
        freeReply = freeReply,
        onFreeReply = { freeReply = true }
    )
}
```

**Flow**:
1. The AI generates an AIMessage with a CommunicationModule
2. The event processor creates `WaitingContext.Communication`
3. State transition → `WAITING_COMMUNICATION_RESPONSE`
4. The UI observes `aiState.waitingContext` and shows it inline
5. The user answers → `resumeWithResponse(response)`
6. Event `CommunicationResponseReceived` → store the answer
7. Transition `CALLING_AI` → send back to the AI

### Validation of the AI's actions
**Levels**: each keeps what it holds directly, nothing by default; the validation holds where someone is there to answer (the app's conversation, an outside AI through the connector, whose protected write waits for a notification with Accept and Refuse, 90 s at most under the relay's 120 s cut, `McpApprovals`), the access mask where nobody is (a routine, a connector's client). The app (`validation_config.validate_app`): the zones, the home screen's groups. A zone (`zones.validate`): its tools, their configs included, its tool groups, its variables. A tool (`validate_data` of its config): its entries. Apart from the levels, a config change that removes, deletes or reads differently recorded values asks for agreement as long as "Protect past data" is checked (`validation_config.validate_past_data`, on by default, `docs/DATA.md`, « Config change »), its counts read through a dry run (`tools.update` with `dry_run`). The session extends a level to all its objects (`SessionValidation`: three checkboxes, which only add); the AI can ask by itself (`validation_request` at `true`; `false` is worth its absence). An action is validated as soon as one of them asks for it; what the resolver cannot read to decide is asked too, with the reason. An automation asks nothing: it is bounded by its access mask (`AccessMask`, `automations.access_json`, empty by default: everything is reached), a list of zones and tools (the Thing brick) each in read, use or full; a zone in read gives its tools in read, in use or full gives them in full. On a zone, read reaches the zone, its tools, their data and its variables, use adds creating, changing and deleting its tools and variables, full its own config; on a tool, use adds writing its data, full its config. A list given, everything outside it is refused, creating a zone and changing the home screen's groups included. `AIEventProcessor` runs its AI's queries and actions under an `AccessScope`, which the coordinator reads for every command the app does not make itself: `AccessRules` says what each operation reaches and refuses what goes beyond, the app and what they cannot place included, the lists, schemas, icons and the date staying free. The prompt's level 2 tells the AI the mask. The "always send" tools reach it even outside the mask, in read: a context chosen for every AI, which the mask's card and the prompt say. The protections change only through the user or the app itself (`Protections`, according to the origin): the AI, the connector or the scheduler that changes them is refused, and a tool config they write without `validate_data` keeps the tool's.

**Flow**: ValidationResolver analyses the actions → generates a ValidationContext (verbalised actions + reasons + config warnings) → `WaitingContext.Validation` created → the UI shows it inline → the user validates/refuses → `resumeWithValidation(validated)`.

**Fallback messages**: COMMUNICATION_CANCELLED / VALIDATION_CANCELLED created BEFORE the suspension (a trace if the app closes or navigates). Deleted if the user actually answers/validates.

**Persistence**: none. The context is deduced from the AI's last message and the validation rules; a session restored in a waiting phase finds it again because `AIEventProcessor`, collecting the restored state, rebuilds it as on entering that phase.

## 13. SystemMessages

### Generation and storage
**Generated by**: CommandExecutor after each series of commands. **Stored as**: SessionMessage sender=SYSTEM. **Single point**: CommandExecutor alone responsible (User and AI).

### Types and placement
**Actions in the thread**: under each successful write command of an ACTIONS_EXECUTED message, the thread shows the entries written field by field, like the validation request (`ProposedEntries.read`, `ProposedEntryItem` cards). Nothing is stored for that: they are read in the commands of the AI's preceding message, one action result per command, in order, with the tool's fields as it is today.
**User enrichments**: generated after running the enrichments, stored after the USER message, type DATA_ADDED with formattedData.
**AI queries**: generated after running the AI's dataCommands, stored after the AI's answer, type DATA_ADDED with formattedData.
**AI actions**: generated after running the AI's actionCommands, stored after the AI's answer, type ACTIONS_EXECUTED without formattedData.
**Limits**: generated when a limit is reached, type LIMIT_REACHED with summary, no automatic sending back (waits for a user message).
**Format errors**: generated when the AI's answer cannot be read or breaks a rule of the format (communication module included), type FORMAT_ERROR with the errors' details, sent back automatically to the AI for correction.
**Text around the JSON**: an answer that holds exactly one JSON object surrounded by text or by a ```json block is read through that object (`ResponseEnvelope`); no object or several stay a FORMAT_ERROR. The AI's message keeps only the JSON, which its history replays; a TEXT_OUTSIDE_JSON message quotes the text set aside to the user, and `PromptManager` passes on to the AI only the mention that it was ignored, never the text.
**System errors**: generated for network errors (NETWORK_ERROR), provider errors (PROVIDER_ERROR) and the watchdog timeout (SESSION_TIMEOUT). **ALWAYS visible in the UI** for the user's transparency. Filtered from the AI prompt (excludeFromPrompt=true, audit only).

### Format in prompts
The provider decides the format of inclusion. Usually merged with consecutive USER messages (formattedData added as a content block).

**Filtering**: NETWORK_ERROR, PROVIDER_ERROR, SESSION_TIMEOUT and messages with `excludeFromPrompt=true` excluded from the AI's context (visible in the UI + audit, not in the prompt).

---

*The event-driven V2 AI architecture guarantees state consistency, automatic recovery, controlled autonomy and extensibility.*
