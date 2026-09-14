package com.tomoyasu.canvasapp

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
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

    sealed class BoardResult {
        data class Success(val board: CanvasBoard) : BoardResult()
        data class Failure(val message: String) : BoardResult()
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
        } catch (e: Exception) {
            TestResult.Failure(describeError(e))
        }
    }

    /** ネットワーク通信を行うので、呼び出し側はバックグラウンドスレッドから呼ぶこと。 */
    fun fetchBoard(host: String, port: String, board: String = "global"): BoardResult {
        val url = "http://$host:$port/api/canvas?board=" + java.net.URLEncoder.encode(board, "UTF-8")
        return try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return BoardResult.Failure("サーバーがエラーを返しました（HTTP ${response.code}）")
                }
                val body = response.body?.string().orEmpty()
                BoardResult.Success(parseCanvasBoard(org.json.JSONObject(body)))
            }
        } catch (e: Exception) {
            BoardResult.Failure(describeError(e))
        }
    }

    sealed class OpResult {
        data class Success(val json: JSONObject) : OpResult()
        data class Failure(val message: String) : OpResult()
    }

    /** card_add / card_update / card_delete など、/api/canvas/op への操作を1つ送る。
     * ネットワーク通信を行うので、呼び出し側はバックグラウンドスレッドから呼ぶこと。 */
    fun canvasOp(host: String, port: String, board: String, op: String, params: JSONObject): OpResult {
        val url = "http://$host:$port/api/canvas/op"
        val body = JSONObject(params.toString()).apply {
            put("board", board)
            put("op", op)
        }
        return try {
            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return OpResult.Failure("サーバーがエラーを返しました（HTTP ${response.code}）")
                }
                val json = JSONObject(text)
                if (json.has("error")) OpResult.Failure(json.getString("error"))
                else OpResult.Success(json)
            }
        } catch (e: Exception) {
            OpResult.Failure(describeError(e))
        }
    }

    private fun describeError(e: Exception): String = when (e) {
        is java.net.ConnectException -> "接続を拒否されました。IPアドレスとポート、Studioが起動しているかを確認してください"
        is java.net.SocketTimeoutException -> "応答がありません（タイムアウト）。同じネットワークにいるか確認してください"
        is java.net.UnknownHostException -> "IPアドレスの形式が正しくありません"
        else -> "エラー: ${e.message}"
    }
}
