package com.example.lumora.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope

import com.example.lumora.GeminiHelper
import com.example.lumora.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var geminiHelper: GeminiHelper
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private var isTtsReady = false
    private var isSearchingForObject = false
    private var targetObject = ""
    private var autoScanJob: Job? = null
    private lateinit var vibrator: Vibrator

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startCamera()
            setupSpeechRecognizer()
        } else {
            Toast.makeText(this, "Camera and microphone permissions are needed.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        geminiHelper = GeminiHelper("AIzaSyA2LSMewR8XH9rde0GqJ8Q5WxPrAbOOJ9g")
        textToSpeech = TextToSpeech(this, this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        @Suppress("DEPRECATION")
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        if (allPermissionsGranted()) {
            startCamera()
            setupSpeechRecognizer()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }

        setupButtons()
    }

    private fun setupButtons() {
        binding.btnDescribeScene.setOnClickListener {
            stopObjectSearch()
            captureAndDescribe(
                "Describe this scene in detail for a visually impaired person. " +
                        "Include objects, people, text, colors, spatial layout, and any hazards. " +
                        "Be concise but thorough."
            )
        }

        binding.btnFindObject.setOnClickListener {
            showFindObjectDialog()
        }

        binding.btnVoiceCommand.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startListening()
                    binding.btnVoiceCommand.text = "Listening..."
                }
                MotionEvent.ACTION_UP -> {
                    speechRecognizer.stopListening()
                    binding.btnVoiceCommand.text = "Hold for Voice Command"
                }
            }
            true
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
                updateStatus("Ready. Tap a button or speak a command.")
            } catch (exc: Exception) {
                updateStatus("Error starting camera: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndDescribe(prompt: String, onResult: ((String) -> Unit)? = null) {
        val capture = imageCapture ?: return
        showLoading(true)
        updateStatus("Capturing image...")

        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap: Bitmap = image.toBitmap()
                    image.close()
                    updateStatus("Analyzing with AI...")
                    lifecycleScope.launch(Dispatchers.IO) {
                        val result = geminiHelper.analyzeImage(bitmap, prompt)
                        withContext(Dispatchers.Main) {
                            showLoading(false)
                            showDescription(result)
                            speak(result)
                            onResult?.invoke(result)
                        }
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    showLoading(false)
                    updateStatus("Error capturing image: ${exception.message}")
                }
            }
        )
    }

    private fun showFindObjectDialog() {
        val input = android.widget.EditText(this)
        input.hint = "e.g. chair, door, person"
        input.setPadding(48, 24, 48, 24)

        AlertDialog.Builder(this)
            .setTitle("Find an Object")
            .setMessage("What object should I look for?")
            .setView(input)
            .setPositiveButton("Start Searching") { _, _ ->
                val objectName = input.text.toString().trim()
                if (objectName.isNotEmpty()) {
                    startObjectSearch(objectName)
                } else {
                    speak("Please tell me what object to find.")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startObjectSearch(objectName: String) {
        stopObjectSearch()
        targetObject = objectName
        isSearchingForObject = true
        binding.tvSearchTarget.text = "Searching for: $objectName"
        binding.tvSearchTarget.visibility = View.VISIBLE
        updateStatus("Scanning for '$objectName'...")
        speak("Starting search for $objectName. I will alert you when found.")

        autoScanJob = lifecycleScope.launch {
            while (isSearchingForObject) {
                delay(3000)
                if (isSearchingForObject) scanForObject()
            }
        }
    }

    private fun scanForObject() {
        val prompt = """
            Look carefully at this image.
            Is there a '$targetObject' visible?
            Reply in EXACTLY this format:
            FOUND: yes or no
            LOCATION: where in the image
            CONFIDENCE: high, medium, or low
        """.trimIndent()

        captureAndDescribe(prompt) { result ->
            val isFound = result.contains("FOUND: yes", ignoreCase = true)
            if (isFound) {
                stopObjectSearch()
                val locationLine = result.lines()
                    .find { it.startsWith("LOCATION:", ignoreCase = true) }
                val location = locationLine?.substringAfter(":")?.trim() ?: "in the frame"
                val message = "Found $targetObject! It is $location."
                vibratePing()
                speak(message)
                updateStatus(message)
                showDescription("FOUND: $targetObject\n$location")
            } else {
                updateStatus("Scanning... '$targetObject' not yet visible.")
            }
        }
    }

    private fun vibratePing() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 100, 100, 300, 100, 500)
            val amplitudes = intArrayOf(0, 200, 0, 220, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 100, 100, 300, 100, 500), -1)
        }
    }

    private fun stopObjectSearch() {
        isSearchingForObject = false
        autoScanJob?.cancel()
        autoScanJob = null
        targetObject = ""
        binding.tvSearchTarget.visibility = View.GONE
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateStatus("Listening...")
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull()?.lowercase() ?: ""
                if (spokenText.isNotEmpty()) {
                    updateStatus("I heard: \"$spokenText\"")
                    processVoiceCommand(spokenText)
                } else {
                    speak("I did not catch that. Please try again.")
                }
            }
            override fun onError(error: Int) {
                updateStatus("Could not hear command. Please try again.")
            }
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onRmsChanged(rmsdB: Float) {}
        })
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            updateStatus("Could not start listening.")
        }
    }

    private fun processVoiceCommand(command: String) {
        when {
            command.contains("describe") || command.contains("what do you see") ||
                    command.contains("surroundings") -> {
                stopObjectSearch()
                captureAndDescribe(
                    "Describe this scene in detail for a visually impaired person. " +
                            "Include objects, people, text, layout, colors, and hazards."
                )
            }
            command.contains("find") || command.contains("look for") ||
                    command.contains("where is") || command.contains("search for") -> {
                val objectName = command
                    .replace(Regex("find|look for|search for|where is|the|a|an"), "")
                    .trim()
                if (objectName.isNotEmpty()) {
                    startObjectSearch(objectName)
                } else {
                    speak("What would you like me to find?")
                }
            }
            command.contains("read") || command.contains("what does it say") -> {
                stopObjectSearch()
                captureAndDescribe(
                    "Read ALL visible text in this image exactly as it appears. " +
                            "If there is no text, say so."
                )
            }
            command.contains("stop") || command.contains("cancel") -> {
                stopObjectSearch()
                textToSpeech.stop()
                updateStatus("Stopped.")
                speak("Stopped.")
            }
            command.contains("help") -> {
                speak("Say describe to describe the scene. Say find followed by an object name to search for it. Say read text to read visible text. Say stop to cancel.")
            }
            else -> {
                speak("I did not understand. Say help to hear available commands.")
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.setLanguage(Locale.US)
            textToSpeech.setSpeechRate(0.9f)
            isTtsReady = true
            speak("Vision Aid is ready.")
        }
    }

    private fun speak(text: String) {
        if (isTtsReady) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        }
    }

    private fun updateStatus(message: String) {
        runOnUiThread { binding.tvStatus.text = message }
    }

    private fun showDescription(text: String) {
        runOnUiThread {
            binding.tvDescription.text = text
            binding.tvDescription.visibility = View.VISIBLE
        }
    }

    private fun showLoading(show: Boolean) {
        runOnUiThread {
            binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnDescribeScene.isEnabled = !show
            binding.btnFindObject.isEnabled = !show
            if (show) binding.tvDescription.visibility = View.GONE
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        textToSpeech.stop()
        textToSpeech.shutdown()
        if (::speechRecognizer.isInitialized) speechRecognizer.destroy()
        stopObjectSearch()
    }
}