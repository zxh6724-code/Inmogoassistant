package com.example.academicvoiceassistan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * MainActivity - Academic Voice Assistant
 *
 * Uses Android SpeechRecognizer API to transcribe speech into text
 * for academic purposes: lecture notes, study questions, research ideas.
 */
class MainActivity : AppCompatActivity() {

    // --- Speech-to-Text state ---
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    // --- UI Components ---
    private lateinit var btnMic: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvTranscript: TextView
    private lateinit var btnClear: Button

    // Permission launcher for RECORD_AUDIO
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
            btnMic.isEnabled = true
        } else {
            Toast.makeText(
                this,
                "Microphone permission is required for speech recognition",
                Toast.LENGTH_LONG
            ).show()
            btnMic.isEnabled = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI
        btnMic = findViewById(R.id.btn_mic)
        tvStatus = findViewById(R.id.tv_status)
        tvTranscript = findViewById(R.id.tv_transcript)
        btnClear = findViewById(R.id.btn_clear)

        // Check and request microphone permission
        checkMicrophonePermission()

        // Set up microphone button
        btnMic.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }

        // Set up clear button
        btnClear.setOnClickListener {
            tvTranscript.text = getString(R.string.transcript_placeholder)
            tvStatus.text = getString(R.string.status_ready)
        }
    }

    /**
     * Checks if RECORD_AUDIO permission is granted.
     */
    private fun checkMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                btnMic.isEnabled = true
                tvStatus.text = getString(R.string.status_ready)
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                Toast.makeText(
                    this,
                    "Microphone access is needed for speech recognition in your academic assistant",
                    Toast.LENGTH_LONG
                ).show()
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // ========================================================================
    //  SPEECH-TO-TEXT
    // ========================================================================

    /**
     * Initializes and starts the SpeechRecognizer for real-time transcription.
     */
    private fun startListening() {
        // Check if SpeechRecognizer is available
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(
                this,
                "Speech recognition is not available on this device",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Create or reuse SpeechRecognizer
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    runOnUiThread {
                        tvStatus.text = getString(R.string.status_listening)
                    }
                }

                override fun onBeginningOfSpeech() {
                    runOnUiThread {
                        tvStatus.text = getString(R.string.status_listening)
                    }
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Can be used for visual feedback (e.g., volume meter)
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // Audio buffer received
                }

                override fun onEndOfSpeech() {
                    runOnUiThread {
                        tvStatus.text = getString(R.string.status_processing)
                    }
                }

                override fun onError(error: Int) {
                    isListening = false
                    updateButtonForIdle()

                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                        SpeechRecognizer.ERROR_NETWORK -> {
                            "Network error. Check your internet connection"
                        }
                        SpeechRecognizer.ERROR_AUDIO,
                        SpeechRecognizer.ERROR_NO_MATCH -> {
                            "Audio error. Please try again"
                        }
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            "No speech detected. Please try again."
                        }
                        SpeechRecognizer.ERROR_CLIENT -> {
                            "Speech recognition client error."
                        }
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            "Microphone permission is required."
                        }
                        else -> {
                            "Speech recognition error. Please try again"
                        }
                    }

                    runOnUiThread {
                        tvStatus.text = errorMessage
                        Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    updateButtonForIdle()

                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val transcribedText = matches[0]
                        runOnUiThread {
                            // Append new text to existing transcript
                            val currentText = tvTranscript.text.toString()
                            val placeholder = getString(R.string.transcript_placeholder)
                            if (currentText == placeholder) {
                                tvTranscript.text = transcribedText
                            } else {
                                tvTranscript.text = "$currentText\n\n$transcribedText"
                            }
                            tvStatus.text = getString(R.string.status_ready)
                        }
                    } else {
                        runOnUiThread {
                            tvStatus.text = "No speech recognized. Please try again."
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        runOnUiThread {
                            // Show partial result inline
                            val currentText = tvTranscript.text.toString()
                            val placeholder = getString(R.string.transcript_placeholder)
                            if (currentText == placeholder || currentText.endsWith("...")) {
                                tvTranscript.text = matches[0] + "..."
                            } else {
                                val lines = currentText.split("\n")
                                val baseText = lines.dropLast(1).joinToString("\n")
                                tvTranscript.text = "$baseText\n${matches[0]}..."
                            }
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Reserved for future events
                }
            })
        }

        // Build the speech recognition intent
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000)
        }

        isListening = true
        updateButtonForListening()
        speechRecognizer?.startListening(intent)

        Toast.makeText(this, "Listening... speak your question", Toast.LENGTH_SHORT).show()
    }

    /**
     * Stops the SpeechRecognizer if it is currently listening.
     */
    private fun stopListening() {
        speechRecognizer?.apply {
            stopListening()
            destroy()
        }
        speechRecognizer = null
        isListening = false
        updateButtonForIdle()
        tvStatus.text = getString(R.string.status_ready)
    }

    private fun updateButtonForListening() {
        btnMic.text = getString(R.string.btn_stop)
    }

    private fun updateButtonForIdle() {
        btnMic.text = getString(R.string.btn_speak)
    }

    // ========================================================================
    //  LIFECYCLE
    // ========================================================================

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.apply {
            destroy()
        }
        speechRecognizer = null
    }
}