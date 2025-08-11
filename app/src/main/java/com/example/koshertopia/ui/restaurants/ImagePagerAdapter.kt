package com.example.koshertopia.ui.restaurants

import android.view.LayoutInflater; import android.view.View; import android.view.ViewGroup
import android.widget.ImageView; import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide; import com.example.koshertopia.R

class ImagePagerAdapter(private val imageUrls: List<String>) : RecyclerView.Adapter<ImagePagerAdapter.VH>() {
    inner class VH(v: View): RecyclerView.ViewHolder(v) { val img: ImageView = v.findViewById(R.id.pager_img) }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.pager_image_item, p, false))
    override fun onBindViewHolder(h: VH, i: Int) { Glide.with(h.img).load(imageUrls[i]).centerCrop().into(h.img) }
    override fun getItemCount() = imageUrls.size
}
