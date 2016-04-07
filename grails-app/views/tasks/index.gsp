<%@ page import="au.org.ala.spatial.service.Task" %>
<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main">
    <g:set var="entityName" value="${message(code: 'task.label', default: 'Task')}"/>
    <title><g:message code="default.list.label" args="[entityName]"/></title>
</head>

<body>
<a href="#list-task" class="skip" tabindex="-1"><g:message code="default.link.skip.label"
                                                           default="Skip to content&hellip;"/></a>

<div class="nav" role="navigation">
    <ul>
        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
        <li><g:link class="create" action="create"><g:message code="default.new.label"
                                                              args="[entityName]"/></g:link></li>
    </ul>
</div>

<div id="list-task" class="content scaffold-list" role="main">
    <h1><g:message code="default.list.label" args="[entityName]"/></h1>
    <g:if test="${flash.message}">
        <div class="message" role="status">${flash.message}</div>
    </g:if>
    <table>
        <thead>
        <tr>

            <g:sortableColumn property="message" title="${message(code: 'task.message.label', default: 'Message')}"/>

            <g:sortableColumn property="url" title="${message(code: 'task.url.label', default: 'Url')}"/>

            <g:sortableColumn property="name" title="${message(code: 'task.name.label', default: 'Name')}"/>

            <g:sortableColumn property="tag" title="${message(code: 'task.tag.label', default: 'Tag')}"/>

            <g:sortableColumn property="created" title="${message(code: 'task.created.label', default: 'Created')}"/>

        </tr>
        </thead>
        <tbody>
        <g:each in="${taskInstanceList}" status="i" var="taskInstance">
            <tr class="${(i % 2) == 0 ? 'even' : 'odd'}">

                <td><g:link action="show"
                            id="${taskInstance.id}">${fieldValue(bean: taskInstance, field: "message")}</g:link></td>

                <td><g:if test="${taskInstance.status < 2}">${fieldValue(bean: taskInstance, field: "url")}</g:if></td>

                <td>${fieldValue(bean: taskInstance, field: "name")}</td>

                <td>${fieldValue(bean: taskInstance, field: "tag")}</td>

                <td><g:formatDate date="${taskInstance.created}"/></td>

                <td><g:link target="_blank" action="reRun" id="${taskInstance.id}">re-run task</g:link></td>

            </tr>
        </g:each>
        </tbody>
    </table>

    <div class="pagination">
        <g:paginate total="${taskInstanceCount ?: 0}"/>
    </div>
</div>
</body>
</html>
