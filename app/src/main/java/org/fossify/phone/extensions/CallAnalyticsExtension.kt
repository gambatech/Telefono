package org.fossify.phone.extensions

import android.content.Context
import android.content.SharedPreferences
import android.provider.CallLog
import android.util.Log
import kotlinx.coroutines.*
import org.fossify.phone.extensions.base.BaseExtension
import org.fossify.phone.extensions.base.CallState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

/**
 * Call Analytics Extension - Sistema de Analítica de Llamadas
 * 
 * Funcionalidades:
 * - Estadísticas detalladas de llamadas
 * - Análisis de patrones de comunicación
 * - Dashboard ejecutivo en tiempo real
 * - Reportes exportables (PDF, Excel, CSV)
 * - Métricas de productividad
 * - Análisis de tendencias temporales
 */
class CallAnalyticsExtension : BaseExtension() {
    
    override val name = "Analítica de Llamadas"
    override val description = "Dashboard ejecutivo con estadísticas avanzadas"
    override val version = "1.0.0"
    
    private val extensionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var analyticsConfig: AnalyticsConfiguration
    private lateinit var metricsCollector: MetricsCollector
    private lateinit var reportGenerator: ReportGenerator
    
    // Cache de métricas
    private var cachedMetrics: CallMetrics? = null
    private var lastCacheUpdate: Long = 0
    private val cacheValidityMs = 300000 // 5 minutos
    
    override fun initialize(context: Context) {
        super.initialize(context)
        
        analyticsConfig = AnalyticsConfiguration(context)
        metricsCollector = MetricsCollector(context)
        reportGenerator = ReportGenerator(context)
        
        // Iniciar recolección de métricas en tiempo real
        startMetricsCollection()
        
        Log.d(TAG, "Call Analytics Extension initialized")
    }
    
    override fun onIncomingCall(phoneNumber: String): Boolean {
        if (!isEnabled) return false
        
        // Registrar llamada entrante
        extensionScope.launch {
            recordCallEvent(
                CallEvent(
                    phoneNumber = phoneNumber,
                    type = CallType.INCOMING,
                    timestamp = System.currentTimeMillis(),
                    status = CallEventStatus.STARTED
                )
            )
        }
        
        return false // No interceptar la llamada
    }
    
    override fun onCallStateChanged(state: CallState, phoneNumber: String?) {
        if (!isEnabled || phoneNumber == null) return
        
        extensionScope.launch {
            when (state) {
                CallState.ACTIVE -> {
                    recordCallEvent(
                        CallEvent(
                            phoneNumber = phoneNumber,
                            type = determineCallType(phoneNumber),
                            timestamp = System.currentTimeMillis(),
                            status = CallEventStatus.ANSWERED
                        )
                    )
                }
                CallState.ENDED -> {
                    val duration = getCurrentCallDuration(phoneNumber)
                    recordCallEvent(
                        CallEvent(
                            phoneNumber = phoneNumber,
                            type = determineCallType(phoneNumber),
                            timestamp = System.currentTimeMillis(),
                            status = CallEventStatus.ENDED,
                            duration = duration
                        )
                    )
                    
                    // Actualizar métricas en tiempo real
                    updateRealTimeMetrics()
                }
                else -> {}
            }
        }
    }
    
    private fun startMetricsCollection() {
        extensionScope.launch {
            while (isActive) {
                try {
                    collectAndCacheMetrics()
                    delay(analyticsConfig.getMetricsUpdateInterval())
                } catch (e: Exception) {
                    Log.e(TAG, "Error collecting metrics", e)
                    delay(60000) // Retry after 1 minute on error
                }
            }
        }
    }
    
    private suspend fun collectAndCacheMetrics() {
        val metrics = metricsCollector.collectAllMetrics()
        cachedMetrics = metrics
        lastCacheUpdate = System.currentTimeMillis()
        
        // Guardar métricas históricas
        saveMetricsSnapshot(metrics)
        
        Log.d(TAG, "Metrics collected and cached")
    }
    
    suspend fun getCurrentMetrics(): CallMetrics {
        return if (isCacheValid()) {
            cachedMetrics!!
        } else {
            collectAndCacheMetrics()
            cachedMetrics!!
        }
    }
    
    private fun isCacheValid(): Boolean {
        return cachedMetrics != null && 
               (System.currentTimeMillis() - lastCacheUpdate) < cacheValidityMs
    }
    
    suspend fun generateDashboardData(): DashboardData {
        val metrics = getCurrentMetrics()
        val trends = calculateTrends()
        val topContacts = getTopContacts()
        val hourlyDistribution = getHourlyCallDistribution()
        val weeklyPattern = getWeeklyCallPattern()
        
        return DashboardData(
            totalCalls = metrics.totalCalls,
            totalDuration = metrics.totalDuration,
            averageDuration = metrics.averageDuration,
            incomingCalls = metrics.incomingCalls,
            outgoingCalls = metrics.outgoingCalls,
            missedCalls = metrics.missedCalls,
            answerRate = metrics.answerRate,
            trends = trends,
            topContacts = topContacts,
            hourlyDistribution = hourlyDistribution,
            weeklyPattern = weeklyPattern,
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    private suspend fun calculateTrends(): TrendData {
        val currentPeriod = metricsCollector.getMetricsForPeriod(
            System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000, // Última semana
            System.currentTimeMillis()
        )
        
        val previousPeriod = metricsCollector.getMetricsForPeriod(
            System.currentTimeMillis() - 14 * 24 * 60 * 60 * 1000, // Semana anterior
            System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000
        )
        
        return TrendData(
            callsChange = calculatePercentageChange(previousPeriod.totalCalls, currentPeriod.totalCalls),
            durationChange = calculatePercentageChange(previousPeriod.totalDuration, currentPeriod.totalDuration),
            answerRateChange = calculatePercentageChange(previousPeriod.answerRate, currentPeriod.answerRate)
        )
    }
    
    private fun calculatePercentageChange(previous: Long, current: Long): Double {
        return if (previous == 0L) 0.0 else ((current - previous).toDouble() / previous) * 100
    }
    
    private fun calculatePercentageChange(previous: Double, current: Double): Double {
        return if (previous == 0.0) 0.0 else ((current - previous) / previous) * 100
    }
    
    private suspend fun getTopContacts(): List<ContactStats> {
        return metricsCollector.getTopContactsByCallCount(10)
    }
    
    private suspend fun getHourlyCallDistribution(): Map<Int, Int> {
        return metricsCollector.getHourlyCallDistribution()
    }
    
    private suspend fun getWeeklyCallPattern(): Map<Int, Int> {
        return metricsCollector.getWeeklyCallPattern()
    }
    
    suspend fun generateReport(reportType: ReportType, period: ReportPeriod): ReportData {
        val startTime = getStartTimeForPeriod(period)
        val endTime = System.currentTimeMillis()
        
        val metrics = metricsCollector.getMetricsForPeriod(startTime, endTime)
        val detailedData = when (reportType) {
            ReportType.EXECUTIVE_SUMMARY -> generateExecutiveSummary(metrics, startTime, endTime)
            ReportType.DETAILED_ANALYSIS -> generateDetailedAnalysis(metrics, startTime, endTime)
            ReportType.PRODUCTIVITY_REPORT -> generateProductivityReport(metrics, startTime, endTime)
            ReportType.CONTACT_ANALYSIS -> generateContactAnalysis(metrics, startTime, endTime)
        }
        
        return ReportData(
            type = reportType,
            period = period,
            generatedAt = System.currentTimeMillis(),
            data = detailedData
        )
    }
    
    private fun getStartTimeForPeriod(period: ReportPeriod): Long {
        val calendar = Calendar.getInstance()
        return when (period) {
            ReportPeriod.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.timeInMillis
            }
            ReportPeriod.WEEK -> System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000
            ReportPeriod.MONTH -> System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000
            ReportPeriod.QUARTER -> System.currentTimeMillis() - 90 * 24 * 60 * 60 * 1000
            ReportPeriod.YEAR -> System.currentTimeMillis() - 365 * 24 * 60 * 60 * 1000
        }
    }
    
    private suspend fun generateExecutiveSummary(metrics: CallMetrics, startTime: Long, endTime: Long): JSONObject {
        return JSONObject().apply {
            put("summary", "Resumen Ejecutivo de Llamadas")
            put("period", "${formatDate(startTime)} - ${formatDate(endTime)}")
            put("totalCalls", metrics.totalCalls)
            put("totalDuration", formatDuration(metrics.totalDuration))
            put("averageDuration", formatDuration(metrics.averageDuration.toLong()))
            put("answerRate", String.format("%.1f%%", metrics.answerRate))
            put("productivity", calculateProductivityScore(metrics))
            put("recommendations", generateRecommendations(metrics))
        }
    }
    
    private suspend fun generateDetailedAnalysis(metrics: CallMetrics, startTime: Long, endTime: Long): JSONObject {
        val hourlyData = getHourlyCallDistribution()
        val contactStats = getTopContacts()
        
        return JSONObject().apply {
            put("analysis", "Análisis Detallado de Llamadas")
            put("metrics", metrics.toJSON())
            put("hourlyDistribution", JSONObject(hourlyData))
            put("topContacts", JSONArray(contactStats.map { it.toJSON() }))
            put("callPatterns", analyzeCallPatterns())
        }
    }
    
    private suspend fun generateProductivityReport(metrics: CallMetrics, startTime: Long, endTime: Long): JSONObject {
        val productivityScore = calculateProductivityScore(metrics)
        val efficiency = calculateEfficiency(metrics)
        
        return JSONObject().apply {
            put("report", "Reporte de Productividad")
            put("productivityScore", productivityScore)
            put("efficiency", efficiency)
            put("callsPerHour", metrics.totalCalls.toDouble() / 8) // Asumiendo 8 horas laborales
            put("averageCallLength", formatDuration(metrics.averageDuration.toLong()))
            put("recommendations", generateProductivityRecommendations(metrics))
        }
    }
    
    private suspend fun generateContactAnalysis(metrics: CallMetrics, startTime: Long, endTime: Long): JSONObject {
        val contactStats = metricsCollector.getAllContactStats()
        val frequentContacts = contactStats.filter { it.callCount >= 5 }
        
        return JSONObject().apply {
            put("analysis", "Análisis de Contactos")
            put("totalContacts", contactStats.size)
            put("frequentContacts", frequentContacts.size)
            put("contactDetails", JSONArray(contactStats.map { it.toJSON() }))
            put("communicationPatterns", analyzeContactPatterns(contactStats))
        }
    }
    
    private fun calculateProductivityScore(metrics: CallMetrics): Double {
        // Algoritmo simple de productividad basado en múltiples factores
        val answerRateScore = metrics.answerRate / 100.0 * 30 // 30% del score
        val durationScore = if (metrics.averageDuration > 0) {
            Math.min(metrics.averageDuration / 300.0, 1.0) * 40 // 40% del score, máximo 5 min
        } else 0.0
        val volumeScore = Math.min(metrics.totalCalls / 50.0, 1.0) * 30 // 30% del score, máximo 50 llamadas
        
        return (answerRateScore + durationScore + volumeScore) * 100
    }
    
    private fun calculateEfficiency(metrics: CallMetrics): Double {
        return if (metrics.totalCalls > 0) {
            metrics.totalDuration.toDouble() / metrics.totalCalls / 60.0 // Minutos promedio por llamada
        } else 0.0
    }
    
    private fun generateRecommendations(metrics: CallMetrics): JSONArray {
        val recommendations = JSONArray()
        
        if (metrics.answerRate < 80) {
            recommendations.put("Considere mejorar la disponibilidad para aumentar la tasa de respuesta")
        }
        
        if (metrics.averageDuration < 60) {
            recommendations.put("Las llamadas son muy cortas, considere si se está proporcionando suficiente información")
        }
        
        if (metrics.averageDuration > 600) {
            recommendations.put("Las llamadas son muy largas, considere optimizar la eficiencia de las conversaciones")
        }
        
        return recommendations
    }
    
    private fun generateProductivityRecommendations(metrics: CallMetrics): JSONArray {
        val recommendations = JSONArray()
        val score = calculateProductivityScore(metrics)
        
        when {
            score < 50 -> {
                recommendations.put("Productividad baja: Revise procesos de comunicación")
                recommendations.put("Considere capacitación en técnicas de llamadas efectivas")
            }
            score < 75 -> {
                recommendations.put("Productividad media: Hay oportunidades de mejora")
                recommendations.put("Analice patrones de llamadas para optimizar horarios")
            }
            else -> {
                recommendations.put("Excelente productividad: Mantenga las buenas prácticas")
            }
        }
        
        return recommendations
    }
    
    private suspend fun analyzeCallPatterns(): JSONObject {
        val patterns = JSONObject()
        
        // Analizar patrones por hora del día
        val hourlyData = getHourlyCallDistribution()
        val peakHour = hourlyData.maxByOrNull { it.value }?.key ?: 0
        patterns.put("peakHour", peakHour)
        
        // Analizar patrones por día de la semana
        val weeklyData = getWeeklyCallPattern()
        val peakDay = weeklyData.maxByOrNull { it.value }?.key ?: 1
        patterns.put("peakDay", peakDay)
        
        return patterns
    }
    
    private fun analyzeContactPatterns(contactStats: List<ContactStats>): JSONObject {
        val patterns = JSONObject()
        
        val totalCalls = contactStats.sumOf { it.callCount }
        val averageCallsPerContact = if (contactStats.isNotEmpty()) totalCalls.toDouble() / contactStats.size else 0.0
        
        patterns.put("averageCallsPerContact", averageCallsPerContact)
        patterns.put("mostActiveContact", contactStats.maxByOrNull { it.callCount }?.phoneNumber ?: "")
        patterns.put("contactsWithMultipleCalls", contactStats.count { it.callCount > 1 })
        
        return patterns
    }
    
    suspend fun exportReport(reportData: ReportData, format: ExportFormat): File {
        return when (format) {
            ExportFormat.JSON -> reportGenerator.exportToJSON(reportData)
            ExportFormat.CSV -> reportGenerator.exportToCSV(reportData)
            ExportFormat.PDF -> reportGenerator.exportToPDF(reportData)
            ExportFormat.EXCEL -> reportGenerator.exportToExcel(reportData)
        }
    }
    
    private suspend fun recordCallEvent(event: CallEvent) {
        metricsCollector.recordEvent(event)
    }
    
    private fun determineCallType(phoneNumber: String): CallType {
        // Lógica simple para determinar tipo de llamada
        // En implementación real, se consultaría el log de llamadas
        return CallType.INCOMING // Simplificado
    }
    
    private fun getCurrentCallDuration(phoneNumber: String): Long {
        // Obtener duración de la llamada actual
        // En implementación real, se calcularía desde el inicio de la llamada
        return 0L // Simplificado
    }
    
    private suspend fun updateRealTimeMetrics() {
        // Invalidar cache para forzar actualización
        lastCacheUpdate = 0
    }
    
    private suspend fun saveMetricsSnapshot(metrics: CallMetrics) {
        val snapshot = MetricsSnapshot(
            timestamp = System.currentTimeMillis(),
            metrics = metrics
        )
        
        // Guardar en archivo local para historial
        val snapshotFile = getSnapshotFile()
        val jsonData = snapshot.toJSON().toString()
        
        withContext(Dispatchers.IO) {
            snapshotFile.appendText("$jsonData\n")
        }
    }
    
    private fun getSnapshotFile(): File {
        val metricsDir = File(context?.filesDir, "call_metrics")
        if (!metricsDir.exists()) {
            metricsDir.mkdirs()
        }
        
        val dateFormat = SimpleDateFormat("yyyyMM", Locale.getDefault())
        val monthYear = dateFormat.format(Date())
        
        return File(metricsDir, "metrics_$monthYear.jsonl")
    }
    
    private fun formatDate(timestamp: Long): String {
        val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return format.format(Date(timestamp))
    }
    
    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
    
    override fun cleanup() {
        extensionScope.cancel()
        super.cleanup()
    }
    
    companion object {
        private const val TAG = "CallAnalyticsExtension"
    }
}

// Clases de datos
data class CallEvent(
    val phoneNumber: String,
    val type: CallType,
    val timestamp: Long,
    val status: CallEventStatus,
    val duration: Long = 0
)

enum class CallType {
    INCOMING, OUTGOING, MISSED
}

enum class CallEventStatus {
    STARTED, ANSWERED, ENDED, MISSED
}

data class CallMetrics(
    val totalCalls: Long,
    val totalDuration: Long,
    val averageDuration: Double,
    val incomingCalls: Long,
    val outgoingCalls: Long,
    val missedCalls: Long,
    val answerRate: Double
) {
    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("totalCalls", totalCalls)
            put("totalDuration", totalDuration)
            put("averageDuration", averageDuration)
            put("incomingCalls", incomingCalls)
            put("outgoingCalls", outgoingCalls)
            put("missedCalls", missedCalls)
            put("answerRate", answerRate)
        }
    }
}

data class DashboardData(
    val totalCalls: Long,
    val totalDuration: Long,
    val averageDuration: Double,
    val incomingCalls: Long,
    val outgoingCalls: Long,
    val missedCalls: Long,
    val answerRate: Double,
    val trends: TrendData,
    val topContacts: List<ContactStats>,
    val hourlyDistribution: Map<Int, Int>,
    val weeklyPattern: Map<Int, Int>,
    val lastUpdated: Long
)

data class TrendData(
    val callsChange: Double,
    val durationChange: Double,
    val answerRateChange: Double
)

data class ContactStats(
    val phoneNumber: String,
    val displayName: String?,
    val callCount: Long,
    val totalDuration: Long,
    val lastCallTime: Long
) {
    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("phoneNumber", phoneNumber)
            put("displayName", displayName)
            put("callCount", callCount)
            put("totalDuration", totalDuration)
            put("lastCallTime", lastCallTime)
        }
    }
}

data class ReportData(
    val type: ReportType,
    val period: ReportPeriod,
    val generatedAt: Long,
    val data: JSONObject
)

enum class ReportType {
    EXECUTIVE_SUMMARY,
    DETAILED_ANALYSIS,
    PRODUCTIVITY_REPORT,
    CONTACT_ANALYSIS
}

enum class ReportPeriod {
    TODAY, WEEK, MONTH, QUARTER, YEAR
}

enum class ExportFormat {
    JSON, CSV, PDF, EXCEL
}

data class MetricsSnapshot(
    val timestamp: Long,
    val metrics: CallMetrics
) {
    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("timestamp", timestamp)
            put("metrics", metrics.toJSON())
        }
    }
}

// Clases auxiliares
class AnalyticsConfiguration(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("analytics_config", Context.MODE_PRIVATE)
    
    fun getMetricsUpdateInterval(): Long = prefs.getLong("update_interval", 300000) // 5 minutos
    
    fun isRealTimeEnabled(): Boolean = prefs.getBoolean("realtime_enabled", true)
    
    fun getRetentionDays(): Int = prefs.getInt("retention_days", 90)
}

class MetricsCollector(private val context: Context) {
    
    suspend fun collectAllMetrics(): CallMetrics {
        return withContext(Dispatchers.IO) {
            val callLog = getCallLogData()
            calculateMetrics(callLog)
        }
    }
    
    suspend fun getMetricsForPeriod(startTime: Long, endTime: Long): CallMetrics {
        return withContext(Dispatchers.IO) {
            val callLog = getCallLogData(startTime, endTime)
            calculateMetrics(callLog)
        }
    }
    
    suspend fun getTopContactsByCallCount(limit: Int): List<ContactStats> {
        return withContext(Dispatchers.IO) {
            val contactMap = mutableMapOf<String, ContactStats>()
            
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null, null, null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                    val duration = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE))
                    
                    val existing = contactMap[number]
                    if (existing != null) {
                        contactMap[number] = existing.copy(
                            callCount = existing.callCount + 1,
                            totalDuration = existing.totalDuration + duration,
                            lastCallTime = maxOf(existing.lastCallTime, date)
                        )
                    } else {
                        contactMap[number] = ContactStats(
                            phoneNumber = number,
                            displayName = null, // Se podría obtener del directorio de contactos
                            callCount = 1,
                            totalDuration = duration,
                            lastCallTime = date
                        )
                    }
                }
            }
            
            contactMap.values.sortedByDescending { it.callCount }.take(limit)
        }
    }
    
    suspend fun getHourlyCallDistribution(): Map<Int, Int> {
        return withContext(Dispatchers.IO) {
            val distribution = mutableMapOf<Int, Int>()
            
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.DATE),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE))
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = date
                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                    
                    distribution[hour] = distribution.getOrDefault(hour, 0) + 1
                }
            }
            
            distribution
        }
    }
    
    suspend fun getWeeklyCallPattern(): Map<Int, Int> {
        return withContext(Dispatchers.IO) {
            val pattern = mutableMapOf<Int, Int>()
            
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.DATE),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE))
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = date
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    
                    pattern[dayOfWeek] = pattern.getOrDefault(dayOfWeek, 0) + 1
                }
            }
            
            pattern
        }
    }
    
    suspend fun getAllContactStats(): List<ContactStats> {
        return getTopContactsByCallCount(Int.MAX_VALUE)
    }
    
    suspend fun recordEvent(event: CallEvent) {
        // Registrar evento para métricas en tiempo real
        Log.d("MetricsCollector", "Event recorded: ${event.type} - ${event.phoneNumber}")
    }
    
    private fun getCallLogData(startTime: Long? = null, endTime: Long? = null): List<CallLogEntry> {
        val entries = mutableListOf<CallLogEntry>()
        
        val selection = if (startTime != null && endTime != null) {
            "${CallLog.Calls.DATE} BETWEEN ? AND ?"
        } else null
        
        val selectionArgs = if (startTime != null && endTime != null) {
            arrayOf(startTime.toString(), endTime.toString())
        } else null
        
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null, selection, selectionArgs,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val entry = CallLogEntry(
                    number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)),
                    type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)),
                    date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)),
                    duration = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                )
                entries.add(entry)
            }
        }
        
        return entries
    }
    
    private fun calculateMetrics(callLog: List<CallLogEntry>): CallMetrics {
        val totalCalls = callLog.size.toLong()
        val totalDuration = callLog.sumOf { it.duration }
        val averageDuration = if (totalCalls > 0) totalDuration.toDouble() / totalCalls else 0.0
        
        val incomingCalls = callLog.count { it.type == CallLog.Calls.INCOMING_TYPE }.toLong()
        val outgoingCalls = callLog.count { it.type == CallLog.Calls.OUTGOING_TYPE }.toLong()
        val missedCalls = callLog.count { it.type == CallLog.Calls.MISSED_TYPE }.toLong()
        
        val answeredCalls = incomingCalls + outgoingCalls
        val answerRate = if (totalCalls > 0) (answeredCalls.toDouble() / totalCalls) * 100 else 0.0
        
        return CallMetrics(
            totalCalls = totalCalls,
            totalDuration = totalDuration,
            averageDuration = averageDuration,
            incomingCalls = incomingCalls,
            outgoingCalls = outgoingCalls,
            missedCalls = missedCalls,
            answerRate = answerRate
        )
    }
}

data class CallLogEntry(
    val number: String,
    val type: Int,
    val date: Long,
    val duration: Long
)

class ReportGenerator(private val context: Context) {
    
    suspend fun exportToJSON(reportData: ReportData): File {
        return withContext(Dispatchers.IO) {
            val file = createReportFile("json")
            file.writeText(reportData.data.toString(2))
            file
        }
    }
    
    suspend fun exportToCSV(reportData: ReportData): File {
        return withContext(Dispatchers.IO) {
            val file = createReportFile("csv")
            // Implementar conversión a CSV
            file.writeText("CSV export not implemented yet")
            file
        }
    }
    
    suspend fun exportToPDF(reportData: ReportData): File {
        return withContext(Dispatchers.IO) {
            val file = createReportFile("pdf")
            // Implementar conversión a PDF
            file.writeText("PDF export not implemented yet")
            file
        }
    }
    
    suspend fun exportToExcel(reportData: ReportData): File {
        return withContext(Dispatchers.IO) {
            val file = createReportFile("xlsx")
            // Implementar conversión a Excel
            file.writeText("Excel export not implemented yet")
            file
        }
    }
    
    private fun createReportFile(extension: String): File {
        val reportsDir = File(context.filesDir, "call_reports")
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
        }
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(reportsDir, "call_report_$timestamp.$extension")
    }
}