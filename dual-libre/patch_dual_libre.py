#!/usr/bin/env python3
"""Dual Libre PRO build-time customization. Source remains in the upstream engine."""
from pathlib import Path
import shutil
import re

root = Path(__file__).resolve().parents[2] / "DualLibre"
assets = Path(__file__).resolve().parent
assert (root / "app/build.gradle").is_file(), "Clone of upstream Android project missing"
app = root / "app"
res = app / "src/main/res"

def patch(filename, old, new):
    p = app / filename
    data = p.read_text(encoding="utf-8")
    if old not in data:
        raise RuntimeError(f"Missing patch marker {old[:55]} in {p}")
    p.write_text(data.replace(old, new), encoding="utf-8")

# New package ID lets this version coexist with the older APK whose randomly
# generated signing key cannot be reproduced; NEVER erase existing clone data.
patch("build.gradle", 'applicationId "com.dualspace.clone"', 'applicationId "com.duallibre.pro"')
patch("build.gradle", 'applicationId "com.dualspace.clone32"', 'applicationId "com.duallibre.pro32"')
patch("build.gradle", 'versionCode 11', 'versionCode 20')
patch("build.gradle", 'versionName "1.2.7"', 'versionName "2.0.0"')
patch("build.gradle",
      'resourceConfigurations += ["en", "in", "zh-rCN"]',
      'resourceConfigurations += ["en", "es", "pt", "fr", "in", "zh-rCN"]')
patch("build.gradle", 'resValue "string", "app_name", "Dual Space"',
      'resValue "string", "app_name", "Dual Libre PRO"')
patch("build.gradle", 'resValue "string", "app_name", "Dual Space 32-bit"',
      'resValue "string", "app_name", "Dual Libre PRO 32-bit"')

# Explicit choice of interface language, persisted by AndroidX AppCompat.
settings = "src/main/java/com/dualspace/clone/ui/SettingsActivity.kt"
patch(settings,
      "import androidx.appcompat.app.AppCompatDelegate",
      "import androidx.appcompat.app.AppCompatDelegate\nimport androidx.core.os.LocaleListCompat")
patch(settings,
      '            val theme = findPreference<ListPreference>("theme")!!',
      '''            val language = findPreference<ListPreference>("language")!!
            val localeTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            language.value = when {
                localeTags.isEmpty() -> "system"
                localeTags.startsWith("zh") -> "zh-CN"
                localeTags.startsWith("id") || localeTags.startsWith("in") -> "id"
                localeTags.startsWith("es") -> "es"
                localeTags.startsWith("pt") -> "pt"
                localeTags.startsWith("fr") -> "fr"
                else -> "en"
            }
            language.setOnPreferenceChangeListener { _, value ->
                val selected = value as String
                val tags = if (selected == "system") "" else selected
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
                true
            }

            val theme = findPreference<ListPreference>("theme")!!''')

prefs = "src/main/res/xml/preferences.xml"
patch(prefs,
      '    <PreferenceCategory android:title="@string/pref_cat_appearance">',
      '''    <PreferenceCategory android:title="@string/pref_cat_appearance">
        <ListPreference
            android:key="language"
            android:title="@string/pref_language"
            android:entries="@array/language_entries"
            android:entryValues="@array/language_values"
            android:defaultValue="system"
            app:useSimpleSummaryProvider="true"
            app:iconSpaceReserved="false" />''')

arr = "src/main/res/values/arrays.xml"
patch(arr, "</resources>",
      '''    <string-array name="language_entries">
        <item>@string/language_system</item>
        <item>Español</item>
        <item>English</item>
        <item>Português</item>
        <item>Français</item>
        <item>Bahasa Indonesia</item>
        <item>简体中文</item>
    </string-array>
    <string-array name="language_values" translatable="false">
        <item>system</item><item>es</item><item>en</item>
        <item>pt</item><item>fr</item><item>id</item><item>zh-CN</item>
    </string-array>
</resources>''')
# Default English resources for the added settings labels
base_strings = res / "values/strings.xml"
data = base_strings.read_text(encoding="utf-8")
data = data.replace("</resources>", '''    <string name="pref_language">Language</string>
    <string name="language_system">Automatic (phone language)</string>
</resources>''')
base_strings.write_text(data, encoding="utf-8")

for folder in ("values-es", "values-pt", "values-fr"):
    source = assets / "i18n" / folder / "strings.xml"
    assert source.exists(), f"Missing language: {source}"
    destination = res / folder
    destination.mkdir(parents=True, exist_ok=True)
    shutil.copy(source, destination / "strings.xml")

(res / "xml/locales_config.xml").write_text(
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<locale-config xmlns:android="http://schemas.android.com/apk/res/android">\n'
    '  <locale android:name="es"/><locale android:name="en"/>\n'
    '  <locale android:name="pt"/><locale android:name="fr"/>\n'
    '  <locale android:name="id"/><locale android:name="zh-CN"/>\n'
    '</locale-config>\n', encoding="utf-8"
)
manifest="src/main/AndroidManifest.xml"
patch(manifest, 'android:allowBackup="false"',
      'android:allowBackup="false"\n        android:localeConfig="@xml/locales_config"')
patch(manifest, "</application>", '''    <service android:name="androidx.appcompat.app.AppLocalesMetadataHolderService"
            android:enabled="false"
            android:exported="false">
            <meta-data android:name="autoStoreLocales" android:value="true" />
        </service>
    </application>''')

# Premium color system: deep graphite, purple, rounded clone cards.
palette = (res / "values/colors.xml")
s = palette.read_text(encoding="utf-8")
s = (s.replace("#1E63C8","#7647E9")
       .replace("#D8E2FF","#E8DEFF")
       .replace("#001A41","#2A1759")
       .replace("#ADC6FF","#C0A7FF")
       .replace("#004494","#443073")
       .replace("#00A67E","#9B80F5"))
s = s.replace("</resources>", '    <color name="clone_card_background">#F0EAFE</color>\n</resources>')
palette.write_text(s, encoding="utf-8")
nightcolors = res / "values-night/colors.xml"
nightcolors.write_text('''<?xml version="1.0" encoding="utf-8"?>
<resources><color name="clone_card_background">#20182F</color></resources>
''', encoding="utf-8")

theme = "src/main/res/values-night/themes.xml"
patch(theme, '        <item name="colorSecondary">@color/seed_secondary</item>',
      '''        <item name="colorSecondary">@color/seed_secondary</item>
        <item name="colorSurface">#0E0C15</item>
        <item name="colorOnSurface">#F6F0FF</item>
        <item name="colorOnSurfaceVariant">#C7B9DE</item>
        <item name="colorPrimary">@color/seed_primary_dark</item>'''.replace('        <item name="colorPrimary">@color/seed_primary_dark</item>\n',''))
# Prefer the premium dark palette at first launch; the user can change it in Settings.
patch("src/main/java/com/dualspace/clone/util/Prefs.kt",
      'sp.getString("theme_mode", "system") ?: "system"',
      'sp.getString("theme_mode", "dark") ?: "dark"')
# Ignore wallpaper-based system colors so branding remains consistent.
patch("src/main/java/com/dualspace/clone/DualSpaceApp.kt",
      '        DynamicColors.applyToActivitiesIfAvailable(this)',
      '        // Keep the chosen Dual Libre PRO palette; do not override it with wallpaper colors.')

card = res / "drawable/clone_card_bg.xml"
card.write_text('''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
  <solid android:color="@color/clone_card_background"/>
  <corners android:radius="20dp"/>
  <stroke android:width="1dp" android:color="#493366"/>
</shape>
''',encoding="utf-8")
patch("src/main/res/layout/item_clone.xml",
      'android:background="?attr/selectableItemBackgroundBorderless"',
      'android:background="@drawable/clone_card_bg"\n    android:layout_margin="8dp"\n    android:elevation="3dp"')
patch("src/main/res/layout/item_clone.xml",
      'android:padding="8dp">',
      'android:padding="16dp">')
print("Dual Libre PRO 2.0: six languages, native selector, graphite-purple UI, distinct package ID")
