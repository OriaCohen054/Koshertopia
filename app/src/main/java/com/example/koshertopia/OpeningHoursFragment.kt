package com.example.koshertopia

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

class OpeningHoursFragment : Fragment() {

    companion object {
        private const val ARG_DAY_LABEL = "day_label"

        fun newInstance(dayLabel: String): OpeningHoursFragment {
            val fragment = OpeningHoursFragment()
            val args = Bundle()
            args.putString(ARG_DAY_LABEL, dayLabel)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dayLabel = arguments?.getString(ARG_DAY_LABEL) ?: "Day"
        view.findViewById<TextView>(R.id.opening_hours_label).text = "Opening hours – $dayLabel"
    }
}
