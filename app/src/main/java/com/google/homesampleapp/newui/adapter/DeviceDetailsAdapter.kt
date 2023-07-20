package com.google.homesampleapp.newui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.homesampleapp.R
import com.google.homesampleapp.databinding.ItemDeviceDetailsBinding
import com.google.homesampleapp.newui.model.DeviceDetails

class DeviceDetailsAdapter (val context: Context , var list: ArrayList<DeviceDetails>):RecyclerView.Adapter<DeviceDetailsVH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceDetailsVH {
        return DeviceDetailsVH(LayoutInflater.from(context).inflate(R.layout.item_device_details , parent , false))
    }

    override fun getItemCount(): Int {
        return list.count()
    }

    override fun onBindViewHolder(holder: DeviceDetailsVH, position: Int) {
        val item = list[position]
        holder.binding.title.text = item.valueTitle
        holder.binding.tmpTitle.text = item.value
        holder.binding.tmpTitle.setTextColor(ResourcesCompat.getColor(context.resources , item.textColor ?:0 , null))

        if (item.valueBgColor == R.color.cardCheckedColor){
            holder.binding.tmpTitle.setTextColor(ResourcesCompat.getColor(context.resources , R.color.white ?:0 , null))
            holder.binding.title.setTextColor(ResourcesCompat.getColor(context.resources , R.color.white ?:0 , null))

        }
        holder.binding.card.setCardBackgroundColor(ResourcesCompat.getColor(context.resources , item.valueBgColor ?:0 , null))
    }
}
class  DeviceDetailsVH(itemView : View):RecyclerView.ViewHolder(itemView){
    val binding = ItemDeviceDetailsBinding.bind(itemView)
}