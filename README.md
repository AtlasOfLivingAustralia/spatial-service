###    [![Build Status](https://travis-ci.org/AtlasOfLivingAustralia/spatial-service.svg?branch=master)](https://travis-ci.org/AtlasOfLivingAustralia/spatial-service)

# spatial-service

This component provides the bulk of the spatial web services for the Atlas' spatial portal that make use of spatial 
data in Shape or Grid format.

It includes:
 
* A management console ingestion of Shape and Grid files
* Flexible field mapping capability allowing grouping of multipolygons by different fields provided in the DBF
* Maxent modelling 
* Tabulation
* Species polygon distributions
* Intersection services
* Track...


# Architecture

* Grails 3 web application ran in the tomcat 7 or as standalone executable jar
* Open JDK 8
* PostGIS database (9.6 or above)
* Geoserver

# Installation

There are ansible scripts for this applications (and other ALA tools) 
in the ala-install project. 
The ansible playbook for the spatial-service is here

You can also run this application locally by following the instructions on its wiki page.

# Running it locally

Here are some instructions for running spatial-service locally for development.
The assumption here is that you are trying to run spatial-service in an IDE such as IntelliJ


There is a docker-compose YML file that can be used to run postgres & geoserver
 locally for local development purposes. To use, run:

```
docker-compose -f geoserver-postgis.yml up -d
```

And to shutdown:

```
docker-compose -f geoserver-postgis.yml kill
```

There is also a requirement for have GDAL installed locally. This can be done with HomeBrew on Mac OSX

```
brew install gdal
```

Note: On Mac OSX, GGDAL tools are installed here `/usr/local/bin/`. You can use the `gdal.dir` configuration property to specify the location if different.

Configure geoserver with the required postgis layers using spatial-service: 
```
http://localhost:8080/tasks/create?name=InitGeoserver&input=%7B%22postgresqlPath%22:%22postgis%22%7D
```

Disable authentication when running locally by creating the configuration file `/data/spatial-service/config/spatial-service-config.properties` with the contents:
```
security.cas.bypass=true
security.cas.disableCAS=true
```
RequireApiKey annotation has been used in ShapeController.

It supports standard 'apiKey' stored in http header.
It also supports 'api_key' stored in http request params and POST body.

Make sure ApiKey check url has been set in config:

```
security:
    apikey:
        check:
            serviceUrl: "https://auth.ala.org.au/apikey/ws/check?apikey="
```