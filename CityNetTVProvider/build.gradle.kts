// Use an integer for version numbers
version = 6

cloudstream {
    description = "CityNetTV - Azərbaycan, Türkiyə, Rusiya TV kanalları"
    authors = listOf("CityNetTV Dev")

    /**
     * Status int as one of the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta-only
     **/
    status = 1

    tvTypes = listOf("Live")

    requiresResources = false
    language = "az"

    // CityNetTV logo
    iconUrl = "https://citynettv.az/favicon.ico"
}
