<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title>Edit Layers</title>
    <meta name="breadcrumbs" content="${g.createLink( controller: 'main', action: 'index')}, Spatial Service"/>
    <meta name="layout" content="main"/>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'leaflet.css')}"/>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'manage.css')}" type="text/css">
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'jquery.dataTables.min.css')}" type="text/css">
    <script src="${resource(dir: 'js', file: 'jquery.js')}"></script>
    <script src="${resource(dir: 'js', file: 'jquery.dataTables.min.js')}"></script>
    <script src="${resource(dir: 'js', file: 'leaflet.js')}"></script>
    <script src="${resource(dir: 'js', file: 'BetterWMS.js')}"></script>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'fluid.css')}" type="text/css">
</head>
<body class="fluid">

<div>
    <div class="col-lg-8">
        <h1>Edit Layer</h1>
    </div>
    <div class="col-lg-4">
        <div class="panel panel-default">
            <div class="panel-heading">
                <h4 class="panel-title">Navigation</h4>
            </div>
            <div class="panel-body">
                <li><g:link controller="manageLayers" action="uploads">Show all uploads</g:link></li>
                <li><g:link controller="manageLayers" action="layers">Show all Layers</g:link></li>
                <li><g:link controller="tasks" action="index">Show all Tasks</g:link></li>
                <li><g:link controller="manageLayers" action="remote">Copy Layers from remote server</g:link></li>
            </div>
        </div>
    </div>
</div>

<div class="col-lg-12">
    <g:if test="${error != null}">
        <b class="alert alert-danger">${error}</b>
    </g:if>
    <g:if test="${message != null}">
        <b class="alert alert-success">${message}</b>
    </g:if>

    <g:if test="${layer_creation != null && !has_layer}"><h2 style="color:red">Layer created: <b>${has_layer}</b></h2><br/>
        <b>********* LAYER CREATION IN PROGRESS, WAIT AND REFRESH PAGE *******</b><br/></g:if>
</div>

<div class="row">
    <div class="col-lg-12">
        <div role="tabpanel">
        <ul class="nav nav-tabs" role="tablist">
            <li role="presentation" class="active"><a href="#settings" aria-controls="settings" role="tab"
                                                      data-toggle="tab">Layer</a></li>
            <g:if test="${has_layer}"><li role="presentation" class=""><a href="#existingFields"
                                                                          aria-controls="existingFields" role="tab"
                                                                          data-toggle="tab">Fields</a></li></g:if>
            <li role="presentation" class=""><a href="#geoserverPreview" aria-controls="geoserverPreview" role="tab"
                                                data-toggle="tab" onclick="setTimeout(function () {
                        map.invalidateSize()
                    }, 0)">Map</a></li>
            <li role="presentation" class=""><a href="#backgroundProcesses" aria-controls="backgroundProcesses"
                                                role="tab"
                                                data-toggle="tab">Background Processes</a></li>
        </ul>

        <div class="tab-content">

            <g:if test="${has_layer}">
                <div role="tabpanel" class="tab-pane" id="existingFields">
                    <table class="table table-condensed">
                        <tr>
                            <td>Id</td>
                            <td>name</td>
                            <td>description</td>
                            <td>sid</td>
                            <td>sname</td>
                        </tr>
                        <g:each in="${fields}" var="item">
                            <tr>
                                <td>${item.id}</td>
                                <td>${item.name}</td>
                                <td>${item.desc}</td>
                                <td>${item.sid}</td>
                                <td>${item.sname}</td>
                                <td><g:link controller="manageLayers" action="field" id="${item.id}">edit</g:link></td>
                                <td><a onclick="return confirmDelete('${item.id}');">delete</a></td>
                            </tr>
                        </g:each>
                        <tr><td colspan="5"><g:link controller="manageLayers" action="field" class="btn btn-sm btn-default"
                                                    id="${id}"><i class="glyphicon-plus"></i> Add new Field</g:link></td></tr>
                    </table>
                </div>
            </g:if>

            <div ole="tabpanel" class="tab-pane" id="geoserverPreview">
                <style>
                #map {
                    height: 500px;
                }
                </style>
                Click on map to get values/columns.
                <div id="map"></div>
                <script>
                    function confirmDelete(id, name) {
                        if (confirm("Permanently delete field " + name + "?")) {
                            var url = '${createLink(action: "delete", controller:"manageLayers")}/' + id
                            $(location).attr('href', url);
                        }
                    }

                    var map = L.map('map').setView([-22, 122], 4);

                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                    }).addTo(map);

                    var wmsLayer = L.tileLayer.betterWms("${grailsApplication.config.geoserver.url}/wms", {
                        layers: '${name}',
                        format: 'image/png',
                        version: '1.1.0',
                        transparent: true
                    }).addTo(map);

                    setTimeout(function () {
                        map.invalidateSize()
                    }, 0)
                </script>
            </div>

            <div cole="tabpanel" class="tab-pane active" id="settings">

                <form method="POST">

                    <table class="table table-condensed">

                        <tr><td class="col-md-2">
                            <label for="name"
                                   style="color:red">Name (lowercase, characters a-z, 0-9, _ only, as short as possible, used internally)
                            <g:if test="${layer_creation != null || has_layer}">[readonly because layer is created]</g:if>
                            [cannot be changed after layer is created]:</label></td><td class="col-md-1">
                            <input class="form-control" type="text" id="name" name="name" value="${name}"
                                   maxlength="150"
                                   <g:if test="${layer_creation != null || has_layer}">readonly="true"</g:if>/>
                        </td></tr><tr><td>

                        <label for="displayname">Display name:</label></td><td>
                        <input class="form-control" type="text" id="displayname" name="displayname"
                               value="${displayname}" maxlength="150"/>
                    </td></tr>

                        <tr><td>

                            <label for="description">Description (A short description of this layer):</label></td><td>
                            <textarea class="form-control" id="description" name="description" cols="150"
                                      rows="10">${description}</textarea>
                        </td></tr>
                        <tr><td>

                            <label for="requestedId">requestedId (optional):</label></td><td>
                            <input type="text" class="form-control" id="requestedId" name="requestedId"
                                   value="${requestedId}"
                                   maxlength="15"/>
                        </td></tr><tr><td>

                        <label for="type" style="color:red">Type
                        <g:if test="${layer_creation != null || has_layer}">[readonly because layer is created]</g:if>
                        [cannot be changed after layer is created]
                        :</label></td><td>
                        <g:if test="${layer_creation == null && !has_layer}">
                            <select class="form-control" id="type" name="type">
                                <option value="Contextual"
                                        <g:if test="${type == 'Contextual'}">selected</g:if>>Contextual
                                </option>
                                <option value="Environmental"
                                        <g:if test="${type == 'Environmental'}">selected</g:if>>Environmental
                                </option>
                            </select>
                        </g:if>
                        <g:if test="${layer_creation != null || has_layer}">
                            <input class="form-control" readonly type="text" id="type" name="type" value="${type}"
                                   maxlength="150"/>
                        </g:if>
                    </td></tr><tr><td>

                        <label for="domain" style="color:red">Domain
                        <g:if test="${layer_creation != null || has_layer}">[readonly because layer is created]</g:if>
                        [cannot be changed after layer is created]
                        :</label></td><td>
                        <g:if test="${layer_creation == null && !has_layer}">
                            <select class="form-control" id="domain" name="domain">
                                <option value="Terrestrial"
                                        <g:if test="${domain == 'Terrestrial'}">selected</g:if>>Terrestrial
                                </option>
                                <option value="Marine"
                                        <g:if test="${domain == 'Marine'}">selected</g:if>>Marine
                                </option>
                                <option value="Terrestrial,Marine"
                                        <g:if test="${domain == 'Terrestrial,Marine'}">selected</g:if>>Terrestrial,Marine
                                </option>
                            </select>
                        </g:if>
                        <g:if test="${layer_creation != null || has_layer}">
                            <input class="form-control" readonly type="text" id="domain" name="domain" value="${domain}"
                                   maxlength="150"/>
                        </g:if>
                    </td></tr><tr><td>

                        <label for="source">Source (e.g. organisation name):</label></td><td>
                        <input class="form-control" type="text" id="source" name="source" value="${source}"
                               maxlength="150"/>
                    </td></tr><tr><td>

                        <label for="scale">Scale (e.g. "0.01 degree (~1km)", "> 1:150,000", for display in metadata only) :</label>
                    </td><td>
                        <input class="form-control" type="text" id="scale" name="scale" value="${scale}"
                               maxlength="20"/>
                    </td></tr><tr><td>

                        <label for="environmentalvaluemin">Min Environmental value (Environmental Only) [readonly]</label>
                    </td><td>
                        <input class="form-control" type="readonly" id="environmentalvaluemin"
                               name="environmentalvaluemin" value="${environmentalvaluemin}"
                               readonly/>
                    </td></tr><tr><td>

                        <label for="environmentalvaluemax">Max Environmental value (Environmental Only) [readonly]</label>
                    </td><td>
                        <input class="form-control" type="readonly" id="environmentalvaluemax"
                               name="environmentalvaluemax" value="${environmentalvaluemax}"
                               readonly/>
                    </td></tr><tr><td>

                        <label for="environmentalvalueunits"
                               style="color:red">Environmental value units (Environmental Only) (e.g. degrees C, mm, dimensionless)
                        <g:if test="${layer_creation != null || has_layer}">[readonly because layer is created]</g:if>
                        [cannot be changed after layer is created]
                        </label></td><td>
                    <input class="form-control"
                            type="text" id="environmentalvalueunits" name="environmentalvalueunits" value="${environmentalvalueunits}"
                            maxlength="150" <g:if test="${layer_creation != null || has_layer}">readonly="true"</g:if>
                    </input>
                    </td></tr><tr><td>

                        <label for="minlongitude">Minimum Longitude [readonly]</label></td><td>
                        <input class="form-control" type="readonly" id="minlongitude" name="minlongitude"
                               value="${minlongitude}" maxlength="256" readonly/>
                    </td></tr><tr><td>

                        <label for="maxlongitude">Maximum Longitude [readonly]</label></td><td>
                        <input class="form-control" type="readonly" id="maxlongitude" name="maxlongitude"
                               value="${maxlongitude}" maxlength="256" readonly/>
                    </td></tr><tr><td>

                        <label for="minlatitude">Minimum Latitude [readonly]</label></td><td>
                        <input class="form-control" type="readonly" id="minlatitude" name="minlatitude"
                               value="${minlatitude}" maxlength="256" readonly/>
                    </td></tr><tr><td>

                        <label for="maxlatitude">Maximum Latitude [readonly]</label></td><td>
                        <input class="form-control" type="readonly" id="maxlatitude" name="maxlatitude"
                               value="${maxlatitude}" maxlength="256" readonly/>
                    </td></tr><tr><td>

                        <label for="metadatapath">Metadata path (e.g. URL to original metadata, if available)</label>
                    </td><td>
                        <input class="form-control" type="text" id="metadatapath" name="metadatapath"
                               value="${metadatapath}" maxlength="300"/>
                    </td></tr><tr><td>

                        <i>List of all classifications already defined:</i></td><td>
                        <select class="form-control" id='classificationList' onchange="setClassification()">
                            <g:each in="${classifications}" var="classification">
                                <option>${classification}</option>
                            </g:each>
                        </select>
                        <script>
                            function setClassification() {
                                classification1.value = classificationList.value.split(' > ')[0].replace("null", "").replace(">", "").trim();
                                classification2.value = classificationList.value.split(' > ').length < 2 ? "" : classificationList.value.split(' > ')[1].replace("null", "").replace(">", "").trim();
                            }
                        </script>
                    </td></tr><tr><td>

                        <label for="classification1">Classification 1 (e.g. choose from existing or enter a new classification1, e.g.
                        Climate)</label></td><td>
                        <input class="form-control" type="text" id="classification1" name="classification1"
                               value="${classification1}" maxlength="150"/>
                    </td></tr><tr><td>

                        <label for="classification2">Classification 2 (e.g. choose from existing or enter a new classification1, e.g.
                        Humidity)</label></td><td>
                        <input class="form-control" type="text" id="classification2" name="classification2"
                               value="${classification2}" maxlength="150"/>
                    </td></tr><tr><td>

                        <label for="mddatest">Metadata date (e.g. "2011-08-23")</label></td><td>
                        <input class="form-control" type="text" id="mddatest" name="mddatest" value="${mddatest}"
                               maxlength="30"/>
                    </td></tr><tr><td>

                        <label for="citation_date">Citation date (e.g. "2011-08-23")</label></td><td>
                        <input class="form-control" type="text" id="citation_date" name="citation_date"
                               value="${citation_date}" maxlength="30"/>
                    </td></tr><tr><td>

                        <label for="datalang">Data language (e.g. "eng")</label></td><td>
                        <input class="form-control" type="text" id="datalang" name="datalang" value="${datalang}"
                               maxlength="5"/>
                    </td></tr><tr><td>

                        <label for="respparty_role">Responsible party role (e.g. Custodian, Author, Processor, Distributor, ResourceProvider)</label>
                    </td><td>
                        <input class="form-control" type="text" id="respparty_role" name="respparty_role"
                               value="${respparty_role}" maxlength="30"/>
                    </td></tr><tr><td>

                        <label for="licence_link">Licence link (URL to licence)</label></td><td>
                        <input class="form-control" type="text" id="licence_link" name="licence_link"
                               value="${licence_link}"/>
                    </td></tr><tr><td>

                        <label for="licence_level">Licence level</label></td><td>
                        <select class="form-control" id="licence_level" name="licence_level">
                            <option value="1"
                                    <g:if test="${licence_level == 1}">selected</g:if>>Permission to distribute, see Licence notes
                            </option>
                            <option value="2"
                                    <g:if test="${licence_level == 2}">selected</g:if>>Varies, see Licence notes
                            </option>
                            <option value="3"
                                    <g:if test="${licence_level == 3}">selected</g:if>>Permitted to distribute, see Licence notes
                            </option>
                        </select>
                    </td></tr><tr><td>

                        <label for="licence_notes">Licence notes (details of the licence, e.g. "CC BY")</label></td><td>
                        <textarea class="form-control" id="licence_notes" name="licence_notes" cols="150" rows="6"
                                  maxlength="1024">${licence_notes}</textarea>
                    </td></tr><tr><td>

                        <label for="source_link">Source link (URL to source data or information, if available)</label>
                    </td><td>
                        <input class="form-control" type="text" id="source_link" name="source_link"
                               value="${source_link}" maxlength="300"/>
                    </td></tr><tr><td>

                        <label for="keywords">Keywords (Used in layer searches, e.g. "solar, sun"):</label></td><td>
                        <input class="form-control" type="text" id="keywords" name="keywords" value="${keywords}"
                               maxlength="256"/>
                    </td></tr><tr><td>

                        <label for="notes">Notes (any other information about the layer):</label></td><td>
                        <textarea class="form-control" id="notes" name="notes" cols="150" rows="10">${notes}</textarea>
                    </td></tr><tr><td>

                        <label for="enabled">Enabled (makes the layer available for use, disable to remove layers from use)</label>
                    </td><td>
                        <input class="form-control" type="checkbox" id="enabled" name="enabled"
                               <g:if test="${enabled}">checked</g:if>/>

                    </td></tr><tr><td>
                    </table>
                    <input type="submit" class="btn btn-default"
                           value='${has_layer ? "Update Layer" : "Create Layer"}'/>

                    <input type="hidden" name="raw_id" value="${raw_id}"/>

                    <input type="hidden" name="id" value="${id}"/>
                </form>
            </div>

            <div role="tabpanel" class="tab-pane" id="backgroundProcesses">
                <table class="table table-condensed">
                    <tr>
                        <td>Task ID</td>
                        <td>Task</td>
                        <td>Created</td>
                        <td>Tag</td>
                        <td>Status</td>
                        <td>Message</td>
                    </tr>
                    <g:each var="t" in="${task}">
                        <tr>
                            <td>${t.id}</td>
                            <td>${t.name}</td>
                            <td>${t.created}</td>
                            <td>${t.tag}</td>
                            <td>${t.status}</td>
                            <td>${t.message}</td>
                        </tr>
                    </g:each>
                </table>
            </div>
        </div>
    </div>
    </div>
</div>
</body>
</html>

