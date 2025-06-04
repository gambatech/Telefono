package org.fossify.phone.extensions.ussd

import android.content.Context
import android.content.Intent
import android.util.Log
import org.fossify.phone.extensions.core.CallState
import org.fossify.phone.extensions.core.PhoneExtension

/**
 * Extensión para interceptar códigos USSD específicos
 * Permite mostrar pantallas personalizadas en lugar de las respuestas estándar del sistema
 */
class USSDInterceptorExtension : PhoneExtension {
    
    companion object {
        private const val TAG = "USSDInterceptor"
        const val EXTENSION_ID = "ussd_interceptor"
    }
    
    override val id: String = EXTENSION_ID
    override val name: String = "Interceptor de Códigos USSD"
    override val version: String = "1.0.0"
    override val description: String = "Intercepta códigos USSD específicos como *#06# para mostrar información personalizada"
    override val isEnabled: Boolean = true
    override val priority: Int = 100 // Alta prioridad para interceptar antes que otros
    
    private var context: Context? = null
    private val interceptedCodes = mutableSetOf<String>()
    
    init {
        // Códigos USSD que queremos interceptar
        interceptedCodes.add("*#06#") // IMEI
        interceptedCodes.add("*#*#4636#*#*") // Información del teléfono
        interceptedCodes.add("*#*#8255#*#*") // Monitor de servicios Google
    }
    
    override fun initialize(context: Context) {
        this.context = context
        Log.d(TAG, "USSD Interceptor Extension initialized")
        Log.d(TAG, "Intercepting codes: ${interceptedCodes.joinToString(", ")}")
    }
    
    override fun onCallStateChanged(state: CallState, phoneNumber: String?) {
        // No necesitamos manejar cambios de estado para esta extensión
    }
    
    override fun onDialpadInput(input: String): Boolean {
        // Verificar si el input coincide con algún código USSD interceptado
        return interceptedCodes.any { code ->
            if (input == code) {
                handleInterceptedCode(code)
                true
            } else {
                false
            }
        }
    }
    
    override fun onUSSDCode(code: String): Boolean {
        return if (interceptedCodes.contains(code)) {
            handleInterceptedCode(code)
            true
        } else {
            false
        }
    }
    
    /**
     * Maneja un código USSD interceptado
     */
    private fun handleInterceptedCode(code: String) {
        Log.d(TAG, "Intercepting USSD code: $code")
        
        context?.let { ctx ->
            when (code) {
                "*#06#" -> showCustomIMEIScreen(ctx)
                "*#*#4636#*#*" -> showCustomPhoneInfoScreen(ctx)
                "*#*#8255#*#*" -> showCustomServiceMonitorScreen(ctx)
                else -> showGenericInterceptedScreen(ctx, code)
            }
        }
    }
    
    /**
     * Muestra pantalla personalizada para IMEI
     */
    private fun showCustomIMEIScreen(context: Context) {
        val intent = Intent(context, CustomUSSDActivity::class.java).apply {
            putExtra("ussd_code", "*#06#")
            putExtra("screen_type", "imei")
            putExtra("title", "Información del Dispositivo")
            putExtra("custom_message", "Esta es una pantalla personalizada para mostrar información del dispositivo.")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    /**
     * Muestra pantalla personalizada para información del teléfono
     */
    private fun showCustomPhoneInfoScreen(context: Context) {
        val intent = Intent(context, CustomUSSDActivity::class.java).apply {
            putExtra("ussd_code", "*#*#4636#*#*")
            putExtra("screen_type", "phone_info")
            putExtra("title", "Información del Teléfono")
            putExtra("custom_message", "Información detallada del teléfono interceptada por la extensión.")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    /**
     * Muestra pantalla personalizada para monitor de servicios
     */
    private fun showCustomServiceMonitorScreen(context: Context) {
        val intent = Intent(context, CustomUSSDActivity::class.java).apply {
            putExtra("ussd_code", "*#*#8255#*#*")
            putExtra("screen_type", "service_monitor")
            putExtra("title", "Monitor de Servicios")
            putExtra("custom_message", "Monitor personalizado de servicios del sistema.")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    /**
     * Muestra pantalla genérica para códigos interceptados
     */
    private fun showGenericInterceptedScreen(context: Context, code: String) {
        val intent = Intent(context, CustomUSSDActivity::class.java).apply {
            putExtra("ussd_code", code)
            putExtra("screen_type", "generic")
            putExtra("title", "Código Interceptado")
            putExtra("custom_message", "El código $code ha sido interceptado por la extensión USSD.")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    /**
     * Añade un nuevo código para interceptar
     */
    fun addInterceptedCode(code: String) {
        interceptedCodes.add(code)
        Log.d(TAG, "Added intercepted code: $code")
    }
    
    /**
     * Remueve un código de la lista de interceptados
     */
    fun removeInterceptedCode(code: String) {
        interceptedCodes.remove(code)
        Log.d(TAG, "Removed intercepted code: $code")
    }
    
    /**
     * Obtiene la lista de códigos interceptados
     */
    fun getInterceptedCodes(): Set<String> {
        return interceptedCodes.toSet()
    }
    
    override fun cleanup() {
        context = null
        Log.d(TAG, "USSD Interceptor Extension cleaned up")
    }
}