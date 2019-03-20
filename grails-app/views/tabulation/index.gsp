<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title>Tabulations</title>
    <meta name="breadcrumbs" content="${g.createLink( controller: 'main', action: 'index')}, Spatial Service"/>
    <meta name="layout" content="main"/>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'fluid.css')}" type="text/css">
</head>
<body class="fluid">
<div class="container-fluid">

    <h1>Tabulations</h1>

    <table class="table table-bordered">
        <thead>
            <th>Contextual layer 1</th>
            <th>Contextual layer 2</th>
            <th>Area</th>
            <th>Species</th>
            <th>Occurrences</th>
        </thead>
        <g:each var="item" in="${tabulations}">
            <tr>
                <td>${item.name1}</td>
                <td>${item.name2}</td>
                <td>
                    <g:link controller="tabulation" action="show"
                            params="[func1: 'area', fid1: item.fid1, fid2: item.fid2, type: 'html']">html</g:link>
                    <g:link controller="tabulation" action="show"
                            params="[func1: 'area', fid1: item.fid1, fid2: item.fid2, type: 'csv']">csv</g:link>
                    <g:link controller="tabulation" action="show"
                            params="[func1: 'area', fid1: item.fid1, fid2: item.fid2, type: 'json']">json</g:link>
                </td>
                <td>
                    <g:link controller="tabulation" action="show"
                            params="[func1: 'species', fid1: item.fid1, fid2: item.fid2, type: 'html']">html</g:link>
                    <g:link controller="tabulation" action="show"
                            params="[func1: 'species', fid1: item.fid1, fid2: item.fid2, type: 'csv']">csv</g:link>
                    <g:link controller="tabulation" action="show"
                            params="[func1: 'species', fid1: item.fid1, fid2: item.fid2, type: 'json']">json</g:link>
                </td>
                <td>
                    <g:link controller="tabulation" action="show"
                            params="[func1: 'occurrences', fid1: item.fid1, fid2: item.fid2, type: 'html']">html</g:link>
                    <g:link controller="tabulation" action="show"
                            params="[func1: 'occurrences', fid1: item.fid1, fid2: item.fid2, type: 'csv']">csv</g:link>
                    <g:link controller="tabulation" action="show"
                            params="[func1: 'aoccurrencesrea', fid1: item.fid1, fid2: item.fid2, type: 'json']">json</g:link>
                </td>
            </tr>
        </g:each>
    </table>
</div>
</body>
</html>