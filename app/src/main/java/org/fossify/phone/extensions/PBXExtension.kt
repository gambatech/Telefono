package org.fossify.phone.extensions

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.*
import org.fossify.phone.extensions.base.BaseExtension
import org.fossify.phone.extensions.base.CallState
import java.io.File
import java.util.*
import kotlin.collections.HashMap

/**
 * PBX Extension - Sistema de Centralita Empresarial
 * 
 * Funcionalidades:
 * - Menús de voz multinivel
 * - Enrutamiento de llamadas por departamentos
 * - Cola de espera con música
 * - Horarios de atención
 * - Grabación de llamadas
 * - Estadísticas en tiempo real
 */
class PBXExtension : BaseExtension() {
    
    override val name = "Sistema PBX"
    override val description = "Centralita empresarial con enrutamiento avanzado"
    override val version = "1.0.0"
    
    private var textToSpeech: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioManager: AudioManager? = null
    private var telephonyManager: TelephonyManager? = null
    
    private val extensionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentCall: PBXCall? = null
    private val callQueue = mutableListOf<PBXCall>()
    
    // Configuración PBX
    private lateinit var pbxConfig: PBXConfiguration
    
    override fun initialize(context: Context) {
        super.initialize(context)
        
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        
        // Inicializar configuración PBX
        pbxConfig = PBXConfiguration(context)
        
        initializeTextToSpeech(context)
        loadPBXConfiguration()
        
        Log.d(TAG, "PBX Extension initialized")
    }
    
    override fun onIncomingCall(phoneNumber: String): Boolean {
        if (!isEnabled) return false
        
        Log.d(TAG, "PBX handling incoming call from: $phoneNumber")
        
        // Verificar horarios de atención
        if (!pbxConfig.isBusinessHours()) {
            handleAfterHoursCall(phoneNumber)
            return true
        }
        
        // Crear nueva llamada PBX
        val pbxCall = PBXCall(
            phoneNumber = phoneNumber,
            timestamp = System.currentTimeMillis(),
            status = CallStatus.INCOMING
        )
        
        currentCall = pbxCall
        
        // Iniciar flujo de centralita
        extensionScope.launch {
            startPBXFlow(pbxCall)
        }
        
        return true
    }
    
    override fun onCallStateChanged(state: CallState, phoneNumber: String?) {
        when (state) {
            CallState.ACTIVE -> {
                currentCall?.status = CallStatus.ACTIVE
                startCallRecording()
            }
            CallState.ENDED -> {
                currentCall?.let { call ->
                    call.status = CallStatus.ENDED
                    call.duration = System.currentTimeMillis() - call.timestamp
                    saveCallRecord(call)
                }
                stopCallRecording()
                currentCall = null
            }
            else -> {}
        }
    }
    
    private suspend fun startPBXFlow(call: PBXCall) {
        try {
            // Reproducir mensaje de bienvenida
            playWelcomeMessage()
            
            // Mostrar menú principal
            showMainMenu(call)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in PBX flow", e)
            handlePBXError(call)
        }
    }
    
    private suspend fun playWelcomeMessage() {
        val welcomeMessage = pbxConfig.getWelcomeMessage()
        speakText(welcomeMessage)
    }
    
    private suspend fun showMainMenu(call: PBXCall) {
        val menuOptions = pbxConfig.getMainMenuOptions()
        val menuText = buildMenuText(menuOptions)
        
        speakText(menuText)
        
        // Esperar entrada DTMF
        waitForDTMFInput(call) { input ->
            handleMenuSelection(call, input, menuOptions)
        }
    }
    
    private fun handleMenuSelection(call: PBXCall, input: String, options: List<MenuOption>) {
        val selectedOption = options.find { it.key == input }
        
        if (selectedOption != null) {
            when (selectedOption.action) {
                MenuAction.TRANSFER_TO_DEPARTMENT -> {
                    transferToDepartment(call, selectedOption.target)
                }
                MenuAction.PLAY_INFORMATION -> {
                    playInformation(selectedOption.target)
                }
                MenuAction.QUEUE_CALL -> {
                    addToQueue(call, selectedOption.target)
                }
                MenuAction.SUBMENU -> {
                    showSubMenu(call, selectedOption.target)
                }
                MenuAction.RECORD_MESSAGE -> {
                    startMessageRecording(call)
                }
            }
        } else {
            // Opción inválida
            extensionScope.launch {
                speakText("Opción inválida. Por favor, intente nuevamente.")
                delay(1000)
                showMainMenu(call)
            }
        }
    }
    
    private fun transferToDepartment(call: PBXCall, department: String) {
        val departmentConfig = pbxConfig.getDepartmentConfig(department)
        
        if (departmentConfig.isAvailable()) {
            // Transferir llamada
            call.transferredTo = department
            call.status = CallStatus.TRANSFERRED
            
            extensionScope.launch {
                speakText("Transfiriendo su llamada al departamento de ${departmentConfig.name}. Por favor espere.")
                // Aquí se implementaría la transferencia real
                simulateTransfer(call, departmentConfig)
            }
        } else {
            // Departamento no disponible
            addToQueue(call, department)
        }
    }
    
    private fun addToQueue(call: PBXCall, department: String) {
        call.queuedFor = department
        call.status = CallStatus.QUEUED
        callQueue.add(call)
        
        val position = callQueue.size
        
        extensionScope.launch {
            speakText("Todos nuestros agentes están ocupados. Su llamada es importante para nosotros. Usted es el número $position en la cola.")
            startHoldMusic()
        }
    }
    
    private suspend fun startHoldMusic() {
        try {
            val holdMusicFile = pbxConfig.getHoldMusicFile()
            if (holdMusicFile.exists()) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(holdMusicFile.absolutePath)
                    isLooping = true
                    prepare()
                    start()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing hold music", e)
        }
    }
    
    private fun handleAfterHoursCall(phoneNumber: String) {
        extensionScope.launch {
            val afterHoursMessage = pbxConfig.getAfterHoursMessage()
            speakText(afterHoursMessage)
            
            // Ofrecer opciones fuera de horario
            speakText("Presione 1 para dejar un mensaje, presione 2 para conocer nuestros horarios de atención.")
            
            waitForDTMFInput(null) { input ->
                when (input) {
                    "1" -> startMessageRecording(null)
                    "2" -> playBusinessHours()
                    else -> speakText("Gracias por llamar. Que tenga un buen día.")
                }
            }
        }
    }
    
    private fun startMessageRecording(call: PBXCall?) {
        extensionScope.launch {
            speakText("Por favor, deje su mensaje después del tono. Presione cualquier tecla cuando termine.")
            
            delay(1000) // Tono
            
            try {
                val recordingFile = createRecordingFile()
                mediaRecorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    setOutputFile(recordingFile.absolutePath)
                    prepare()
                    start()
                }
                
                // Esperar que termine la grabación
                waitForDTMFInput(call) { _ ->
                    stopMessageRecording(recordingFile)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting message recording", e)
                speakText("Lo sentimos, no pudimos grabar su mensaje. Por favor, intente más tarde.")
            }
        }
    }
    
    private fun stopMessageRecording(file: File) {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            extensionScope.launch {
                speakText("Su mensaje ha sido grabado. Gracias por llamar.")
                saveVoiceMessage(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }
    
    private fun waitForDTMFInput(call: PBXCall?, callback: (String) -> Unit) {
        // Simular espera de entrada DTMF
        // En implementación real, esto escucharía eventos DTMF del sistema telefónico
        extensionScope.launch {
            delay(5000) // Timeout de 5 segundos
            // Por ahora, simular entrada aleatoria para demo
            val simulatedInput = listOf("1", "2", "3", "0").random()
            callback(simulatedInput)
        }
    }
    
    private suspend fun speakText(text: String) {
        return suspendCancellableCoroutine { continuation ->
            textToSpeech?.let { tts ->
                val utteranceId = UUID.randomUUID().toString()
                
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        continuation.resume(Unit) {}
                    }
                    override fun onError(utteranceId: String?) {
                        continuation.resume(Unit) {}
                    }
                })
                
                val params = HashMap<String, String>()
                params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
                
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, params)
            } ?: continuation.resume(Unit) {}
        }
    }
    
    private fun initializeTextToSpeech(context: Context) {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
                Log.d(TAG, "TextToSpeech initialized successfully")
            } else {
                Log.e(TAG, "TextToSpeech initialization failed")
            }
        }
    }
    
    private fun loadPBXConfiguration() {
        // Cargar configuración desde SharedPreferences
        Log.d(TAG, "PBX configuration loaded")
    }
    
    private fun buildMenuText(options: List<MenuOption>): String {
        val builder = StringBuilder()
        options.forEach { option ->
            builder.append("Presione ${option.key} para ${option.description}. ")
        }
        return builder.toString()
    }
    
    private fun createRecordingFile(): File {
        val recordingsDir = File(context?.filesDir, "pbx_recordings")
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }
        return File(recordingsDir, "message_${System.currentTimeMillis()}.3gp")
    }
    
    private fun saveCallRecord(call: PBXCall) {
        Log.d(TAG, "Call record saved: ${call.phoneNumber}")
    }
    
    private fun saveVoiceMessage(file: File) {
        Log.d(TAG, "Voice message saved: ${file.name}")
    }
    
    private fun startCallRecording() {
        if (pbxConfig.isCallRecordingEnabled()) {
            Log.d(TAG, "Call recording started")
        }
    }
    
    private fun stopCallRecording() {
        Log.d(TAG, "Call recording stopped")
    }
    
    private fun simulateTransfer(call: PBXCall, department: DepartmentConfig) {
        Log.d(TAG, "Call transferred to ${department.name}")
    }
    
    private fun showSubMenu(call: PBXCall, submenuId: String) {
        Log.d(TAG, "Showing submenu: $submenuId")
    }
    
    private fun playInformation(infoId: String) {
        Log.d(TAG, "Playing information: $infoId")
    }
    
    private fun playBusinessHours() {
        extensionScope.launch {
            val hours = pbxConfig.getBusinessHoursText()
            speakText(hours)
        }
    }
    
    private fun handlePBXError(call: PBXCall) {
        extensionScope.launch {
            speakText("Lo sentimos, ha ocurrido un error. Su llamada será transferida a un operador.")
        }
    }
    
    override fun cleanup() {
        extensionScope.cancel()
        textToSpeech?.shutdown()
        mediaPlayer?.release()
        mediaRecorder?.release()
        super.cleanup()
    }
    
    companion object {
        private const val TAG = "PBXExtension"
    }
}

// Clases de datos para el sistema PBX
data class PBXCall(
    val phoneNumber: String,
    val timestamp: Long,
    var status: CallStatus,
    var transferredTo: String? = null,
    var queuedFor: String? = null,
    var duration: Long = 0
)

enum class CallStatus {
    INCOMING, ACTIVE, QUEUED, TRANSFERRED, ENDED
}

data class MenuOption(
    val key: String,
    val description: String,
    val action: MenuAction,
    val target: String
)

enum class MenuAction {
    TRANSFER_TO_DEPARTMENT,
    PLAY_INFORMATION,
    QUEUE_CALL,
    SUBMENU,
    RECORD_MESSAGE
}

data class DepartmentConfig(
    val id: String,
    val name: String,
    val extensions: List<String>,
    val available: Boolean,
    val businessHours: BusinessHours
) {
    fun isAvailable(): Boolean = available && businessHours.isCurrentlyOpen()
}

data class BusinessHours(
    val openTime: String,
    val closeTime: String,
    val workDays: List<Int>
) {
    fun isCurrentlyOpen(): Boolean {
        return true // Simplificado para demo
    }
}

// Configuración del sistema PBX
class PBXConfiguration(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pbx_config", Context.MODE_PRIVATE)
    
    fun getWelcomeMessage(): String {
        return prefs.getString("welcome_message", 
            "Bienvenido a nuestra empresa. Su llamada es importante para nosotros.") ?: ""
    }
    
    fun getMainMenuOptions(): List<MenuOption> {
        return listOf(
            MenuOption("1", "ventas", MenuAction.TRANSFER_TO_DEPARTMENT, "sales"),
            MenuOption("2", "soporte técnico", MenuAction.TRANSFER_TO_DEPARTMENT, "support"),
            MenuOption("3", "información general", MenuAction.PLAY_INFORMATION, "general_info"),
            MenuOption("0", "hablar con un operador", MenuAction.QUEUE_CALL, "operator")
        )
    }
    
    fun isBusinessHours(): Boolean {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        // Lunes a Viernes, 9 AM a 6 PM
        return dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY && hour in 9..17
    }
    
    fun getAfterHoursMessage(): String {
        return "Gracias por llamar. Nuestro horario de atención es de lunes a viernes de 9 AM a 6 PM."
    }
    
    fun getBusinessHoursText(): String {
        return "Nuestro horario de atención es de lunes a viernes de 9 de la mañana a 6 de la tarde."
    }
    
    fun getDepartmentConfig(department: String): DepartmentConfig {
        return when (department) {
            "sales" -> DepartmentConfig(
                "sales", "Ventas", listOf("101", "102"), true,
                BusinessHours("09:00", "18:00", listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY))
            )
            "support" -> DepartmentConfig(
                "support", "Soporte Técnico", listOf("201", "202"), true,
                BusinessHours("08:00", "20:00", listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY))
            )
            else -> DepartmentConfig(
                "general", "General", listOf("100"), true,
                BusinessHours("09:00", "18:00", listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY))
            )
        }
    }
    
    fun getHoldMusicFile(): File {
        return File(context.filesDir, "hold_music.mp3")
    }
    
    fun isCallRecordingEnabled(): Boolean {
        return prefs.getBoolean("call_recording_enabled", false)
    }
}