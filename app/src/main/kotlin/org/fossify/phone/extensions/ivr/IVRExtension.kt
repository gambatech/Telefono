package org.fossify.phone.extensions.ivr

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fossify.phone.extensions.core.PhoneExtension
import org.fossify.phone.extensions.core.CallState
import java.io.File
import java.util.*

/**
 * Extensión IVR (Interactive Voice Response) para contestador automático
 * Permite crear flujos interactivos de voz con opciones de menú
 */
class IVRExtension : PhoneExtension {
    
    private var context: Context? = null
    private var textToSpeech: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioManager: AudioManager? = null
    private var isEnabled = false
    private var currentFlow: IVRFlow? = null
    private var currentStep = 0
    
    override val name = "IVR Answering Machine"
    override val version = "1.0.0"
    override val description = "Sistema de contestador automático con menús interactivos de voz"
    
    override fun initialize(context: Context) {
        this.context = context
        this.audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Inicializar Text-to-Speech
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
            }
        }
    }
    
    override fun onCallStateChanged(state: CallState, phoneNumber: String?) {
        when (state) {
            CallState.INCOMING -> {
                if (isEnabled && shouldAnswerAutomatically(phoneNumber)) {
                    handleIncomingCall(phoneNumber)
                }
            }
            CallState.ANSWERED -> {
                if (isEnabled && currentFlow != null) {
                    startIVRFlow()
                }
            }
            CallState.ENDED -> {
                stopIVRFlow()
            }
            else -> { /* No action needed */ }
        }
    }
    
    override fun handleDialpadInput(input: String): Boolean {
        // No procesa entrada del dialpad en esta extensión
        return false
    }
    
    override fun handleUSSDCode(code: String): Boolean {
        // No procesa códigos USSD en esta extensión
        return false
    }
    
    override fun isEnabled(): Boolean = isEnabled
    
    override fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
    }
    
    override fun getSettings(): Map<String, Any> {
        return mapOf(
            "enabled" to isEnabled,
            "auto_answer_delay" to getAutoAnswerDelay(),
            "default_greeting" to getDefaultGreeting(),
            "flows" to getIVRFlows()
        )
    }
    
    override fun updateSettings(settings: Map<String, Any>) {
        isEnabled = settings["enabled"] as? Boolean ?: false
        // Actualizar otras configuraciones según sea necesario
    }
    
    /**
     * Determina si debe responder automáticamente una llamada
     */
    private fun shouldAnswerAutomatically(phoneNumber: String?): Boolean {
        // Lógica para determinar si responder automáticamente
        // Por ejemplo, basado en lista blanca/negra, horarios, etc.
        return true // Por ahora siempre responde
    }
    
    /**
     * Maneja una llamada entrante
     */
    private fun handleIncomingCall(phoneNumber: String?) {
        CoroutineScope(Dispatchers.Main).launch {
            // Esperar un momento antes de responder
            kotlinx.coroutines.delay(getAutoAnswerDelay())
            
            // Aquí se integraría con el sistema de llamadas para responder
            // Por ahora solo preparamos el flujo IVR
            currentFlow = getDefaultIVRFlow()
            currentStep = 0
        }
    }
    
    /**
     * Inicia el flujo IVR
     */
    private fun startIVRFlow() {
        currentFlow?.let { flow ->
            if (currentStep < flow.steps.size) {
                val step = flow.steps[currentStep]
                playMessage(step.message)
            }
        }
    }
    
    /**
     * Detiene el flujo IVR
     */
    private fun stopIVRFlow() {
        mediaPlayer?.stop()
        mediaRecorder?.stop()
        currentFlow = null
        currentStep = 0
    }
    
    /**
     * Reproduce un mensaje de audio
     */
    private fun playMessage(message: String) {
        textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "IVR_MESSAGE")
    }
    
    /**
     * Procesa entrada DTMF del usuario
     */
    fun processDTMFInput(digit: Char) {
        currentFlow?.let { flow ->
            if (currentStep < flow.steps.size) {
                val step = flow.steps[currentStep]
                val option = step.options.find { it.key == digit }
                
                if (option != null) {
                    when (option.action) {
                        IVRAction.NEXT_STEP -> {
                            currentStep++
                            startIVRFlow()
                        }
                        IVRAction.GOTO_STEP -> {
                            currentStep = option.targetStep
                            startIVRFlow()
                        }
                        IVRAction.END_CALL -> {
                            playMessage(option.message)
                            // Terminar llamada
                        }
                        IVRAction.RECORD_MESSAGE -> {
                            playMessage(option.message)
                            startRecording()
                        }
                        IVRAction.TRANSFER_CALL -> {
                            playMessage(option.message)
                            // Transferir llamada
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Inicia grabación de mensaje
     */
    private fun startRecording() {
        try {
            val recordingFile = File(context?.filesDir, "recording_${System.currentTimeMillis()}.3gp")
            
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(recordingFile.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun getAutoAnswerDelay(): Long = 3000L // 3 segundos
    
    private fun getDefaultGreeting(): String = "Hola, has llamado al contestador automático. Presiona 1 para dejar un mensaje, 2 para hablar con un operador, o 0 para repetir este menú."
    
    private fun getIVRFlows(): List<IVRFlow> = listOf(getDefaultIVRFlow())
    
    private fun getDefaultIVRFlow(): IVRFlow {
        return IVRFlow(
            id = "default",
            name = "Flujo Principal",
            steps = listOf(
                IVRStep(
                    id = 0,
                    message = getDefaultGreeting(),
                    options = listOf(
                        IVROption('1', "Dejar mensaje", IVRAction.RECORD_MESSAGE, "Por favor, deje su mensaje después del tono."),
                        IVROption('2', "Hablar con operador", IVRAction.TRANSFER_CALL, "Transfiriendo su llamada..."),
                        IVROption('0', "Repetir menú", IVRAction.GOTO_STEP, "Repitiendo menú principal...", 0)
                    )
                )
            )
        )
    }
    
    override fun cleanup() {
        textToSpeech?.shutdown()
        mediaPlayer?.release()
        mediaRecorder?.release()
        context = null
    }
}

/**
 * Representa un flujo completo de IVR
 */
data class IVRFlow(
    val id: String,
    val name: String,
    val steps: List<IVRStep>
)

/**
 * Representa un paso individual en el flujo IVR
 */
data class IVRStep(
    val id: Int,
    val message: String,
    val options: List<IVROption>
)

/**
 * Representa una opción disponible en un paso IVR
 */
data class IVROption(
    val key: Char,
    val description: String,
    val action: IVRAction,
    val message: String,
    val targetStep: Int = -1
)

/**
 * Acciones disponibles en el sistema IVR
 */
enum class IVRAction {
    NEXT_STEP,
    GOTO_STEP,
    END_CALL,
    RECORD_MESSAGE,
    TRANSFER_CALL
}