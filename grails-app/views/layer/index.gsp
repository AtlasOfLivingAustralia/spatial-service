<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title></title>
    <meta name="layout" content="main"/>

    <script src="${resource(dir: 'js', file: 'jquery.js')}"></script>
    <script src="${resource(dir: 'js', file: 'jquery.dataTables.min.js')}"></script>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'jquery.dataTables.min.css')}" type="text/css">
</head>

<body>
<style>
    body {
        font-size: 12px;
    }
</style>
<ul class="breadcrumb">
    <li><g:link controller="main" action="index">Home</g:link></li>
    <li class="active">Layers</li>
</ul>

<div class="container-fluid">
    <div style="float:right">
        <button onclick="downloadCSV()">download as CSV</button>
    </div>
    <table id="layersTable" name="layersTable" class="table table-bordered table-striped table-condensed">
        <thead>
        <tr>
            <th>Classification 1</th>
            <th>Classification 2</th>
            <th>Display name</th>
            <th>Short name</th>
            <th>Description</th>
            <th>Type</th>
            <th>Date added</th>
            <th>Metadata contact organization</th>
            <th>Keywords</th>
            <th>Preview</th>
        </tr>
        </thead>
        <tbody>
        <g:each var="item" in="${layers}">
            <tr>
                <td>${item.classification1}</td>
                <td>${item.classification2}</td>
                <td>
                    <g:link controller="layer" action="more" params="[id: item.name]">${item.displayname}</g:link>
                </td>
                <td>${item.name}</td>
                <td>${item.description}</td>
                <td>${item.type}</td>
                <td><g:formatDate format="yyyy-MM-dd" date="${item.last_update}"/></td>
                <td>${item.source}</td>
                <td>${item.keywords}</td>
                <td><img defer_src="${grailsApplication.config.grails.serverURL}/layer/img/${item.name}.jpg" />
                <g:link controller="layer" action="more" id="${item.name}">more information</g:link>
                </td>
            </tr>
        </g:each>
        </tbody>
    </table>
</div>
<script>
    $(document).ready(function(){
        var imgs = $("img")
        imgs.attr('src',function(i,currentvalue) {
            if (imgs[i].attributes !== undefined && imgs[i].attributes.defer_src) {
                return imgs[i].attributes.defer_src.value
            } else {
                return currentvalue
            }
        })
        $('#layersTable').DataTable();
    });

    function downloadCSV() {
        var downloadurl = "${grailsApplication.config.grails.serverURL}/layers/csv";
        location.href = downloadurl;
    }
</script>
</body>
</html>