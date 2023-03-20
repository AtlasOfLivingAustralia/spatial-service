package au.org.ala.spatial.dto

import groovy.transform.CompileStatic
//@CompileStatic
class MapDTO {

    Boolean available = false
    String url
    String dataResourceUID
    String dataResourceUrl
    String dataResourceName
    String licenseType
    String licenseVersion
    String rights
    String metadataUrl

    Boolean getAvailable() {
        return available
    }

    void setAvailable(Boolean available) {
        this.available = available
    }

    String getLicenseType() {
        return licenseType
    }

    void setLicenseType(String licenseType) {
        this.licenseType = licenseType
    }

    String getLicenseVersion() {
        return licenseVersion
    }

    void setLicenseVersion(String licenseVersion) {
        this.licenseVersion = licenseVersion
    }

    String getMetadataUrl() {
        return metadataUrl
    }

    void setMetadataUrl(String metadataUrl) {
        this.metadataUrl = metadataUrl
    }

    String getRights() {
        return rights

    }

    void setRights(String rights) {
        this.rights = rights
    }

    String getUrl() {
        return url
    }

    void setUrl(String url) {
        this.url = url
    }

    String getDataResourceUID() {
        return dataResourceUID
    }

    void setDataResourceUID(String dataResourceUID) {
        this.dataResourceUID = dataResourceUID
    }

    String getDataResourceUrl() {
        return dataResourceUrl
    }

    void setDataResourceUrl(String dataResourceUrl) {
        this.dataResourceUrl = dataResourceUrl
    }

    String getDataResourceName() {
        return dataResourceName
    }

    void setDataResourceName(String dataResourceName) {
        this.dataResourceName = dataResourceName
    }
}
