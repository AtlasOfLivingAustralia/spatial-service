<%@ page import="au.org.ala.spatial.service.Task" %>
<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main">
    <g:set var="entityName" value="${message(code: 'task.label', default: 'Task')}"/>
    <title><g:message code="default.edit.label" args="[entityName]"/></title>
</head>

<body>
<a href="#edit-task" class="skip" tabindex="-1"><g:message code="default.link.skip.label"
                                                           default="Skip to content&hellip;"/></a>
<div class="col-lg-8">
    <ul class="breadcrumb">
        <li><g:link controller="main" action="index">Home</g:link></li>
        <li class="active">Edit Task</li>
    </ul>
</div>

<div class="panel panel-default col-lg-4">
    <div class="panel-heading">
        <h4 class="panel-title">Navigation</h4>
    </div>
    <div class="panel-body">
        <li><g:link controller="manageLayers" action="uploads">Show all uploads</g:link></li>
        <li><g:link controller="manageLayers" action="layers">Show all Layers</g:link></li>
        <li><g:link controller="tasks" action="index">Show all Tasks</g:link></li>
        <li><g:link controller="manageLayers" action="remote">Copy Layers from remote server</g:link></li>
    </div>
</div>


<div id="edit-task" class="content scaffold-edit" role="main">
    <h1><g:message code="default.edit.label" args="[entityName]"/></h1>
    <g:if test="${flash.message}">
        <div class="message" role="status">${flash.message}</div>
    </g:if>
    <g:hasErrors bean="${taskInstance}">
        <ul class="errors" role="alert">
            <g:eachError bean="${taskInstance}" var="error">
                <li <g:if test="${error in org.springframework.validation.FieldError}">data-field-id="${error.field}"</g:if>><g:message
                        error="${error}"/></li>
            </g:eachError>
        </ul>
    </g:hasErrors>
    <g:form url="[resource: taskInstance, action: 'update']" method="PUT">
        <g:hiddenField name="version" value="${taskInstance?.version}"/>
        <fieldset class="form">
            <g:render template="form"/>
        </fieldset>
        <fieldset class="buttons">
            <g:actionSubmit class="save" action="update"
                            value="${message(code: 'default.button.update.label', default: 'Update')}"/>
        </fieldset>
    </g:form>
</div>
</body>
</html>
