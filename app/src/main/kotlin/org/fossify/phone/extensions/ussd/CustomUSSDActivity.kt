package org.fossify.phone.extensions.ussd

import android.os.Bundle
import android.telephony.TelephonyManager
import android.view.View
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.phone.R
import org.fossify.phone.databinding.ActivityCustomUssdBinding

/**
 * Actividad personalizada para mostrar información cuando se interceptan códigos USSD
 */
class CustomUSSDActivity : BaseSimpleActivity() {
    
    private val binding by viewBinding(ActivityCustomUssdBinding::inflate)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        setupUI()
        loadUSSDInfo()
    }
    
    private fun setupUI() {
        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.customUssdCoordinator,
            nestedScrollView = binding.customUssdNestedScrollview,
            useTransparentNavigation = true,
            useTopSearchMenu = false
        )
        
        setupMaterialScrollListener(binding.customUssdNestedScrollview, binding.customUssdToolbar)
        
        binding.customUssdToolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun loadUSSDInfo() {
        val ussdCode = intent.getStringExtra("ussd_code") ?: ""
        val screenType = intent.getStringExtra("screen_type") ?: "generic"
        val title = intent.getStringExtra("title") ?: "Información USSD"
        val customMessage = intent.getStringExtra("custom_message") ?: ""
        
        // Configurar título
        binding.customUssdToolbar.title = title
        
        // Configurar información según el tipo de pantalla
        when (screenType) {
            "imei" -> setupIMEIInfo(ussdCode, customMessage)
            "phone_info" -> setupPhoneInfo(ussdCode, customMessage)
            "service_monitor" -> setupServiceMonitor(ussdCode, customMessage)
            else -> setupGenericInfo(ussdCode, customMessage)
        }
    }
    
    private fun setupIMEIInfo(ussdCode: String, customMessage: String) {
        binding.apply {
            customUssdCode.text = "Código interceptado: $ussdCode"
            customUssdMessage.text = customMessage
            
            // Mostrar información real del IMEI si está disponible
            try {
                val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
                val imei = if (hasPermission(android.Manifest.permission.READ_PHONE_STATE)) {
                    telephonyManager.deviceId ?: "No disponible"
                } else {
                    "Permisos requeridos"
                }
                
                customUssdDetails.text = """
                    📱 INFORMACIÓN DEL DISPOSITIVO
                    
                    IMEI: $imei
                    Modelo: ${android.os.Build.MODEL}
                    Fabricante: ${android.os.Build.MANUFACTURER}
                    Versión Android: ${android.os.Build.VERSION.RELEASE}
                    API Level: ${android.os.Build.VERSION.SDK_INT}
                    
                    ⚠️ Esta información ha sido interceptada por la extensión personalizada.
                    La pantalla estándar del sistema ha sido reemplazada.
                """.trimIndent()
                
            } catch (e: Exception) {
                customUssdDetails.text = "Error al obtener información del dispositivo: ${e.message}"
            }
            
            customUssdDetails.visibility = View.VISIBLE
        }
    }
    
    private fun setupPhoneInfo(ussdCode: String, customMessage: String) {
        binding.apply {
            customUssdCode.text = "Código interceptado: $ussdCode"
            customUssdMessage.text = customMessage
            
            customUssdDetails.text = """
                📞 INFORMACIÓN DEL TELÉFONO
                
                Este código normalmente abriría el menú de pruebas del teléfono.
                
                Funcionalidades disponibles:
                • Información de la red
                • Estadísticas de uso
                • Configuración de radio
                • Información de la batería
                
                🔧 La extensión ha interceptado este código para mostrar
                información personalizada en lugar del menú estándar.
            """.trimIndent()
            
            customUssdDetails.visibility = View.VISIBLE
        }
    }
    
    private fun setupServiceMonitor(ussdCode: String, customMessage: String) {
        binding.apply {
            customUssdCode.text = "Código interceptado: $ussdCode"
            customUssdMessage.text = customMessage
            
            customUssdDetails.text = """
                🔍 MONITOR DE SERVICIOS
                
                Este código normalmente abriría el monitor de servicios de Google.
                
                Servicios monitoreados:
                • Google Talk Service Monitor
                • Estado de conexión
                • Logs de servicio
                • Información de red
                
                📊 La extensión proporciona un monitor personalizado
                con información adicional y controles avanzados.
            """.trimIndent()
            
            customUssdDetails.visibility = View.VISIBLE
        }
    }
    
    private fun setupGenericInfo(ussdCode: String, customMessage: String) {
        binding.apply {
            customUssdCode.text = "Código interceptado: $ussdCode"
            customUssdMessage.text = customMessage
            
            customUssdDetails.text = """
                ℹ️ CÓDIGO USSD INTERCEPTADO
                
                El código $ussdCode ha sido interceptado por el sistema de extensiones.
                
                Esto significa que:
                • La respuesta estándar del sistema ha sido bloqueada
                • Se muestra esta pantalla personalizada en su lugar
                • Puedes configurar qué información mostrar
                
                🛠️ Puedes personalizar esta pantalla desde la configuración
                de extensiones en los ajustes de la aplicación.
            """.trimIndent()
            
            customUssdDetails.visibility = View.VISIBLE
        }
    }
}