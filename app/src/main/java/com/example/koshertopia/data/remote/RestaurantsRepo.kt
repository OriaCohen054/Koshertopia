package com.example.koshertopia.data.remote

import com.example.koshertopia.util.Constants.BUSINESS_DB_NAME
import com.example.koshertopia.util.Constants.RESTAURANTS_DB_NAME
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import java.io.Serializable




// ------- List card VM -------
data class RestaurantCardVM(
    val id: String,
    val name: String,
    val summary: String,
    val country: String,
    val cuisine: String,          // normalized: MEAT / DAIRY / PAREVE / "" (unknown)
    val shabbat: Boolean,
    val coverUrl: String,
    val kidFriendly: Boolean = false,
    val accessible: Boolean = false,
    var favorite: Boolean = false
)

// ------- Lite (for Intent extra) -------
data class RestaurantLite(
    val restaurantId: String? = null,
    val name: String? = null,
    val shortDescription: String? = null,
    val kosherCertification: String? = null,
    val cuisineType: String? = null,
    val priceLevel: String? = null,
    val languages: List<String>? = null,

    // Opening hours (strings; empty => Closed)
    val hoursSunday: String? = null,
    val hoursMonday: String? = null,
    val hoursTuesday: String? = null,
    val hoursWednesday: String? = null,
    val hoursThursday: String? = null,
    val hoursFriday: String? = null,
    val hoursSaturday: String? = null,

    // Shabbat details
    val hasFridayMeal: Boolean? = null,
    val fridayMealCost: String? = null,
    val hasShabbatMeal: Boolean? = null,
    val shabbatMealCost: String? = null,
    val havdalahOnMotzash: Boolean? = null,

    // Features
    val accessible: Boolean? = null,
    val kidsMenu: Boolean? = null,
    val takeaway: Boolean? = null,
    val seating: Boolean? = null,
    val jew2go: Boolean? = null,

    // Media
    val logoUrl: String? = null,
    val kosherCertificateUrl: String? = null,
    val menuImageUrl: String? = null,

    // Extras
    val tableFee: String? = null,
    val toiletFee: String? = null,
    val currencySymbol: String? = null,
    val notes: String? = null
) : Serializable

// ------- Full DTO for details -------
data class RestaurantDetailsDTO(
    val id: String,
    val name: String = "",
    val shortDescription: String = "",
    val kosherCertification: String = "",
    val cuisineType: String = "",
    val priceLevel: String = "",          // "$", "$$", ...
    val languages: List<String> = emptyList(),

    // Opening hours (ready-to-display strings; empty => Closed)
    val hoursSunday: String = "",
    val hoursMonday: String = "",
    val hoursTuesday: String = "",
    val hoursWednesday: String = "",
    val hoursThursday: String = "",
    val hoursFriday: String = "",
    val hoursSaturday: String = "",

    // Shabbat details
    val hasFridayMeal: Boolean = false,
    val fridayMealCost: String = "",
    val hasShabbatMeal: Boolean = false,
    val shabbatMealCost: String = "",
    val havdalahOnMotzash: Boolean = false,

    // Features
    val accessible: Boolean = false,
    val kidsMenu: Boolean = false,
    val takeaway: Boolean = false,
    val seating: Boolean = false,
    val jew2go: Boolean = false,
    val nearTransit: Boolean = false, // kept for compatibility (not shown)

    // Media
    val logoUrl: String = "",
    val kosherCertificateUrl: String = "",
    val menuImageUrl: String = "",

    // NEW (fees + currency + notes)
    val tableFee: String = "",
    val toiletFee: String = "",
    val currencySymbol: String = "",
    val notes: String = ""
)

object RestaurantsRepo {
    private val db = FirebaseFirestore.getInstance()

    // -------------------------
    // Cards (list screen)
    // -------------------------
    //private const val BUSINESS_COLLECTION = "BUSINESSES"

    private fun anyString(doc: DocumentSnapshot?, vararg keys: String): String {
        if (doc == null) return ""
        for (k in keys) {
            val v = doc.getString(k)
            if (!v.isNullOrBlank()) return v
        }
        return ""
    }
    private fun businessName(doc: DocumentSnapshot?) =
        anyString(doc, "businessName", "name", "displayName", "title", "companyName")
    private fun businessLogo(doc: DocumentSnapshot?) =
        anyString(doc, "imageUrl", "logo", "businessLogoUrl")


    private fun fetchBusinessesMapFlexible(
        ids: Set<String>,
        onResult: (Map<String, DocumentSnapshot>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (ids.isEmpty()) { onResult(emptyMap()); return }
        val db = FirebaseFirestore.getInstance()
        val chunks = ids.chunked(10)
        val out = mutableMapOf<String, DocumentSnapshot>()
        var remaining = chunks.size * 2

        fun done() { if (--remaining == 0) onResult(out) }

        chunks.forEach { chunk ->
            db.collection(BUSINESS_DB_NAME)
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .addOnSuccessListener { s ->
                    s.documents.forEach { d ->
                        out[d.id] = d
                        d.getString("uid")?.let { out[it] = d }
                    }
                    done()
                }.addOnFailureListener(onError)

            db.collection(BUSINESS_DB_NAME)
                .whereIn("uid", chunk)
                .get()
                .addOnSuccessListener { s ->
                    s.documents.forEach { d ->
                        out[d.id] = d
                        d.getString("uid")?.let { out[it] = d }
                    }
                    done()
                }.addOnFailureListener(onError)
        }
    }


    private fun toCardFromBizAndRest(
        biz: DocumentSnapshot,
        rest: DocumentSnapshot?
    ): RestaurantCardVM {
        fun rs(key: String) = rest?.getString(key).orEmpty()
        fun rb(key: String) = rest?.getBoolean(key) == true

        val name = businessName(biz)
            .ifBlank { rs("restaurantName") }
            .ifBlank { rs("businessName") }
            .ifBlank { rs("name") }
            .ifBlank { "Restaurant" }

        val cover = when {
            businessLogo(biz).isNotBlank() -> businessLogo(biz)
            rs("logoUrl").isNotBlank() -> rs("logoUrl")
            rs("kosherCertificateUrl").isNotBlank() -> rs("kosherCertificateUrl")
            rs("menuImageUrl").isNotBlank() -> rs("menuImageUrl")
            else -> ""
        }

        val cuisine = normalizeCuisine(rs("cuisineType"))
        val shabbat = rb("fridayMealAvailable") || rb("saturdayMealAvailable") || rb("havdalahAvailable")

        val country = biz.getString("country").orEmpty()
            .ifBlank { rs("country") }

        return RestaurantCardVM(
            id = biz.id,                    // ← אותו מזהה לשני המסמכים
            name = name,
            summary = rs("notes"),
            country = country,
            cuisine = cuisine,
            shabbat = shabbat,
            coverUrl = cover,
            kidFriendly = rb("kidsMenu"),
            accessible = rb("accessible")
        )
    }

    private fun fetchRestaurantsMap(
        ids: Set<String>,
        onResult: (Map<String, DocumentSnapshot>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (ids.isEmpty()) { onResult(emptyMap()); return }
        val chunks = ids.chunked(10)
        val out = mutableMapOf<String, DocumentSnapshot>()
        var remain = chunks.size
        chunks.forEach { chunk ->
            FirebaseFirestore.getInstance().collection(RESTAURANTS_DB_NAME)
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .addOnSuccessListener { snap ->
                    snap.documents.forEach { out[it.id] = it }
                    if (--remain == 0) onResult(out)
                }
                .addOnFailureListener(onError)
        }
    }

    // קריאה במכה למסמכי Business לפי ה־IDs (whereIn בצ'אנקים של 10)
    private fun fetchBusinessesMap(
        ids: Set<String>,
        onResult: (Map<String, DocumentSnapshot>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (ids.isEmpty()) { onResult(emptyMap()); return }
        val out = mutableMapOf<String, DocumentSnapshot>()
        val chunks = ids.chunked(10)
        var remain = chunks.size
        chunks.forEach { chunk ->
            db.collection(BUSINESS_DB_NAME)
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .addOnSuccessListener { s ->
                    s.documents.forEach { out[it.id] = it }
                    if (--remain == 0) onResult(out)
                }
                .addOnFailureListener(onError)
        }
    }
    fun fetchByCountry(
        countryName: String,
        shabbatOnly: Boolean,
        cuisineFilter: Set<String>,
        onResult: (List<RestaurantCardVM>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        db.collection(RESTAURANTS_DB_NAME)
            .whereEqualTo("country", countryName)
            .get()
            .addOnSuccessListener { snap ->
                val restDocs = snap.documents
                val ids = restDocs.map { it.id }.toSet()

                fetchBusinessesMapFlexible( // ← מחפש גם לפי documentId וגם לפי uid
                    ids,
                    onResult = { bizMap ->
                        val list = restDocs.map { r ->
                            toCardEnriched(r, bizMap[r.id]) // ← bizMap מכיל גם key של uid
                        }.filter { vm ->
                            val passShabbat = if (!shabbatOnly) true else vm.shabbat
                            val passCuisine = if (cuisineFilter.isEmpty()) true else cuisineFilter.contains(vm.cuisine)
                            passShabbat && passCuisine
                        }
                        onResult(list)
                    },
                    onError = onError
                )
            }
            .addOnFailureListener(onError)
    }

    private fun toCard(d: DocumentSnapshot): RestaurantCardVM {
        fun s(k: String) = d.getString(k).orEmpty()
        fun b(k: String) = d.getBoolean(k) == true

        // שם: כל הנפילות האפשריות
        val name = s("restaurantName")
            .ifBlank { s("businessName") }
            .ifBlank { s("name") }
            .ifBlank { "Restaurant" }

        val summary = s("notes")

        // כשרות: לנרמל ל‑MEAT/DAIRY/PAREVE
        val cuisine = normalizeCuisine(s("cuisineType"))

        // יש משהו לשבת?
        val shabbat = b("fridayMealAvailable") || b("saturdayMealAvailable") || b("havdalahAvailable")

        // תמונה לכרטיס: לוגו -> תעודה -> תפריט
        val cover = when {
            s("logoUrl").isNotBlank() -> s("logoUrl")
            s("kosherCertificateUrl").isNotBlank() -> s("kosherCertificateUrl")
            s("menuImageUrl").isNotBlank() -> s("menuImageUrl")
            else -> "" // ה‑Adapter כבר מציג placeholder
        }

        return RestaurantCardVM(
            id = d.id,
            name = name,
            summary = summary,
            country = s("country"),
            cuisine = cuisine,
            shabbat = shabbat,
            coverUrl = cover,
            kidFriendly = b("kidsMenu"),
            accessible  = b("accessible")
        )
    }

    // -------------------------
    // Details (details screen)
    // -------------------------

    fun fetchById(restaurantId: String, onResult: (RestaurantDetailsDTO) -> Unit, onError: (Exception) -> Unit) {
        db.collection(RESTAURANTS_DB_NAME).document(restaurantId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) { onError(IllegalStateException("Restaurant not found")); return@addOnSuccessListener }
                val base = toDetails(doc)
                val needBiz = base.name.isBlank() || base.logoUrl.isBlank()
                if (!needBiz) {
                    onResult(base)
                } else {
                    db.collection(BUSINESS_DB_NAME).document(restaurantId).get()
                        .addOnSuccessListener { biz ->
                            onResult(base.copy(
                                name = base.name.ifBlank { businessName(biz) },
                                logoUrl = base.logoUrl.ifBlank { businessLogo(biz) }
                            ))
                        }
                        .addOnFailureListener { onResult(base) }
                }
            }
            .addOnFailureListener(onError)
    }
    private fun toDetails(d: DocumentSnapshot): RestaurantDetailsDTO {
        fun s(k: String) = d.getString(k).orEmpty()
        fun b(k: String) = d.getBoolean(k) == true
        @Suppress("UNCHECKED_CAST")
        fun strList(k: String) = (d.get(k) as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

        return RestaurantDetailsDTO(
            id = d.id,
            name = s("restaurantName").ifBlank { s("businessName") }.ifBlank { s("name") },
            shortDescription = s("shortDescription"),
            kosherCertification = s("kosherCertification"),
            cuisineType = s("cuisineType"),
            priceLevel = s("priceLevel"),
            languages = strList("languages"),

            hoursSunday = hoursFromMap(d, "Sunday"),
            hoursMonday = hoursFromMap(d, "Monday"),
            hoursTuesday = hoursFromMap(d, "Tuesday"),
            hoursWednesday = hoursFromMap(d, "Wednesday"),
            hoursThursday = hoursFromMap(d, "Thursday"),
            hoursFriday = hoursFromMap(d, "Friday"),
            hoursSaturday = hoursFromMap(d, "Saturday"),

            hasFridayMeal = b("fridayMealAvailable"),
            fridayMealCost = s("fridayMealFee"),
            hasShabbatMeal = b("saturdayMealAvailable"),
            shabbatMealCost = s("saturdayMealFee"),
            havdalahOnMotzash = b("havdalahAvailable"),

            accessible = b("accessible"),
            kidsMenu = b("kidsMenu"),
            takeaway = b("takeaway"),
            seating = b("seatingAvailable"),
            jew2go = b("jew2go"),
            nearTransit = false,

            logoUrl = s("logoUrl"),
            kosherCertificateUrl = s("kosherCertificateUrl"),
            menuImageUrl = s("menuImageUrl"),

            tableFee = s("tableFee"),
            toiletFee = s("toiletFee"),
            currencySymbol = s("currency"),
            notes = s("notes")
        )
    }


    @Deprecated("Use fetchById(...) which returns RestaurantDetailsDTO")
    fun getById(id: String, onResult: (Map<String, Any?>?) -> Unit, onError: (Exception)->Unit) {
        db.collection(RESTAURANTS_DB_NAME).document(id).get()
            .addOnSuccessListener { onResult(it.data) }
            .addOnFailureListener(onError)
    }

    private fun normalizeCuisine(raw: String): String {
        val up = raw.trim().uppercase()
        return when {
            up.contains("MEAT") || up.contains("בשר") -> "MEAT"
            up.contains("DAIRY") || up.contains("חלבי") -> "DAIRY"
            up.contains("PAREVE") || up.contains("PARVE") || up.contains("פרווה") -> "PAREVE"
            else -> up
        }
    }

    private fun formatRanges(list: List<Map<String, Any?>>?): String {
        if (list.isNullOrEmpty()) return ""
        return list.joinToString(", ") { m ->
            val a = m["first"]?.toString().orEmpty()
            val b = m["second"]?.toString().orEmpty()
            if (a.isNotBlank() && b.isNotBlank()) "$a – $b" else ""
        }.trim().trim(',')
    }

    @Suppress("UNCHECKED_CAST")
    private fun hoursFromMap(d: DocumentSnapshot, day: String): String {
        val oh = d.get("openingHours") as? Map<String, Any?> ?: return ""
        val arr = oh[day] as? List<Map<String, Any?>>
        return formatRanges(arr)
    }

    private fun toCardEnriched(rest: DocumentSnapshot, biz: DocumentSnapshot?): RestaurantCardVM {
        fun rs(k: String) = rest.getString(k).orEmpty()
        fun rb(k: String) = rest.getBoolean(k) == true

        val name = (biz?.getString("businessName")).orEmpty()
            .ifBlank { rs("restaurantName") }
            .ifBlank { rs("businessName") }
            .ifBlank { rs("name") }
        val logo = (biz?.getString("imageUrl")).orEmpty()
            .ifBlank { rs("logoUrl") }
            .ifBlank { rs("kosherCertificateUrl") }
            .ifBlank { rs("menuImageUrl") }

        val cuisine = normalizeCuisine(rs("cuisineType"))
        val shabbat = rb("fridayMealAvailable") || rb("saturdayMealAvailable") || rb("havdalahAvailable")

        return RestaurantCardVM(
            id = rest.id,
            name = name.ifBlank { "Restaurant" },
            summary = rs("notes"),
            country = rs("country"),
            cuisine = cuisine,
            shabbat = shabbat,
            coverUrl = logo,
            kidFriendly = rb("kidsMenu"),
            accessible = rb("accessible")
        )
    }
}


