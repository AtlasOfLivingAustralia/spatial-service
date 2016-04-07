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
</ul>

<div class="container-fluid">
    <table class="table table-bordered">
        <tr>
            <td>Classification 1</td>
            <td>Classification 2</td>
            <td>Display name</td>
            <td>Short name</td>
            <td>Description</td>
            <td>Type</td>
            <td>Metadata contact organization</td>
            <td>Keywords</td>
            <td>Preview</td>
        </tr>
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
                <td>${item.source}</td>
                <td>${item.keywords}</td>
                <td><g:img absolute="true" uri="/layer/img/${item.name}.jpg" width="200"></g:img>
                <g:link controller="layer" action="more" id="${item.name}">more information</g:link>
                </td>
            </tr>
        </g:each>
    </table>
</div>
</body>
</html>