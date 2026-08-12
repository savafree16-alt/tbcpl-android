package com.tbcpl.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.Locale

private data class Site(val name: String, val url: String, val logo: String?, val enabled: Boolean)
private data class Category(val id: String, val name: String, val sites: List<Site>)
private data class Region(val code: String, val name: String, val flag: String, val enabled: Boolean)

class MainActivity : android.app.Activity() {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var search: EditText
    private lateinit var regionButton: Button
    private lateinit var favoritesButton: Button
    private lateinit var scroll: ScrollView
    private val categories = mutableListOf<Category>()
    private val regions = mutableListOf<Region>()
    private val favorites = linkedSetOf<String>()
    private val recent = ArrayDeque<String>()
    private var currentRegion = "USA"

    private val bg = Color.rgb(9, 9, 9)
    private val card = Color.rgb(23, 23, 23)
    private val text = Color.WHITE
    private val muted = Color.rgb(165, 165, 165)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setStatusBarColor(bg)
        window.setNavigationBarColor(bg)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        loadState()
        buildShell()
        loadRegions()
        loadRegion(currentRegion)
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(8))
        }
        val logo = ImageView(this).apply {
            setImageResource(com.tbcpl.app.R.drawable.tbcpl_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)))
        val title = TextView(this).apply {
            text = "TBCPL"
            setTextColor(text)
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(10), 0, 0, 0)
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))

        search = EditText(this).apply {
            hint = "Search sites..."
            setHintTextColor(Color.rgb(120, 120, 120))
            setTextColor(text)
            textSize = 14f
            singleLine = true
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(Color.rgb(25,25,25), 22)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render() }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        header.addView(search, LinearLayout.LayoutParams(dp(190), dp(42)).apply { setMargins(0, 0, dp(8), 0) })

        regionButton = headerButton("USA")
        regionButton.setOnClickListener { showRegionDialog() }
        header.addView(regionButton, LinearLayout.LayoutParams(dp(90), dp(42)).apply { setMargins(0,0,dp(8),0) })

        favoritesButton = headerButton("★")
        favoritesButton.contentDescription = "Favorites"
        favoritesButton.setOnClickListener { showFavorites() }
        header.addView(favoritesButton, LinearLayout.LayoutParams(dp(52), dp(42)))

        root.addView(header)

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), 0, dp(18), dp(6))
        }
        nav.addView(navButton("HOME") { loadRegion(currentRegion) }, navParams())
        nav.addView(navButton("FAVORITES") { showFavorites() }, navParams())
        nav.addView(navButton("RECENT") { showRecent() }, navParams())
        root.addView(nav)

        scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(28))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        search.requestFocus()
    }

    private fun loadRegions() {
        val json = readAsset("regions.json")
        val arr = JSONObject(json).optJSONArray("regions") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optBoolean("enabled", true)) regions.add(Region(o.optString("code"), o.optString("name"), o.optString("flag"), true))
        }
    }

    private fun loadRegion(code: String) {
        currentRegion = code.uppercase(Locale.US)
        regionButton.text = currentRegion
        categories.clear()
        val file = if (currentRegion == "USA") "links.USA.json" else "links.$currentRegion.json"
        val raw = try { readAsset(file) } catch (_: Exception) { readAsset("links.USA.json") }
        val arr = JSONObject(raw).optJSONArray("categories") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val sites = mutableListOf<Site>()
            val sa = c.optJSONArray("sites") ?: JSONArray()
            for (j in 0 until sa.length()) {
                val s = sa.getJSONObject(j)
                if (!s.optBoolean("enabled", true)) continue
                sites.add(Site(s.optString("name"), s.optString("url"), s.optString("logo", null), true))
            }
            categories.add(Category(c.optString("id"), c.optString("name"), sites))
        }
        render()
    }

    private fun render() {
        content.removeAllViews()
        val q = search.text?.toString()?.trim()?.lowercase(Locale.US).orEmpty()
        if (q.isNotEmpty()) {
            val all = categories.flatMap { it.sites.map { s -> it to s } }.filter { (c,s) -> s.name.lowercase(Locale.US).contains(q) || c.name.lowercase(Locale.US).contains(q) }
            addHeading("Search results", "${all.size} sites")
            if (all.isEmpty()) addEmpty("No matching sites found") else all.forEach { addSiteCard(it.second, single = true) }
        } else {
            addHero()
            categories.forEach { c ->
                if (c.sites.isNotEmpty()) {
                    addHeading(c.name, "${c.sites.size} sites")
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    c.sites.forEach { s -> row.addView(siteView(s), LinearLayout.LayoutParams(dp(165), dp(190)).apply { setMargins(0,0,dp(12),dp(10)) }) }
                    val hsv = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
                    content.addView(hsv)
                }
            }
            addFooter()
        }
    }

    private fun addHero() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = rounded(Color.rgb(19,19,19), 18)
        }
        val h = TextView(this).apply { text = "Your media index"; setTextColor(text); textSize = 26f; setTypeface(null, android.graphics.Typeface.BOLD) }
        val p = TextView(this).apply { text = "Movies & Shows • Anime • Manga • Live TV & Sports • Paid • Apps"; setTextColor(muted); textSize = 14f; setPadding(0,dp(7),0,0) }
        box.addView(h); box.addView(p)
        content.addView(box, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0,0,0,dp(18)) })
    }

    private fun addHeading(title: String, count: String) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(6)) }
        val t = TextView(this).apply { text = title; setTextColor(text); textSize = 20f; setTypeface(null, android.graphics.Typeface.BOLD) }
        val c = TextView(this).apply { text = "  $count"; setTextColor(muted); textSize = 12f }
        row.addView(t); row.addView(c)
        content.addView(row)
    }

    private fun addSiteCard(site: Site, single: Boolean) {
        content.addView(siteView(site), LinearLayout.LayoutParams(if (single) -1 else dp(165), dp(190)).apply { setMargins(0,0,0,dp(10)) })
    }

    private fun siteView(site: Site): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = getDrawable(R.drawable.card_bg)
            isFocusable = true
            isClickable = true
            setOnClickListener { openSite(site) }
        }
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
        loadLogo(iv, site.logo)
        box.addView(iv, LinearLayout.LayoutParams(-1, dp(105)))
        val name = TextView(this).apply {
            text = site.name
            setTextColor(text)
            textSize = 14f
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(5), 0, dp(2))
        }
        box.addView(name, LinearLayout.LayoutParams(-1, dp(42)))
        val star = TextView(this).apply {
            text = if (favorites.contains(key(site))) "★" else "☆"
            setTextColor(text)
            textSize = 18f
            gravity = Gravity.CENTER
            isFocusable = true
            setOnClickListener { toggleFavorite(site, this) }
            contentDescription = "Favorite ${site.name}"
        }
        box.addView(star, LinearLayout.LayoutParams(-1, dp(30)))
        return box
    }

    private fun addEmpty(message: String) {
        val t = TextView(this).apply { text = message; setTextColor(muted); textSize = 16f; gravity = Gravity.CENTER; setPadding(0,dp(50),0,dp(50)) }
        content.addView(t, LinearLayout.LayoutParams(-1, -2))
    }

    private fun addFooter() {
        val t = TextView(this).apply { text = "TBCPL • Directory / index\nExternal links open in your browser. Check the legality and safety of any service in your region."; setTextColor(Color.rgb(110,110,110)); textSize = 11f; gravity = Gravity.CENTER; setPadding(dp(10),dp(35),dp(10),dp(10)) }
        content.addView(t)
    }

    private fun showRegionDialog() {
        val names = regions.map { "${it.flag.uppercase()}  ${it.name}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Choose region").setItems(names) { _, which -> loadRegion(regions[which].code) }.show()
    }

    private fun showFavorites() {
        content.removeAllViews()
        addHeading("Favorites", "${favorites.size} saved")
        val sites = categories.flatMap { it.sites }.filter { favorites.contains(key(it)) }
        if (sites.isEmpty()) addEmpty("No favorites yet. Tap ☆ on a site to save it.") else sites.forEach { addSiteCard(it, true) }
    }

    private fun showRecent() {
        content.removeAllViews()
        addHeading("Recently visited", "${recent.size}")
        val map = categories.flatMap { it.sites }.associateBy { key(it) }
        val sites = recent.mapNotNull { map[it] }
        if (sites.isEmpty()) addEmpty("No recently visited sites.") else sites.forEach { addSiteCard(it, true) }
    }

    private fun openSite(site: Site) {
        try {
            val uri = Uri.parse(site.url)
            if (uri.scheme != "http" && uri.scheme != "https") return
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            val k = key(site)
            recent.remove(k); recent.addFirst(k)
            while (recent.size > 20) recent.removeLast()
            saveState()
        } catch (_: Exception) { Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show() }
    }

    private fun toggleFavorite(site: Site, view: TextView) {
        val k = key(site)
        if (!favorites.add(k)) favorites.remove(k)
        view.text = if (favorites.contains(k)) "★" else "☆"
        saveState()
    }

    private fun key(site: Site) = site.name.lowercase(Locale.US) + "|" + site.url

    private fun loadLogo(iv: ImageView, logo: String?) {
        if (logo.isNullOrBlank() || logo.startsWith("http")) {
            iv.setImageResource(R.drawable.tbcpl_logo)
            return
        }
        val clean = logo.removePrefix("./")
        try {
            assets.open(clean).use { stream -> iv.setImageBitmap(android.graphics.BitmapFactory.decodeStream(stream)) }
        } catch (_: Exception) { iv.setImageResource(R.drawable.tbcpl_logo) }
    }

    private fun loadState() {
        val p = getSharedPreferences("tbcpl", Context.MODE_PRIVATE)
        currentRegion = p.getString("region", "USA") ?: "USA"
        favorites.addAll(p.getStringSet("favorites", emptySet()) ?: emptySet())
        recent.addAll(p.getString("recent", "").orEmpty().split("\n").filter { it.isNotBlank() })
    }

    private fun saveState() {
        getSharedPreferences("tbcpl", Context.MODE_PRIVATE).edit()
            .putString("region", currentRegion)
            .putStringSet("favorites", favorites)
            .putString("recent", recent.joinToString("\n"))
            .apply()
    }

    private fun headerButton(label: String) = Button(this).apply {
        text = label; setTextColor(text); textSize = 12f; isAllCaps = false; background = getDrawable(R.drawable.button_bg); minHeight = 0; minWidth = 0; stateListAnimator = null
    }

    private fun navButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; setTextColor(text); textSize = 11f; isAllCaps = false; background = getDrawable(R.drawable.button_bg); minHeight = 0; minWidth = 0; stateListAnimator = null; setOnClickListener { action() }
    }

    private fun navParams() = LinearLayout.LayoutParams(0, dp(38), 1f).apply { setMargins(0,0,dp(7),0) }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }

    private fun readAsset(name: String): String = assets.open(name).bufferedReader().use { it.readText() }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
