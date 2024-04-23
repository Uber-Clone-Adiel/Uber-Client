package com.optic.uberclonekotlin.services

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.optic.uberclonekotlin.channel.NotificationHelper

class MyFirebaseMessagingClient: FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val title = data["title"]
        val body = data["body"]

        Log.d("NOTIFICACION", "Data: $data")
        Log.d("NOTIFICACION", "Title: $title")
        Log.d("NOTIFICACION", "Body: $body")

        if (!title.isNullOrBlank() && !body.isNullOrBlank()) {
            showNotification(title, body)
        }

    }

    private fun showNotification(title: String, body: String) {
        val helper = NotificationHelper(baseContext)
        val builder = helper.getNotification(title, body)
        helper.getManager().notify(1, builder.build())
    }


}