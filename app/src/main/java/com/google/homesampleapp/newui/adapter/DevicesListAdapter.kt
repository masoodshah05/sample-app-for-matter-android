package com.google.homesampleapp.newui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.homesampleapp.R
import com.google.homesampleapp.databinding.ItemDevicesBinding
import com.google.homesampleapp.newui.model.DevicesModel

class DevicesListAdapter(var context:Context , var list: ArrayList<DevicesModel> , val titleVisible : Boolean):RecyclerView.Adapter<DeviceVH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceVH {
        return DeviceVH(LayoutInflater.from(context).inflate(R.layout.item_devices , parent, false))
    }

    override fun getItemCount(): Int {
        return list.count()
    }

    override fun onBindViewHolder(holder: DeviceVH, position: Int) {
        val item = list[position]
        if (titleVisible) {

            holder.binding.title.text = item.deviceTitle
        }else{
            holder.binding.title.text = ""

        }
        holder.binding.recycler.layoutManager = GridLayoutManager(context , 4 , RecyclerView.VERTICAL , false)
        val adpter = DeviceDetailsAdapter(context , item.deviceDetails)
        holder.binding.recycler.adapter = adpter
    }
}
class DeviceVH(itemView :View) :RecyclerView.ViewHolder(itemView){
    val binding = ItemDevicesBinding.bind(itemView)
}