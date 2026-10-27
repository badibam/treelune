package app.treelune.core.fields.migration

/**
 * What a configuration change does to the values of one entry's container ("data" or "extra"):
 * the strategies of the changes applied to its map. EntryMigration runs it over every entry.
 */
object FieldDataMigrator {

    /**
     * Apply migration strategies to the values of one container.
     *
     * @param values Original values of the container (copied, not modified)
     * @param changes List of detected configuration changes
     * @param strategies Map of migration strategies for each change
     * @param entryInstance The tool instance of an entry a reference designates, null when the
     *   entry no longer exists: a deleted target keeps its reference
     * @return The values the container keeps
     *
     * Visible to the tests: this is where a configuration change meets real entries, and what it
     * does to them cannot be read off the strategies alone.
     */
    internal fun applyMigrationStrategies(
        values: Map<String, Any?>,
        changes: List<FieldChange>,
        strategies: Map<FieldChange, MigrationStrategy>,
        entryInstance: (String) -> String? = { null }
    ): Map<String, Any?> {
        val result = values.toMutableMap()

        changes.forEach { change ->
            val strategy = strategies[change] ?: return@forEach

            when (strategy) {
                MigrationStrategy.STRIP_FIELD -> {
                    // Remove the field from the entry: the field deleted, or its type changed
                    when (change) {
                        is FieldChange.Removed -> result.remove(change.name)
                        is FieldChange.TypeChanged -> result.remove(change.name)
                        else -> {} // Strategy mismatch, should not happen
                    }
                }

                MigrationStrategy.CONVERT -> {
                    val name = when (change) {
                        is FieldChange.ScaleRangeChanged -> change.name
                        is FieldChange.ChoiceShapeChanged -> change.name
                        is FieldChange.ReferenceShapeChanged -> change.name
                        else -> return@forEach // Strategy mismatch, should not happen
                    }
                    val value = result[name] ?: return@forEach
                    val converted = convert(change, value)
                    if (converted == null) result.remove(name) else result[name] = converted
                }

                MigrationStrategy.STRIP_FIELD_IF_VALUE -> {
                    // Remove field only if value matches condition
                    when (change) {
                        is FieldChange.ChoiceOptionsRemoved -> {
                            val fieldValue = result[change.name]

                            // Check if value matches removed options
                            val shouldRemove = when (fieldValue) {
                                // Single choice: direct string comparison
                                is String -> change.removedOptions.contains(fieldValue)

                                // Multiple choice: check if any selected value was removed
                                is List<*> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    val selectedOptions = fieldValue as? List<String> ?: emptyList()
                                    selectedOptions.any { it in change.removedOptions }
                                }

                                else -> false
                            }

                            if (shouldRemove) {
                                result.remove(change.name)
                            }
                        }
                        is FieldChange.ConfigRestricted -> {
                            // The type says whether the stored value still fits. A widened bound
                            // reaches here too, and keeps everything.
                            if (!change.fieldType.permits(result[change.name], change.newConfig)) {
                                result.remove(change.name)
                            }
                        }
                        is FieldChange.ReferenceTargetNarrowed -> {
                            // Every reference of a list must fit, as every option of a multiple choice must remain
                            val target = app.treelune.core.fields.ReferenceTarget.fromConfig(change.newConfig)
                            val fits = app.treelune.core.fields.ReferenceTarget.referencesOf(result[change.name]).all { reference ->
                                target.acceptsKind(reference) &&
                                    (reference.kind != app.treelune.core.selection.ReferenceKind.ENTRY ||
                                        entryInstance(reference.id!!)?.let { target.acceptsEntryOf(it) } ?: true)
                            }
                            if (!fits) result.remove(change.name)
                        }
                        else -> {} // Strategy mismatch, should not happen
                    }
                }

                MigrationStrategy.NONE -> {
                    // No transformation needed
                }
            }
        }

        return result
    }

    /** [value] in the field's new form after [change], or null when that form cannot take it. */
    private fun convert(change: FieldChange, value: Any): Any? = when (change) {
        is FieldChange.ScaleRangeChanged -> (value as? Number)?.toDouble()
            ?.takeIf { it >= change.newMin.toDouble() && it <= change.newMax.toDouble() }?.let { value }
        is FieldChange.ChoiceShapeChanged -> when {
            !change.oldShape.isList && change.newShape.isList -> listOf(value)
            change.oldShape.isList && !change.newShape.isList -> (value as? List<*>)?.singleOrNull()
            else -> value
        }
        is FieldChange.ReferenceShapeChanged -> if (change.multiple) (value as? List<*>) ?: listOf(value) else (value as? List<*>)?.singleOrNull() ?: value.takeIf { it !is List<*> }
        else -> value
    }

    /**
     * Whether a value kept through [change] is read under another meaning: a scale's value between
     * other bounds, a set of answers read as a ranking.
     */
    internal fun reinterprets(change: FieldChange): Boolean = when (change) {
        is FieldChange.ScaleRangeChanged -> true
        is FieldChange.ChoiceShapeChanged -> change.oldShape == app.treelune.core.fields.ChoiceShape.MULTIPLE &&
            change.newShape == app.treelune.core.fields.ChoiceShape.ORDERED
        else -> false
    }
}
