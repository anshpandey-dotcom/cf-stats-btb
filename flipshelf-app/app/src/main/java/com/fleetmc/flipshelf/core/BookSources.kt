package com.fleetmc.flipshelf.core

import java.io.File

object BookSources {
    fun open(library: Library, book: Book): BookSource {
        val file = library.fileOf(book)
        return when (book.format) {
            Format.PDF -> PdfSource(file)
            Format.EPUB -> ReflowSource.fromEpub(file)
            Format.TEXT -> ReflowSource.fromText(file)
            Format.COMIC -> ImageSource.fromZip(file)
            Format.IMAGES -> ImageSource.fromFolder(file)
        }
    }

    fun openFile(format: Format, file: File): BookSource = when (format) {
        Format.PDF -> PdfSource(file)
        Format.EPUB -> ReflowSource.fromEpub(file)
        Format.TEXT -> ReflowSource.fromText(file)
        Format.COMIC -> ImageSource.fromZip(file)
        Format.IMAGES -> ImageSource.fromFolder(file)
    }
}
