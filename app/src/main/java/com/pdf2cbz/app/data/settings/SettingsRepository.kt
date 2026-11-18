package com.pdf2cbz.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pdf2cbz.app.data.model.AppSettings
import com.pdf2cbz.app.data.model.DuplicatePolicy
import com.pdf2cbz.app.data.model.ImageFormat
import com.pdf2cbz.app.data.model.SourcePdfAction
import com.pdf2cbz.app.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "pdf2cbz_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val IMAGE_FORMAT = stringPreferencesKey("image_format")
        val DPI = intPreferencesKey("dpi")
        val JPEG_QUALITY = intPreferencesKey("jpeg_quality")
        val PREFER_DIRECT_EXTRACTION = booleanPreferencesKey("prefer_direct_extraction")
        val INCLUDE_COMIC_INFO = booleanPreferencesKey("include_comic_info")
        val OUTPUT_TREE_URI = stringPreferencesKey("output_tree_uri")
        val OUTPUT_TREE_NAME = stringPreferencesKey("output_tree_name")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DUPLICATE_POLICY = stringPreferencesKey("duplicate_policy")
        val SOURCE_PDF_ACTION = stringPreferencesKey("source_pdf_action")
        val AUTO_DELETE_TEMP = booleanPreferencesKey("auto_delete_temp")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            imageFormat = prefs[Keys.IMAGE_FORMAT]?.let {
                runCatching { ImageFormat.valueOf(it) }.getOrNull()
            } ?: ImageFormat.JPEG,
            dpi = prefs[Keys.DPI] ?: 150,
            jpegQuality = (prefs[Keys.JPEG_QUALITY] ?: 90).coerceIn(1, 100),
            preferDirectImageExtraction = prefs[Keys.PREFER_DIRECT_EXTRACTION] ?: true,
            includeComicInfoXml = prefs[Keys.INCLUDE_COMIC_INFO] ?: false,
            outputTreeUri = prefs[Keys.OUTPUT_TREE_URI],
            outputTreeDisplayName = prefs[Keys.OUTPUT_TREE_NAME] ?: "未選択",
            themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            duplicatePolicy = prefs[Keys.DUPLICATE_POLICY]?.let {
                runCatching { DuplicatePolicy.valueOf(it) }.getOrNull()
            } ?: DuplicatePolicy.RENAME,
            sourcePdfAction = prefs[Keys.SOURCE_PDF_ACTION]?.let {
                runCatching { SourcePdfAction.valueOf(it) }.getOrNull()
            } ?: SourcePdfAction.KEEP,
            autoDeleteTempFiles = prefs[Keys.AUTO_DELETE_TEMP] ?: true
        )
    }

    suspend fun updateImageFormat(format: ImageFormat) {
        context.settingsDataStore.edit { it[Keys.IMAGE_FORMAT] = format.name }
    }

    suspend fun updateDpi(dpi: Int) {
        context.settingsDataStore.edit { it[Keys.DPI] = dpi }
    }

    suspend fun updateJpegQuality(quality: Int) {
        context.settingsDataStore.edit { it[Keys.JPEG_QUALITY] = quality.coerceIn(1, 100) }
    }

    suspend fun updatePreferDirectExtraction(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.PREFER_DIRECT_EXTRACTION] = enabled }
    }

    suspend fun updateIncludeComicInfo(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.INCLUDE_COMIC_INFO] = enabled }
    }

    suspend fun updateOutputFolder(uriString: String?, displayName: String) {
        context.settingsDataStore.edit { prefs ->
            if (uriString == null) {
                prefs.remove(Keys.OUTPUT_TREE_URI)
                prefs[Keys.OUTPUT_TREE_NAME] = "未選択"
            } else {
                prefs[Keys.OUTPUT_TREE_URI] = uriString
                prefs[Keys.OUTPUT_TREE_NAME] = displayName
            }
        }
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun updateDuplicatePolicy(policy: DuplicatePolicy) {
        context.settingsDataStore.edit { it[Keys.DUPLICATE_POLICY] = policy.name }
    }

    suspend fun updateSourcePdfAction(action: SourcePdfAction) {
        context.settingsDataStore.edit { it[Keys.SOURCE_PDF_ACTION] = action.name }
    }

    suspend fun updateAutoDeleteTemp(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_DELETE_TEMP] = enabled }
    }
}
