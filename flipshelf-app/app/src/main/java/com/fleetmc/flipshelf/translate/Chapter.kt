package com.fleetmc.flipshelf.translate

data class Chapter(val heading: String?, val paragraphs: List<String>)

class NoTextException(message: String) : Exception(message)
