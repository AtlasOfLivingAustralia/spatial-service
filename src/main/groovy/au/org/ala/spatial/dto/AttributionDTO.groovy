package au.org.ala.spatial.dto

import groovy.transform.CompileStatic
//@CompileStatic
class AttributionDTO {

    String websiteUrl
    String name
    String uid
    String rights
    String licenseType
    String licenseVersion
    String alaPublicUrl

    String getAlaPublicUrl() {
        return alaPublicUrl
    }

    void setAlaPublicUrl(String alaPublicUrl) {
        this.alaPublicUrl = alaPublicUrl
    }

    String getWebsiteUrl() {
        return websiteUrl
    }

    void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl
    }

    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }

    String getUid() {
        return uid
    }

    void setUid(String uid) {
        this.uid = uid
    }

    String getRights() {
        return rights
    }

    void setRights(String rights) {
        this.rights = rights
    }

    String getLicenseVersion() {
        return licenseVersion
    }

    void setLicenseVersion(String licenseVersion) {
        this.licenseVersion = licenseVersion
    }

    String getLicenseType() {
        return licenseType
    }

    void setLicenseType(String licenseType) {
        this.licenseType = licenseType
    }
}
