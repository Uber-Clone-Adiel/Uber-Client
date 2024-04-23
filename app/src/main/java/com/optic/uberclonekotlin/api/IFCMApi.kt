package com.optic.uberclonekotlin.api

import com.optic.uberclonekotlin.models.FCMBody
import com.optic.uberclonekotlin.models.FCMResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface IFCMApi {
    @Headers(
        "Content-Type:application/json",
        "Authorization:key=AAAAtdvR3v0:APA91bHDc5-LcZVrfD7YoAYQUoHpUfGKnVf2Ic6m3cxVjme9qqaa4964I9R1-wNM558wx4je9omfqEbR-meRG3XGyVHEjWLO7we6PkU4QTQgXv30mBQIeDGBVcsArpE-HtywRtBONI9K"
    )
    @POST("fcm/send")
    fun send(@Body body: FCMBody): Call<FCMResponse>
}