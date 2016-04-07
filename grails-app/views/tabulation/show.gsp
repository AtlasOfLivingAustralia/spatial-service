<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title></title>
    <meta name="layout" content="main"/>
</head>

<body>
<ul class="breadcrumb">
    <li><g:link controller="main" action="index">Home</g:link></li>
    <li><g:link controller="tabulation" action="index">Tabulations</g:link></li>
    <li class="active">${label}</li>
</ul>

<div class="container-fluid">
    <div><span>${info}</span></div>
    <table class="table table-bordered">
        <g:each var="row" in="${data}">
            <tr>
                <g:each var="value" in="${row}">
                    <td>${(value.trim().matches('^\\d+\\.?\\d*$')) ? (int) value.toDouble() : value}</td>
                </g:each>
            </tr>
        </g:each>
    </table>
</div>
</body>
</html>