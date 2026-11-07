# DuoSpace Lite — beta Android

**Proyecto independiente**, escrito en Java, sin SDK de terceros, anuncios ni servicios permanentes en segundo plano.

## Funcionalidad implementada

- Crea un **perfil de trabajo Android** (Device Policy Controller / Profile Owner) cuando el dispositivo permite aprovisionamiento.
- Enumera aplicaciones con actividad de lanzamiento, con buscador y selector.
- Solicita duplicación hacia el perfil gestionado mediante el enrutamiento de intents del sistema.
- Transfiere la base y los **split APK** de la aplicación a través de un ContentProvider de sólo lectura, sujeto a los permisos y restricciones de Android.
- En el perfil de trabajo ejecuta una sesión de PackageInstaller; puede solicitar al usuario autorización para instalar aplicaciones de origen externo.
- Si la instalación no es posible, ofrece abrir la ficha de la app en Play Store dentro del perfil de trabajo (si está disponible).
- Permite abrir y desinstalar aplicaciones en el perfil de trabajo.
- El perfil y sus datos **persisten tras reinicios** por diseño del sistema Android. No contiene receptor al arranque ni servicios residentes.

## Limitaciones / estado

**BETA SIN VERIFICACIÓN EN UN TELÉFONO REAL.** El empaquetado APK por sí solo no demuestra que el aprovisionamiento, la transferencia entre perfiles ni el instalador funcionen en todas las marcas/versiones. Probar en un dispositivo secundario antes de confiar datos personales.

Android normalmente admite un solo perfil de trabajo; no permite copias ilimitadas. La presencia de otros perfiles, Android personalizado, políticas del fabricante o apps con comprobaciones de integridad pueden impedir la creación/instalación. No permite duplicar cuentas de apps que prohíban ejecutar varias instancias. No proporciona copias automáticas de conversaciones ni las exporta entre perfiles.

La instalación desde una copia de APK puede requerir permisos explícitos. Las apps que se actualizan en el perfil personal no se actualizan necesariamente en el duplicado hasta que se actualicen allí. Para WhatsApp, la aplicación original y copia usan almacenamiento independiente, por lo que los números y el contenido deben configurarse por separado.

> Precaución importante: **borrar el perfil de trabajo borra todas sus aplicaciones y datos**, incluido WhatsApp duplicado. No borres ese perfil sin antes realizar la copia de seguridad de cada aplicación.

## Requisitos

- Android 9 (API 28) en adelante; recomendado Android 11 a 16.
- El teléfono debe permitir `android.software.managed_users`.
- Sin un perfil de trabajo existente (salvo si se elimina voluntariamente tras respaldar sus datos).
- Android Studio compatible, JDK 17, SDK API 36, Gradle 8.13, Android Gradle Plugin 8.13.2.

## Compilar (en Android Studio)

1. Abrir la carpeta `DuoSpaceLite` como proyecto en Android Studio.
2. Sincronizar Gradle y descargar `Android SDK Platform 36`.
3. Build > Build Bundle(s) / APK(s) > Build APK(s).
4. La compilación de depuración quedará en `app/build/outputs/apk/debug/app-debug.apk`.
5. Instalarla, abrirla y seleccionar **Crear perfil independiente**.

## Compilar (CLI)

Con Gradle 8.13 instalado y Android SDK definido en `ANDROID_HOME`:

```bash
gradle :app:assembleDebug
```

## Qué permisos se utilizan

- `REQUEST_INSTALL_PACKAGES`: necesario sólo para las instalaciones iniciadas por el usuario dentro del perfil gestionado.
- `INTERACT_ACROSS_PROFILES`: acceso entre instancias del propio gestor en personal/trabajo, sujeto a consentimiento del usuario.
- `BIND_DEVICE_ADMIN`: lo requiere Android para establecer el Profile Owner, se declara sólo en el receptor.

No requiere Internet, lectura de contactos, SMS, almacenamiento externo, accesibilidad ni notificaciones para el gestor.

## Licencia

Código original para este proyecto. Sin dependencias comerciales.
