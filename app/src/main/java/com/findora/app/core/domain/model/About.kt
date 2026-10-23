package com.findora.app.core.domain.model

import androidx.compose.runtime.Immutable

/** A third-party library shipped in the app and its licence. */
@Immutable
data class OpenSourceLibrary(
    val name: String,
    val author: String,
    val license: String,
    val url: String,
)

/** A photographer whose photos illustrate the sample listings, and how many of them are used. */
@Immutable
data class Photographer(
    val name: String,
    val url: String,
    val photos: Int,
)

/** Everything the About screen credits: libraries, listing photos (Pexels) and map data (OpenStreetMap). */
@Immutable
data class AboutCredits(
    val libraries: List<OpenSourceLibrary>,
    val photoSource: String,
    val photoLicense: String,
    val photoLicenseUrl: String,
    val photographers: List<Photographer>,
    val mapAttribution: String,
    val mapLicense: String,
    val mapUrl: String,
)
