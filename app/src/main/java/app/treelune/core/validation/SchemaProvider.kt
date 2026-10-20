package app.treelune.core.validation

import android.content.Context

/**
 * Schema provider interface for direct schema ID lookup
 * All schemas are complete and standalone
 */
interface SchemaProvider {
    /**
     * Gets a schema by its unique ID
     * @param schemaId Unique schema identifier (e.g., "tracking_config_numeric", "zone_config")
     * @param context Android context for string resource access (required for internationalization)
     * @param toolInstanceId Optional tool instance ID for data schemas requiring custom fields enrichment
     * @return Complete Schema object, or null if schema ID not supported by this provider
     */
    fun getSchema(schemaId: String, context: Context, toolInstanceId: String? = null): Schema?

    /**
     * Gets all schema IDs supported by this provider
     * Used for AI system integration and schema discovery
     * @return List of all schema IDs this provider can handle
     */
    fun getAllSchemaIds(): List<String>

}