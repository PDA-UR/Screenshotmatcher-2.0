package com.pda.screenshotmatcher2.utils

/**
 * Helper enum for all supported MIME types.
 *
 * Currently supported: PNG (= image/png)
 */
enum class MimeTypes {
    PNG, // add more mime types here if necessary
}

/**
 * Helper class to turn [MimeTypes] into their corresponding string representation.
 *
 * @constructor Creates a new [MimeType] instance.
 *
 * @param mimeType The [MimeTypes] enum to convert.
 */
class MimeType (mimeType: MimeTypes) {
    val string = when(mimeType) {
        MimeTypes.PNG -> "image/png"
        // add more mime types here if necessary
    }
}