package com.fleetmc.verity.ui.nav

object Routes {
    const val HOME = "home"
    const val SAVED = "saved"
    const val INFO = "info"
    const val DETAIL_ARG = "id"
    const val DETAIL = "detail/{$DETAIL_ARG}"

    val topLevel = setOf(HOME, SAVED, INFO)

    fun detail(id: String) = "detail/$id"
}
