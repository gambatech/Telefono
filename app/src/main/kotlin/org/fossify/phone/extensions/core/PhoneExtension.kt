package org.fossify.phone.extensions.core

import android.content.Context

/**
 * Interfaz base para todas las extensiones del sistema telefónico
 */
interface PhoneExtension {
    /** Identificador único de la extensión */
    val id: String
    
    /** Nombre legible de la extensión */
    val name: String
    
    /** Versión de la extensión */
    val version: String
    
    /** Descripción de la funcionalidad */
    val description: String
    
    /** Estado de activación */
    val isEnabled: Boolean
    
    /** Prioridad de ejecución (mayor número = mayor prioridad) */
    val priority: Int get() = 0
    
    /**
     * Inicializa la extensión
     * @param context Contexto de la aplicación
     */
    fun initialize(context: Context)
    
    /**
     * Notifica cambios en el estado de llamada
     * @param state Nuevo estado de la llamada
     * @param phoneNumber Número de teléfono involucrado (puede ser null)
     */
    fun onCallStateChanged(state: CallState, phoneNumber: String?)
    
    /**
     * Procesa entrada del dialpad
     * @param input Entrada del usuario
     * @return true si la extensión consume la entrada, false para continuar procesamiento
     */
    fun onDialpadInput(input: String): Boolean = false
    
    /**
     * Intercepta códigos USSD
     * @param code Código USSD ingresado
     * @return true si la extensión intercepta el código, false para procesamiento normal
     */
    fun onUSSDCode(code: String): Boolean = false
    
    /**
     * Procesa audio durante llamadas
     * @param audioData Datos de audio
     * @param isIncoming true si es audio entrante, false si es saliente
     */
    fun onAudioData(audioData: ByteArray, isIncoming: Boolean) {}
    
    /**
     * Limpia recursos de la extensión
     */
    fun cleanup()
}

/**
 * Estados posibles de una llamada
 */
enum class CallState {
    IDLE,
    RINGING,
    OFFHOOK,
    INCOMING,
    OUTGOING,
    ACTIVE,
    HOLD,
    DISCONNECTED
}

/**
 * Configuración base para extensiones
 */
data class ExtensionConfig(
    val id: String,
    val isEnabled: Boolean = false,
    val settings: Map<String, Any> = emptyMap()
)

/**
 * Resultado de procesamiento de extensión
 */
sealed class ExtensionResult {
    object Continue : ExtensionResult()
    object Consumed : ExtensionResult()
    data class Error(val message: String, val exception: Throwable? = null) : ExtensionResult()
}