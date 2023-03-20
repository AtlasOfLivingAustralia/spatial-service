<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title>Available Spatial Layers</title>
    <meta name="breadcrumbs" content="${g.createLink(controller: 'main', action: 'index')}, Spatial Service"/>
    <meta name="layout" content="ala-main"/>
    <script src="${resource(dir: 'js', file: 'jquery.js')}"></script>
    <script src="${resource(dir: 'js', file: 'jquery.dataTables.min.js')}"></script>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'jquery.dataTables.min.css')}" type="text/css">
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'fluid.css')}" type="text/css">
    <style>
    #layersTable {
        font-size: 11px;
    }
    </style>
</head>

<body class="fluid">
<div class="pull-right">
    <button onclick="downloadCSV()" class="btn btn-sm btn-default">
        <i class="glyphicon glyphicon-download-alt"></i> Download as CSV
    </button>
    <g:link controller="layer" action="index" class="btn btn-sm btn-default">
        <i class="glyphicon glyphicon-"></i> JSON
    </g:link>
</div>

<h1>Available Spatial Layers</h1>
<table id="layersTable" name="layersTable" class="table table-bordered table-striped table-condensed">
    <thead>
    <tr>
        <th>Display&nbsp;name</th>
        %{--<th>Short&nbsp;name</th>--}%
        <th>Classification&nbsp;1</th>
        <th>Classification&nbsp;2</th>
        <th>Description</th>
        <th>Type</th>
        <th>Date&nbsp;added</th>
        <th>Metadata&nbsp;contact&nbsp;organization</th>
        <th>Keywords</th>
        <th>Preview</th>
    </tr>
    </thead>
    <tbody>
    <g:each var="item" in="${layers}">
        <tr>

            <td>
                <g:link controller="layer" action="more" params="[id: item.name]">${item.displayname}</g:link>
            </td>
            %{--<td>${item.name}</td>--}%
            <td>${item.classification1}</td>
            <td>${item.classification2}</td>
            <td>${item.description}</td>
            <td>${item.type}</td>
            <td><g:formatDate format="yyyy-MM-dd" date="${item.last_update}"/></td>
            <td>${item.source}</td>
            <td>${item.keywords}</td>
            <td>
                <img defer_src="${spatialConfig.grails.serverURL}/layer/img/${item.name}.jpg"/>
                <br/>
                <g:link controller="layer" action="more" id="${item.name}">more information</g:link>
            </td>
        </tr>
    </g:each>
    </tbody>
</table>
<script>
    $(document).ready(function () {
        var imgs = $("img")
        imgs.attr('src', function (i, currentvalue) {
            if (imgs[i].attributes !== undefined && imgs[i].attributes.defer_src) {
                return imgs[i].attributes.defer_src.value
            } else {
                return currentvalue
            }
        })
        $('#layersTable').DataTable();
        $('#layersTable_wrapper input').addClass('form-control');
        $('#layersTable_filter').addClass('form-group');
    });

    function downloadCSV() {
        var downloadurl = "${spatialConfig.grails.serverURL}/layers/layers.csv";
        location.href = downloadurl;
    }
</script>
</body>
</html>
