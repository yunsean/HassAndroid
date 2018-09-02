package cn.com.thinkwatch.ihass2.model

import android.os.Parcel
import android.os.Parcelable

data class Location(var latitude: Double = .0,
                    var longitude: Double = .0) : Parcelable {

    constructor(source: Parcel) : this(
            source.readDouble(),
            source.readDouble()
    )

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeDouble(latitude)
        writeDouble(longitude)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Location> = object : Parcelable.Creator<Location> {
            override fun createFromParcel(source: Parcel): Location = Location(source)
            override fun newArray(size: Int): Array<Location?> = arrayOfNulls(size)
        }
    }
}