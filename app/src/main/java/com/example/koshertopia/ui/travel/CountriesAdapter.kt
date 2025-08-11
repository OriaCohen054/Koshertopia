package com.example.koshertopia.ui.travel

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.koshertopia.R

data class CountryItem(val name: String, val drawableRes: Int)

class CountriesAdapter(
    private val items: MutableList<CountryItem> = mutableListOf(),
    private val onClick: (CountryItem) -> Unit
) : RecyclerView.Adapter<CountriesAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.country_image)
        val name: TextView = v.findViewById(R.id.country_name)
        val cityHint: TextView = v.findViewById(R.id.country_city_hint)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_country_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, i: Int) {
        val item = items[i]
        h.name.text = item.name
        h.img.setImageResource(item.drawableRes)
        h.itemView.setOnClickListener { _ -> onClick(item) }
        // או: h.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size

    fun submit(list: List<CountryItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }
}
