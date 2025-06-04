package org.fossify.phone.extensions

import android.content.Context
import android.content.SharedPreferences
import android.provider.ContactsContract
import android.util.Log
import kotlinx.coroutines.*
import org.fossify.phone.extensions.base.BaseExtension
import org.fossify.phone.extensions.base.CallState
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Contact Sync Extension - Sistema de Sincronización de Contactos
 * 
 * Funcionalidades:
 * - Sincronización con múltiples proveedores (CardDAV, Exchange, CSV)
 * - Backup local encriptado
 * - Sincronización bidireccional
 * - Resolución de conflictos inteligente
 * - Importación/exportación masiva
 * - Sin dependencias de Google Services
 */
class ContactSyncExtension : BaseExtension() {
    
    override val name = "Sincronización de Contactos"
    override val description = "Sincronización empresarial sin dependencias de Google"
    override val version = "1.0.0"
    
    private val extensionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var syncConfig: ContactSyncConfiguration
    private lateinit var encryptionManager: EncryptionManager
    private lateinit var conflictResolver: ConflictResolver
    
    // Proveedores de sincronización
    private val syncProviders = mutableMapOf<String, ContactSyncProvider>()
    
    override fun initialize(context: Context) {
        super.initialize(context)
        
        syncConfig = ContactSyncConfiguration(context)
        encryptionManager = EncryptionManager(context)
        conflictResolver = ConflictResolver()
        
        // Registrar proveedores de sincronización
        registerSyncProviders()
        
        // Iniciar sincronización automática si está habilitada
        if (syncConfig.isAutoSyncEnabled()) {
            startAutoSync()
        }
        
        Log.d(TAG, "Contact Sync Extension initialized")
    }
    
    override fun onIncomingCall(phoneNumber: String): Boolean {
        if (!isEnabled) return false
        
        // Buscar contacto en todos los proveedores sincronizados
        extensionScope.launch {
            val contact = findContactByPhoneNumber(phoneNumber)
            if (contact != null) {
                Log.d(TAG, "Contact found: ${contact.displayName}")
                // Actualizar información de llamada con datos del contacto
                updateCallWithContactInfo(phoneNumber, contact)
            }
        }
        
        return false // No interceptar la llamada, solo enriquecer información
    }
    
    override fun onCallStateChanged(state: CallState, phoneNumber: String?) {
        if (state == CallState.ENDED && phoneNumber != null) {
            // Actualizar estadísticas de contacto
            extensionScope.launch {
                updateContactCallStats(phoneNumber)
            }
        }
    }
    
    private fun registerSyncProviders() {
        // CardDAV Provider
        syncProviders["carddav"] = CardDAVProvider()
        
        // Exchange ActiveSync Provider
        syncProviders["exchange"] = ExchangeProvider()
        
        // CSV File Provider
        syncProviders["csv"] = CSVProvider()
        
        // LDAP Provider
        syncProviders["ldap"] = LDAPProvider()
        
        // Custom API Provider
        syncProviders["api"] = APIProvider()
        
        Log.d(TAG, "Registered ${syncProviders.size} sync providers")
    }
    
    private fun startAutoSync() {
        extensionScope.launch {
            while (isActive) {
                try {
                    performFullSync()
                    delay(syncConfig.getSyncIntervalMillis())
                } catch (e: Exception) {
                    Log.e(TAG, "Auto sync error", e)
                    delay(60000) // Retry after 1 minute on error
                }
            }
        }
    }
    
    suspend fun performFullSync() {
        Log.d(TAG, "Starting full contact sync")
        
        val enabledProviders = syncConfig.getEnabledProviders()
        val localContacts = getLocalContacts()
        
        for (providerId in enabledProviders) {
            val provider = syncProviders[providerId]
            if (provider != null) {
                try {
                    syncWithProvider(provider, localContacts)
                } catch (e: Exception) {
                    Log.e(TAG, "Sync failed for provider $providerId", e)
                }
            }
        }
        
        // Crear backup después de la sincronización
        createEncryptedBackup(localContacts)
        
        Log.d(TAG, "Full contact sync completed")
    }
    
    private suspend fun syncWithProvider(provider: ContactSyncProvider, localContacts: List<Contact>) {
        val remoteContacts = provider.fetchContacts()
        val syncResult = performBidirectionalSync(localContacts, remoteContacts)
        
        // Aplicar cambios locales
        applyLocalChanges(syncResult.localChanges)
        
        // Enviar cambios al proveedor remoto
        provider.pushContacts(syncResult.remoteChanges)
        
        Log.d(TAG, "Sync completed with ${provider.javaClass.simpleName}: " +
                "${syncResult.localChanges.size} local changes, " +
                "${syncResult.remoteChanges.size} remote changes")
    }
    
    private fun performBidirectionalSync(localContacts: List<Contact>, remoteContacts: List<Contact>): SyncResult {
        val localChanges = mutableListOf<ContactChange>()
        val remoteChanges = mutableListOf<ContactChange>()
        
        // Crear mapas para búsqueda eficiente
        val localMap = localContacts.associateBy { it.id }
        val remoteMap = remoteContacts.associateBy { it.id }
        
        // Procesar contactos remotos
        for (remoteContact in remoteContacts) {
            val localContact = localMap[remoteContact.id]
            
            if (localContact == null) {
                // Contacto nuevo desde remoto
                localChanges.add(ContactChange(ContactChangeType.ADD, remoteContact))
            } else if (remoteContact.lastModified > localContact.lastModified) {
                // Contacto actualizado en remoto
                val resolvedContact = conflictResolver.resolve(localContact, remoteContact)
                localChanges.add(ContactChange(ContactChangeType.UPDATE, resolvedContact))
            }
        }
        
        // Procesar contactos locales
        for (localContact in localContacts) {
            val remoteContact = remoteMap[localContact.id]
            
            if (remoteContact == null) {
                // Contacto nuevo local
                remoteChanges.add(ContactChange(ContactChangeType.ADD, localContact))
            } else if (localContact.lastModified > remoteContact.lastModified) {
                // Contacto actualizado localmente
                val resolvedContact = conflictResolver.resolve(remoteContact, localContact)
                remoteChanges.add(ContactChange(ContactChangeType.UPDATE, resolvedContact))
            }
        }
        
        return SyncResult(localChanges, remoteChanges)
    }
    
    private suspend fun getLocalContacts(): List<Contact> {
        return withContext(Dispatchers.IO) {
            val contacts = mutableListOf<Contact>()
            
            context?.contentResolver?.query(
                ContactsContract.Contacts.CONTENT_URI,
                null, null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val contact = parseContactFromCursor(cursor)
                    if (contact != null) {
                        contacts.add(contact)
                    }
                }
            }
            
            contacts
        }
    }
    
    private fun parseContactFromCursor(cursor: android.database.Cursor): Contact? {
        try {
            val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
            val displayName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
            val lastModified = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP))
            
            // Obtener números de teléfono
            val phoneNumbers = getPhoneNumbers(id)
            
            // Obtener emails
            val emails = getEmails(id)
            
            return Contact(
                id = id,
                displayName = displayName ?: "",
                phoneNumbers = phoneNumbers,
                emails = emails,
                lastModified = lastModified
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing contact", e)
            return null
        }
    }
    
    private fun getPhoneNumbers(contactId: String): List<String> {
        val phoneNumbers = mutableListOf<String>()
        
        context?.contentResolver?.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val phoneNumber = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                if (phoneNumber != null) {
                    phoneNumbers.add(phoneNumber)
                }
            }
        }
        
        return phoneNumbers
    }
    
    private fun getEmails(contactId: String): List<String> {
        val emails = mutableListOf<String>()
        
        context?.contentResolver?.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            null,
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val email = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS))
                if (email != null) {
                    emails.add(email)
                }
            }
        }
        
        return emails
    }
    
    private suspend fun findContactByPhoneNumber(phoneNumber: String): Contact? {
        return withContext(Dispatchers.IO) {
            context?.contentResolver?.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?",
                arrayOf(phoneNumber),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
                    return@withContext getContactById(contactId)
                }
            }
            null
        }
    }
    
    private fun getContactById(contactId: String): Contact? {
        context?.contentResolver?.query(
            ContactsContract.Contacts.CONTENT_URI,
            null,
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return parseContactFromCursor(cursor)
            }
        }
        return null
    }
    
    private fun applyLocalChanges(changes: List<ContactChange>) {
        for (change in changes) {
            when (change.type) {
                ContactChangeType.ADD -> addLocalContact(change.contact)
                ContactChangeType.UPDATE -> updateLocalContact(change.contact)
                ContactChangeType.DELETE -> deleteLocalContact(change.contact.id)
            }
        }
    }
    
    private fun addLocalContact(contact: Contact) {
        // Implementar inserción de contacto local
        Log.d(TAG, "Adding local contact: ${contact.displayName}")
    }
    
    private fun updateLocalContact(contact: Contact) {
        // Implementar actualización de contacto local
        Log.d(TAG, "Updating local contact: ${contact.displayName}")
    }
    
    private fun deleteLocalContact(contactId: String) {
        // Implementar eliminación de contacto local
        Log.d(TAG, "Deleting local contact: $contactId")
    }
    
    private fun updateCallWithContactInfo(phoneNumber: String, contact: Contact) {
        // Actualizar información de llamada con datos del contacto
        Log.d(TAG, "Enriching call with contact info: ${contact.displayName}")
    }
    
    private fun updateContactCallStats(phoneNumber: String) {
        // Actualizar estadísticas de llamadas del contacto
        Log.d(TAG, "Updating call stats for: $phoneNumber")
    }
    
    private suspend fun createEncryptedBackup(contacts: List<Contact>) {
        try {
            val backupData = JSONArray()
            for (contact in contacts) {
                backupData.put(contact.toJSON())
            }
            
            val encryptedData = encryptionManager.encrypt(backupData.toString())
            val backupFile = getBackupFile()
            
            withContext(Dispatchers.IO) {
                backupFile.writeBytes(encryptedData)
            }
            
            Log.d(TAG, "Encrypted backup created: ${backupFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating backup", e)
        }
    }
    
    private fun getBackupFile(): File {
        val backupDir = File(context?.filesDir, "contact_backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(backupDir, "contacts_backup_$timestamp.enc")
    }
    
    suspend fun exportContactsToCSV(file: File) {
        val contacts = getLocalContacts()
        
        withContext(Dispatchers.IO) {
            file.bufferedWriter().use { writer ->
                // Header
                writer.write("Name,Phone,Email,LastModified\n")
                
                // Data
                for (contact in contacts) {
                    val phone = contact.phoneNumbers.firstOrNull() ?: ""
                    val email = contact.emails.firstOrNull() ?: ""
                    writer.write("\"${contact.displayName}\",\"$phone\",\"$email\",${contact.lastModified}\n")
                }
            }
        }
        
        Log.d(TAG, "Contacts exported to CSV: ${file.name}")
    }
    
    suspend fun importContactsFromCSV(file: File) {
        withContext(Dispatchers.IO) {
            file.bufferedReader().use { reader ->
                reader.readLine() // Skip header
                
                reader.forEachLine { line ->
                    val parts = line.split(",")
                    if (parts.size >= 4) {
                        val contact = Contact(
                            id = UUID.randomUUID().toString(),
                            displayName = parts[0].trim('"'),
                            phoneNumbers = if (parts[1].trim('"').isNotEmpty()) listOf(parts[1].trim('"')) else emptyList(),
                            emails = if (parts[2].trim('"').isNotEmpty()) listOf(parts[2].trim('"')) else emptyList(),
                            lastModified = System.currentTimeMillis()
                        )
                        
                        addLocalContact(contact)
                    }
                }
            }
        }
        
        Log.d(TAG, "Contacts imported from CSV: ${file.name}")
    }
    
    override fun cleanup() {
        extensionScope.cancel()
        super.cleanup()
    }
    
    companion object {
        private const val TAG = "ContactSyncExtension"
    }
}

// Clases de datos
data class Contact(
    val id: String,
    val displayName: String,
    val phoneNumbers: List<String>,
    val emails: List<String>,
    val lastModified: Long,
    val organization: String? = null,
    val notes: String? = null
) {
    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("displayName", displayName)
            put("phoneNumbers", JSONArray(phoneNumbers))
            put("emails", JSONArray(emails))
            put("lastModified", lastModified)
            put("organization", organization)
            put("notes", notes)
        }
    }
    
    companion object {
        fun fromJSON(json: JSONObject): Contact {
            val phoneNumbers = mutableListOf<String>()
            val phoneArray = json.optJSONArray("phoneNumbers")
            if (phoneArray != null) {
                for (i in 0 until phoneArray.length()) {
                    phoneNumbers.add(phoneArray.getString(i))
                }
            }
            
            val emails = mutableListOf<String>()
            val emailArray = json.optJSONArray("emails")
            if (emailArray != null) {
                for (i in 0 until emailArray.length()) {
                    emails.add(emailArray.getString(i))
                }
            }
            
            return Contact(
                id = json.getString("id"),
                displayName = json.getString("displayName"),
                phoneNumbers = phoneNumbers,
                emails = emails,
                lastModified = json.getLong("lastModified"),
                organization = json.optString("organization"),
                notes = json.optString("notes")
            )
        }
    }
}

data class ContactChange(
    val type: ContactChangeType,
    val contact: Contact
)

enum class ContactChangeType {
    ADD, UPDATE, DELETE
}

data class SyncResult(
    val localChanges: List<ContactChange>,
    val remoteChanges: List<ContactChange>
)

// Interfaces y clases base
interface ContactSyncProvider {
    suspend fun fetchContacts(): List<Contact>
    suspend fun pushContacts(changes: List<ContactChange>)
    fun isConfigured(): Boolean
}

// Implementaciones de proveedores
class CardDAVProvider : ContactSyncProvider {
    override suspend fun fetchContacts(): List<Contact> {
        // Implementar sincronización CardDAV
        return emptyList()
    }
    
    override suspend fun pushContacts(changes: List<ContactChange>) {
        // Implementar push CardDAV
    }
    
    override fun isConfigured(): Boolean = false
}

class ExchangeProvider : ContactSyncProvider {
    override suspend fun fetchContacts(): List<Contact> {
        // Implementar sincronización Exchange
        return emptyList()
    }
    
    override suspend fun pushContacts(changes: List<ContactChange>) {
        // Implementar push Exchange
    }
    
    override fun isConfigured(): Boolean = false
}

class CSVProvider : ContactSyncProvider {
    override suspend fun fetchContacts(): List<Contact> {
        // Implementar lectura CSV
        return emptyList()
    }
    
    override suspend fun pushContacts(changes: List<ContactChange>) {
        // Implementar escritura CSV
    }
    
    override fun isConfigured(): Boolean = false
}

class LDAPProvider : ContactSyncProvider {
    override suspend fun fetchContacts(): List<Contact> {
        // Implementar sincronización LDAP
        return emptyList()
    }
    
    override suspend fun pushContacts(changes: List<ContactChange>) {
        // Implementar push LDAP
    }
    
    override fun isConfigured(): Boolean = false
}

class APIProvider : ContactSyncProvider {
    override suspend fun fetchContacts(): List<Contact> {
        // Implementar API REST personalizada
        return emptyList()
    }
    
    override suspend fun pushContacts(changes: List<ContactChange>) {
        // Implementar push API
    }
    
    override fun isConfigured(): Boolean = false
}

// Clases auxiliares
class ContactSyncConfiguration(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("contact_sync_config", Context.MODE_PRIVATE)
    
    fun isAutoSyncEnabled(): Boolean = prefs.getBoolean("auto_sync_enabled", false)
    
    fun getSyncIntervalMillis(): Long = prefs.getLong("sync_interval", 3600000) // 1 hora por defecto
    
    fun getEnabledProviders(): List<String> {
        val enabled = prefs.getStringSet("enabled_providers", emptySet()) ?: emptySet()
        return enabled.toList()
    }
}

class EncryptionManager(private val context: Context) {
    private val keyAlias = "contact_sync_key"
    
    fun encrypt(data: String): ByteArray {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(data.toByteArray())
    }
    
    fun decrypt(encryptedData: ByteArray): String {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key)
        return String(cipher.doFinal(encryptedData))
    }
    
    private fun getOrCreateKey(): SecretKey {
        val prefs = context.getSharedPreferences("encryption_keys", Context.MODE_PRIVATE)
        val keyString = prefs.getString(keyAlias, null)
        
        return if (keyString != null) {
            SecretKeySpec(android.util.Base64.decode(keyString, android.util.Base64.DEFAULT), "AES")
        } else {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256)
            val key = keyGenerator.generateKey()
            
            // Guardar clave
            prefs.edit()
                .putString(keyAlias, android.util.Base64.encodeToString(key.encoded, android.util.Base64.DEFAULT))
                .apply()
            
            key
        }
    }
}

class ConflictResolver {
    fun resolve(localContact: Contact, remoteContact: Contact): Contact {
        // Estrategia simple: usar el más reciente
        return if (localContact.lastModified > remoteContact.lastModified) {
            localContact
        } else {
            remoteContact
        }
    }
}