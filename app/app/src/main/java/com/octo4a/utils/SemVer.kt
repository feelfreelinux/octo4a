package com.octo4a.utils

// Utility taken from: https://github.com/swiftzer/semver

/**
 * Version number in [Semantic Versioning 2.0.0](http://semver.org/spec/v2.0.0.html) specification (SemVer).
 *
 * @property major major version, increment it when you make incompatible API changes.
 * @property minor minor version, increment it when you add functionality in a backwards-compatible manner.
 * @property patch patch version, increment it when you make backwards-compatible bug fixes.
 * @property preRelease pre-release version.
 * @property buildMetadata build metadata.
 */
data class SemVer(
    val major: Int = 0,
    val minor: Int = 0,
    val patch: Int = 0,
    val preRelease: String? = null,
    val buildMetadata: String? = null
) : Comparable<SemVer> {
    companion object {
        /**
         * Parse the version string to [SemVer] data object.
         * @param version version string.
         * @throws IllegalArgumentException if the version is not valid.
         */
        @JvmStatic
        fun parse(version: String): SemVer {
            val pattern = Regex("""(0|[1-9]\d*)?(?:\.)?(0|[1-9]\d*)?(?:\.)?(0|[1-9]\d*)?(?:-([\dA-z\-]+(?:\.[\dA-z\-]+)*))?(?:\+([\dA-z\-]+(?:\.[\dA-z\-]+)*))?""")
            val result = pattern.matchEntire(version) ?: throw IllegalArgumentException("Invalid version string [$version]")
            return SemVer(
                major = if (result.groupValues[1].isEmpty()) 0 else result.groupValues[1].toInt(),
                minor = if (result.groupValues[2].isEmpty()) 0 else result.groupValues[2].toInt(),
                patch = if (result.groupValues[3].isEmpty()) 0 else result.groupValues[3].toInt(),
                preRelease = if (result.groupValues[4].isEmpty()) null else result.groupValues[4],
                buildMetadata = if (result.groupValues[5].isEmpty()) null else result.groupValues[5]
            )
        }
    }

    init {
        require(major >= 0) { "Major version must be a positive number" }
        require(minor >= 0) { "Minor version must be a positive number" }
        require(patch >= 0) { "Patch version must be a positive number" }
        if (preRelease != null) require(preRelease.matches(Regex("""[\dA-z\-]+(?:\.[\dA-z\-]+)*"""))) { "Pre-release version is not valid" }
        if (buildMetadata != null) require(buildMetadata.matches(Regex("""[\dA-z\-]+(?:\.[\dA-z\-]+)*"""))) { "Build metadata is not valid" }
    }

    /**
     * Build the version name string.
     * @return version name string in Semantic Versioning 2.0.0 specification.
     */
    override fun toString(): String = buildString {
        append("$major.$minor.$patch")
        if (preRelease != null) {
            append('-')
            append(preRelease)
        }
        if (buildMetadata != null) {
            append('+')
            append(buildMetadata)
        }
    }

    /**
     * Check the version number is in initial development.
     * @return true if it is in initial development.
     */
    fun isInitialDevelopmentPhase(): Boolean = major == 0

    /**
     * Compare two SemVer objects using major, minor, patch and pre-release version as specified in SemVer specification.
     *
     * For comparing the whole SemVer object including build metadata, use [equals] instead.
     *
     * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object.
     */
    override fun compareTo(other: SemVer): Int {
        if (major > other.major) return 1
        if (major < other.major) return -1
        if (minor > other.minor) return 1
        if (minor < other.minor) return -1
        if (patch > other.patch) return 1
        if (patch < other.patch) return -1

        if (preRelease == null && other.preRelease == null) return 0
        if (preRelease != null && other.preRelease == null) return -1
        if (preRelease == null && other.preRelease != null) return 1

        val parts = preRelease.orEmpty().split(".")
        val otherParts = other.preRelease.orEmpty().split(".")

        val endIndex = Math.min(parts.size, otherParts.size) - 1
        for (i in 0..endIndex) {
            val part = parts[i]
            val otherPart = otherParts[i]
            if (part == otherPart) continue

            val partIsNumeric = part.isNumeric()
            val otherPartIsNumeric = otherPart.isNumeric()

            when {
                partIsNumeric && !otherPartIsNumeric -> {
                    // lower priority
                    return -1
                }
                !partIsNumeric && otherPartIsNumeric -> {
                    // higher priority
                    return 1
                }
                !partIsNumeric && !otherPartIsNumeric -> {
                    if (part > otherPart) return 1
                    if (part < otherPart) return -1
                }
                else -> {
                    val partInt = part.toInt()
                    val otherPartInt = otherPart.toInt()
                    if (partInt > otherPartInt) return 1
                    if (partInt < otherPartInt) return -1
                }
            }
        }

        return if (parts.size == endIndex + 1 && otherParts.size > endIndex + 1) {
            // parts is ended and otherParts is not ended
            -1
        } else if (parts.size > endIndex + 1 && otherParts.size == endIndex + 1) {
            // parts is not ended and otherParts is ended
            1
        } else {
            0
        }
    }

    private fun String.isNumeric(): Boolean = this.matches(Regex("""\d+"""))
}