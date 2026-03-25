package com.example.contextawarenotify

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import java.nio.LongBuffer
import org.json.JSONObject

class ModelHandler(private val context: Context) {
    private val TAG = "ModelHandler"
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val vocab = mutableMapOf<String, Int>()
    private val labels = mutableMapOf<Int, String>()

    init {
        Log.d(TAG, "Initializing ModelHandler...")
        val modelBytes = context.assets.open("model_quantized.onnx").use { it.readBytes() }
        session = env.createSession(modelBytes)

        // Load vocabulary
        context.assets.open("vocab.txt").bufferedReader().use { reader ->
            reader.readLines().forEachIndexed { index, word ->
                vocab[word] = index
            }
        }

        // Load labels
        try {
            val labelJson = context.assets.open("label_map.json").bufferedReader().use { it.readText() }
            val labelObj = JSONObject(labelJson)
            labelObj.keys().forEach { key ->
                labels[key.toInt()] = labelObj.get(key).toString()
            }
            Log.d(TAG, "Loaded ${labels.size} labels")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading label_map.json: ${e.message}")
        }
    }

    fun predict(text: String): String {
        try {
            val tokens = tokenize(text)
            Log.d(TAG, "Tokens for \"$text\": $tokens")

            val maxLen = 128 
            val inputIds = LongArray(maxLen) { 0L } 
            val attentionMask = LongArray(maxLen) { 0L }

            for (i in 0 until tokens.size.coerceAtMost(maxLen)) {
                inputIds[i] = tokens[i].toLong()
                attentionMask[i] = 1L
            }

            val shape = longArrayOf(1, maxLen.toLong())
            val inputIdTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape)
            val attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape)

            val inputs = mapOf(
                "input_ids" to inputIdTensor,
                "attention_mask" to attentionMaskTensor
            )

            session.run(inputs).use { results ->
                val output = results[0].value
                if (output is Array<*> && output[0] is FloatArray) {
                    val logits = output[0] as FloatArray
                    val predictionIdx = argMax(logits)
                    val label = labels[predictionIdx] ?: "Class $predictionIdx"
                    return "Result: $label"
                }
                return "Error: Unexpected model output format"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Prediction error", e)
            return "Error: ${e.message}"
        }
    }

    private fun tokenize(text: String): List<Int> {
        val tokens = mutableListOf<Int>()
        tokens.add(vocab["[CLS]"] ?: 101)

        // Improved logic: preserve punctuation as separate tokens like BertTokenizer
        val words = text.lowercase()
            .replace(Regex("([.,!?;])"), " $1 ") // Ensure punctuation is split out
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

        for (word in words) {
            if (vocab.containsKey(word)) {
                tokens.add(vocab[word]!!)
            } else {
                // Simplified WordPiece: check if it's a known word or UNK
                tokens.add(vocab["[UNK]"] ?: 100)
            }
        }

        tokens.add(vocab["[SEP]"] ?: 102)
        return tokens
    }

    private fun argMax(array: FloatArray): Int {
        var maxIdx = 0
        for (i in array.indices) {
            if (array[i] > array[maxIdx]) maxIdx = i
        }
        return maxIdx
    }

    fun close() {
        session.close()
        env.close()
    }
}
