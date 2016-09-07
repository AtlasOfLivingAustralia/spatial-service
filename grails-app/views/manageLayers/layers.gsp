<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title></title>
    <meta name="layout" content="main"/>
</head>

<body>
<ul class="breadcrumb">
    <li><g:link controller="main" action="index">Home</g:link></li>
    <li class="active">Layers</li>
    <br>
    <li><g:link controller="manageLayers" action="layers">Layers</g:link></li>
    <li><g:link controller="manageLayers" action="uploads">Uploads</g:link></li>
    <li><g:link controller="tasks" action="index">Tasks</g:link></li>
    <li><g:link controller="tasks" action="remote">Copy Layer</g:link></li>
</ul>

<g:if test="${error != null}">
    <b class="error">${error}</b>
    <br/>
    <br/>
</g:if>

<div class="container-fluid">
    <table class="table table-bordered" id="layersTable">
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
                                id="${field.id}">${field.id}: ${field.name}</g:link>, type:${field.type}<br/>
                    </g:each>
                </td>
                <td><g:link controller="manageLayers" action="field" id="${item.id}">add field</g:link><br/>
                </td>
                <td><g:link controller="manageLayers" action="layer" id="${item.id}">edit</g:link></td>
                <td><a onclick="return confirmDelete(${item.id}, '${item.name}');">delete</a></td>
            </tr>
        </g:each>
        </tbody>
    </table>
</div>

<script src="/spatial-service/js/jquery.js"></script>
<script src="/spatial-service/js/jquery.dataTables.min.js"></script>

<script>
    function confirmDelete(id, name) {
        if (confirm("Permanently delete layer " + name + "?")) {
            var url = "delete/" + id
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