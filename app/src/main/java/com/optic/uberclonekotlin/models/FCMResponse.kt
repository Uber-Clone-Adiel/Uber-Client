package com.optic.uberclonekotlin.models

class FCMResponse(
    val success: Int? = null,
    val failure: Int? = null,
    val canonical_ids: Int? = null,
    val multicast_ids: Long? = null,
    val results: ArrayList<Any> = ArrayList<Any>(),
) {
}