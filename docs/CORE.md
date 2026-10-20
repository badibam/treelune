# Core architecture

Technical guide to the central system architecture.

## Main flow: UI → CommandDispatcher → Service

### CommandDispatcher pattern
**Principle**: a unified resource.operation architecture for the UI, the AI, the Scheduler and the System.

**Standard format**:
- zones.create - Create a zone
- tools.list - List a zone's tools
- tool_data.get - Retrieve a tool's data
- app_config.get - Application configuration

### ServiceRegistry
**Core services**: zones → ZoneService, tools → ToolInstanceService, tool_data → ToolDataService, app_config → AppConfigService, backup → BackupService, ai_provider_config → AIProviderConfigService, notifications → NotificationService

**Tool services** (dynamic discovery): tracking → ToolTypeManager.getServiceForToolType()

### Dispatching commands
1. **Parse**: "zones.create" → resource="zones", operation="create"
2. **Resolve**: ServiceRegistry.getService("zones") → ZoneService
3. **Execute**: service.execute("create", params, token)

### Naming of parameters per service
The keys that cross the dispatcher are in snake_case, whatever the resource: `tool_instance_id`, `zone_id`, `schema_id`. camelCase stays internal to Kotlin (variable names, entity properties) and does not cross the boundary.

A compound word become a term of the domain is written joined — `tooltype`, like `timestamp` — and that name holds from the key down to the column.

The rule holds for the whole project and lives in `docs/reference.md`, kept by `./scripts/check_key_case.py`. The JSON written before it is converted by migration 26→27, which reads its table of correspondences in `KeyCaseRenames` — the same as the backup import.

### Complete CRUD operations of ToolDataService
**Operations available**: create, update, delete, get (with pagination), get_single (by ID), stats, delete_all, batch_create, batch_update, batch_delete

**Batch operations**: all the AI's data operations use the batch operations by default for efficiency. The batches reuse the logic of the single operations for consistency. A batch reads once the entries of each tool it writes and keeps them up to date in memory (unique names, rule between entries), then notifies the screens once per tool at its end; its result gives each refusal by its place in the batch (`refusals`: `index`, `error`).

### Token management
Each operation automatically receives a unique CancellationToken, created by the CommandDispatcher, stored in a ConcurrentHashMap and cleaned up automatically.

### DataChangeNotifier
**Principle**: reactive notification of data changes for the UI to reload automatically.

**Events**: ZonesChanged, ToolsChanged(zoneId), ToolDataChanged(toolInstanceId, zoneId), AppConfigChanged

**Use by services**: the services call the notify* methods automatically after changes (create/update/delete)

**Use by the UI**: a LaunchedEffect collects DataChangeNotifier.changes to reload the data when relevant

## Centralised scheduling

### CoreScheduler
**Principle**: a single entry point for AI + Tools scheduling through the discovery pattern.

**Heartbeat**: a 1 min coroutine (app open) + an exact 10 min alarm (`SchedulerAlarm`, app closed and phone asleep included), set again at each ring, at the phone's restart, at the app's update and after each tick; earlier when a tool's scheduler announces its next action (`ToolScheduler.nextAction`, an event's reminder), within Android's limit in deep sleep (one ring every ~9 min). A tool config created or changed runs its type's scheduler at once, as the scheduler (`CoreScheduler.runTool`, called by `ToolInstanceService`): a new Agenda series receives its occurrences without waiting for the tick, wherever it comes from. The process sets the scheduler's context (`CoreScheduler.attach`, in `TreeluneApplication`) and the tick initialises the AI orchestrator if it is not: the alarm can start the process without a screen.
**Triggers**: periodic + event-driven (CRUD of automations, CRUD of tool instances, end of a session)

**Discovery**: `ToolTypeManager.getAllToolTypes().forEach { toolType.getScheduler()?.checkScheduled() }`

### ToolScheduler interface
```kotlin
interface ToolScheduler {
    suspend fun checkScheduled(context: Context)
}
```
Part of `ToolTypeContract.getScheduler(): ToolScheduler?` (default null)

**Tool extension**: return a scheduler in the ToolTypeContract, no change to the core needed.

## Discovery pattern

An architecture without hardcoded imports in the Core, with a centralised ServiceRegistry.

### ServiceRegistry
Each core service is tied there to its resource by its constructor, in a single place; an unknown resource passes to ToolTypeManager for the tools' services.

### ToolTypeManager
A unified API for dynamic discovery: getServiceForToolType(), getDaoForToolType(), getToolTypeName(), getAllToolTypes()

### Automatic extension
**New tool type**:
1. The service implements ExecutableService
2. The ToolType implements ToolTypeContract
3. Add it to the ToolTypeScanner
4. **Use**: coordinator.processUserAction("{toolType}.operation", params)

No change to the Core needed.

## Data management

**See DATA.md** for hierarchical navigation, validation and data patterns.

## Development rules

### Service implementation
- Inherit ExecutableService
- Validation through SchemaValidator.validate(toolType, data, context, useDataSchema)
- Explicit error logs
- Handling of token cancellation

### Discovery pattern
- Never hardcoded imports in the Core
- Services discovered through ToolTypeManager
- Automatic extension by adding to the Scanner

### Data consistency
- No event log: the state in the database is the only source (`docs/DATA.md`). The undo history is designed in `docs/design/undo-history.md`
- **Centralised validation**: SchemaValidator for config/data
- JSON schemas for automatic validation
- **Recursive conversion**: JsonUtils.toJSONObject() converts Kotlin Map/List → JSONObject/JSONArray recursively (the Coordinator applies it before passing to the services)

### CommandDispatcher extensions pattern
coordinator.executeWithLoading() with operation, params, onLoading, onError and result.mapData().
Status checked with result.status == CommandStatus.SUCCESS.

### Commands pattern
coordinator.processUserAction(), processAICommand(), processScheduledTask() with resource.operation

**Order**: the operations given to a same instance pass one at a time, in order of arrival (a `Mutex`); each caller runs its own and receives its own result. An operation that calls another does so through another instance, otherwise it would wait for its own end.

**Long operation**: a service declares those of its operations that write or read again a whole (`longOperations`: the demo, `imports.apply`, the export, the restore and the automatic copy of a backup). The coordinator passes them through the app's single place (`LongOperation`): only one runs at a time, a second one asked meanwhile is refused naming the one running, never queued; the ordinary operations go on. It runs in the app's scope, not in its caller's: leaving the screen that started it does not stop it, and its outcome then shows once as a toast. Its service says its step (`LongOperation.at`), which a card floating at the bottom left of each screen shows (`LongOperationBar`), moving nothing, beside the conversation button. It does not survive the app stopping.

**Origin of a call** (`Source`: USER, AI, EXTERNAL, SCHEDULER, SYSTEM): the coordinator runs it with its origin in the coroutine's context (`Origin`), which every call made from the operation keeps — an import started by the AI writes its rows as the AI. `processAICommand` sets AI, `processExternalCommand` EXTERNAL (an outside AI, through the MCP server), `processScheduledTask` SCHEDULER, `process(source, …)` the one it is given (`CommandExecutor`: AI for the AI's commands, USER for the pointers, SYSTEM for the prompt); `processUserAction` keeps that of the operation in progress, USER outside any operation (a screen). The tick of `CoreScheduler` runs as SCHEDULER. A service reads the origin through `currentOrigin()`, for what only a person may do; outside an operation there is none, and asking for it is an error.

### Coordinator usage pattern - reference

**Imports required**:
```kotlin
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.commands.CommandStatus
import app.treelune.core.strings.Strings
```

**Examples**:
```kotlin
// Basic pattern with error handling (CommandResult)
val result = coordinator.processUserAction("resource.operation", mapOf(
    "param1" to value1,
    "param2" to value2
))

if (result.status == CommandStatus.SUCCESS) {
    // Success handling
    val data = result.data?.get("field") as? Type
} else {
    // Error handling
    LogManager.service("Operation failed: ${result.error}", "ERROR")
    errorMessage = result.error ?: s.shared("error_operation_failed")
}

// UI pattern with loading/error states
coordinator.executeWithLoading(
    operation = "resource.operation",
    params = mapOf("id" to itemId),
    onLoading = { isLoading = it },
    onError = { error -> errorMessage = error }
)?.let { result ->
    // Result handling
    data = result.mapSingleData<Type>("field")
}
```

### Error handling pattern with strings

**Rules**:
- NEVER hardcoded messages - always use s.shared() or s.tool()
- Use the service's `result.error` (already translated) first
- Supply a translated fallback through s.shared() when result.error is null
- For exceptions, prefix with a translated string: `"${s.shared("error_key")}: ${e.message}"`

**Example**:
```kotlin
try {
    val result = coordinator.processUserAction("backup.export", mapOf("uri" to uri.toString()))

    if (result.status == CommandStatus.SUCCESS) {
        Toast.makeText(context, s.shared("backup_export_success"), Toast.LENGTH_SHORT).show()
    } else {
        // Service error (result.error first, already translated)
        errorMessage = result.error ?: s.shared("backup_export_failed")
    }
} catch (e: Exception) {
    // Exception handling with a translated prefix
    errorMessage = "${s.shared("backup_export_failed")}: ${e.message}"
}
```

## Data validation

**See DATA.md** for SchemaValidator V3, centralised validation and data patterns.

## Modular strings system

A unified architecture for internationalisation with the discovery pattern and automatic generation.

### Structure
- **Sources**: core/strings/sources/shared.xml + tools/*/strings.xml
- **Generation**: a Gradle script → res/values/strings_generated.xml
- **API**: Strings.for(context) for shared, Strings.for(tool = "tracking", context) for tools

### Use
Import Strings, then s.shared("action_save") or s.tool("display_name"), with the shared fallback always reachable.

### Shared categories
- **action_***: save, create, cancel, delete, validate, confirm, etc.
- **label_***: name_zone, description, icon, enabled, disabled, etc.
- **message_***: loading_tools, validation_error, no_zones_created, etc.
- **period_***: today, yesterday, this_week, last_month, etc.
- **content_***: app_name, tool_description, unnamed, etc.
- **month_***: january, february, march, etc.

### Automatic escaping
The Gradle script handles apostrophes, quotes and the conversion of placeholders (%s → %1$s).

### Android strings
**Format required**: numbered placeholders %1$s, %2$s and not %s
## AppConfigManager - cached global configuration

A cache singleton for global application parameters used often.

**Parameters**: `dayStartHour`, `weekStartDay`

**API**:
```kotlin
AppConfigManager.initialize(context) // MainActivity.onCreate
val dayStartHour = AppConfigManager.getDayStartHour()
val weekStartDay = AppConfigManager.getWeekStartDay()
AppConfigManager.refresh(context) // After a config change
```

**Rules**:
- Initialize required at startup
- Getters throw `IllegalStateException` if not initialised
- No hardcoded fallback (explicit failure)
- Settings in the database (`app_settings_categories`): an absent category receives its default values, drawn from `AppSettingsDefaults` alone; an unreadable category, or one missing a required key, raises an error, never replaced. The format overrides (time zone, language, date pattern, 24 h) stay optional: absent, they follow the phone.
- Extension: getter in AppConfigService + volatile cache + initialize + public getter

## Result types

### OperationResult vs CommandResult

| Type | Package | Key fields | Use |
|------|---------|-------------|-------|
| OperationResult | services | `.success: Boolean`<br>`.data: Map?`<br>`.error: String?` | ExecutableService services |
| CommandResult | commands | `.status: CommandStatus`<br>`.data: Map?`<br>`.error: String?` | Coordinator.processUserAction() |

**Correct pattern**:
```kotlin
// Services (OperationResult)
val result = service.execute(...)
if (result.success) { ... }

// Coordinator (CommandResult)
val result = coordinator.processUserAction(...)
if (result.status == CommandStatus.SUCCESS) { ... }
```

## Unified logging system

A centralised architecture for all the project's logs with structured tags and robust error handling.

### Centralised LogManager
**Location**: core/utils/LogManager.kt

**Methods available**: aiSession, aiPrompt, aiUI, aiService, aiEnrichment, schema, coordination, tracking, database, service, ui

### Standard API
LogManager.schema(), .coordination(), .tracking(), .database() etc. with DEBUG level by default, INFO/WARN/ERROR levels and an optional throwable.

### Writing to the database
A line goes to the console at once, and to the database through a single bounded queue (2,000 lines) that a single task empties. While a long transaction holds the database (an import), the lines beyond the queue go only to the console; their number is written once the database is free. A schema validation writes only its refusals: a trace per validated write saturated the memory of an import of 64,000 rows.

### Robust error handling
- Automatic try/catch with a println() fallback for unit tests
- Optional throwable with stack trace
- Compatible with Android and tests

### Debugging guide for service resolution
**Common problems**:
1. **"Service not found"**: add logs in the service
2. **"Tool instance ID is required"**: check that the key is indeed `tool_instance_id`
3. **Wrong routing**: the CommandDispatcher logs automatically
4. **LaunchedEffect does not fire again**: add ALL the variables checked to the dependencies

---

*The Core architecture guarantees extensibility and consistency without excessive complexity.*
