<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title></title>
    <meta name="layout" content="main"/>
</head>

<body>
<ul class="breadcrumb">
    <li><g:link controller="main" action="index">Home</g:link></li>
    <li class="active">Manage Layers</li>
</ul>

<g:if test="${error != null}">
    <b class="error">${error}</b>
    <br/>
    <br/>
</g:if>

<div class="container-fluid">
    <div role="tabpanel">
        <ul class="nav nav-tabs" role="tablist">
            <li role="presentation" class="active"><a href="#upload" aria-controls="upload" role="tab"
                                                      data-toggle="tab">Upload Layer</a></li>
            <li role="presentation" class=""><a href="#uploadedFiles" aria-controls="uploadedFiles" role="tab"
                                                data-toggle="tab">Uploads</a></li>
            <li role="presentation"><a href="#allLayers" aria-controls="allLayers" role="tab"
                                       data-toggle="tab">All Layers</a></li>
            <li role="presentation"><a href="#backgroundProcesses" aria-controls="backgroundProcesses" role="tab"
                                       data-toggle="tab">Background Processes</a></li>
        </ul>

        <div class="tab-content">
            <div role="tabpanel" class="tab-pane active" id="upload">
                <p>Upload a new grid file (zipped bil, hdr with prj) or a new shape file (zipped shape file with prj)</p>
                <g:form method="POST" enctype="multipart/form-data"
                        action="upload">
                    <div class="input-group">
                        <input class="form-control" type="file" name="file">
                        <span class="input-group-btn">
                            <input class="form-control" type="submit" value="Upload">
                        </span>
                    </div>
                </g:form>
            </div>

            <div role="tabpanel" class="tab-pane" id="uploadedFiles">
                <table class="table table-bordered">
                    <tr>
                        <td>Raw Id</td>
                        <td>Filename</td>
                        <td>Layer Id</td>
                    </tr>
                    <g:each var="item" in="${files}">
                        <tr>
                            <td>${item.raw_id}</td>
                            <td>${item.filename}</td>
                            <td>${item.layer_id}</td>
                            <td>
                                <g:each in="${item.fields}" var="field">
                                    <g:link controller="manageLayers" action="field"
                                            id="${field.id}">${field.id}</g:link>, type:${field.type}<br/>
                                </g:each>
                            </td>
                            <td><g:link controller="manageLayers" action="layer"
                                        id="${item.containsKey('layer_id') ? item.layer_id : item.raw_id}">
                                <g:if test="${!item.containsKey('layer_id')}">create layer</g:if>
                                <g:if test="${item.containsKey('layer_id')}">edit layer</g:if>
                            </g:link>
                                <g:if test="${!item.containsKey('layer_id')}">
                                    <br/>
                                    <g:link controller="manageLayers" action="distribution"
                                            id="${item.containsKey('data_resource_uid') ? item.data_resource_uid : item.raw_id}">
                                        <g:if test="${!item.containsKey('data_resource_uid')}">import as expert distribution</g:if>
                                    </g:link><g:if
                                        test="${item.containsKey('data_resource_uid')}">Expert distribution exists: ${item.data_resource_uid}
                                    <g:link controller="manageLayers" action="delete"
                                            id="${item.raw_id}">delete distribution</g:link></g:if>
                                    <br/>
                                    <g:link controller="manageLayers" action="checklist"
                                            id="${item.containsKey('checklist') ? item.checklist : item.raw_id}">
                                        <g:if test="${!item.containsKey('checklist')}">import as checklist</g:if>
                                    </g:link><g:if
                                        test="${item.containsKey('checklist')}">Checklist exists: ${item.checklist}
                                    <g:link controller="manageLayers" action="delete"
                                            id="${item.raw_id}">delete checklist</g:link></g:if>
                                </g:if></td>
                            <td><a onclick="return confirmDelete(${item.raw_id}, '${item.filename}');">delete</a></td>
                        </tr>
                    </g:each>
                </table>
            </div>

            <div role="tabpanel" class="tab-pane" id="allLayers">
                <table class="table table-bordered">
                    <tr>
                        <td>Id</td>
                        <td>Name</td>
                        <td>Display Name</td>
                        <td>Enabled</td>
                        <td>Fields</td>
                    </tr>
                    <g:each var="item" in="${layers}">
                        <tr>
                            <td>${item.id}</td>
                            <td>${item.name}</td>
                            <td>${item.displayname}</td>
                            <td>${item.enabled}</td>
                            <td>
                                <g:each in="${item.fields}" var="field">
                                    <g:link controller="manageLayers" action="field"
                                            id="${field.id}">${field.id}: ${field.name}</g:link>, type:${field.type}<br/>
                                </g:each>
                            </td>
                            <td><g:link controller="manageLayers" action="field" id="${item.id}">add field</g:link><br/>
                            </td>
                            <td><g:link controller="manageLayers" action="layer" id="${item.id}">edit</g:link></td>
                            <td><a onclick="return confirmDelete(${item.id}, '${item.name}');">delete</a></td>
                        </tr>
                    </g:each>
                </table>
            </div>

            <div role="tabpanel" class="tab-pane" id="backgroundProcesses">
                <table class="table table-bordered">
                    <tr>
                        <td>Task Id</td>
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
</div>
<script>
    function confirmDelete(id, name) {
        if (confirm("Permanently delete layer " + name + "?")) {
            var url = "delete/" + id
            $(location).attr('href', url);
        }
    }
</script>
</div>
</body>
</html>