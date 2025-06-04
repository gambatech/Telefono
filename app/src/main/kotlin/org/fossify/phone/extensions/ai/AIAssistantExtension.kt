package org.fossify.phone.extensions.ai

import android.content.Context
import android.media.AudioManager
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.RecognizerIntent
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.phone.extensions.core.PhoneExtension
import org.fossify.phone.extensions.core.CallState
import java.util.*

/**
 * Extensión de Asistente de IA conversacional
 * Permite interactuar con llamantes usando reconocimiento de voz y respuestas inteligentes
 */
class AIAssistantExtension : PhoneExtension {
    
    private var context: Context? = null
    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var audioManager: AudioManager? = null
    private var isEnabled = false
    private var isListening = false
    private var currentConversation = mutableListOf<ConversationMessage>()
    private var assistantPersonality = AssistantPersonality.PROFESSIONAL
    
    override val name = "AI Assistant"
    override val version = "1.0.0"
    override val description = "Asistente de IA conversacional para interactuar con llamantes"
    
    override fun initialize(context: Context) {
        this.context = context
        this.audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Inicializar Text-to-Speech
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
            }
        }
        
        // Inicializar reconocimiento de voz
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(speechRecognitionListener)
        }
    }
    
    override fun onCallStateChanged(state: CallState, phoneNumber: String?) {
        when (state) {
            CallState.INCOMING -> {
                if (isEnabled && shouldActivateAssistant(phoneNumber)) {
                    prepareAssistant(phoneNumber)
                }
            }
            CallState.ANSWERED -> {
                if (isEnabled) {
                    startConversation()
                }
            }
            CallState.ENDED -> {
                endConversation()
            }
            else -> { /* No action needed */ }
        }
    }
    
    override fun handleDialpadInput(input: String): Boolean {
        // El asistente puede responder a comandos específicos del dialpad
        when (input) {
            "*AI*" -> {
                toggleAssistant()
                return true
            }
            "*HELP*" -> {
                provideHelp()
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
    }
    
    override fun getSettings(): Map<String, Any> {
        return mapOf(
            "enabled" to isEnabled,
            "personality" to assistantPersonality.name,
            "auto_activate" to getAutoActivateSettings(),
            "conversation_timeout" to getConversationTimeout(),
            "language" to getAssistantLanguage()
        )
    }
    
    override fun updateSettings(settings: Map<String, Any>) {
        isEnabled = settings["enabled"] as? Boolean ?: false
        val personalityName = settings["personality"] as? String
        assistantPersonality = personalityName?.let { 
            AssistantPersonality.valueOf(it) 
        } ?: AssistantPersonality.PROFESSIONAL
    }
    
    /**
     * Determina si debe activar el asistente para una llamada
     */
    private fun shouldActivateAssistant(phoneNumber: String?): Boolean {
        // Lógica para determinar cuándo activar el asistente
        // Por ejemplo, basado en configuración del usuario, horarios, etc.
        return true // Por ahora siempre se activa si está habilitado
    }
    
    /**
     * Prepara el asistente para una nueva conversación
     */
    private fun prepareAssistant(phoneNumber: String?) {
        currentConversation.clear()
        currentConversation.add(
            ConversationMessage(
                speaker = Speaker.SYSTEM,
                message = "Nueva llamada de: ${phoneNumber ?: "Número desconocido"}",
                timestamp = System.currentTimeMillis()
            )
        )
    }
    
    /**
     * Inicia la conversación con el asistente
     */
    private fun startConversation() {
        val greeting = getGreetingMessage()
        speak(greeting)
        
        currentConversation.add(
            ConversationMessage(
                speaker = Speaker.ASSISTANT,
                message = greeting,
                timestamp = System.currentTimeMillis()
            )
        )
        
        // Comenzar a escuchar después del saludo
        CoroutineScope(Dispatchers.Main).launch {
            kotlinx.coroutines.delay(3000) // Esperar que termine el saludo
            startListening()
        }
    }
    
    /**
     * Termina la conversación
     */
    private fun endConversation() {
        stopListening()
        currentConversation.clear()
    }
    
    /**
     * Inicia el reconocimiento de voz
     */
    private fun startListening() {
        if (!isListening && speechRecognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            isListening = true
            speechRecognizer?.startListening(intent)
        }
    }
    
    /**
     * Detiene el reconocimiento de voz
     */
    private fun stopListening() {
        if (isListening) {
            isListening = false
            speechRecognizer?.stopListening()
        }
    }
    
    /**
     * Reproduce un mensaje de voz
     */
    private fun speak(message: String) {
        textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "AI_RESPONSE")
    }
    
    /**
     * Procesa el texto reconocido del usuario
     */
    private fun processUserInput(userInput: String) {
        currentConversation.add(
            ConversationMessage(
                speaker = Speaker.USER,
                message = userInput,
                timestamp = System.currentTimeMillis()
            )
        )
        
        // Generar respuesta del asistente
        CoroutineScope(Dispatchers.IO).launch {
            val response = generateAIResponse(userInput)
            
            withContext(Dispatchers.Main) {
                currentConversation.add(
                    ConversationMessage(
                        speaker = Speaker.ASSISTANT,
                        message = response,
                        timestamp = System.currentTimeMillis()
                    )
                )
                
                speak(response)
                
                // Continuar escuchando después de responder
                CoroutineScope(Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(2000)
                    startListening()
                }
            }
        }
    }
    
    /**
     * Genera una respuesta del asistente de IA
     */
    private suspend fun generateAIResponse(userInput: String): String {
        // Aquí se integraría con un servicio de IA real (OpenAI, Google AI, etc.)
        // Por ahora, implementamos respuestas básicas
        
        val lowerInput = userInput.lowercase()
        
        return when {
            lowerInput.contains("hola") || lowerInput.contains("buenos") -> {
                getPersonalizedResponse("¡Hola! ¿En qué puedo ayudarte hoy?")
            }
            lowerInput.contains("ayuda") || lowerInput.contains("help") -> {
                getPersonalizedResponse("Por supuesto, estoy aquí para ayudarte. ¿Qué necesitas?")
            }
            lowerInput.contains("información") || lowerInput.contains("info") -> {
                getPersonalizedResponse("Puedo proporcionarte información sobre nuestros servicios. ¿Qué te interesa saber?")
            }
            lowerInput.contains("horario") || lowerInput.contains("hora") -> {
                getPersonalizedResponse("Nuestro horario de atención es de lunes a viernes de 9:00 AM a 6:00 PM.")
            }
            lowerInput.contains("precio") || lowerInput.contains("costo") -> {
                getPersonalizedResponse("Para información sobre precios, te puedo conectar con un especialista. ¿Te parece bien?")
            }
            lowerInput.contains("adiós") || lowerInput.contains("gracias") -> {
                getPersonalizedResponse("¡De nada! Ha sido un placer ayudarte. ¡Que tengas un excelente día!")
            }
            else -> {
                getPersonalizedResponse("Entiendo tu consulta. Déjame conectarte con un especialista que puede ayudarte mejor.")
            }
        }
    }
    
    /**
     * Personaliza la respuesta según la personalidad del asistente
     */
    private fun getPersonalizedResponse(baseResponse: String): String {
        return when (assistantPersonality) {
            AssistantPersonality.PROFESSIONAL -> baseResponse
            AssistantPersonality.FRIENDLY -> "😊 $baseResponse"
            AssistantPersonality.FORMAL -> "Estimado cliente, $baseResponse"
            AssistantPersonality.CASUAL -> "¡Hey! $baseResponse"
        }
    }
    
    private fun getGreetingMessage(): String {
        return when (assistantPersonality) {
            AssistantPersonality.PROFESSIONAL -> "Hola, soy tu asistente virtual. ¿En qué puedo ayudarte?"
            AssistantPersonality.FRIENDLY -> "¡Hola! Soy tu asistente virtual amigable. ¡Estoy aquí para ayudarte!"
            AssistantPersonality.FORMAL -> "Buenos días. Soy el asistente virtual. ¿Cómo puedo asistirle hoy?"
            AssistantPersonality.CASUAL -> "¡Hey! Soy tu asistente virtual. ¿Qué necesitas?"
        }
    }
    
    private fun toggleAssistant() {
        isEnabled = !isEnabled
        val message = if (isEnabled) "Asistente activado" else "Asistente desactivado"
        speak(message)
    }
    
    private fun provideHelp() {
        speak("Soy tu asistente de IA. Puedo ayudarte con información, responder preguntas y conectarte con especialistas. ¿En qué puedo ayudarte?")
    }
    
    private fun getAutoActivateSettings(): Boolean = true
    private fun getConversationTimeout(): Long = 30000L // 30 segundos
    private fun getAssistantLanguage(): String = Locale.getDefault().language
    
    /**
     * Listener para el reconocimiento de voz
     */
    private val speechRecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            // Listo para escuchar
        }
        
        override fun onBeginningOfSpeech() {
            // Comenzó a hablar
        }
        
        override fun onRmsChanged(rmsdB: Float) {
            // Cambio en el volumen
        }
        
        override fun onBufferReceived(buffer: ByteArray?) {
            // Buffer de audio recibido
        }
        
        override fun onEndOfSpeech() {
            isListening = false
        }
        
        override fun onError(error: Int) {
            isListening = false
            // Manejar errores de reconocimiento
        }
        
        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                processUserInput(matches[0])
            }
        }
        
        override fun onPartialResults(partialResults: Bundle?) {
            // Resultados parciales
        }
        
        override fun onEvent(eventType: Int, params: Bundle?) {
            // Eventos adicionales
        }
    }
    
    override fun cleanup() {
        stopListening()
        textToSpeech?.shutdown()
        speechRecognizer?.destroy()
        context = null
    }
}

/**
 * Representa un mensaje en la conversación
 */
data class ConversationMessage(
    val speaker: Speaker,
    val message: String,
    val timestamp: Long
)

/**
 * Tipos de hablantes en la conversación
 */
enum class Speaker {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * Personalidades disponibles para el asistente
 */
enum class AssistantPersonality {
    PROFESSIONAL,
    FRIENDLY,
    FORMAL,
    CASUAL
}