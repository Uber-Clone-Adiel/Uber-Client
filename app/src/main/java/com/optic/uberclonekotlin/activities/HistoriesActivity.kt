package com.optic.uberclonekotlin.activities

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.optic.uberclonekotlin.adapters.HistoriesAdapter
import com.optic.uberclonekotlin.databinding.ActivityHistoriesBinding
import com.optic.uberclonekotlin.models.History
import com.optic.uberclonekotlin.providers.HistoryProvider

class HistoriesActivity : AppCompatActivity() {

    private lateinit var binding:  ActivityHistoriesBinding
    private var historyProvider = HistoryProvider()
    private var histories = ArrayList<History>()
    private lateinit var adapter: HistoriesAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val linearLayoutManager = LinearLayoutManager(this)
        binding.recyclerViewHistories.layoutManager = linearLayoutManager

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Historial de viajes"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setTitleTextColor(Color.WHITE)

        getHistories()
    }

    private fun getHistories() {
        histories.clear()

        historyProvider.getHistories().get().addOnSuccessListener { query ->
            if (query != null) {
                if (query.documents.size > 0) {
                    val documents = query.documents

                    for (d in documents) {
                        var history = d.toObject(History::class.java)
                        history?.id = d.id
                        histories.add(history!!)
                    }
                    adapter = HistoriesAdapter(this@HistoriesActivity, histories)
                    binding.recyclerViewHistories.adapter = adapter
                }
            }
        }
    }

}