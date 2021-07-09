<%@ page import="au.org.ala.spatial.service.Task" %>
<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main">
    <title>Tasks</title>
    <meta name="breadcrumbs" content="${g.createLink( controller: 'main', action: 'index')}, Spatial Service"/>
    <g:set var="entityName" value="${message(code: 'task.label', default: 'Task')}"/>
    <script src="${resource(dir: 'js', file: 'jquery.js')}"></script>
    <script src="${resource(dir: 'js', file: 'jquery.dataTables.min.js')}"></script>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'jquery.dataTables.min.css')}" type="text/css">
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'fluid.css')}" type="text/css">
</head>
<body class="fluid">
<script type="text/javascript">
    $(document).ready(function () {
        // make table header cells clickable
        $("table .sortable").each(function (i) {
            var href = $(this).find("a").attr("href");
            $(this).css("cursor", "pointer");
            $(this).click(function () {
                window.location.href = href;
            });
        });
    });

    function reloadWithMax(el) {
        var max = $(el).find(":selected").val();
        //collect all the params that are applicable for the a page resizing
        var paramStr = "${raw(params.findAll {key, value -> key != 'max' && key != 'offset' && key != 'controller' && key != 'action'}.collect { it }.join('&'))}" + "&max=" + max
        //alert(paramStr)
        window.location.href = window.location.pathname + '?' + paramStr;
    }
</script>

<div class="col-lg-8">
</div>

<div class="col-lg-4">
    <div class="panel panel-default">
        <div class="panel-heading">Navigation</div>
        <div class="panel-body">
            <li><g:link controller="manageLayers" action="uploads">Show all uploads</g:link></li>
            <li><g:link controller="manageLayers" action="layers">Show all Layers</g:link></li>
            <li><g:link controller="tasks" action="index">Show all Tasks</g:link></li>
            <li><g:link controller="manageLayers" action="remote">Copy Layers from remote server</g:link></li>
        </div>
    </div>
</div>

<form class="listSearchForm">
    <div class="input-append" id="searchLists">
        <div class="form-inline">
            <label>Search term</label>
            <input class="input-xlarge" id="appendedInputButton" name="q" type="text" value="${params.q}"
                   placeholder="Search tasks">

            <label style="margin-left:20px">Status</label>
            <select style="width:100px" class="form-control" id="status" name="status">
                <option value="" <g:if test="${params.status == ''}">selected</g:if>>All</option>
                <option value="0" <g:if test="${params.status == '0'}">selected</g:if>>Queued</option>
                <option value="1" <g:if test="${params.status == '1'}">selected</g:if>>Running</option>
                <option value="2" <g:if test="${params.status == '2'}">selected</g:if>>Cancelled</option>
                <option value="3" <g:if test="${params.status == '3'}">selected</g:if>>Error</option>
                <option value="4" <g:if test="${params.status == '4'}">selected</g:if>>Successful</option>
            </select>

            <button style="margin-left:20px" class="btn" type="submit">Search</button>
        </div>
    </div>
</form>
<g:if test="${params.q || params.status}">
    <form class="listSearchForm">
        <button class="btn btn-primary" type="submit">Clear search</button>
    </form>
</g:if>

<div id="list-task" class="content scaffold-list" role="main">
    <div style="float: right;">
        Items per page:
        <select id="maxItems" class="input-mini" onchange="reloadWithMax(this)">
            <g:each in="${['10', '25', '50', '100']}" var="max">
                <option ${(params.max == max) ? 'selected="selected"' : ''}>${max}</option>
            </g:each>
        </select>
    </div>

    <h1><g:message code="default.list.label" args="[entityName]"/> (${taskInstanceCount ?: 0})</h1>
    <g:if test="${flash.message}">
        <div class="message" role="status">${flash.message}</div>
    </g:if>

    <div>
        <g:link action="cancelAll" params="${params}">Cancel these ${taskInstanceCount ?: 0} tasks</g:link>
    </div>
    <table class="table table-bordered table-striped" name="tasks">
        <thead>

            <g:sortableColumn property="message" title="${message(code: 'task.message.label', default: 'Message')}"/>

            <g:sortableColumn property="url" title="${message(code: 'task.url.label', default: 'Url')}"/>

            <g:sortableColumn property="name" title="${message(code: 'task.name.label', default: 'Name')}"/>

            <g:sortableColumn property="tag" title="${message(code: 'task.tag.label', default: 'Tag')}"/>

            <g:sortableColumn property="created" title="${message(code: 'task.created.label', default: 'Created')}"/>

            <g:sortableColumn property="status" title="${message(code: 'task.status.label', default: 'Status')}"/>

            <g:sortableColumn property="history"
                              title="${message(code: 'task.history.label', default: 'History (last 4 entries)')}"/>

            <th></th>

            <th></th>
        </thead>
        <tbody>
        <g:each in="${taskInstanceList}" status="i" var="taskInstance">
            <tr class="${(i % 2) == 0 ? 'odd' : 'even'}">

                <td><g:link action="show"
                            id="${taskInstance.id}">${fieldValue(bean: taskInstance, field: "message")}</g:link></td>

                <td><g:if test="${taskInstance.status < 2}">${fieldValue(bean: taskInstance, field: "url")}</g:if></td>

                <td>${fieldValue(bean: taskInstance, field: "name")}</td>

                <td>${fieldValue(bean: taskInstance, field: "tag") && fieldValue(bean: taskInstance, field: "tag") != 'null' ?  fieldValue(bean: taskInstance, field: "tag") : ''}</td>

                <td><g:formatDate date="${taskInstance.created}" format="dd/MM/yy hh:mm:ss"/></td>

                <td>${fieldValue(bean: taskInstance, field: "status")}</td>

                <td><g:each in="${taskInstance.history}" var="h">
                    <g:formatDate date="${h.key}" format="dd/MM/yy hh:mm:ss"/>=${h.value}<br/>
                </g:each></td>

                <td><g:link action="reRun" class="btn btn-sm btn-default" id="${taskInstance.id}" params="${params}">re-run task</g:link></td>
                <td><g:link action="cancel" class="btn btn-sm btn-default" id="${taskInstance.id}" params="${params}">cancel</g:link></td>

            </tr>
        </g:each>
        </tbody>
    </table>

    <div class="pagination">
        <g:paginate total="${taskInstanceCount ?: 0}" params="${params}"/>
    </div>
</div>
</body>
</html>
