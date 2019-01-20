<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title>Tabulation</title>
    <meta name="breadcrumbs" content="${g.createLink( controller: 'main', action: 'index')}, Spatial Service \\ ${g.createLink( controller: 'tabulation', action: 'index')}, Tabulations"/>
    <meta name="layout" content="main"/>
</head>

<body>
<style>
    body {
        font-size: 12px;
    }
</style>

<ul class="breadcrumb">
    <h1>Tabulation</h1>
</ul>
<div class="container-fluid">
    <div style="margin:10px">
        <a style="cursor:pointer" onclick="downloadCSV()">download as CSV</a>
    </div>
</div>
<div class="container-fluid">
    <div><span>${info}</span></div>
    <table id="layersTable" name="layersTable" class="table table-bordered table-striped table-condensed">
        <thead>
            <tr>
                <g:each var="row" in="${data.take(1)}">
                    <g:each var="value" in="${row}">
                        <th>${(value.trim().matches('^\\d+\\.?\\d*$')) ? (long) value.toDouble() : value}&nbsp;</th>
                    </g:each>
                </g:each>
            </tr>
        </thead>
        <tbody>
            <g:each var="row" in="${data}" status="idx">
                <g:if test="${idx > 0}">
                    <tr>
                        <g:each var="value" in="${row}">
                            <td>${(value.trim().matches('^\\d+\\.?\\d*$')) ? (long) value.toDouble() : value}</td>
                        </g:each>
                    </tr>
                </g:if>
            </g:each>
        </tbody>
    </table>
</div>
<script>
    function downloadCSV() {
        var downloadurl = '${request.forwardURI.replace(".html",".csv")}';
        location.href = downloadurl;
    }

    function downloadJSON() {
        var downloadurl = '${request.forwardURI.replace(".html",".json")}';
        location.href = downloadurl;
    }
</script>
</body>
</html>