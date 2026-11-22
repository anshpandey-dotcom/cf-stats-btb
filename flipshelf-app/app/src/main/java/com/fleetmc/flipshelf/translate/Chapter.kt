package com.fleetmc.flipshelf.translate

data class Block(val text: String, val level: Int = 0)

data class Chapter(val heading: String?, val blocks: List<Block>)

class NoTextException(message: String) : Exception(message)
