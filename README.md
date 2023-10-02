### [![Build Status](https://travis-ci.org/AtlasOfLivingAustralia/spatial-service.svg?branch=master)](https://travis-ci.org/AtlasOfLivingAustralia/spatial-service)

# spatial-service

####_See also:_  [Integration Test](#integration-test)

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

* Grails 4 web application ran in the tomcat 9 or as standalone executable jar
* Open JDK 8
* PostGIS database (9.6 or above)
* Geoserver

## Setup environment

Modify configurations in

    /data/spatial-service/config/spatial-service-config.yml

The dependent services point to other production servers by default

The default production url is https://spatial.ala.org.au/ws

The default develop url is http://devt.ala.org.au:8080/ws

#### Minimum configurations in external config file:

        api_key: xxxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxxx
        google:
            apikey: xxxxxxxxxxxxxx

#### Set the following configurations if deployed on servers, instead of development/prod environment

    grails.serverURL: https://spatial-test.ala.org.au/ws
    #grails.server.context: /ws

    google:
        apikey: "xxxxxxxxxxxx"

    api_key: xxxxxxxxxxxxx
    spatialHubUrl: https://spatial-test.ala.org.au/

    geoserver:
        url: 'https://spatial-test.ala.org.au/geoserver'
        username: 'admin'
        password: 'xxxxxxxx'

    dataSource:
        url: 'jdbc:postgresql://localhost/layersdb'
        username: postgres
        password: xxxxxxxxx
    
    batch_sampling_passwords: ""

    # au.org.ala.spatial.process config
    spatialService.url: "https://spatial-test.ala.org.au/ws"
    shp2pgsql.path: "/usr/bin/shp2pgsql"
    gdal.dir: "/usr/bin/"

    slaveKey: "xxxxxxxxxxxxxx"
    serviceKey: "xxxxxxxxxxxxxx"

    layers_store.GEONETWORK_URL: 'https://spatial-test.ala.org.au/geonetwork'

# Installation

There are ansible scripts for this applications (and other ALA tools)
in the ala-install project.
The ansible playbook for the spatial-service is here

You can also run this application locally by following the instructions on its wiki page.

# Running it locally

Here are some instructions for running spatial-service locally for development.
The assumption here is that you are trying to run spatial-service in an IDE such as IntelliJ

There is a docker-compose YML file under `docker` folder that can be used to run postgres & geoserver
locally for local development purposes. To use, run:

```
cd ./docker
docker-compose up -d
```

And to shutdown:

```
docker-compose -f kill
```

`Geoserver:  http://localhost:8079/geoserver`

`Postgis is under standard 5432 port`

There is also a requirement for have GDAL installed locally. This can be done with HomeBrew on Mac OSX

```
brew install gdal
```

Note: On Mac OSX, GGDAL tools are installed here `/usr/local/bin/` or `/opt/homebrew/Cellar/gdal/xxxx-version`. You can use the `gdal.dir` configuration property to
specify the location if different.

Configure geoserver with the required postgis layers using spatial-service:

```
http://localhost:8080/tasks/create?name=InitGeoserver&input=%7B%22postgresqlPath%22:%22postgis%22%7D
```

Disable authentication when running locally by creating the configuration
file `/data/spatial-service/config/spatial-service-config.properties` with the contents:

```
security.oidc.enabled=false
```

# PDF area report

Override output by putting edited files from `/src/main/resources/areareport/` into `/data/spatial-service/config/`.

* `*.html` Edit for internationalization.
* `AreaReport.css` Edit for style changes.
* `header.jpg` Edit the default front page header image.
* `AreaReportDetails.json` Definition of pages and page content in the area report.

The struture of `AreaReportDetails.json`

```
[
 {
  type: pageType,
  pageType attributes,
  items: [
   {
    type: itemType,
    itemType attributes
   }
  ]
 }  
]
```

Pages

| pageType | attribute | attribute description |
| --- | --- | --- |
| title | image | relative path to the image file
| | counts | array of items displayed in the style of a dashboard
| general | items | array of items
| | subpages | the number of subpages. Each page is identical except for the fq used.
| | fqs | array of biocache-service fq terms. One fq is required for each subpage.
| | {itemAttribute}{itemNumber} | array of item attribute values.
| file | file | relative path to the html file

Items

| itemType | attribute | attribute description |
| --- | --- | --- | 
| table | table | one of: species, expertdistributions, checklists, journalmap, occurrences, tabulation
| | value | mandatory for table=tabulation. The field for tabulation, e.g. cl22
| | endemic | optional for table=species. true or false (default=false)
| text | text | HTML text to display
| figure
| map | buffer | approximate buffer around the selected area as a % of the area width
| | layer | optional layer to add. Use layer short name.
| | legendUrl | optional URL to geoserver GetLegendGraphic request
| | fq | optional fq
| LABELS | type | one of: species, expertdistributions, checklists, journalmap, occurrences
| | label | HTML text format. e.g. "label value \<b>%s\</b>"
| | name | mandatory for type=attribute, area attribute: area_km, name
| | field | mandatory for type=species, count of unique values in a SOLR field. e.g. names_and_lsid
| | endemic | optional for type=species, use endemic species count. true or false (default=false)
| | fq | optional for type=species OR type=occurrences

## Integration Test

## Description

The build is setup to work with Firefox and Chrome.

Have a look at the `build.gradle` and the `src/test/resources/GebConfig.groovy` file.

From line 200 in build.gradle, you will find how we pass different test servers and authentication into tests.

## Usage

### Run with Firefox (default):

    ./gradlew :integrationTest -Dusername=xxxx -Dpassword=xxxxx

Or store authentication into file:

    /data/spatial-service/test/default.properties

then run:

    ./gradlew :integrationTest

**See** [How to pass authentication in](#Authentication)

### run with Chrome:

    ./gradlew :integrationTest -Ddriver=chrome

Chrome driver > 89 is not available for webdirver
Use npm to set the chrome driver version and reference the lib path from node_modules.

Add `"chromedriver": "89.0.0"` to package.json

Run `npm install`

    In ./gebConfig.groovy

    if (!System.getProperty("webdriver.chrome.driver")) {
        System.setProperty("webdriver.chrome.driver", "node_modules/chromedriver/bin/chromedriver")
    } 

### Test other servers:

    ./gradlew :integrationTest -DbaseUrl=http://spatial-test.ala.org.au/ws

### Authentication

Authentication info can be passed through with -Dusername and -Dpassword

    /gradlew :integrationTest -Dusername=xxxx -Dpassword=xxxxx

Or stored in a config file. The default config file is located in

    /data/spatial-service/test/default.properties
    
    username="xxxx@csiro.au"
    password="xxxxx"

We can change the config file with -DconfigFile

    /gradlew :integrationTest -DconfigFile="myconfig.properties"

