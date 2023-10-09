package com.sameh.realtimelanguagetranslation

import android.R
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.sameh.realtimelanguagetranslation.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var lastTranslateQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinnerAdapters()
        setActions()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    private fun setupSpinnerAdapters() {
        val adapter = ArrayAdapter(this, R.layout.simple_spinner_item, languageNames)
        adapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        binding.apply {
            spinnerFrom.adapter = adapter
            spinnerTo.adapter = adapter
            spinnerFrom.setSelection(2)
            spinnerTo.setSelection(12)
        }
    }

    private fun setActions() {
        binding.apply {
            btnClearText.setOnClickListener {
                etInputTranslation.setText("")
                updateOutputET("")
            }
            btnRecordVoice.setOnClickListener {
                val languageCode = getLanguageCode(spinnerFrom.selectedItem.toString())
                if (languageCode != null)
                    recordVoice(languageCode)
                else
                    "Language Not Found".showToast()
            }
            spinnerFrom.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (etInputTranslation.text.toString().isNotEmpty())
                        handleTranslateFun(etInputTranslation.text.toString())
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

            }
            spinnerTo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (etInputTranslation.text.toString().isNotEmpty())
                        handleTranslateFun(etInputTranslation.text.toString())
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }
            etInputTranslation.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val newText = s.toString()
                    coroutineScope.coroutineContext.cancelChildren()
                    coroutineScope.launch {
                        delay(1000)
                        if (newText != lastTranslateQuery) {
                            handleTranslateFun(newText)
                        }
                    }
                }

                override fun afterTextChanged(s: Editable?) {

                }
            })
        }
    }

    private fun handleTranslateFun(text: String) {
        val currentSelectedLanguageFromCode =
            getTranslateLanguageCode(binding.spinnerFrom.selectedItem.toString())
        val currentSelectedLanguageToCode =
            getTranslateLanguageCode(binding.spinnerTo.selectedItem.toString())
        if (currentSelectedLanguageFromCode == currentSelectedLanguageToCode)
            "From Language Equal To Language !".showToast()
        else
            translateText(text, currentSelectedLanguageFromCode, currentSelectedLanguageToCode)
        lastTranslateQuery = text
    }

    private fun translateText(text: String, fromLanguage: String, toLanguage: String) {
        showProgress(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(fromLanguage)
                .setTargetLanguage(toLanguage)
                .build()
            val translator = Translation.getClient(options)

            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    translator.translate(text)
                        .addOnSuccessListener { translatedText ->
                            // Translation successful.
                            updateOutputET(translatedText)
                            translatedText.toLogD()
                            showProgress(false)
                        }
                        .addOnFailureListener { exception ->
                            // Error.
                            updateOutputET("")
                            exception.toString().toLogE()
                            exception.toString().showToast()
                            showProgress(false)
                        }
                }
                .addOnFailureListener { exception ->
                    // Model could not be downloaded or other internal error.
                    updateOutputET("")
                    exception.toString().toLogE()
                    exception.toString().showToast()
                    showProgress(false)
                }
        }
    }

    private fun showProgress(show: Boolean) {
        if (show)
            binding.outputProgressBar.visibility = View.VISIBLE
        else
            binding.outputProgressBar.visibility = View.GONE
    }

    private fun updateOutputET(text: String) {
        binding.etOutputTranslation.setText(text)
    }

    private fun recordVoice(languageCode: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to convert into text")
        }
        try {
            resultLauncher.launch(intent)
        } catch (e: Exception) {
            e.toString().toLogE()
            e.toString().showToast()
        }
    }

    @SuppressLint("SetTextI18n")
    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val intent: Intent? = result.data
                if (intent != null) {
                    val intentResult =
                        intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    if (intentResult != null)
                        binding.etInputTranslation.setText("${binding.etInputTranslation.text.toString()} ${intentResult[0]}")
                }
            }
        }

    private fun String.showToast() {
        Toast.makeText(this@MainActivity, this, Toast.LENGTH_SHORT).show()
    }

    private fun String.toLogD(tag: String = "appDebugTAG") {
        Log.d(tag, "Data: $this")
    }

    private fun String.toLogE(tag: String = "appDebugTAG") {
        Log.e(tag, "Data: $this")
    }

    private fun getLanguageCode(languageName: String): String? {
        val index = languageNames.indexOf(languageName)
        return if (index != -1 && index < languageCodes.size) {
            languageCodes[index]
        } else {
            null // Language not found
        }
    }

    private fun getTranslateLanguageCode(languageName: String): String {
        return when (languageName.lowercase()) {
            "afrikaans" -> TranslateLanguage.AFRIKAANS
            "albanian" -> TranslateLanguage.ALBANIAN
            "arabic" -> TranslateLanguage.ARABIC
            "belarusian" -> TranslateLanguage.BELARUSIAN
            "bengali" -> TranslateLanguage.BENGALI
            "bulgarian" -> TranslateLanguage.BULGARIAN
            "catalan" -> TranslateLanguage.CATALAN
            "chinese" -> TranslateLanguage.CHINESE
            "croatian" -> TranslateLanguage.CROATIAN
            "czech" -> TranslateLanguage.CZECH
            "danish" -> TranslateLanguage.DANISH
            "dutch" -> TranslateLanguage.DUTCH
            "english" -> TranslateLanguage.ENGLISH
            "esperanto" -> TranslateLanguage.ESPERANTO
            "estonian" -> TranslateLanguage.ESTONIAN
            "finnish" -> TranslateLanguage.FINNISH
            "french" -> TranslateLanguage.FRENCH
            "galician" -> TranslateLanguage.GALICIAN
            "georgian" -> TranslateLanguage.GEORGIAN
            "german" -> TranslateLanguage.GERMAN
            "greek" -> TranslateLanguage.GREEK
            "gujarati" -> TranslateLanguage.GUJARATI
            "hebrew" -> TranslateLanguage.HEBREW
            "hindi" -> TranslateLanguage.HINDI
            "hungarian" -> TranslateLanguage.HUNGARIAN
            "icelandic" -> TranslateLanguage.ICELANDIC
            "indonesian" -> TranslateLanguage.INDONESIAN
            "irish" -> TranslateLanguage.IRISH
            "italian" -> TranslateLanguage.ITALIAN
            "japanese" -> TranslateLanguage.JAPANESE
            "kannada" -> TranslateLanguage.KANNADA
            "korean" -> TranslateLanguage.KOREAN
            "lithuanian" -> TranslateLanguage.LITHUANIAN
            "latvian" -> TranslateLanguage.LATVIAN
            "macedonian" -> TranslateLanguage.MACEDONIAN
            "marathi" -> TranslateLanguage.MARATHI
            "malay" -> TranslateLanguage.MALAY
            "maltese" -> TranslateLanguage.MALTESE
            "norwegian" -> TranslateLanguage.NORWEGIAN
            "persian" -> TranslateLanguage.PERSIAN
            "polish" -> TranslateLanguage.POLISH
            "portuguese" -> TranslateLanguage.PORTUGUESE
            "romanian" -> TranslateLanguage.ROMANIAN
            "russian" -> TranslateLanguage.RUSSIAN
            "slovak" -> TranslateLanguage.SLOVAK
            "slovenian" -> TranslateLanguage.SLOVENIAN
            "spanish" -> TranslateLanguage.SPANISH
            "swedish" -> TranslateLanguage.SWEDISH
            "swahili" -> TranslateLanguage.SWAHILI
            "tagalog" -> TranslateLanguage.TAGALOG
            "tamil" -> TranslateLanguage.TAMIL
            "telugu" -> TranslateLanguage.TELUGU
            "thai" -> TranslateLanguage.THAI
            "turkish" -> TranslateLanguage.TURKISH
            "ukrainian" -> TranslateLanguage.UKRAINIAN
            "urdu" -> TranslateLanguage.URDU
            "vietnamese" -> TranslateLanguage.VIETNAMESE
            "welsh" -> TranslateLanguage.WELSH
            else -> TranslateLanguage.ENGLISH
        }
    }

    private val languageNames = arrayOf(
        "Afrikaans",
        "Albanian",
        "Arabic",
        "Belarusian",
        "Bengali",
        "Bulgarian",
        "Catalan",
        "Chinese",
        "Croatian",
        "Czech",
        "Danish",
        "Dutch",
        "English",
        "Esperanto",
        "Estonian",
        "Finnish",
        "French",
        "Galician",
        "Georgian",
        "German",
        "Greek",
        "Gujarati",
        "Hebrew",
        "Hindi",
        "Hungarian",
        "Icelandic",
        "Indonesian",
        "Irish",
        "Italian",
        "Japanese",
        "Kannada",
        "Korean",
        "Lithuanian",
        "Latvian",
        "Macedonian",
        "Marathi",
        "Malay",
        "Maltese",
        "Norwegian",
        "Persian",
        "Polish",
        "Portuguese",
        "Romanian",
        "Russian",
        "Slovak",
        "Slovenian",
        "Spanish",
        "Swedish",
        "Swahili",
        "Tagalog",
        "Tamil",
        "Telugu",
        "Thai",
        "Turkish",
        "Ukrainian",
        "Urdu",
        "Vietnamese",
        "Welsh"
    )

    private val languageCodes = arrayOf(
        "af",   // Afrikaans
        "sq",   // Albanian
        "ar",   // Arabic
        "be",   // Belarusian
        "bn",   // Bengali
        "bg",   // Bulgarian
        "ca",   // Catalan
        "zh",   // Chinese
        "hr",   // Croatian
        "cs",   // Czech
        "da",   // Danish
        "nl",   // Dutch
        "en",   // English
        "eo",   // Esperanto
        "et",   // Estonian
        "fi",   // Finnish
        "fr",   // French
        "gl",   // Galician
        "ka",   // Georgian
        "de",   // German
        "el",   // Greek
        "gu",   // Gujarati
        "he",   // Hebrew
        "hi",   // Hindi
        "hu",   // Hungarian
        "is",   // Icelandic
        "id",   // Indonesian
        "ga",   // Irish
        "it",   // Italian
        "ja",   // Japanese
        "kn",   // Kannada
        "ko",   // Korean
        "lt",   // Lithuanian
        "lv",   // Latvian
        "mk",   // Macedonian
        "mr",   // Marathi
        "ms",   // Malay
        "mt",   // Maltese
        "no",   // Norwegian
        "fa",   // Persian
        "pl",   // Polish
        "pt",   // Portuguese
        "ro",   // Romanian
        "ru",   // Russian
        "sk",   // Slovak
        "sl",   // Slovenian
        "es",   // Spanish
        "sv",   // Swedish
        "sw",   // Swahili
        "tl",   // Tagalog
        "ta",   // Tamil
        "te",   // Telugu
        "th",   // Thai
        "tr",   // Turkish
        "uk",   // Ukrainian
        "ur",   // Urdu
        "vi",   // Vietnamese
        "cy"    // Welsh
    )
}