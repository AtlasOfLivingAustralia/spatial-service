<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title></title>
    <meta name="layout" content="main"/>
</head>

<body>
<ul class="breadcrumb">
    <li><g:link controller="main" action="index">Home</g:link></li>
    <li class="active">Tabulations</li>
</ul>

<div class="container-fluid">
    <table class="table table-bordered">
        <tr>
            <td>Contextual layer 1</td>
            <td>Contextual layer 2</td>
            <td>Area</td>
            <td>Species</td>
            <td>Occurrences</td>
        </tr>
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