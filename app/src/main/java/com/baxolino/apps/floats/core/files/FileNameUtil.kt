package com.baxolino.apps.floats.core.files

object FileNameUtil {

  @JvmStatic
  fun toShortDisplayName(name: String): String {
    name.apply {
      // we limit the file name to certain characters
      // but while also displaying the file type
      val maximumChars = 12

      val dotIndex = indexOf('.')
      var fileName = if (dotIndex != -1) {
        substring(0, dotIndex)
      } else {
        this
      }
      val fileType = if (dotIndex != -1) {
        substring(dotIndex)
      } else {
        ""
      }
      if (fileName.length > maximumChars) {
        fileName = fileName.substring(0, maximumChars) + ".."
      }
      return fileName + fileType
    }
  }
}