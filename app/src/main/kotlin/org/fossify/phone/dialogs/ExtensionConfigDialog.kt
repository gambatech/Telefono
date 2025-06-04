package org.fossify.phone.dialogs

import android.app.Activity
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.phone.R
import org.fossify.phone.extensions.core.PhoneExtension

/**
 * Diálogo para configurar las extensiones
 */
class ExtensionConfigDialog(
    private val activity: Activity,
    private val extension: PhoneExtension,
    private val settings: Map<String, Any>,
    private val callback: (Map<String, Any>) -> Unit
) {
    
    private val configViews = mutableMapOf<String, Any>()
    
    init {
        showDialog()
    }
    
    private fun showDialog() {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_extension_config, null)
        val container = view.findViewById<LinearLayout>(R.id.config_container)
        
        // Título
        val titleView = view.findViewById<TextView>(R.id.extension_title)
        titleView.text = extension.name
        titleView.setTextColor(activity.getProperTextColor())
        
        // Descripción
        val descriptionView = view.findViewById<TextView>(R.id.extension_description)
        descriptionView.text = extension.description
        descriptionView.setTextColor(activity.getProperTextColor())
        
        // Generar controles de configuración dinámicamente
        generateConfigControls(container)
        
        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok) { _, _ ->
                val updatedSettings = collectSettings()
                callback(updatedSettings)
            }
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this, R.string.configure_extension)
            }
    }
    
    private fun generateConfigControls(container: LinearLayout) {
        settings.forEach { (key, value) ->
            when (value) {
                is Boolean -> {
                    val checkBox = CheckBox(activity).apply {
                        text = getSettingDisplayName(key)
                        isChecked = value
                        setTextColor(activity.getProperTextColor())
                    }
                    container.addView(checkBox)
                    configViews[key] = checkBox
                }
                is String -> {
                    val label = TextView(activity).apply {
                        text = getSettingDisplayName(key)
                        setTextColor(activity.getProperTextColor())
                        setPadding(0, 16, 0, 8)
                    }
                    val editText = EditText(activity).apply {
                        setText(value)
                        setTextColor(activity.getProperTextColor())
                    }
                    container.addView(label)
                    container.addView(editText)
                    configViews[key] = editText
                }
                is Int -> {
                    val label = TextView(activity).apply {
                        text = getSettingDisplayName(key)
                        setTextColor(activity.getProperTextColor())
                        setPadding(0, 16, 0, 8)
                    }
                    val editText = EditText(activity).apply {
                        setText(value.toString())
                        setTextColor(activity.getProperTextColor())
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    }
                    container.addView(label)
                    container.addView(editText)
                    configViews[key] = editText
                }
                is Long -> {
                    val label = TextView(activity).apply {
                        text = getSettingDisplayName(key)
                        setTextColor(activity.getProperTextColor())
                        setPadding(0, 16, 0, 8)
                    }
                    val editText = EditText(activity).apply {
                        setText(value.toString())
                        setTextColor(activity.getProperTextColor())
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    }
                    container.addView(label)
                    container.addView(editText)
                    configViews[key] = editText
                }
                is List<*> -> {
                    val label = TextView(activity).apply {
                        text = "${getSettingDisplayName(key)} (${value.size} items)"
                        setTextColor(activity.getProperTextColor())
                        setPadding(0, 16, 0, 8)
                    }
                    container.addView(label)
                    // Para listas, mostrar solo información por ahora
                }
            }
        }
    }
    
    private fun collectSettings(): Map<String, Any> {
        val updatedSettings = mutableMapOf<String, Any>()
        
        configViews.forEach { (key, view) ->
            when (view) {
                is CheckBox -> {
                    updatedSettings[key] = view.isChecked
                }
                is EditText -> {
                    val text = view.text.toString()
                    val originalValue = settings[key]
                    
                    updatedSettings[key] = when (originalValue) {
                        is String -> text
                        is Int -> text.toIntOrNull() ?: originalValue
                        is Long -> text.toLongOrNull() ?: originalValue
                        else -> text
                    }
                }
            }
        }
        
        // Mantener valores que no se pueden editar
        settings.forEach { (key, value) ->
            if (!updatedSettings.containsKey(key)) {
                updatedSettings[key] = value
            }
        }
        
        return updatedSettings
    }
    
    private fun getSettingDisplayName(key: String): String {
        return when (key) {
            "enabled" -> activity.getString(R.string.enabled)
            "auto_answer_delay" -> activity.getString(R.string.auto_answer_delay)
            "default_greeting" -> activity.getString(R.string.default_greeting)
            "assistant_personality" -> activity.getString(R.string.assistant_personality)
            "conversation_timeout" -> activity.getString(R.string.conversation_timeout)
            "block_unknown_numbers" -> activity.getString(R.string.block_unknown_numbers)
            "block_private_numbers" -> activity.getString(R.string.block_private_numbers)
            "block_international_numbers" -> activity.getString(R.string.block_international_numbers)
            "blocked_numbers" -> activity.getString(R.string.blocked_numbers)
            "whitelisted_numbers" -> activity.getString(R.string.whitelisted_numbers)
            "blocked_calls_count" -> activity.getString(R.string.blocked_calls_count)
            else -> key.replace("_", " ").capitalize()
        }
    }
}