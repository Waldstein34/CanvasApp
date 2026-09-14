package com.tomoyasu.canvasapp

import android.content.Context

/** PC(Studio)への接続先を保存・読み込みするだけの小さな置き場。 */
object ConnectionPrefs {
    private const val FILE = "canvas_app_prefs"
    private const val KEY_HOST = "host"
    private const val KEY_PORT = "port"
    const val DEFAULT_PORT = "8770"

    fun loadHost(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_HOST, "") ?: ""

    fun loadPort(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_PORT, DEFAULT_PORT) ?: DEFAULT_PORT

    fun save(context: Context, host: String, port: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_HOST, host)
            .putString(KEY_PORT, port)
            .apply()
    }
}
