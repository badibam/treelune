package app.treelune.core.ai.database

import androidx.room.*

/**
 * An AI provider the user created (docs/AI.md, « Provider abstraction »): a kind (AIProviderKinds), a
 * name, unique, and its config, its secrets sealed (SecretSettings). At most one is the default,
 * which a new conversation takes (enforced by AIProviderConfigService).
 */
@Entity(
    tableName = "ai_provider_configs",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["is_default"])
    ]
)
data class AIProviderConfigEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "kind") val kind: String,                     // AIProviderKind.kind: "claude", "openai", "deepseek", "compatible"
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "config_json") val configJson: String,        // API key sealed, model and settings
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
