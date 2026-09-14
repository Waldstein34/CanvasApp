package com.tomoyasu.canvasapp

import org.json.JSONObject

/** Studioの canvasフォルダに保存されるJSONと同じ形。サーバー側の card_add 等が作る形に合わせている。 */
data class CanvasCard(
    val id: Int,
    val text: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val color: String?
)

data class CanvasZone(
    val id: Int,
    val name: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val color: String
)

data class CanvasLink(
    val id: Int,
    val from: Int,
    val to: Int,
    val label: String
)

data class CanvasBoard(
    val title: String,
    val cards: List<CanvasCard>,
    val zones: List<CanvasZone>,
    val links: List<CanvasLink>
)

fun parseCanvasBoard(json: JSONObject): CanvasBoard {
    val cards = mutableListOf<CanvasCard>()
    val cardsArr = json.optJSONArray("cards")
    if (cardsArr != null) {
        for (i in 0 until cardsArr.length()) {
            val o = cardsArr.getJSONObject(i)
            cards.add(
                CanvasCard(
                    id = o.optInt("id"),
                    text = o.optString("text", ""),
                    x = o.optDouble("x", 100.0).toFloat(),
                    y = o.optDouble("y", 100.0).toFloat(),
                    w = o.optDouble("w", 168.0).toFloat(),
                    color = o.optString("color").takeIf { it.isNotBlank() }
                )
            )
        }
    }

    val zones = mutableListOf<CanvasZone>()
    val zonesArr = json.optJSONArray("zones")
    if (zonesArr != null) {
        for (i in 0 until zonesArr.length()) {
            val o = zonesArr.getJSONObject(i)
            zones.add(
                CanvasZone(
                    id = o.optInt("id"),
                    name = o.optString("name", ""),
                    x = o.optDouble("x", 60.0).toFloat(),
                    y = o.optDouble("y", 60.0).toFloat(),
                    w = o.optDouble("w", 240.0).toFloat(),
                    h = o.optDouble("h", 170.0).toFloat(),
                    color = o.optString("color", "#4285F4")
                )
            )
        }
    }

    val links = mutableListOf<CanvasLink>()
    val linksArr = json.optJSONArray("links")
    if (linksArr != null) {
        for (i in 0 until linksArr.length()) {
            val o = linksArr.getJSONObject(i)
            links.add(
                CanvasLink(
                    id = o.optInt("id"),
                    from = o.optInt("from"),
                    to = o.optInt("to"),
                    label = o.optString("label", "")
                )
            )
        }
    }

    return CanvasBoard(
        title = json.optString("title", "キャンバス"),
        cards = cards,
        zones = zones,
        links = links
    )
}
