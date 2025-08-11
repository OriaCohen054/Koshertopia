package com.example.koshertopia.ui.restaurants

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.koshertopia.R

class SimpleImagePagerAdapter(
    private val ctx: Context,
    private val urls: List<String>
) : RecyclerView.Adapter<SimpleImagePagerAdapter.Holder>() {

    inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.item_IMG)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pager_image, parent, false)
        return Holder(v)
    }

    override fun getItemCount() = urls.size

    override fun onBindViewHolder(h: Holder, pos: Int) {
        val u = urls[pos]
        Glide.with(ctx)
            .load(u)
            .centerCrop()
            .into(h.img)
    }
}
