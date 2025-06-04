package org.fossify.phone.extensions.callblocking

import android.content.Context
import android.content.SharedPreferences
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fossify.phone.extensions.core.PhoneExtension
import org.fossify.phone.extensions.core.CallState
import java.util.regex.Pattern

/**
 * Extensión para bloqueo de llamadas
 * Permite bloquear llamadas basándose en números, patrones y listas negras
 */
class CallBlockingExtension : PhoneExtension {
    
    private var context: Context? = null
    private var preferences: SharedPreferences? = null
    private var isEnabled = false
    private val blockedNumbers = mutableSetOf<String>()
    private val blockedPatterns = mutableSetOf<String>()
    private val whitelistedNumbers = mutableSetOf<String>()
    private var blockUnknownNumbers = false
    private var blockPrivateNumbers = false
    private var blockInternationalNumbers = false
    
    override val name = "Call Blocking"
    override val version = "1.0.0"
    override val description = "Bloquea llamadas no deseadas basándose en números, patrones y reglas personalizadas"
    
    override fun initialize(context: Context) {
        this.context = context
        this.preferences = context.getSharedPreferences("call_blocking_prefs", Context.MODE_PRIVATE)
        loadSettings()
    }
    
    override fun onCallStateChanged(state: CallState, phoneNumber: String?) {
        when (state) {
            CallState.INCOMING -> {
                if (isEnabled && shouldBlockCall(phoneNumber)) {
                    blockCall(phoneNumber)
                }
            }
            else -> { /* No action needed */ }
        }
    }
    
    override fun handleDialpadInput(input: String): Boolean {
        // Comandos especiales para gestión de bloqueo
        when (input) {
            "*BLOCK*" -> {
                showBlockingMenu()
                return true
            }
            "*UNBLOCK*" -> {
                showUnblockingMenu()
                return true
            }
        }
        return false
    }
    
    override fun handleUSSDCode(code: String): Boolean {
        // No procesa códigos USSD en esta extensión
        return false
    }
    
    override fun isEnabled(): Boolean = isEnabled
    
    override fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
        saveSettings()
    }
    
    override fun getSettings(): Map<String, Any> {
        return mapOf(
            "enabled" to isEnabled,
            "blocked_numbers" to blockedNumbers.toList(),
            "blocked_patterns" to blockedPatterns.toList(),
            "whitelisted_numbers" to whitelistedNumbers.toList(),
            "block_unknown_numbers" to blockUnknownNumbers,
            "block_private_numbers" to blockPrivateNumbers,
            "block_international_numbers" to blockInternationalNumbers,
            "blocked_calls_count" to getBlockedCallsCount(),
            "last_blocked_number" to getLastBlockedNumber()
        )
    }
    
    override fun updateSettings(settings: Map<String, Any>) {
        isEnabled = settings["enabled"] as? Boolean ?: false
        
        @Suppress("UNCHECKED_CAST")
        val blockedNumbersList = settings["blocked_numbers"] as? List<String> ?: emptyList()
        blockedNumbers.clear()
        blockedNumbers.addAll(blockedNumbersList)
        
        @Suppress("UNCHECKED_CAST")
        val blockedPatternsList = settings["blocked_patterns"] as? List<String> ?: emptyList()
        blockedPatterns.clear()
        blockedPatterns.addAll(blockedPatternsList)
        
        @Suppress("UNCHECKED_CAST")
        val whitelistedNumbersList = settings["whitelisted_numbers"] as? List<String> ?: emptyList()
        whitelistedNumbers.clear()
        whitelistedNumbers.addAll(whitelistedNumbersList)
        
        blockUnknownNumbers = settings["block_unknown_numbers"] as? Boolean ?: false
        blockPrivateNumbers = settings["block_private_numbers"] as? Boolean ?: false
        blockInternationalNumbers = settings["block_international_numbers"] as? Boolean ?: false
        
        saveSettings()
    }
    
    /**
     * Determina si una llamada debe ser bloqueada
     */
    private fun shouldBlockCall(phoneNumber: String?): Boolean {
        if (phoneNumber == null) {
            return blockUnknownNumbers
        }
        
        val cleanNumber = cleanPhoneNumber(phoneNumber)
        
        // Verificar lista blanca primero
        if (isWhitelisted(cleanNumber)) {
            return false
        }
        
        // Verificar números privados/ocultos
        if (isPrivateNumber(phoneNumber) && blockPrivateNumbers) {
            return true
        }
        
        // Verificar números internacionales
        if (isInternationalNumber(cleanNumber) && blockInternationalNumbers) {
            return true
        }
        
        // Verificar lista negra exacta
        if (blockedNumbers.contains(cleanNumber)) {
            return true
        }
        
        // Verificar patrones
        if (matchesBlockedPattern(cleanNumber)) {
            return true
        }
        
        // Verificar reglas adicionales
        if (shouldBlockByAdditionalRules(cleanNumber)) {
            return true
        }
        
        return false
    }
    
    /**
     * Bloquea una llamada
     */
    private fun blockCall(phoneNumber: String?) {
        CoroutineScope(Dispatchers.Main).launch {
            // Aquí se integraría con el sistema de llamadas para rechazar la llamada
            // Por ahora, solo registramos el bloqueo
            logBlockedCall(phoneNumber)
            
            // Notificar al usuario si está configurado
            if (shouldNotifyBlocking()) {
                showBlockingNotification(phoneNumber)
            }
        }
    }
    
    /**
     * Limpia un número de teléfono para comparación
     */
    private fun cleanPhoneNumber(phoneNumber: String): String {
        return phoneNumber.replace(Regex("[^0-9+]"), "")
    }
    
    /**
     * Verifica si un número está en la lista blanca
     */
    private fun isWhitelisted(phoneNumber: String): Boolean {
        return whitelistedNumbers.any { whitelisted ->
            phoneNumber.endsWith(whitelisted) || whitelisted.endsWith(phoneNumber)
        }
    }
    
    /**
     * Verifica si es un número privado/oculto
     */
    private fun isPrivateNumber(phoneNumber: String): Boolean {
        return phoneNumber.isEmpty() || 
               phoneNumber == "Unknown" || 
               phoneNumber == "Private" ||
               phoneNumber == "Blocked"
    }
    
    /**
     * Verifica si es un número internacional
     */
    private fun isInternationalNumber(phoneNumber: String): Boolean {
        return phoneNumber.startsWith("+") && !phoneNumber.startsWith("+34") // Ejemplo para España
    }
    
    /**
     * Verifica si el número coincide con algún patrón bloqueado
     */
    private fun matchesBlockedPattern(phoneNumber: String): Boolean {
        return blockedPatterns.any { pattern ->
            try {
                Pattern.matches(pattern, phoneNumber)
            } catch (e: Exception) {
                // Si el patrón es inválido, usar coincidencia simple
                phoneNumber.contains(pattern)
            }
        }
    }
    
    /**
     * Verifica reglas adicionales de bloqueo
     */
    private fun shouldBlockByAdditionalRules(phoneNumber: String): Boolean {
        // Bloquear números que parecen spam (muchos dígitos repetidos)
        if (hasRepeatedDigits(phoneNumber, 4)) {
            return true
        }
        
        // Bloquear números de telemarketing conocidos
        if (isTelemarketingNumber(phoneNumber)) {
            return true
        }
        
        return false
    }
    
    /**
     * Verifica si un número tiene dígitos repetidos
     */
    private fun hasRepeatedDigits(phoneNumber: String, minRepeats: Int): Boolean {
        var currentChar = ' '
        var count = 0
        
        for (char in phoneNumber) {
            if (char.isDigit()) {
                if (char == currentChar) {
                    count++
                    if (count >= minRepeats) {
                        return true
                    }
                } else {
                    currentChar = char
                    count = 1
                }
            }
        }
        
        return false
    }
    
    /**
     * Verifica si es un número de telemarketing conocido
     */
    private fun isTelemarketingNumber(phoneNumber: String): Boolean {
        val telemarketingPrefixes = listOf(
            "900", "901", "902", "905", // Números premium
            "800", "803", "806", "807"  // Números gratuitos usados para spam
        )
        
        return telemarketingPrefixes.any { prefix ->
            phoneNumber.contains(prefix)
        }
    }
    
    /**
     * Registra una llamada bloqueada
     */
    private fun logBlockedCall(phoneNumber: String?) {
        val timestamp = System.currentTimeMillis()
        val count = getBlockedCallsCount() + 1
        
        preferences?.edit()?.apply {
            putInt("blocked_calls_count", count)
            putString("last_blocked_number", phoneNumber ?: "Unknown")
            putLong("last_blocked_timestamp", timestamp)
            apply()
        }
    }
    
    /**
     * Agrega un número a la lista negra
     */
    fun addBlockedNumber(phoneNumber: String) {
        val cleanNumber = cleanPhoneNumber(phoneNumber)
        blockedNumbers.add(cleanNumber)
        saveSettings()
    }
    
    /**
     * Remueve un número de la lista negra
     */
    fun removeBlockedNumber(phoneNumber: String) {
        val cleanNumber = cleanPhoneNumber(phoneNumber)
        blockedNumbers.remove(cleanNumber)
        saveSettings()
    }
    
    /**
     * Agrega un número a la lista blanca
     */
    fun addWhitelistedNumber(phoneNumber: String) {
        val cleanNumber = cleanPhoneNumber(phoneNumber)
        whitelistedNumbers.add(cleanNumber)
        saveSettings()
    }
    
    /**
     * Agrega un patrón de bloqueo
     */
    fun addBlockedPattern(pattern: String) {
        blockedPatterns.add(pattern)
        saveSettings()
    }
    
    /**
     * Obtiene la lista de números bloqueados
     */
    fun getBlockedNumbers(): Set<String> = blockedNumbers.toSet()
    
    /**
     * Obtiene la lista de números en lista blanca
     */
    fun getWhitelistedNumbers(): Set<String> = whitelistedNumbers.toSet()
    
    /**
     * Obtiene el conteo de llamadas bloqueadas
     */
    private fun getBlockedCallsCount(): Int {
        return preferences?.getInt("blocked_calls_count", 0) ?: 0
    }
    
    /**
     * Obtiene el último número bloqueado
     */
    private fun getLastBlockedNumber(): String {
        return preferences?.getString("last_blocked_number", "") ?: ""
    }
    
    private fun shouldNotifyBlocking(): Boolean = true
    
    private fun showBlockingNotification(phoneNumber: String?) {
        // Implementar notificación de bloqueo
    }
    
    private fun showBlockingMenu() {
        // Implementar menú de bloqueo
    }
    
    private fun showUnblockingMenu() {
        // Implementar menú de desbloqueo
    }
    
    /**
     * Carga la configuración desde SharedPreferences
     */
    private fun loadSettings() {
        preferences?.let { prefs ->
            isEnabled = prefs.getBoolean("enabled", false)
            blockUnknownNumbers = prefs.getBoolean("block_unknown_numbers", false)
            blockPrivateNumbers = prefs.getBoolean("block_private_numbers", false)
            blockInternationalNumbers = prefs.getBoolean("block_international_numbers", false)
            
            // Cargar números bloqueados
            val blockedNumbersString = prefs.getString("blocked_numbers", "")
            if (!blockedNumbersString.isNullOrEmpty()) {
                blockedNumbers.addAll(blockedNumbersString.split(","))
            }
            
            // Cargar patrones bloqueados
            val blockedPatternsString = prefs.getString("blocked_patterns", "")
            if (!blockedPatternsString.isNullOrEmpty()) {
                blockedPatterns.addAll(blockedPatternsString.split(","))
            }
            
            // Cargar números en lista blanca
            val whitelistedNumbersString = prefs.getString("whitelisted_numbers", "")
            if (!whitelistedNumbersString.isNullOrEmpty()) {
                whitelistedNumbers.addAll(whitelistedNumbersString.split(","))
            }
        }
    }
    
    /**
     * Guarda la configuración en SharedPreferences
     */
    private fun saveSettings() {
        preferences?.edit()?.apply {
            putBoolean("enabled", isEnabled)
            putBoolean("block_unknown_numbers", blockUnknownNumbers)
            putBoolean("block_private_numbers", blockPrivateNumbers)
            putBoolean("block_international_numbers", blockInternationalNumbers)
            putString("blocked_numbers", blockedNumbers.joinToString(","))
            putString("blocked_patterns", blockedPatterns.joinToString(","))
            putString("whitelisted_numbers", whitelistedNumbers.joinToString(","))
            apply()
        }
    }
    
    override fun cleanup() {
        context = null
        preferences = null
    }
}