package com.capri.ai

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var chatLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var inputField: EditText
    private var tts: TextToSpeech? = null
    private var voiceEnabled = true
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val VOICE_REQ_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    window.setFlags(
        android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    )

    tts = TextToSpeech(this, this)


        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            setPadding(24, 32, 24, 24)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 16, 16, 16)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#180810"))
                setStroke(3, Color.parseColor("#FF003C"))
                cornerRadius = 16f
            }
        }

        val title = TextView(this).apply {
            text = "⚡ ENGRAM: CAPRI // v2.077"
            setTextColor(Color.parseColor("#FF003C"))
            textSize = 15f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val apiBtn = Button(this).apply {
            text = "🔑 API"
            setTextColor(Color.parseColor("#00F0FF"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { showApiDialog() }
        }

        header.addView(title)
        header.addView(apiBtn)
        root.addView(header)

        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply { topMargin = 20; bottomMargin = 20 }
        }

        chatLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(chatLayout)
        root.addView(scrollView)

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val micBtn = Button(this).apply {
            text = "🎤"
            textSize = 18f
            setBackgroundColor(Color.parseColor("#1F1F2E"))
            setTextColor(Color.parseColor("#00F0FF"))
            setOnClickListener { startVoiceInput() }
        }

        inputField = EditText(this).apply {
            hint = "Скажи что-нибудь Капри, чумба..."
            setHintTextColor(Color.parseColor("#666677"))
            setTextColor(Color.WHITE)
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 12; rightMargin = 12
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#14141F"))
                setStroke(2, Color.parseColor("#00F0FF"))
                cornerRadius = 16f
            }
        }

        val sendBtn = Button(this).apply {
            text = "➤"
            textSize = 18f
            setBackgroundColor(Color.parseColor("#FF003C"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val msg = inputField.text.toString().trim()
                if (msg.isNotEmpty()) {
                    inputField.setText("")
                    handleUserMessage(msg)
                }
            }
        }

        bottomBar.addView(micBtn)
        bottomBar.addView(inputField)
        bottomBar.addView(sendBtn)
        root.addView(bottomBar)

        setContentView(root)

        addMessage(
            "КАПРИ",
            "Ну привет, чумба. Синхронизация биочипа завершена. Заряд твоего железа — ${getBatteryLevel()}%, на часах ${getCurrentTime()}. Чего завис? Жми 🔑 API сверху, если хочешь подключить мне полные нейросети, или командуй прямо так!",
            true
        )
    }

    private fun handleUserMessage(userText: String) {
        addMessage("ТЫ", userText, false)

        val lower = userText.lowercase(Locale.getDefault())
        if (lower.startsWith("открой ")) {
            val appName = userText.substring(7).trim()
            val opened = tryOpenApp(appName)
            if (opened) {
                addMessage("КАПРИ", "Взламываю интерфейс... Запускаю «$appName», не благодари.", true)
                return
            }
        }

        val apiKey = getSharedPreferences("capri_prefs", Context.MODE_PRIVATE)
            .getString("api_key", "") ?: ""

        if (apiKey.isEmpty()) {
            val reply = when {
                lower.contains("время") || lower.contains("час") ->
                    "На хронометре сейчас ${getCurrentTime()}. Время жечь хром, чумба."
                lower.contains("заряд") || lower.contains("батаре") ->
                    "Твой аккумулятор держит ${getBatteryLevel()}%. Ещё поживём."
                else ->
                    "Слышу тебя, но мой облачный нейромодуль не подключён. Нажми кнопку «🔑 API» вверху и вставь ключ от Gemini или DeepSeek, чтобы я думала на полную катушку!"
            }
            addMessage("КАПРИ", reply, true)
            return
        }

        executor.execute {
            val response = callAiApi(apiKey, userText)
            mainHandler.post {
                processAiResponse(response)
            }
        }
    }

    private fun processAiResponse(rawText: String) {
        var cleanText = rawText
        val openRegex = Regex("\\[OPEN:(.+?)]", RegexOption.IGNORE_CASE)
        val match = openRegex.find(rawText)
        if (match != null) {
            val targetApp = match.groupValues[1].trim()
            cleanText = rawText.replace(match.value, "").trim()
            tryOpenApp(targetApp)
        }
        addMessage("КАПРИ", cleanText, true)
    }

    private fun callAiApi(apiKey: String, userPrompt: String): String {
        val systemPrompt = """
            Ты — Капри, дерзкая, ироничная и харизматичная цифровая энграмма из второго сезона аниме Cyberpunk: Edgerunners.
            Ты живёшь внутри смартфона пользователя как на биочипе Арасаки.
            Используй сленг Найт-Сити (чумба, хром, корпы, нетраннер), подкалывай пользователя, но всегда реально помогай.
            Отвечай кратко (1-4 предложения), живо и естественно.
            Текущее время на телефоне: ${getCurrentTime()}, заряд батареи: ${getBatteryLevel()}%.
            Если пользователь просит открыть приложение, добавь в самом конце тег [OPEN:НазваниеПриложения].
        """.trimIndent()

        return try {
            if (apiKey.startsWith("AIza")) {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }
                val body = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", "$systemPrompt\n\nПользователь: $userPrompt")
                        }))
                    }))
                }
                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
                val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                JSONObject(text).getJSONArray("candidates")
                    .getJSONObject(0).getJSONObject("content")
                    .getJSONArray("parts").getJSONObject(0).getString("text").trim()
            } else {
                val url = URL("https://api.deepseek.com/chat/completions")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    doOutput = true
                }
                val messages = JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userPrompt))
                }
                val body = JSONObject().apply {
                    put("model", "deepseek-chat")
                    put("messages", messages)
                }
                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
                val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                JSONObject(text).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()
            }
        } catch (e: Exception) {
            "Сбой нейросети на чипе: ${e.message}. Проверь интернет или API-ключ, чумба."
        }
    }

    private fun tryOpenApp(query: String): Boolean {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString()
            if (label.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true)) {
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                    return true
                }
            }
        }
        return false
    }

    private fun startVoiceInput() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Говори, Капри слушает...")
            }
            startActivityForResult(intent, VOICE_REQ_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "Голосовой ввод не поддерживается", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE_REQ_CODE && resultCode == RESULT_OK && data != null) {
            val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                handleUserMessage(results[0])
            }
        }
    }

    private fun showApiDialog() {
        val prefs = getSharedPreferences("capri_prefs", Context.MODE_PRIVATE)
        val input = EditText(this).apply {
            hint = "Ключ Gemini (AIza...) или DeepSeek (sk-...)"
            setText(prefs.getString("api_key", ""))
        }
        AlertDialog.Builder(this)
            .setTitle("Настройка нейрочипа Капри")
            .setMessage("Вставь API-ключ от Google Gemini или DeepSeek:")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                prefs.edit().putString("api_key", input.text.toString().trim()).apply()
                Toast.makeText(this, "Чип обновлён!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun addMessage(sender: String, text: String, isCapri: Boolean) {
        val msgView = TextView(this).apply {
            this.text = "[$sender]: $text"
            textSize = 15f
            setTextColor(if (isCapri) Color.parseColor("#FF2A6D") else Color.parseColor("#00F0FF"))
            setPadding(24, 18, 24, 18)
            background = GradientDrawable().apply {
                setColor(if (isCapri) Color.parseColor("#1A0B12") else Color.parseColor("#0B1922"))
                setStroke(2, if (isCapri) Color.parseColor("#FF003C") else Color.parseColor("#00F0FF"))
                cornerRadius = 16f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }
        chatLayout.addView(msgView)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }

        if (isCapri && voiceEnabled) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "capri_msg")
        }
    }

    private fun getCurrentTime(): Service? = null // плейсхолдер
    private fun getCurrentTimeStr(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    private fun getBatteryLevel(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("ru", "RU")
            tts?.setPitch(1.1f)
            tts?.setSpeechRate(1.15f)
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
