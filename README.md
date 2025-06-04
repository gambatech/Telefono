# 📱 Zaphone - Extensible Phone App
<img alt="Logo" src="graphics/icon.webp" width="120" />

**Zaphone** es un fork avanzado de [Fossify Phone](https://github.com/FossifyOrg/Phone) que incorpora un **sistema modular de extensiones** para ampliar las funcionalidades de la aplicación de teléfono de Android de forma flexible y escalable.

Empower your calls with advanced extensions. Zaphone redefines the mobile app experience with unmatched privacy, efficiency, and extensibility. Free from ads and intrusive permissions, it's designed for seamless and secure everyday communication with powerful customization capabilities.

📱 **YOUR PRIVACY, OUR PRIORITY:**  
Welcome to the Fossify Phone App, where your digital privacy is paramount. Switch to a mobile experience that respects your data, ensuring your personal information remains secure and private.

🚀 **SEAMLESS PERFORMANCE:**  
The Fossify Phone App offers a fluid and responsive mobile interface, enhancing your phone's performance while safeguarding your privacy. Experience a lag-free, smooth user experience, optimized for efficiency and speed.

🌐 **OPEN-SOURCE ASSURANCE:**  
With the Fossify Phone App, transparency is at your fingertips. Built on an open-source foundation, our app allows you to review our code on GitHub, fostering trust and a community committed to privacy.

## 🧩 Sistema de Extensiones (Nuevo en Zaphone)

Zaphone introduce un sistema modular revolucionario que permite agregar nuevas funcionalidades sin modificar el código base:

### 📞 Extensiones Disponibles

#### 1. ✅ **Contestador IVR (Interactive Voice Response)**
- Flujos interactivos por voz configurables
- Respuestas automáticas con texto-a-voz
- Captura de entradas DTMF (teclas pulsadas)
- Gestión de flujos reutilizables

#### 2. 🤖 **Asistente de IA Conversacional**
- Interacción inteligente durante llamadas
- Procesamiento de voz en tiempo real
- Respuestas contextuales automáticas
- Configuración personalizable de diálogos

#### 3. 📱 **Interceptor de Códigos USSD**
- Interceptación del código `*#06#` (IMEI)
- Pantallas personalizadas en lugar del IMEI estándar
- Extensible para otros códigos USSD

#### 4. 🚫 **Bloqueo de Llamadas Avanzado**
- Bloqueo por números específicos
- Bloqueo por patrones (regex)
- Lista negra configurable
- Registro de llamadas bloqueadas

### ⚙️ Configuración de Extensiones
1. Abre Zaphone
2. Ve a Menú → **Configuración de Extensiones**
3. Activa/desactiva extensiones según necesites
4. Configura cada extensión individualmente

🖼️ **TAILOR-MADE CUSTOMIZATION:**  
Customize your mobile experience with the Fossify Phone App. Adjust your app settings for a personalized interface, from thematic designs to functional preferences. Enjoy a user interface that's intuitive and uniquely yours.

🔋 **EFFICIENT RESOURCE MANAGEMENT:**  
The Fossify Phone App is designed for optimal resource usage, contributing to extended battery life. It's light on your phone's resources, ensuring your device runs efficiently with minimized battery drain.

## 📋 Roadmap - Próximas Extensiones

### 🔄 **Sistema de Sincronización de Contactos**
- Sincronización con Google Contacts sin dependencias de Google
- Soporte para múltiples proveedores (CardDAV, Exchange)
- Sincronización bidireccional
- Backup local encriptado

### 📊 **Analítica de Llamadas**
- Estadísticas detalladas de uso
- Patrones de llamadas
- Reportes exportables
- Gráficos de actividad

### 🔐 **Seguridad Avanzada**
- Encriptación de llamadas (cuando sea posible)
- Verificación de identidad de llamantes
- Detección de spam inteligente
- Alertas de seguridad

### 🌐 **Integración VoIP**
- Soporte para SIP
- Llamadas por internet
- Múltiples cuentas VoIP
- Calidad de llamada adaptativa

### 📝 **Transcripción de Llamadas**
- Conversión de voz a texto en tiempo real
- Búsqueda en transcripciones
- Resúmenes automáticos
- Soporte multiidioma

## 📦 Instalación

### Desde APK
1. Descarga `Zaphone-v1.0-debug.apk` desde este repositorio
2. Habilita "Fuentes desconocidas" en Configuración
3. Instala el APK
4. Configura como aplicación de teléfono predeterminada

### Compilación desde Código
```bash
git clone https://github.com/gambatech/Telefono.git
cd Telefono
./gradlew assembleFossDebug
```

Download Zaphone now and step into a mobile world where privacy seamlessly blends with functionality and extensibility. Your journey towards a safer, personalized mobile experience starts here.

➡️ Explore more Fossify apps: https://www.fossify.org<br>
➡️ Open-Source Code: https://www.github.com/FossifyOrg<br>
➡️ Join the community on Reddit: https://www.reddit.com/r/Fossify<br>
➡️ Connect on Telegram: https://t.me/Fossify

<div align="center">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" width="30%">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" width="30%">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" width="30%">
</div>
