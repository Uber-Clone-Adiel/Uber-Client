package com.optic.uberclonekotlin.models

import com.beust.klaxon.*

private val klaxon = Klaxon()

data class Client (
    var id: String? = null,
    val name: String? = null,
    val lastname: String? = null,
    val email: String? = null,
    val phone: String? = null,
    var image: String? = null,
    var token: String? = null,
) {
    public fun toJson() = klaxon.toJsonString(this)

    companion object {
        public fun fromJson(json: String) = klaxon.parse<Client>(json)
    }
}
