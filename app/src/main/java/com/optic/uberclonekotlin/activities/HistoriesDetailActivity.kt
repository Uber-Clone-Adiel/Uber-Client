package com.optic.uberclonekotlin.activities

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.WindowManager
import com.bumptech.glide.Glide
import com.optic.uberclonekotlin.databinding.ActivityHistoriesDetailBinding
import com.optic.uberclonekotlin.models.Client
import com.optic.uberclonekotlin.models.Driver
import com.optic.uberclonekotlin.models.History
import com.optic.uberclonekotlin.providers.ClientProvider
import com.optic.uberclonekotlin.providers.DriverProvider
import com.optic.uberclonekotlin.providers.HistoryProvider
import com.optic.uberclonekotlin.utils.RelativeTime

class HistoriesDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoriesDetailBinding
    private var historyProvider = HistoryProvider()
    private var driverProvider = DriverProvider()
    private var extraId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoriesDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        extraId = intent.getStringExtra("id")!!
        getHistory()

        binding.imageViewBack.setOnClickListener { finish() }
    }

    private fun getHistory() {
        historyProvider.getHistoryById(extraId).addOnSuccessListener { document ->

            if (document.exists()) {
                val history = document.toObject(History::class.java)
                binding.textViewOrigin.text = history?.origin
                binding.textViewDestination.text = history?.destination
                binding.textViewDate.text = RelativeTime.getTimeAgo(history?.timestamp!!, this@HistoriesDetailActivity)
                binding.textViewPrice.text = "${String.format("%.1f", history?.price)}$"
                binding.textViewMyCalification.text = "${history?.calificationToDriver}$"
                binding.textViewClientCalification.text = "${history?.calificationToClient}$"
                binding.textViewTimeAndDistance.text = "${history?.time} Min - ${String.format("%.1f", history?.km)} Km"

                getDriverInfo(history?.idDriver!!)
            }
        }
    }

    private fun getDriverInfo(id: String) {
        driverProvider.getDriver(id).addOnSuccessListener { document ->
            if (document.exists()) {
                val driver = document.toObject(Driver::class.java)
                binding.textViewEmail.text = driver?.email
                binding.textViewName.text = "${driver?.name} ${driver?.lastname}"
                if (driver?.image != null) {
                    if (driver?.image != "") {
                        Glide.with(this).load(driver?.image).into(binding.circleImageProfile)
                    }
                }
            }
        }
    }
}