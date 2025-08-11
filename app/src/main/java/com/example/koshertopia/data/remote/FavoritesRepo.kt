package com.example.koshertopia.data.remote

import com.example.koshertopia.util.Constants.TRAVELER_DB_NAME
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

object FavoritesRepo {
    private val db = FirebaseFirestore.getInstance()
    private fun favs(uid: String) = db.collection(TRAVELER_DB_NAME).document(uid).collection("favorites")

    fun listen(uid: String, onSnapshot: (Set<String>) -> Unit, onError: (Exception)->Unit): ListenerRegistration =
        favs(uid).addSnapshotListener { s,e -> if (e!=null) onError(e) else onSnapshot(s?.documents?.map{it.id}?.toSet()?: emptySet()) }

    fun toggle(uid: String, businessId: String, countryKey: String, onDone: (Boolean)->Unit = {}) {
        val doc = favs(uid).document(businessId)
        doc.get().addOnSuccessListener { d ->
            if (d.exists()) doc.delete().addOnSuccessListener { onDone(false) }
            else doc.set(mapOf("businessId" to businessId, "countryKey" to countryKey, "createdAt" to com.google.firebase.Timestamp.now()))
                .addOnSuccessListener { onDone(true) }
        }
    }
}
