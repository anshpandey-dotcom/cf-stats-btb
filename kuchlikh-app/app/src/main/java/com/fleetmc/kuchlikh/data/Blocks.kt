package com.fleetmc.kuchlikh.data

enum class Block(val mark: Char?, val label: String) {
    Normal(null, "Normal"),
    Heading('⁡', "Heading"),
    Subtitle('⁢', "Subtitle"),
    Highlight('⁣', "Highlight"),
    Quote('⁤', "Quote"),
    Center('⁠', "Centered");

    val sized: Boolean get() = this == Heading || this == Subtitle
}

object Blocks {

    private val byMark: Map<Char, Block> = Block.entries.filter { it.mark != null }.associateBy { it.mark!! }

    fun isMark(c: Char): Boolean = c in byMark

    fun of(c: Char): Block? = byMark[c]

    fun strip(text: String): String = if (text.any { isMark(it) }) text.filterNot { isMark(it) } else text

    fun split(paragraph: String): Pair<Block, String> {
        val block = paragraph.firstOrNull()?.let { of(it) } ?: return Block.Normal to paragraph
        return block to paragraph.substring(1)
    }

    fun join(block: Block, text: String): String = (block.mark?.toString() ?: "") + text

    fun starts(pages: List<Page>, index: Int): Boolean =
        index == 0 || pages[index].flowStart || pages[index].chapter != null

    fun carry(pages: List<Page>, index: Int): Block? {
        if (index <= 0 || starts(pages, index) || pages[index - 1].para) return null
        return endStyle(pages, index - 1)
    }

    private fun endStyle(pages: List<Page>, index: Int): Block? {
        val text = pages[index].text
        val newline = text.lastIndexOf('\n')
        if (newline >= 0) return text.getOrNull(newline + 1)?.let { of(it) }
        text.firstOrNull()?.let { of(it) }?.let { return it }
        return carry(pages, index)
    }

    fun paragraphStart(pages: List<Page>, index: Int, offset: Int): Pair<Int, Int> {
        var page = index
        var limit = offset
        while (true) {
            val newline = if (limit <= 0) -1 else pages[page].text.lastIndexOf('\n', limit - 1)
            if (newline >= 0) return page to newline + 1
            if (page == 0 || starts(pages, page) || pages[page - 1].para) return page to 0
            page--
            limit = pages[page].text.length
        }
    }

    fun styleAt(pages: List<Page>, index: Int, offset: Int): Block {
        val (page, at) = paragraphStart(pages, index, offset)
        return pages[page].text.getOrNull(at)?.let { of(it) } ?: Block.Normal
    }
}
