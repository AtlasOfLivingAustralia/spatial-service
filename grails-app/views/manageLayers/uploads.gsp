<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title>Uploads</title>
    <meta name="breadcrumbs" content="${g.createLink( controller: 'main', action: 'index')}, Spatial Service"/>
    <meta name="layout" content="ala-main"/>
    <script src="${resource(dir: 'js', file: 'jquery.js')}"></script>
    <script src="${resource(dir: 'js', file: 'jquery.dataTables.min.js')}"></script>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'jquery.dataTables.min.css')}" type="text/css">
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'fluid.css')}" type="text/css">
</head>
<body class="fluid">

<div class="col-lg-8">
    <h1>Uploads</h1>

    <g:if test="${error || message}">
        <div class="col-lg-12">
            <g:if test="${error != null}">
                <b class="alert alert-danger">${error}</b>
            </g:if>
            <g:if test="${message != null}">
                <b class="alert alert-success">${message}</b>
            </g:if>
        </div>
    </g:if>

    <p>Upload a new grid file (zipped bil, hdr with prj) or a new shape file (zipped shape file with prj)</p>
    <g:form method="POST" enctype="multipart/form-data"
            action="upload">
        <div class="input-group">
            <input class="form-control" type="file" name="file">
            <span class="input-group-btn">
                <input class="form-control btn-primary" type="submit" value="Upload">
            </span>
        </div>
        <br/>
        <br/>
    </g:form>
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

<div class="container-fluid">
    <table class="table table-bordered" id="uploadTable">
        <thead>
        <tr>
            <th>Date</th>
            <th>Raw Id</th>
            <th>Filename</th>
            <th>Layer Id</th>
            <th>Fields</th>
            <th>Actions</th>
            <th></th>
        </tr>
        </thead>
        <tbody>
        <g:each var="item" in="${files}">
            <tr>
                <td>${item.created}</td>
                <td>${item.raw_id}</td>
                <td>${item.filename}</td>
                <td>${item.layer_id}</td>
                <td>
                    <g:each in="${item.fields}" var="field">
                        <g:link controller="manageLayers" action="field"
                                id="${field.id}">${field.id}</g:link>, type:${field.type}<br/>
                    </g:each>
                </td>
                <td><g:link controller="manageLayers" action="layer" class="btn btn-sm btn-default"
                            id="${item.containsKey('layer_id') ? item.layer_id : item.raw_id}">
                    <g:if test="${!item.containsKey('layer_id')}"><i class="glyphicon glyphicon-plus"></i> create layer</g:if>
                    <g:if test="${item.containsKey('layer_id')}"><i class="glyphicon glyphicon-edit"></i> edit layer</g:if>
                </g:link>
                    <g:if test="${!item.containsKey('layer_id')}">
                        <br/>
                        <g:link controller="manageLayers" action="distribution" class="btn btn-sm btn-default"
                                id="${item.containsKey('data_resource_uid') ? item.data_resource_uid : item.raw_id}">
                            <g:if test="${!item.containsKey('data_resource_uid')}">import as expert distribution</g:if>
                        </g:link><g:if
                            test="${item.containsKey('data_resource_uid')}">Expert distribution exists: ${item.data_resource_uid}
                        <g:link controller="manageLayers" action="delete" class="btn btn-sm btn-danger"
                                id="${item.raw_id}"><i
                                class="glyphicon glyphicon-remove"></i> delete distribution</g:link></g:if>
                        <br/>
                        <g:link controller="manageLayers" action="checklist" class="btn btn-sm btn-default"
                                id="${item.containsKey('checklist') ? item.checklist : item.raw_id}">
                            <g:if test="${!item.containsKey('checklist')}">import as checklist</g:if>
                        </g:link><g:if
                            test="${item.containsKey('checklist')}">Checklist exists: ${item.checklist}
                        <g:link controller="manageLayers" action="delete" class="btn btn-sm btn-default btn-danger"
                                id="${item.raw_id}"><i
                                class="glyphicon glyphicon-remove"></i> delete checklist</g:link></g:if>
                    </g:if></td>
                <td><a onclick="return confirmDelete('${item.raw_id}', '${item.filename}');"
                       class="btn btn-sm btn-danger"><i class="glyphicon glyphicon-remove"></i> delete</a></td>
            </tr>
        </g:each>
        </tbody>
    </table>
</div>

<script>
    function confirmDelete(id, name) {
        if (confirm("Permanently delete layer " + name + "?")) {
            var url = "deleteUpload/" + id
            $(location).attr('href', url);
        }
    }

    jQuery(document).ready(function () {
        // setup the table

        jQuery('#uploadTable').dataTable({
            "aaSorting": [
                [0, "desc"]
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

        jQuery("div.dataTables_filter input").addClass("form-control");
        jQuery("div.dataTables_filter input").attr("placeholder", "Filter within results");
    });
</script>
</body>
</html>