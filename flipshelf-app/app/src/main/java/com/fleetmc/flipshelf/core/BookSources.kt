package com.fleetmc.flipshelf.core

import android.content.Context
import java.io.File

object BookSources {
    fun open(context: Context, library: Library, book: Book): BookSource =
        openFile(context, book.format, library.fileOf(book))

    fun openFile(context: Context, format: Format, file: File): BookSource = when (format) {
        Format.PDF -> PdfSource(context, file)
        Format.EPUB -> ReflowSource.fromEpub(file)
        Format.TEXT -> ReflowSource.fromText(file)
        Format.COMIC -> ImageSource.fromZip(file)
        Format.IMAGES -> ImageSource.fromFolder(file)
    }
}
