package com.example.koshertopia.ui.restaurants

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.koshertopia.R
import com.example.koshertopia.data.remote.RestaurantCardVM

class RestaurantsAdapter(
    private val items: MutableList<RestaurantCardVM>,
    private val onCardClick: (RestaurantCardVM) -> Unit,
    private val onToggleFav: (RestaurantCardVM) -> Unit
) : RecyclerView.Adapter<RestaurantsAdapter.VH>() {

    inner class VH(v: View): RecyclerView.ViewHolder(v) {
        val image: ImageView = v.findViewById(R.id.card_image)
        val title: TextView   = v.findViewById(R.id.card_title)
        val subtitle: TextView= v.findViewById(R.id.card_subtitle)
        init { v.setOnClickListener {
            val pos = bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onCardClick(items[pos])
        }}
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_restaurant_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, i: Int) = bindFull(h, items[i])

    private fun bindFull(h: VH, it: RestaurantCardVM) {
        h.title.text = it.name.ifBlank { "Restaurant" }

        val cuisinePretty = when (it.cuisine.uppercase()) {
            "MEAT" -> "Meat"; "DAIRY" -> "Dairy"; "PAREVE" -> "Pareve"
            else -> it.cuisine.ifBlank { "" }
        }
        h.subtitle.text = buildString {
            if (cuisinePretty.isNotBlank()) append(cuisinePretty)
            if (it.shabbat) append(if (isNotEmpty()) " • Shabbat" else "Shabbat")
        }

        Glide.with(h.image.context)
            .load(it.coverUrl.takeIf { url -> url.isNotBlank() })
            .centerCrop()
            .placeholder(R.drawable.placeholder_img)
            .error(R.drawable.placeholder_img)
            .into(h.image)
    }

    override fun getItemCount() = items.size

    fun submit(newItems: List<RestaurantCardVM>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
