package com.tomoyasu.canvasapp

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/** StudioのキャンバスAPIに繋がるかどうかだけを確認する。 */
object StudioClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    sealed class TestResult {
        data class Success(val boardCount: Int) : TestResult()
        data class Failure(val message: String) : TestResult()
    }

    /** ネットワーク通信を行うので、呼び出し側はバックグラウンドスレッドから呼ぶこと。 */
    fun testConnection(host: String, port: String): TestResult {
        val url = "http://$host:$port/api/canvas/boards"
        return try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return TestResult.Failure("サーバーがエラーを返しました（HTTP ${response.code}）")
                }
                val body = response.body?.string().orEmpty()
                val boards: JSONArray = org.json.JSONObject(body).getJSONArray("boards")
                TestResult.Success(boards.length())
            }
        } catch (e: java.net.ConnectException) {
            TestResult.Failure("接続を拒否されました。IPアドレスとポート、Studioが起動しているかを確認してください")
        } catch (e: java.net.SocketTimeoutException) {
            TestResult.Failure("応答がありません（タイムアウト）。同じネットワークにいるか確認してください")
        } catch (e: java.net.UnknownHostException) {
            TestResult.Failure("IPアドレスの形式が正しくありません")
        } catch (e: Exception) {
            TestResult.Failure("エラー: ${e.message}")
        }
    }
}
