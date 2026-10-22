package com.findora.app.core.domain.model

/** Amenities a listing can offer. [id] is the stable value used by the seed JSON and the database. */
enum class Amenity(
    val id: String,
) {
    Wifi("wifi"),
    AirConditioning("air_conditioning"),
    PowerBackup("power_backup"),
    Lift("lift"),
    Security("security"),
    Cctv("cctv"),
    Gym("gym"),
    SwimmingPool("swimming_pool"),
    Garden("garden"),
    Balcony("balcony"),
    WashingMachine("washing_machine"),
    ModularKitchen("modular_kitchen"),
    WaterPurifier("water_purifier"),
    PlayArea("play_area"),
    Clubhouse("clubhouse"),
    SolarWaterHeater("solar_water_heater"),
    RainwaterHarvesting("rainwater_harvesting"),
    HomeOffice("home_office"),
    SeaView("sea_view"),
    BackwaterView("backwater_view"),
    ;

    companion object {
        private val byId = entries.associateBy(Amenity::id)

        fun fromId(id: String): Amenity = byId[id] ?: throw IllegalArgumentException("Unknown amenity: $id")

        fun fromIdOrNull(id: String): Amenity? = byId[id]
    }
}
