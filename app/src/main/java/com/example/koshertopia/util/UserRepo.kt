package com.example.koshertopia.util
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

object UserRepo {

    /**
     * בודק אם האימייל כבר קיים באוסף הנבחר או באוסף השני.
     * - אם קיים באוסף הנבחר → קורא ל-onAlreadyInSelected
     * - אם קיים באוסף השני   → קורא ל-onExistsInOther
     * - אחרת                  → יוצר document(uid) באוסף הנבחר עם data וקורא ל-onCreated
     */
    fun ensureUniqueAndCreate(
        db: FirebaseFirestore,
        selectedCollection: String,
        otherCollection: String,
        emailRaw: String,
        uid: String,
        data: Map<String, Any?>,
        onAlreadyInSelected: () -> Unit,
        onExistsInOther: (String) -> Unit,
        onCreated: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val email = emailRaw.trim().lowercase()

        val qSelected = db.collection(selectedCollection)
            .whereEqualTo("email", email)
            .limit(1)
            .get()
        val qOther = db.collection(otherCollection)
            .whereEqualTo("email", email)
            .limit(1)
            .get()

        Tasks.whenAllSuccess<QuerySnapshot>(qSelected, qOther)
            .addOnSuccessListener { results ->
                val inSelected = !(results[0] as QuerySnapshot).isEmpty
                val inOther    = !(results[1] as QuerySnapshot).isEmpty

                when {
                    inSelected -> onAlreadyInSelected()
                    inOther    -> onExistsInOther(otherCollection)
                    else -> {
                        db.collection(selectedCollection).document(uid)
                            .set(data)
                            .addOnSuccessListener { onCreated() }
                            .addOnFailureListener(onError)
                    }
                }
            }
            .addOnFailureListener(onError)
    }

    /**
     * בדיקת ייחודיות אימייל בשני האוספים לפני יצירת משתמש ב-Auth.
     * אם לא קיים באף אוסף → onUnique()
     * אם קיים באוסף שנבחר → onExistsInSelected()
     * אם קיים באוסף השני  → onExistsInOther(otherCollection)
     */
    fun ensureEmailUniqueBeforeAuth(
        db: FirebaseFirestore,
        selectedCollection: String,
        otherCollection: String,
        emailRaw: String,
        onUnique: () -> Unit,
        onExistsInSelected: () -> Unit,
        onExistsInOther: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val email = emailRaw.trim().lowercase()

        val qSelected = db.collection(selectedCollection)
            .whereEqualTo("email", email)
            .limit(1)
            .get()
        val qOther = db.collection(otherCollection)
            .whereEqualTo("email", email)
            .limit(1)
            .get()

        Tasks.whenAllSuccess<QuerySnapshot>(qSelected, qOther)
            .addOnSuccessListener { results ->
                val inSelected = !(results[0] as QuerySnapshot).isEmpty
                val inOther    = !(results[1] as QuerySnapshot).isEmpty

                when {
                    inSelected -> onExistsInSelected()
                    inOther    -> onExistsInOther(otherCollection)
                    else       -> onUnique()
                }
            }
            .addOnFailureListener(onError)
    }
}