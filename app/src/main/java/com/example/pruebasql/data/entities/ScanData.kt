package com.example.pruebasql.data.entities

import android.os.Parcel
import android.os.Parcelable

data class ScanData(
    val code: String,
    val inputValue: String
): Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(code)
        parcel.writeString(inputValue)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ScanData> {
        override fun createFromParcel(parcel: Parcel): ScanData {
            return ScanData(parcel)
        }

        override fun newArray(size: Int): Array<ScanData?> {
            return arrayOfNulls(size)
        }
    }
}
