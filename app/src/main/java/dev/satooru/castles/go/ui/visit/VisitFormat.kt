package dev.satooru.castles.go.ui.visit

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VisitFormat {
    /** 訪問日の表示（端末のタイムゾーン） */
    fun date(millis: Long): String =
        SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN).format(Date(millis))
}
