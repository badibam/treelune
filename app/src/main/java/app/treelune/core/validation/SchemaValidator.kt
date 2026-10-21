package app.treelune.core.validation

import android.content.Context
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import org.json.JSONObject
import app.treelune.core.utils.LogManager

/**
 * Unified JSON Schema validator with recursive validation support
 * Clean API for all validation scenarios
 */
object SchemaValidator {
    private const val TAG = "SchemaValidator"
    
    // Cache for compiled schemas - enabled for production performance
    private val schemaCache: MutableMap<String, JsonSchema> = mutableMapOf()
    private val cacheEnabled = true
    
    private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)

    /**
     * The library's messages in the phone's language, read as the ISO-8859-1 its files are
     * written in: Android reads a properties bundle as UTF-8, which turned every accent of the
     * French messages into a replacement character ("dépasser" shown as "d?passer").
     */
    private val schemaConfig = com.networknt.schema.SchemaValidatorsConfig().apply {
        resourceBundle = messageBundle(java.util.Locale.getDefault())
    }

    /**
     * The library's messages for [locale], their accents and apostrophes read right: its own file
     * for the language, the default one beneath. Read here rather than by ResourceBundle.getBundle,
     * whose cache would hand back the bundle the library loaded itself, without either fix.
     */
    internal fun messageBundle(locale: java.util.Locale): java.util.ResourceBundle {
        val base = Latin1Messages.read("jsv-messages.properties", null)
            ?: throw IllegalStateException("jsv-messages.properties not found")
        return Latin1Messages.read("jsv-messages_${locale.language}.properties", base) ?: base
    }

    /**
     * A message file of the library, read in ISO-8859-1, its encoding — Android reads a properties
     * file as UTF-8, which turned every accent into a replacement character ("d?passer") — and
     * each apostrophe doubled: the library formats its messages with MessageFormat, where a lone
     * apostrophe quotes and vanishes ("n'a pas" shown as "na pas").
     */
    private class Latin1Messages(private val messages: Map<String, String>, parent: java.util.ResourceBundle?) : java.util.ResourceBundle() {
        init { parent?.let { setParent(it) } }

        override fun handleGetObject(key: String): Any? = messages[key]
        override fun getKeys(): java.util.Enumeration<String> =
            java.util.Collections.enumeration(messages.keys + (parent?.keySet() ?: emptySet()))

        companion object {
            fun read(resource: String, parent: java.util.ResourceBundle?): Latin1Messages? {
                val stream = SchemaValidator::class.java.classLoader?.getResourceAsStream(resource) ?: return null
                val read = stream.reader(Charsets.ISO_8859_1).use { java.util.Properties().apply { load(it) } }
                return Latin1Messages(read.stringPropertyNames().associateWith { read.getProperty(it).replace("'", "''") }, parent)
            }
        }
    }
    
    /**
     * Main validation function using direct schema objects
     * @param schema Complete Schema object with content and metadata
     * @param data Data to validate as key-value map
     * @param context Android context for string resource access
     * @return ValidationResult with success/error status and user-friendly messages
     */
    fun validate(
        schema: Schema,
        data: Map<String, Any?>,
        context: Context
    ): ValidationResult {
        // Only a refusal is logged: every write is validated, and a trace per validation (the
        // schema and the data written out) made an import of 64 000 lines run out of memory
        return try {
            // Checked as handed: a writer whose null means "not given" takes it out first
            // (JsonNulls), so what is checked is what is stored
            val cleanData = app.treelune.core.utils.JsonUtils.toMap(data)

            val schemaContent = schema.content

            val jsonSchema = getOrCompileSchema(schemaContent)
            val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
            val dataNode = objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(cleanData)

            val errors = jsonSchema.validate(dataNode)

            val result = if (errors.isEmpty()) {
                ValidationResult.success()
            } else {
                // A variant's refusal names nothing to fix: it is replaced by the errors of the
                // branch the value's selector points to
                val (others, explained) = VariantErrors.sort(errors, JSONObject(schemaContent), dataNode)
                val errorMessage = (listOfNotNull(
                    ValidationErrorProcessor.filterErrors(others.toSet(), schema.content).takeIf { it.isNotEmpty() }
                ) + explained).joinToString("\n")
                if (errorMessage.isEmpty()) {
                    ValidationResult.success()
                } else {
                    LogManager.schema("Validation failed for ${schema.id}: $errorMessage", "ERROR")
                    ValidationResult.error(errorMessage)
                }
            }

            result

        } catch (e: Exception) {
            LogManager.schema("Exception during validation: ${e.message}", "ERROR", e)
            ValidationResult.error("Erreur de validation: ${e.message}")
        }
    }
    
    
    /**
     * Gets or compiles schema with optional caching
     */
    private fun getOrCompileSchema(schemaJson: String): JsonSchema {
        val cacheKey = schemaJson.hashCode().toString()
        
        return if (cacheEnabled) {
            schemaCache.getOrPut(cacheKey) {
                LogManager.schema("Compiling and caching schema")
                compileSchema(schemaJson)
            }
        } else {
            LogManager.schema("Compiling schema (no cache)")
            compileSchema(schemaJson)
        }
    }
    
    /**
     * Compiles JSON schema string into JsonSchema object
     */
    private fun compileSchema(schemaJson: String): JsonSchema {
        val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
        val schemaNode = objectMapper.readTree(schemaJson)
        return schemaFactory.getSchema(schemaNode, schemaConfig)
    }
    
    /**
     * Clears schema cache - useful for tests or development
     */
    fun clearCache() {
        schemaCache.clear()
        LogManager.schema("Schema cache cleared")
    }

}