package com.google.homesampleapp.newui.model

import com.google.homesampleapp.R

data class DevicesModel(
    var deviceTitle : String ?="",
    var deviceDetails : ArrayList<DeviceDetails> = ArrayList(),
)
data class DeviceDetails(
    var valueTitle :String ?="",
    var value :String ?="",
    var valueBgColor : Int ?= R.color.cardBG,
    var textColor : Int ?= 0
)