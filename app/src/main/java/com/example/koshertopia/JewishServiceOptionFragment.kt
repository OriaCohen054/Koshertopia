package com.example.koshertopia

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.fragment.app.Fragment

class JewishServiceOptionFragment : Fragment() {

    private lateinit var checkboxTitle: CheckBox
    private lateinit var contentContainer: LinearLayout

//    override fun onCreateView(
//        inflater: LayoutInflater,
//        container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View? {
//        val view = inflater.inflate(R.layout.fragment_jewish_service_option, container, false)
//        checkboxTitle = view.findViewById(R.id.option_checkbox)
//        contentContainer = view.findViewById(R.id.expanded_content)
//
//        val titleArg = arguments?.getString("title") ?: "Option"
//        checkboxTitle.text = titleArg
//
//        checkboxTitle.setOnCheckedChangeListener { _, isChecked ->
//            contentContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
//        }
//
//        return view
//    }
//
//    companion object {
//        fun newInstance(title: String): JewishServiceOptionFragment {
//            val fragment = JewishServiceOptionFragment()
//            val args = Bundle()
//            args.putString("title", title)
//            fragment.arguments = args
//            return fragment
//        }
//    }
}