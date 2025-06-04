package org.fossify.phone.activities

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.phone.R
import org.fossify.phone.adapters.ExtensionSettingsAdapter
import org.fossify.phone.databinding.ActivityExtensionSettingsBinding
import org.fossify.phone.extensions.core.ExtensionManager
import org.fossify.phone.extensions.core.PhoneExtension
import org.fossify.phone.dialogs.ExtensionConfigDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.models.RadioItem

/**
 * Actividad para configurar las extensiones del sistema
 */
class ExtensionSettingsActivity : BaseSimpleActivity() {
    
    override fun getAppIconIDs(): ArrayList<Int> = arrayListOf()
    override fun getAppLauncherName(): String = getString(R.string.app_launcher_name)
    override fun getRepositoryName(): String? = null
    
    private val binding by viewBinding(ActivityExtensionSettingsBinding::inflate)
    private val extensionManager = ExtensionManager.getInstance()
    private lateinit var adapter: ExtensionSettingsAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        setupMaterialScrollListener(binding.extensionsList, binding.extensionsToolbar)
        
        binding.apply {
            updateMaterialActivityViews(
                extensionsCoordinator, 
                extensionsList, 
                useTransparentNavigation = true, 
                useTopSearchMenu = false
            )
            setupMaterialScrollListener(extensionsList, extensionsToolbar)
        }
        
        updateNavigationBarColor(getProperBackgroundColor())
        setupOptionsMenu()
        setupExtensionsList()
    }
    
    override fun onResume() {
        super.onResume()
        setupToolbar(binding.extensionsToolbar, NavigationIcon.Arrow)
    }
    
    private fun setupOptionsMenu() {
        binding.extensionsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_extension -> {
                    // Mostrar diálogo para agregar nueva extensión
                    showAddExtensionDialog()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupExtensionsList() {
        val extensions = extensionManager.getAllExtensions()
        
        adapter = ExtensionSettingsAdapter(
            extensions = extensions.toMutableList(),
            onExtensionToggled = { extension, enabled ->
                extension.isEnabled = enabled
                extensionManager.saveExtensionSettings(extension)
            },
            onExtensionConfigured = { extension ->
                showExtensionConfigDialog(extension)
            }
        )
        
        binding.extensionsList.apply {
            layoutManager = LinearLayoutManager(this@ExtensionSettingsActivity)
            adapter = this@ExtensionSettingsActivity.adapter
        }
        
        updateEmptyState()
    }
    
    private fun updateEmptyState() {
        binding.apply {
            if (adapter.itemCount == 0) {
                extensionsList.beGone()
                extensionsEmptyPlaceholder.beVisible()
                extensionsEmptyText.text = getString(R.string.no_extensions_found)
            } else {
                extensionsList.beVisible()
                extensionsEmptyPlaceholder.beGone()
            }
        }
    }
    
    private fun showAddExtensionDialog() {
        // Aquí se mostraría un diálogo para agregar nuevas extensiones
        // Por ahora, mostrar las extensiones disponibles
        val availableExtensions = listOf(
            "IVR Extension" to "org.fossify.phone.extensions.ivr.IVRExtension",
            "AI Assistant" to "org.fossify.phone.extensions.ai.AIAssistantExtension",
            "USSD Interceptor" to "org.fossify.phone.extensions.ussd.USSDInterceptorExtension"
        )
        
        val radioItems = availableExtensions.mapIndexed { index, (name, _) ->
            RadioItem(index, name)
        }
        
        RadioGroupDialog(
            activity = this,
            items = ArrayList(radioItems),
            checkedItemId = -1
        ) { selectedIndex ->
            val index = selectedIndex as Int
            val extensionClass = availableExtensions[index].second
            addExtension(extensionClass)
        }
    }
    
    private fun addExtension(className: String) {
        try {
            val extensionClass = Class.forName(className)
            val extension = extensionClass.newInstance() as PhoneExtension
            
            extensionManager.registerExtension(extension)
            adapter.addExtension(extension)
            updateEmptyState()
            
            toast(R.string.extension_added_successfully)
        } catch (e: Exception) {
            toast(R.string.error_adding_extension)
        }
    }
    
    private fun showExtensionConfigDialog(extension: PhoneExtension) {
        val settings = extension.getSettings()
        
        // Crear diálogo de configuración basado en los settings de la extensión
        ExtensionConfigDialog(
            activity = this,
            extension = extension,
            settings = settings
        ) { updatedSettings ->
            extension.updateSettings(updatedSettings)
            extensionManager.saveExtensionSettings(extension)
            adapter.notifyDataSetChanged()
        }
    }
}