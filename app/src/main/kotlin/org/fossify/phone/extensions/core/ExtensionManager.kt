package org.fossify.phone.extensions.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap



/**
 * Gestor central del sistema de extensiones
 * Maneja el registro, activación y coordinación de todas las extensiones
 */
class ExtensionManager private constructor() {
    
    companion object {
        private const val TAG = "ExtensionManager"
        
        @Volatile
        private var INSTANCE: ExtensionManager? = null
        
        fun getInstance(): ExtensionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ExtensionManager().also { INSTANCE = it }
            }
        }
    }
    
    private val extensions = ConcurrentHashMap<String, PhoneExtension>()
    private val extensionConfigs = ConcurrentHashMap<String, ExtensionConfig>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var isInitialized = false
    private var context: Context? = null
    
    /**
     * Inicializa el gestor de extensiones
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        
        this.context = context.applicationContext
        loadExtensionConfigs()
        registerBuiltInExtensions()
        isInitialized = true
        
        Log.d(TAG, "ExtensionManager initialized with ${extensions.size} extensions")
    }
    
    /**
     * Registra una nueva extensión
     */
    fun registerExtension(extension: PhoneExtension) {
        try {
            extensions[extension.id] = extension
            
            // Inicializar si está habilitada
            val config = extensionConfigs[extension.id]
            if (config?.isEnabled == true || (config == null && extension.isEnabled)) {
                context?.let { extension.initialize(it) }
                Log.d(TAG, "Extension ${extension.id} registered and initialized")
            } else {
                Log.d(TAG, "Extension ${extension.id} registered but disabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering extension ${extension.id}", e)
        }
    }
    
    /**
     * Desregistra una extensión
     */
    fun unregisterExtension(extensionId: String) {
        extensions[extensionId]?.let { extension ->
            try {
                extension.cleanup()
                extensions.remove(extensionId)
                Log.d(TAG, "Extension $extensionId unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering extension $extensionId", e)
            }
        }
    }
    
    /**
     * Habilita o deshabilita una extensión
     */
    fun setExtensionEnabled(extensionId: String, enabled: Boolean) {
        val extension = extensions[extensionId] ?: return
        val currentConfig = extensionConfigs[extensionId] ?: ExtensionConfig(extensionId)
        
        extensionConfigs[extensionId] = currentConfig.copy(isEnabled = enabled)
        
        try {
            if (enabled && context != null) {
                extension.initialize(context!!)
                Log.d(TAG, "Extension $extensionId enabled")
            } else {
                extension.cleanup()
                Log.d(TAG, "Extension $extensionId disabled")
            }
            saveExtensionConfigs()
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling extension $extensionId", e)
        }
    }
    
    /**
     * Obtiene todas las extensiones registradas
     */
    fun getAllExtensions(): List<PhoneExtension> {
        return extensions.values.toList()
    }
    
    /**
     * Guarda la configuración de una extensión específica
     */
    fun saveExtensionSettings(extension: PhoneExtension) {
        val config = extensionConfigs[extension.id] ?: ExtensionConfig(extension.id)
        extensionConfigs[extension.id] = config.copy(isEnabled = extension.isEnabled)
        saveExtensionConfigs()
    }
    
    /**
     * Obtiene extensiones habilitadas ordenadas por prioridad
     */
    private fun getEnabledExtensions(): List<PhoneExtension> {
        return extensions.values
            .filter { extension ->
                val config = extensionConfigs[extension.id]
                config?.isEnabled ?: extension.isEnabled
            }
            .sortedByDescending { it.priority }
    }
    
    /**
     * Notifica cambio de estado de llamada a todas las extensiones habilitadas
     */
    fun notifyCallStateChanged(state: CallState, phoneNumber: String?) {
        scope.launch {
            getEnabledExtensions().forEach { extension ->
                try {
                    extension.onCallStateChanged(state, phoneNumber)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in extension ${extension.id} onCallStateChanged", e)
                }
            }
        }
    }
    
    /**
     * Procesa entrada del dialpad a través de extensiones
     * @return true si alguna extensión consumió la entrada
     */
    fun handleDialpadInput(input: String): Boolean {
        return getEnabledExtensions().any { extension ->
            try {
                extension.onDialpadInput(input)
            } catch (e: Exception) {
                Log.e(TAG, "Error in extension ${extension.id} onDialpadInput", e)
                false
            }
        }
    }
    
    /**
     * Procesa códigos USSD a través de extensiones
     * @return true si alguna extensión interceptó el código
     */
    fun handleUSSDCode(code: String): Boolean {
        return getEnabledExtensions().any { extension ->
            try {
                extension.onUSSDCode(code)
            } catch (e: Exception) {
                Log.e(TAG, "Error in extension ${extension.id} onUSSDCode", e)
                false
            }
        }
    }
    
    /**
     * Procesa datos de audio a través de extensiones
     */
    fun handleAudioData(audioData: ByteArray, isIncoming: Boolean) {
        scope.launch {
            getEnabledExtensions().forEach { extension ->
                try {
                    extension.onAudioData(audioData, isIncoming)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in extension ${extension.id} onAudioData", e)
                }
            }
        }
    }
    
    /**
     * Obtiene la configuración de una extensión
     */
    fun getExtensionConfig(extensionId: String): ExtensionConfig? {
        return extensionConfigs[extensionId]
    }
    
    /**
     * Actualiza la configuración de una extensión
     */
    fun updateExtensionConfig(config: ExtensionConfig) {
        extensionConfigs[config.id] = config
        saveExtensionConfigs()
    }
    
    /**
     * Limpia todos los recursos
     */
    fun cleanup() {
        scope.cancel()
        extensions.values.forEach { extension ->
            try {
                extension.cleanup()
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up extension ${extension.id}", e)
            }
        }
        extensions.clear()
        extensionConfigs.clear()
        isInitialized = false
        context = null
    }
    
    /**
     * Registra las extensiones integradas
     */
    private fun registerBuiltInExtensions() {
        // Se registrarán las extensiones específicas aquí
        // Por ahora dejamos el método preparado para futuras implementaciones
    }
    
    /**
     * Carga configuraciones de extensiones desde SharedPreferences
     */
    private fun loadExtensionConfigs() {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("extension_configs", Context.MODE_PRIVATE)
            // Implementar carga de configuraciones
            // Por simplicidad, por ahora usamos configuraciones por defecto
        }
    }
    
    /**
     * Guarda configuraciones de extensiones en SharedPreferences
     */
    private fun saveExtensionConfigs() {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("extension_configs", Context.MODE_PRIVATE)
            // Implementar guardado de configuraciones
            // Por simplicidad, por ahora no persistimos
        }
    }
}