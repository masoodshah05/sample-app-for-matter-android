package com.google.homesampleapp.newui.adapter

import android.content.Context
import android.text.Layout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.homesampleapp.R
import com.google.homesampleapp.databinding.ItemNamesDevicesBinding
import java.util.jar.Attributes.Name

class DevicesNamesAdapter(val context : Context , var list : ArrayList<String>, var callback :(String) -> Unit):RecyclerView.Adapter<NamesVH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NamesVH {
        return NamesVH(LayoutInflater.from(context).inflate(R.layout.item_names_devices , parent , false))
    }

    override fun getItemCount(): Int {
        return list.count()
    }

    override fun onBindViewHolder(holder: NamesVH, position: Int) {
        val item = list[position]
        holder.binding.nameTitle.text = item
        holder.itemView.setOnClickListener {
            callback.invoke(item)
        }
    }
}
class NamesVH(itemView : View):RecyclerView.ViewHolder(itemView){
    val binding = ItemNamesDevicesBinding.bind(itemView)
}