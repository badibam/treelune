package app.treelune.core.fields

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.selection.Reference

/** What a reference designates as shown: its current name, or null once it was deleted. */
sealed interface ReferenceName {
    data class Named(val name: String) : ReferenceName
    data object Deleted : ReferenceName
}

/**
 * The current names of [references], read through references.names.
 *
 * @throws IllegalStateException when they cannot be read: a read that fails is never taken for a deletion
 */
suspend fun loadReferenceNames(references: Collection<Reference>, context: Context): Map<Reference, ReferenceName> {
    if (references.isEmpty()) return emptyMap()
    val result = Coordinator(context).processUserAction(
        "references.names",
        mapOf("references" to references.map { app.treelune.core.utils.JsonUtils.toMap(it.toJson()) })
    )
    if (!result.isSuccess) throw IllegalStateException("Reference names not read: ${result.error}")
    return (result.data?.get("references") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>().associate { row ->
        val reference = ReferenceTarget.referenceOf(row) ?: throw IllegalStateException("unreadable reference in the names read: $row")
        reference to ((row["name"] as? String)?.let { ReferenceName.Named(it) } ?: ReferenceName.Deleted)
    }
}

/**
 * The names as text of [references], read together once: what each designates now, or
 * « deleted » (pointer_target_deleted) once it is gone.
 *
 * @throws IllegalStateException when they cannot be read, as [loadReferenceNames]
 */
suspend fun referenceTexts(references: Collection<Reference>, context: Context): Map<Reference, String> {
    if (references.isEmpty()) return emptyMap()
    val s = app.treelune.core.strings.Strings.`for`(context = context)
    val names = loadReferenceNames(references, context)
    return references.associateWith { (names[it] as? ReferenceName.Named)?.name ?: s.shared("pointer_target_deleted") }
}

/**
 * The names as text of the references [values] hold, each the value of a REFERENCE field (one
 * reference or a list), read together once: what [FieldDefinition.formatValue] shows them by.
 *
 * @throws IllegalStateException when they cannot be read, as [loadReferenceNames]
 */
suspend fun referenceValueTexts(values: Iterable<Any?>, context: Context): Map<Reference, String> =
    referenceTexts(values.flatMap { ReferenceTarget.referencesOf(it) }.toSet(), context)
