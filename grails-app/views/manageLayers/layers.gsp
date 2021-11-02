<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title>Layer Administration</title>
    <meta name="breadcrumbs" content="${g.createLink( controller: 'main', action: 'index')}, Spatial Service"/>
    <meta name="layout" content="ala-main"/>
    <script src="${resource(dir: 'js', file: 'jquery.js')}"></script>
    <script src="${resource(dir: 'js', file: 'jquery.dataTables.min.js')}"></script>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'jquery.dataTables.min.css')}" type="text/css">
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'fluid.css')}" type="text/css">
</head>
<body class="fluid">

<div class="col-lg-8">
    <h1>Layer Administration</h1>
</div>

<div class="col-lg-4">
    <div class="panel panel-default">
        <div class="panel-heading">
            Navigation
        </div>
        <div class="panel-body">
            <li><g:link controller="manageLayers" action="uploads">Show all uploads</g:link></li>
            <li><g:link controller="manageLayers" action="layers">Show all Layers</g:link></li>
            <li><g:link controller="tasks" action="index">Show all Tasks</g:link></li>
            <li><g:link controller="manageLayers" action="remote">Copy Layers from remote server</g:link></li>
        </div>
    </div>
</div>

<g:if test="${error != null}">
    <b class="error">${error}</b>
    <br/>
    <br/>
</g:if>

<div class="container-fluid">
    <table class="table table-condensed" id="layersTable">
        <thead>
        <tr>
            <th>Date added</th>
            <th>Id</th>
            <th>Name</th>
            <th>Display Name</th>
            <th>Enabled</th>
            <th>Fields</th>
            <th></th>
            <th></th>
            <th></th>
        </tr>
        </thead>
        <tbody>
        <g:each var="item" in="${layers}">
            <tr>
                <td>${item.dt_added}</td>
                <td>${item.id}</td>
                <td>${item.name}</td>
                <td>${item.displayname}</td>
                <td>${item.enabled}</td>
                <td>
                    <g:each in="${item.fields}" var="field">
                        <g:link controller="manageLayers" action="field"
                                id="${field.id}">${field.id}: ${field.name}</g:link>,
                                ${field.type == 'c' ? 'contextual (polygon)' : ''}
                                ${field.type == 'e' ? 'environmental (raster)' : ''}
                        ${field.type == 'a' ? 'contextual (raster with classes)' : ''}
                        ${field.type == 'b' ? 'contextual (raster with polygons)' : ''}
                    </g:each>
                </td>
                <td><g:link controller="manageLayers" action="field" class="btn btn-sm btn-default" id="${item.id}">
                    <i class="glyphicon glyphicon-plus"></i>
                    add field
                </g:link><br/>
                </td>
                <td><g:link controller="manageLayers" action="layer" class="btn btn-sm btn-default"id="${item.id}">
                    <i class="glyphicon glyphicon-edit"></i>
                    edit
                    </g:link>
                </td>
                <td><a onclick="return confirmDelete(${item.id}, '${item.name}');" class="btn btn-sm btn-danger"><i class="glyphicon glyphicon-remove"></i> delete</a></td>
            </tr>
        </g:each>
        </tbody>
    </table>
</div>

<script>
    function confirmDelete(id, name) {
        if (confirm("Permanently delete layer " + name + "?")) {
            var url = '${createLink(action: "deleteLayer", controller:"manageLayers")}/' + id
            $(location).attr('href', url);
        }
    }

    jQuery(document).ready(function () {
        // setup the table
        jQuery('#layersTable').dataTable({
            "aaSorting": [
                [1, "desc"]
            ],
            "aLengthMenu": [
                [10, 25, 50, 100, -1],
                [10, 25, 50, 100, "All"]
            ],
            "sPaginationType": "full_numbers",
            "sDom": '<"sort-options"fl<"clear">>rt<"pagination"ip<"clear">>',
            "oLanguage": {
                "sSearch": ""
            }
        });

        jQuery("div.dataTables_filter input").attr("placeholder", "Filter within results");
        jQuery("div.dataTables_filter input").addClass("form-control");

    });

    function downloadLayers(type) {
        var downloadurl = "/layers-service/layers";
        var query = jQuery("div.dataTables_filter input").val();
        if (type == "json") {
            downloadurl += ".json";
        } else {
            downloadurl += ".csv";
        }
        if (query != "") {
            downloadurl += "?q=" + query;
        }
        location.href = downloadurl;
    }
</script>
</div>
</body>
</html>