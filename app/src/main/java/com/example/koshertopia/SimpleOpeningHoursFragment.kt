package com.example.koshertopia

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Locale

class SimpleOpeningHoursFragment : Fragment() {

    private lateinit var container: LinearLayout
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // אם תרצי – אפשר להוציא לרשימה ב-arrays.xml ולהחליף כאן בקריאה ל-resources
    private val daysOfWeek = listOf(
        "Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_simple_opening_hours, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view.findViewById(R.id.opening_hours_blocks_container)

        container.removeAllViews()
        daysOfWeek.forEach { day -> addOpeningHoursBlock(day) }
    }

    private fun addOpeningHoursBlock(dayLabel: String) {
        val inflater = LayoutInflater.from(requireContext())
        val block = inflater.inflate(R.layout.opening_hours_block, container, false)

        val title           = block.findViewById<TextView>(R.id.opening_hours_title)
        val checkBoxClosed  = block.findViewById<CheckBox>(R.id.checkbox_closed)
        val rangesContainer = block.findViewById<LinearLayout>(R.id.time_ranges_container)
        val addButton       = block.findViewById<Button>(R.id.button_add_time_range)

        title.text = "Opening Hours – $dayLabel"
        block.tag = dayLabel

        // טווח ראשון ברירת מחדל
        addTimeRangeView(rangesContainer)

        checkBoxClosed.setOnCheckedChangeListener { _, isChecked ->
            rangesContainer.isVisible = !isChecked
            addButton.isVisible = !isChecked
        }

        addButton.setOnClickListener { addTimeRangeView(rangesContainer) }

        container.addView(block)
    }

    private fun addTimeRangeView(parent: LinearLayout) {
        val inflater = LayoutInflater.from(requireContext())
        // זה ה-layout עם שני EditText: editText_opening_time, editText_closing_time
        val item = inflater.inflate(R.layout.item_time_range, parent, false)

        val fromEdit = item.findViewById<EditText>(R.id.editText_opening_time)
        val toEdit   = item.findViewById<EditText>(R.id.editText_closing_time)

        // כפתור מחיקה
        val removeButton = ImageButton(requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = null
            contentDescription = "Remove time range"
        }

        // שורה אופקית: ה-item מקבל משקל כדי שיכיל את השדות, וה-X בצד ימין לחיץ
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            val itemParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            addView(item, itemParams)

            val btnParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 12 }
            addView(removeButton, btnParams)
        }

        fromEdit.setOnClickListener { showTimePicker(fromEdit) }
        toEdit.setOnClickListener   { showTimePicker(toEdit) }

        removeButton.setOnClickListener { parent.removeView(row) }

        parent.addView(row)
    }

    private fun showTimePicker(target: EditText) {
        val timePicker = TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val formatted = String.format("%02d:%02d", hourOfDay, minute)
                target.setText(formatted)
            },
            9, 0, true
        )
        timePicker.show()
    }

    /* ---------- עזרי זמן וחפיפה ---------- */

    private fun parseTime(t: String) = try { timeFormat.parse(t) } catch (_: Exception) { null }

    /** from < to */
    private fun isValidTimeRange(start: String, end: String): Boolean {
        val s = parseTime(start)
        val e = parseTime(end)
        return s != null && e != null && s.before(e)
    }

    /** חפיפה אם start1 < end2 && start2 < end1 */
    private fun rangesOverlap(start1: String, end1: String, start2: String, end2: String): Boolean {
        val s1 = parseTime(start1) ?: return false
        val e1 = parseTime(end1)   ?: return false
        val s2 = parseTime(start2) ?: return false
        val e2 = parseTime(end2)   ?: return false
        return s1.before(e2) && s2.before(e1)
    }

    /* ---------- API ל-Activity ---------- */

    /** מחזיר מפה: יום → רשימת טווחים תקינים בלבד (from,to). אם היום סגור – רשימה ריקה. */
    fun getAllOpeningHours(): Map<String, List<Pair<String, String>>> {
        val result = mutableMapOf<String, List<Pair<String, String>>>()

        for (i in 0 until container.childCount) {
            val block = container.getChildAt(i)
            val day = block.tag as? String ?: continue

            val checkBoxClosed  = block.findViewById<CheckBox>(R.id.checkbox_closed)
            val rangesContainer = block.findViewById<LinearLayout>(R.id.time_ranges_container)

            if (checkBoxClosed.isChecked) {
                result[day] = emptyList()
                continue
            }

            val ranges = mutableListOf<Pair<String, String>>()
            for (j in 0 until rangesContainer.childCount) {
                val row = rangesContainer.getChildAt(j) as ViewGroup
                val fromEdit = row.findViewById<EditText>(R.id.editText_opening_time)
                val toEdit   = row.findViewById<EditText>(R.id.editText_closing_time)

                val from = fromEdit.text.toString().trim()
                val to   = toEdit.text.toString().trim()

                if (from.isNotEmpty() && to.isNotEmpty() && isValidTimeRange(from, to)) {
                    ranges += from to to
                }
            }
            result[day] = ranges
        }
        return result
    }

    /** בדיקת חפיפות לכל יום (לפי המפה שתחזיר getAllOpeningHours) */
    fun validateNoOverlaps(map: Map<String, List<Pair<String, String>>>): Boolean {
        map.forEach { (_, ranges) ->
            for (i in ranges.indices) {
                for (j in i + 1 until ranges.size) {
                    val (s1, e1) = ranges[i]
                    val (s2, e2) = ranges[j]
                    if (rangesOverlap(s1, e1, s2, e2)) return false
                }
            }
        }
        return true
    }

    /** לכל יום *פתוח* חייב להיות לפחות טווח אחד תקין (או שיסומן Closed). */
    fun validateAllDaysHaveRangeOrClosed(): Boolean {
        for (i in 0 until container.childCount) {
            val block = container.getChildAt(i)
            val checkBoxClosed  = block.findViewById<CheckBox>(R.id.checkbox_closed)
            if (checkBoxClosed.isChecked) continue

            val rangesContainer = block.findViewById<LinearLayout>(R.id.time_ranges_container)
            var hasAnyValid = false
            for (j in 0 until rangesContainer.childCount) {
                val row = rangesContainer.getChildAt(j) as ViewGroup
                val fromEdit = row.findViewById<EditText>(R.id.editText_opening_time)
                val toEdit   = row.findViewById<EditText>(R.id.editText_closing_time)
                val from = fromEdit.text.toString().trim()
                val to   = toEdit.text.toString().trim()
                if (from.isNotEmpty() && to.isNotEmpty() && isValidTimeRange(from, to)) {
                    hasAnyValid = true; break
                }
            }
            if (!hasAnyValid) return false
        }
        return true
    }

    /**
     * אופציונלי: ולידציה מלאה שמציגה הודעות שגיאה וממקדת בשדה הבעייתי.
     * אם תבחרי – ב-Activity אפשר פשוט לקרוא: if (!fragment.validateAllOrShowErrors()) return
     */
    fun validateAllOrShowErrors(): Boolean {
        for (i in 0 until container.childCount) {
            val block = container.getChildAt(i)
            val day = block.tag as? String ?: continue

            val checkBoxClosed  = block.findViewById<CheckBox>(R.id.checkbox_closed)
            val rangesContainer = block.findViewById<LinearLayout>(R.id.time_ranges_container)
            if (checkBoxClosed.isChecked) continue

            val ranges = mutableListOf<Pair<String,String>>()
            for (j in 0 until rangesContainer.childCount) {
                val row = rangesContainer.getChildAt(j) as ViewGroup
                val fromEdit = row.findViewById<EditText>(R.id.editText_opening_time)
                val toEdit   = row.findViewById<EditText>(R.id.editText_closing_time)

                val from = fromEdit.text.toString().trim()
                val to   = toEdit.text.toString().trim()

                if (from.isEmpty() || to.isEmpty()) {
                    Toast.makeText(requireContext(), "Please set hours for $day", Toast.LENGTH_LONG).show()
                    (if (from.isEmpty()) fromEdit else toEdit).requestFocus()
                    return false
                }
                if (!isValidTimeRange(from, to)) {
                    Toast.makeText(requireContext(), "$day: start time must be earlier than end time", Toast.LENGTH_LONG).show()
                    fromEdit.requestFocus()
                    return false
                }
                ranges += from to to
            }

            if (ranges.isEmpty()) {
                Toast.makeText(requireContext(), "Please add at least one time range for $day or mark it Closed", Toast.LENGTH_LONG).show()
                return false
            }

            for (a in ranges.indices) {
                for (b in a + 1 until ranges.size) {
                    val (s1, e1) = ranges[a]
                    val (s2, e2) = ranges[b]
                    if (rangesOverlap(s1, e1, s2, e2)) {
                        Toast.makeText(requireContext(), "$day: time ranges overlap", Toast.LENGTH_LONG).show()
                        return false
                    }
                }
            }
        }
        return true
    }

    /** יוצר שורת טווח שעות עם ערכי ברירת-מחדל, מבלי לגעת בפונקציות הקיימות. */
    fun addTimeRangeRowWithPreset(parent: LinearLayout, presetFrom: String?, presetTo: String?) {
        val inflater = LayoutInflater.from(requireContext())
        val item = inflater.inflate(R.layout.item_time_range, parent, false)

        val fromEdit = item.findViewById<EditText>(R.id.editText_opening_time)
        val toEdit   = item.findViewById<EditText>(R.id.editText_closing_time)

        // כפתור מחיקה
        val removeButton = ImageButton(requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = null
            contentDescription = "Remove time range"
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            val itemParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(item, itemParams)
            val btnParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 12 }
            addView(removeButton, btnParams)
        }

        presetFrom?.let { fromEdit.setText(it) }
        presetTo?.let { toEdit.setText(it) }

        fromEdit.setOnClickListener { showTimePicker(fromEdit) }
        toEdit.setOnClickListener   { showTimePicker(toEdit) }
        removeButton.setOnClickListener { parent.removeView(row) }

        parent.addView(row)
    }

    /** טוען שעות פתיחה ממפה שקוראים מפיירסטור (יום -> רשימת טווחים), בלי לגעת בקוד הקיים. */
    fun setAllOpeningHoursCompat(raw: Map<String, *>) {
        for (i in 0 until container.childCount) {
            val block = container.getChildAt(i)
            val day = block.tag as? String ?: continue

            val checkBoxClosed  = block.findViewById<CheckBox>(R.id.checkbox_closed)
            val rangesContainer = block.findViewById<LinearLayout>(R.id.time_ranges_container)
            val addBtn          = block.findViewById<Button>(R.id.button_add_time_range)

            rangesContainer.removeAllViews() // נבנה מחדש לפי הנתונים

            val value = raw[day]
            val ranges: List<Pair<String, String>> = when (value) {
                is List<*> -> value.mapNotNull { entry ->
                    when (entry) {
                        is Map<*, *> -> {
                            val f = (entry["first"] ?: entry["from"] ?: entry["start"])?.toString()?.trim().orEmpty()
                            val s = (entry["second"] ?: entry["to"]   ?: entry["end"])  ?.toString()?.trim().orEmpty()
                            if (f.isNotEmpty() && s.isNotEmpty()) f to s else null
                        }
                        is List<*> -> if (entry.size >= 2)
                            (entry[0]?.toString()?.trim().orEmpty() to entry[1]?.toString()?.trim().orEmpty())
                                .takeIf { it.first.isNotEmpty() && it.second.isNotEmpty() }
                        else null
                        is String -> {
                            val p = entry.split("–","—","-").map { it.trim() }
                            if (p.size >= 2 && p[0].isNotEmpty() && p[1].isNotEmpty()) p[0] to p[1] else null
                        }
                        else -> null
                    }
                }
                else -> emptyList()
            }

            if (ranges.isEmpty()) {
                checkBoxClosed.isChecked = true
                rangesContainer.visibility = View.GONE
                addBtn.visibility = View.GONE
            } else {
                checkBoxClosed.isChecked = false
                rangesContainer.visibility = View.VISIBLE
                addBtn.visibility = View.VISIBLE
                ranges.forEach { (from, to) ->
                    addTimeRangeRowWithPreset(rangesContainer, from, to)
                }
            }
        }
    }

}
