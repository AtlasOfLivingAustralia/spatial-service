<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title>Distribution</title>
    <meta name="layout" content="main"/>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'fluid.css')}" type="text/css">
</head>
<body class="fluid">
<ul class="breadcrumb">
    <li><g:link controller="main" action="index">Home</g:link></li>
    <li><g:link controller="manageLayers" action="layers">Layers</g:link></li>
    <li class="active">${has_layer ? "Edit Layer" : "Create Layer"}</li>
    <br>
    <li><g:link controller="manageLayers" action="layers">Layers</g:link></li>
    <li><g:link controller="manageLayers" action="uploads">Uploads</g:link></li>
    <li><g:link controller="tasks" action="index">Tasks</g:link></li>
    <li><g:link controller="manageLayers" action="remote">Copy Layer</g:link></li>
</ul>

<g:if test="${error != null}">
    <b class="error">${error}</b>
    <br/>
    <br/>
</g:if>
<g:if test="${message != null}">
    <b class="message">${message}</b>
    <br/>
    <br/>
</g:if>
<style>
input[readonly] {
    background-color: lightgrey;
}

input {
    width: 100%;
}

.error {
    color: red;
    font-size: 14px;
}

.message {
    color: green;
    font-size: 14px;
}
</style>

<div class="row-fluid">
    <div role="tabpanel">
        <ul class="nav nav-tabs" role="tablist">
            <li role="presentation" class="active"><a href="#settings" aria-controls="settings" role="tab"
                                                      data-toggle="tab">Layer</a></li>
            <li role="presentation" class=""><a href="#backgroundProcesses" aria-controls="backgroundProcesses"
                                                role="tab"
                                                data-toggle="tab">Background Processes</a></li>
        </ul>

        <div class="tab-content">

            <div cole="tabpanel" class="tab-pane active" id="settings">

                <form method="POST">

                    <table class="table table-bordered">

                        <tr><td class="col-md-2">
                            <label for="data_resource_uid"
                                   style="color:red">Data Resource Uid [cannot be changed after imported]:</label>
                        </td><td class="col-md-1">
                            <input class="form-control" type="text" id="data_resource_uid" name="data_resource_uid"
                                   value="${data_resource_uid}"
                                   maxlength="150"/>
                        </td></tr>

                    </table>
                    <input type="submit" class="btn btn-default"
                           value='${has_layer ? " " : "Import distribution"}'/>

                    <input type="hidden" name="raw_id" value="${raw_id}"/>

                    <input type="hidden" name="id" value="${id}"/>
                </form>
            </div>

            <div role="tabpanel" class="tab-pane" id="backgroundProcesses">
                <table class="table table-bordered">
                    <tr>
                        <td>Task ID</td>
                        <td>Task</td>
                        <td>Created</td>
                        <td>Tag</td>
                        <td>Status</td>
                        <td>Message</td>
                    </tr>
                    <g:each var="t" in="${task}">
                        <tr>
                            <td>${t.id}</td>
                            <td>${t.name}</td>
                            <td>${t.created}</td>
                            <td>${t.tag}</td>
                            <td>${t.status}</td>
                            <td>${t.message}</td>
                        </tr>
                    </g:each>
                </table>
            </div>
        </div>
    </div>
</body>
</html>

