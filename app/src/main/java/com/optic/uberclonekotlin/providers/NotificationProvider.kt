package com.optic.uberclonekotlin.providers

import com.optic.uberclonekotlin.api.IFCMApi
import com.optic.uberclonekotlin.api.RetrofitClient
import com.optic.uberclonekotlin.models.FCMBody
import com.optic.uberclonekotlin.models.FCMResponse
import retrofit2.Call

class NotificationProvider {
    private val URL = "https://fcm.googleapis.com"

    fun sendNotification(body:FCMBody): Call<FCMResponse> {
        return RetrofitClient.getClient(URL).create(IFCMApi::class.java).send(body)
    }
}