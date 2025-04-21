package com.example.queueingcalculator

import android.content.res.Configuration
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.queueingcalculator.databinding.ActivityMainBinding
import com.example.queueingcalculator.databinding.ResultDialogBinding
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var historyAdapter: HistoryAdapter
    private val historyList = mutableListOf<CalculationHistory>()
    private var isNightMode = false
    private lateinit var userId: String
    private lateinit var sharedPreferences: SharedPreferences

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error inflating layout: ${e.message}")
            Toast.makeText(this, "Ошибка загрузки интерфейса", Toast.LENGTH_LONG).show()
            return
        }

        // Инициализация SharedPreferences и userId
        sharedPreferences = getSharedPreferences("QueueingCalculatorPrefs", MODE_PRIVATE)
        userId = sharedPreferences.getString("userId", null) ?: run {
            val newUserId = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("userId", newUserId).apply()
            newUserId
        }
        Log.d("MainActivity", "User ID: $userId")

        // Проверка текущей темы
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        isNightMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
        updateThemeButtonIcon()

        // Инициализация Spinner
        val variationOptions = arrayOf("1 1 1 1", "0.5 0.5 0.5 0.5", "0.1 0.1 0.1 0.1", "Собственное значение")
        binding.variationSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, variationOptions)
        binding.variationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                binding.variationInput.visibility = if (position == 3) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Инициализация RecyclerView
        try {
            historyAdapter = HistoryAdapter(historyList) { history ->
                showResultDialog(history.result)
            }
            binding.historyRecyclerView.apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = historyAdapter
            }
            loadHistoryFromServer()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing RecyclerView: ${e.message}")
            Toast.makeText(this, "Ошибка инициализации журнала", Toast.LENGTH_SHORT).show()
        }

        // TextWatcher и фокус для матрицы
        val matrixInputs = listOf(
            binding.matrix11, binding.matrix12, binding.matrix13, binding.matrix14,
            binding.matrix21, binding.matrix22, binding.matrix23, binding.matrix24,
            binding.matrix31, binding.matrix32, binding.matrix33, binding.matrix34,
            binding.matrix41, binding.matrix42, binding.matrix43, binding.matrix44
        )
        matrixInputs.forEach { editText ->
            editText.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && editText.text.toString() == "0") {
                    editText.setText("")
                } else if (!hasFocus && editText.text.isEmpty()) {
                    editText.setText("0")
                }
            }
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s.isNullOrEmpty() && !editText.hasFocus()) {
                        editText.setText("0")
                    }
                }
            })
        }

        // Кнопка очистки матрицы
        binding.clearMatrixButton.setOnClickListener {
            matrixInputs.forEach { it.setText("0") }
        }

        // Затемнение полей
        binding.serviceRateInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val values = s.toString().trim().split(" ").mapNotNull { it.toDoubleOrNull() }.toTypedArray()
                binding.serviceRateInput.alpha = if (values.size == 4) 1f else 0.5f
            }
        })

        binding.requestsInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val value = s.toString().toIntOrNull()
                binding.requestsInput.alpha = if (value != null) 1f else 0.5f
            }
        })

        binding.variationInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val values = s.toString().trim().split(" ").mapNotNull { it.toDoubleOrNull() }.toTypedArray()
                binding.variationInput.alpha = if (values.size == 4) 1f else 0.5f
            }
        })

        // Кнопка смены темы
        binding.toggleThemeButton.setOnClickListener {
            isNightMode = !isNightMode
            AppCompatDelegate.setDefaultNightMode(
                if (isNightMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            updateThemeButtonIcon()
        }

        // Экспорт истории
        binding.exportHistoryButton.setOnClickListener {
            exportHistoryToCsv()
        }

        // Кнопка "Рассчитать"
        binding.calculateButton.setOnClickListener {
            val matrix = getMatrixFromInputs() ?: return@setOnClickListener

            val serviceRateInput = binding.serviceRateInput.text.toString().trim()
            val serviceRate = serviceRateInput.split(" ").mapNotNull { it.toDoubleOrNull() }.toTypedArray()
            if (serviceRate.size != 4) {
                showErrorDialog("Введите ровно 4 числа в поле 'Интенсивность обслуживания', разделенные пробелами.")
                return@setOnClickListener
            }

            val requests = binding.requestsInput.text.toString().toIntOrNull()
            if (requests == null) {
                showErrorDialog("Введите одно целое число в поле 'Количество заявок'.")
                return@setOnClickListener
            }

            val variation = if (binding.variationSpinner.selectedItemPosition == 3) {
                val customInput = binding.variationInput.text.toString().trim()
                val customVariation = customInput.split(" ").mapNotNull { it.toDoubleOrNull() }.toTypedArray()
                if (customVariation.size != 4) {
                    showErrorDialog("Введите ровно 4 числа в поле 'Квадрат коэффициента вариации', разделенные пробелами.")
                    return@setOnClickListener
                }
                customVariation
            } else {
                binding.variationSpinner.selectedItem.toString().split(" ").map { it.toDouble() }.toTypedArray()
            }

            val request = CalculationRequest(
                userId = userId,
                timestamp = System.currentTimeMillis() / 1000,
                matrix = matrix,
                serviceRate = serviceRate,
                requests = requests,
                variation = variation
            )

            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.apiService.calculate(request)
                    if (response.isSuccessful) {
                        val result = response.body()
                        val newItem = CalculationHistory(
                            timestamp = request.timestamp * 1000,
                            serverTimestamp = result?.serverTimestamp ?: System.currentTimeMillis(),
                            userId = userId,
                            request = RequestData(
                                userId = userId,
                                timestamp = request.timestamp * 1000,
                                matrix = matrix,
                                serviceRate = serviceRate,
                                requests = requests,
                                variation = variation
                            ),
                            result = result
                        )
                        historyList.add(0, newItem)
                        historyAdapter.notifyItemInserted(0)
                        showResultDialog(result)
                        loadHistoryFromServer()
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        showErrorDialog("Ошибка сервера: $errorBody")
                    }
                } catch (e: Exception) {
                    showErrorDialog("Ошибка расчёта: ${e.message}")
                }
            }
        }
    }

    private fun loadHistoryFromServer() {
        lifecycleScope.launch {
            try {
                val history = RetrofitClient.apiService.getHistory(userId)
                historyList.clear()
                historyList.addAll(history)
                historyAdapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Ошибка загрузки истории: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getMatrixFromInputs(): Array<Array<Double>>? {
        val matrix = arrayOf(
            arrayOf(
                binding.matrix11.text.toString().toDoubleOrNull() ?: 0.0,
                binding.matrix12.text.toString().toDoubleOrNull() ?: 0.0,
                binding.matrix13.text.toString().toDoubleOrNull() ?: 0.0,
                binding.matrix14.text.toString().toDoubleOrNull() ?: 0.0
            ),
            arrayOf(
                binding.matrix21.text.toString().toDoubleOrNull() ?: 0.0,
                binding.matrix22.text.toString().toDoubleOrNull() ?: 0.0,
                binding.matrix23.text.toString().toDoubleOrNull() ?: 0.0,
                binding.matrix24.text.toString().toDoubleOrNull() ?: 0.0
            ),
            arrayOf(
                binding.matrix31.text.toString().toDoubleOrNull() ?: 0.0,
                binding.matrix32.text.toString().toDoubleOrNull() ?: 0.0,
                binding.matrix33.text.toString().toDoubleOrNull() ?: 0.0,
                binding.matrix34.text.toString().toDoubleOrNull() ?: 0.0
            ),
            arrayOf(
                binding.matrix41.text.toString().toDoubleOrNull() ?: 0.0,
                binding.matrix42.text.toString().toDoubleOrNull() ?: 0.0,
                binding.matrix43.text.toString().toDoubleOrNull() ?: 0.0,
                binding.matrix44.text.toString().toDoubleOrNull() ?: 0.0
            )
        )

        for (i in matrix.indices) {
            val rowSum = matrix[i].sum()
            if (rowSum > 1.0) {
                showErrorDialog("Сумма значений в строке ${i + 1} матрицы (${rowSum}) превышает 1.")
                return null
            }
        }
        return matrix
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun showResultDialog(result: CalculationResult?) {
        val dialogBinding = ResultDialogBinding.inflate(layoutInflater)
        result?.let {
            val resultText = StringBuilder()
            for (i in 0 until 4) {
                resultText.append("Узел ${i + 1}:\n")
                resultText.append("  Длина очереди: ${it.queueLength[i]}\n")
                resultText.append("  Время ожидания: ${it.waitingTime[i]}\n")
                resultText.append("  Время пребывания: ${it.residenceTime[i]}\n")
                resultText.append("  Интенсивность: ${it.flowRate[i]}\n\n")
            }
            dialogBinding.resultText.text = resultText.toString()
        } ?: run {
            dialogBinding.resultText.text = "Ошибка при расчете"
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("ОК") { dialog, _ -> dialog.dismiss() }
            .setCancelable(true)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(resources.getColor(R.color.purple_50, theme))
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Ошибка")
            .setMessage(message)
            .setPositiveButton("ОК") { dialog, _ -> dialog.dismiss() }
            .setCancelable(true)
            .show()
    }

    private fun exportHistoryToCsv() {
        val csvContent = StringBuilder()
        csvContent.append("UserId,Timestamp,ServerTimestamp,Requests,ServiceRate,Variation,QueueLength,WaitingTime,ResidenceTime,FlowRate\n")
        historyList.forEach { history ->
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val timestamp = dateFormat.format(Date(history.timestamp))
            val serverTimestamp = dateFormat.format(Date(history.serverTimestamp))
            val requests = history.request.requests
            val serviceRate = history.request.serviceRate.joinToString(" ")
            val variation = history.request.variation.joinToString(" ")
            val result = history.result?.let {
                "${it.queueLength.joinToString(" ")},${it.waitingTime.joinToString(" ")},${it.residenceTime.joinToString(" ")},${it.flowRate.joinToString(" ")}"
            } ?: "Error,Error,Error,Error"
            csvContent.append("${history.userId},$timestamp,$serverTimestamp,$requests,\"$serviceRate\",\"$variation\",$result\n")
        }

        val file = File(getExternalFilesDir(null), "history_${System.currentTimeMillis()}.csv")
        file.writeText(csvContent.toString())
        Toast.makeText(this, "История экспортирована в ${file.absolutePath}", Toast.LENGTH_LONG).show()
    }

    private fun updateThemeButtonIcon() {
        binding.toggleThemeButton.setCompoundDrawablesWithIntrinsicBounds(
            if (isNightMode) R.drawable.ic_dark_mode else R.drawable.ic_light_mode,
            0, 0, 0
        )
    }
}