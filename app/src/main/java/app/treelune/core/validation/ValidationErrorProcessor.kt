package app.treelune.core.validation

import com.networknt.schema.ValidationMessage
import org.json.JSONObject
import app.treelune.core.utils.LogManager

/**
 * Processes and formats NetworkNT validation errors
 * Handles error prioritization, filtering, and user-friendly formatting
 */
object ValidationErrorProcessor {
    
    /**
     * Filters and formats validation errors into readable string with error prioritization
     * Filters base schema errors when conditional schemas are present
     * Each field is named by its label, read in [schema] (replaceFieldNameInMessage).
     */
    fun filterErrors(errors: Set<ValidationMessage>, schema: String): String {
        if (errors.isEmpty()) return ""
        
        // Filter out base schema errors if conditional schemas are present
        val filteredErrors = filterRedundantSchemaErrors(errors, schema)
        
        // Group remaining errors by path and prioritize
        val errorsByPath = filteredErrors.groupBy { it.path }
        
        val schemaJson = JSONObject(schema)
        return errorsByPath.map { (_, pathErrors) ->
            replaceFieldNameInMessage(selectMostRelevantError(pathErrors), schemaJson)
        }.joinToString("\n")
    }
    
    /**
     * Filters out base schema errors when conditional schemas (if/then) are present
     * Analyzes the schema definition itself to detect conditional validation
     */
    private fun filterRedundantSchemaErrors(errors: Set<ValidationMessage>, schema: String): List<ValidationMessage> {
        val errorList = errors.toList()
        
        // Robust detection of conditional validation using JSON parsing
        val hasConditionalValidation = detectConditionalValidation(schema)
        
        LogManager.schema("Schema has conditional validation: $hasConditionalValidation")
        
        if (!hasConditionalValidation) {
            // No conditional validation, keep all errors
            return errorList
        }
        
        // Filter out base schema structure errors when conditional validation is present
        val filteredErrors = errorList.filter { error ->
            val isBaseSchemaError = (error.message.contains("not defined in the schema") || 
                                   error.message.contains("additional properties")) &&
                                   !error.schemaPath.contains("/then/") &&
                                   !error.schemaPath.contains("/else/")
            
            if (isBaseSchemaError) {
                LogManager.schema("Filtering out base schema error: ${error.message}")
            }
            
            // Keep error if it's not a base schema error
            !isBaseSchemaError
        }
        
        LogManager.schema("Filtered errors: ${filteredErrors.size} out of ${errorList.size}")
        return filteredErrors
    }
    
    /**
     * Selects the most relevant error message from multiple errors for the same field
     * Prioritizes type errors over schema structure errors
     */
    private fun selectMostRelevantError(errors: List<ValidationMessage>): String {
        val messages = errors.map { it.message }
        
        // Priority 1: Type mismatch errors (most specific)
        val typeErrors = messages.filter { msg ->
            msg.contains("found,") && msg.contains("expected") ||
            msg.contains("string found, number expected") ||
            msg.contains("number found, string expected") ||
            msg.contains("boolean found, string expected") ||
            msg.contains("array found, object expected") ||
            msg.contains("object found, array expected") ||
            msg.contains("found") && msg.contains("expected")
        }
        if (typeErrors.isNotEmpty()) {
            return typeErrors.first()
        }
        
        // Priority 2: Required field errors
        val requiredErrors = messages.filter { msg ->
            msg.contains("is missing but it is required") ||
            msg.contains("required property") ||
            msg.contains("is required") ||
            msg.contains("required")
        }
        if (requiredErrors.isNotEmpty()) {
            return requiredErrors.first()
        }
        
        // Priority 3: Format/pattern errors
        val formatErrors = messages.filter { msg ->
            msg.contains("does not match") ||
            msg.contains("pattern") ||
            msg.contains("format")
        }
        if (formatErrors.isNotEmpty()) {
            return formatErrors.first()
        }
        
        // Priority 4: Range/constraint errors
        val constraintErrors = messages.filter { msg ->
            msg.contains("minimum") ||
            msg.contains("maximum") ||
            msg.contains("minLength") ||
            msg.contains("maxLength") ||
            msg.contains("enum")
        }
        if (constraintErrors.isNotEmpty()) {
            return constraintErrors.first()
        }
        
        // Priority 5 (lowest): Schema structure errors
        val schemaErrors = messages.filter { msg ->
            msg.contains("is not defined in the schema") ||
            msg.contains("additional properties") ||
            msg.contains("additionalProperties")
        }
        if (schemaErrors.isNotEmpty() && messages.size == 1) {
            // Only show schema errors if they're the only error
            return schemaErrors.first()
        }
        
        // Fallback: return first error if no pattern matches
        return messages.firstOrNull() ?: "invalid format"
    }
    
    /**
     * Replaces each $.path in a refusal by the label of the field it points to, the title its
     * schema carries (FieldValueSchema.forReader): "$.name: must be at most 60 characters long"
     * reads "Name: must be at most 60 characters long", "$.extra.kcal" the label of the user's
     * field. A path whose schema has no title is named by its last key, never shown as a path.
     */
    private fun replaceFieldNameInMessage(errorMessage: String, schema: JSONObject): String {
        val fieldPathRegex = Regex("""\$((?:\.[a-zA-Z_][a-zA-Z0-9_]*|\[[0-9]+\])+)""")
        val stepRegex = Regex("""\.([a-zA-Z_][a-zA-Z0-9_]*)|\[[0-9]+\]""")
        return fieldPathRegex.replace(errorMessage) { match ->
            val steps = stepRegex.findAll(match.groupValues[1]).toList()
            var node: JSONObject? = schema
            steps.forEach { step ->
                node = node?.let { if (step.value.startsWith("[")) it.optJSONObject("items") else propertyOf(it, step.groupValues[1]) }
            }
            node?.optString("title")?.takeIf { it.isNotBlank() }
                ?: steps.last { !it.value.startsWith("[") }.groupValues[1]
        }
    }

    /**
     * The schema of [key] under [schema]: among its properties, or those of a branch it combines
     * (allOf, anyOf, oneOf, then, else), where a conditional schema declares some of its fields.
     */
    private fun propertyOf(schema: JSONObject, key: String): JSONObject? {
        schema.optJSONObject("properties")?.optJSONObject(key)?.let { return it }
        val branches = listOf("allOf", "anyOf", "oneOf").flatMap { name ->
            schema.optJSONArray(name)?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it) } } ?: emptyList()
        } + listOfNotNull(schema.optJSONObject("then"), schema.optJSONObject("else"))
        return branches.firstNotNullOfOrNull { propertyOf(it, key) }
    }
    
    /**
     * Detects conditional validation using precise regex patterns
     * Looks for "if": followed by an object AND "then": followed by an object
     */
    private fun detectConditionalValidation(schemaJson: String): Boolean {
        return schemaJson.contains(Regex("\"if\"\\s*:\\s*\\{")) && 
               schemaJson.contains(Regex("\"then\"\\s*:\\s*\\{"))
    }
    
}