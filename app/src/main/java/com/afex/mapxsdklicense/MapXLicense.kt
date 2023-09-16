package com.afex.mapxsdklicense

object MapXLicense {
    init {
        java.lang.System.loadLibrary("crypto")
        java.lang.System.loadLibrary("mapxsdklicense")
    }

    external fun isLicenseValid(licenseKey: String): Boolean
    external fun getLicenseAPIInfo(licenseKey: String): String
    external fun getLicenseInfo(licenseKey: String): String
    class LicenseInitializationException(message: String) : java.lang.Exception(message)
    class InCorrectDeviceDateException(message: String) : java.lang.Exception(message)
    class InvalidLicenseException(message: String) : java.lang.Exception(message)
    class LicenseExpiredException(message: String) : java.lang.Exception(message)
    class LicenseParseException(message: String) : java.lang.Exception(message)
}