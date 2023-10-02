<%@ page import="au.org.ala.spatial.Task" %>
<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="ala-main">
    <g:set var="entityName" value="${message(code: 'task.label', default: 'Task')}"/>
    <title><g:message code="default.show.label" args="[entityName]"/></title>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'fluid.css')}" type="text/css">
</head>

<body class="fluid">

<div class="col-lg-8">
    <ul class="breadcrumb">
        <li><g:link controller="main" action="index">Home</g:link></li>
        <li class="active">Show Workflow</li>
    </ul>
</div>

<div id="show-task" class="content scaffold-show" role="main">
    <g:if test="${flash.message}">
        <div class="message" role="status">${flash.message}</div>
    </g:if>

    <table class="table table-bordered table-condensed">
        <thead>
        <th>Field</th>
        <th>Value</th>
        </thead>
        <tbody>
        <tr>
            <td>ID</td>
            <td>${workflowInstance.id}</td>
        </tr>
        <tr>
            <td>Name</td>
            <td>${workflowInstance.name}</td>
        </tr>
        <tr>
            <td>Is Private</td>
            <td>${workflowInstance.isPrivate}</td>
        </tr>
        <tr>
            <td>Mint Id</td>
            <td>${workflowInstance.mintId}</td>
        </tr>
        <tr>
            <td>Created</td>
            <td>${workflowInstance.created}</td>
        </tr>
        <tr>
            <td>Url</td>
            <td><a href='${workflowInstance.url}?open=true'>
                ${workflowInstance.url}</td>
        </tr>
        <tr>
            <td>Metadata</td>
            <td>${workflowInstance.metadata}</td>
        </tr>
        </tbody>
    </table>
</div>
</body>
</html>
