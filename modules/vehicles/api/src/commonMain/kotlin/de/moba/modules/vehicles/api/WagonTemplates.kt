package de.moba.modules.vehicles.api

/**
 * Bekannte Wagengattungen der Deutschen Bundesbahn als Vorlage zum Anlegen.
 * Eine Vorlage fuellt nur die Felder vor; alles laesst sich danach aendern.
 */
object WagonTemplates {
    private fun t(group: VehicleGroup, code: String, name: String, category: WagonCategory, epoch: Epoch) =
        WagonClass(id = "", group = group, code = code, name = name, category = category, railway = "DB", epoch = epoch)

    val freight = listOf(
        t(VehicleGroup.FREIGHT, "G 10", "Gedeckter Güterwagen", WagonCategory.COVERED, Epoch.III),
        t(VehicleGroup.FREIGHT, "Omm 52", "Offener Güterwagen", WagonCategory.OPEN, Epoch.III),
        t(VehicleGroup.FREIGHT, "Eaos", "Offener Hochbordwagen", WagonCategory.OPEN, Epoch.IV),
        t(VehicleGroup.FREIGHT, "Gbs", "Gedeckter Güterwagen", WagonCategory.COVERED, Epoch.IV),
        t(VehicleGroup.FREIGHT, "Hbbins", "Schiebewandwagen", WagonCategory.SLIDING_WALL, Epoch.IV),
        t(VehicleGroup.FREIGHT, "Res", "Rungenwagen", WagonCategory.FLAT, Epoch.IV),
        t(VehicleGroup.FREIGHT, "Fals", "Selbstentladewagen", WagonCategory.HOPPER, Epoch.IV),
        t(VehicleGroup.FREIGHT, "Tadns", "Schüttgutwagen mit Schwenkdach", WagonCategory.SLIDING_ROOF, Epoch.IV),
        t(VehicleGroup.FREIGHT, "Zacns", "Kesselwagen", WagonCategory.TANK, Epoch.V),
        t(VehicleGroup.FREIGHT, "Sgns", "Containertragwagen", WagonCategory.FLAT_SPECIAL, Epoch.V),
    )

    val passenger = listOf(
        t(VehicleGroup.PASSENGER, "Bnrz 451", "Nahverkehrswagen 2. Klasse (Silberling)", WagonCategory.SECOND, Epoch.IV),
        t(VehicleGroup.PASSENGER, "BDnrzf 740", "Steuerwagen mit Gepäckabteil", WagonCategory.CONTROL, Epoch.IV),
        t(VehicleGroup.PASSENGER, "Avmz 111", "Abteilwagen 1. Klasse", WagonCategory.FIRST, Epoch.IV),
        t(VehicleGroup.PASSENGER, "ABm 225", "Abteilwagen 1./2. Klasse", WagonCategory.MIXED, Epoch.IV),
        t(VehicleGroup.PASSENGER, "Bm 235", "Abteilwagen 2. Klasse", WagonCategory.SECOND, Epoch.IV),
        t(VehicleGroup.PASSENGER, "WRmh 132", "Speisewagen", WagonCategory.DINING, Epoch.IV),
        t(VehicleGroup.PASSENGER, "Bcm 243", "Liegewagen", WagonCategory.COUCHETTE, Epoch.IV),
        t(VehicleGroup.PASSENGER, "Dms 905", "Gepäckwagen", WagonCategory.LUGGAGE, Epoch.IV),
    )

    fun of(group: VehicleGroup) = when (group) {
        VehicleGroup.FREIGHT -> freight
        VehicleGroup.PASSENGER -> passenger
        VehicleGroup.LOCOS -> emptyList()
    }
}
