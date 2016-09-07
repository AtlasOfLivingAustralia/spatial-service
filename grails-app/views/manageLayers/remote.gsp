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
    <br>
    <li><g:link controller="manageLayers" action="layers">Layers</g:link></li>
    <li><g:link controller="manageLayers" action="uploads">Uploads</g:link></li>
    <li><g:link controller="tasks" action="index">Tasks</g:link></li>
    <li><g:link controller="tasks" action="remote">Copy Layer</g:link></li>
</ul>

<g:if test="${error != null}">
    <b class="error">${error}</b>
    <br/>
    <br/>
</g:if>

<div class="container-fluid">
    This will copy a layer from a remote spatial-service to the local spatial-service.

    <br/>
    <br/>
    Layer filter
    <select id="listSelector">
        <option value="divLocal">local only</option>
        <option value="divRemote" selected>remote only</option>
        <option value="divBoth">local and remote</option>
    </select>
    <br/>
    <br/>

    <div id="divBoth" class="listBlock" style="display:none">
        <table class="table table-bordered" id="layersBothTable">
            <thead>
            <tr>
                <th>Date added</th>
                <th>FieldId</th>
                <th>LayerId</th>
                <th>Name</th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            <g:each var="item" in="${layersBoth}">
                <tr>
                    <td>${item.dt_added}</td>
                    <td>${item.id}</td>
                    <td>${item.layerId}</td>
                    <td>${item.name}</td>
                    <td></td>
                </tr>
            </g:each>
            </tbody>
        </table>
    </div>

    <div id="divRemote" class="listBlock">

        <table class="table table-bordered" id="layersRemoteOnlyTable">
            <thead>
            <tr>
                <th>Date added</th>
                <th>FieldId</th>
                <th>LayerId</th>
                <th>Name</th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            <g:each var="item" in="${layersRemoteOnly}">
                <tr>
                    <td>${item.dt_added}</td>
                    <td>${item.id}</td>
                    <td>${item.layerId}</td>
                    <td>${item.name}</td>
                    <td><g:link controller="manageLayers" action="copy"
                                params="[fieldId: item.id, spatialServiceUrl: spatialServiceUrl]">copy</g:link></td>
                </tr>
            </g:each>
            </tbody>
        </table>
    </div>

    <div id="divLocal" class="listBlock" style="display:none">

        <table class="table table-bordered" id="layersLocalOnlyTable">
            <thead>
            <tr>
                <th>Date added</th>
                <th>FieldId</th>
                <th>LayerId</th>
                <th>Name</th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            <g:each var="item" in="${layersLocalOnly}">
                <tr>
                    <td>${item.dt_added}</td>
                    <td>${item.id}</td>
                    <td>${item.layerId}</td>
                    <td>${item.name}</td>
                    <td></td>
                </tr>
            </g:each>
            </tbody>
        </table>
    </div>
</div>

<script src="/spatial-service/js/jquery.js"></script>
<script src="/spatial-service/js/jquery.dataTables.min.js"></script>

<script>
    function confirmDelete(id, name) {
        if (confirm("Permanently delete layer " + name + "?")) {
            var url = "delete/" + id
            $(location).attr('href', url);
        }
    }

    jQuery(document).ready(function () {
        // setup the table
        jQuery('#layersRemoteOnlyTable').dataTable({
            "aaSorting": [
                [0, "desc"]
            ],
            "aLengthMenu": [
                [10, 25, 50, 100, -1],
                [10, 25, 50, 100, "All"]
            ],
            "sPaginationType": "full_numbers",
            "sDom": '<"sort-options"fl<"clear">>rt<"pagination"ip<"clear">>',
            "oLanguage": {
                "sSearch": ""
            }
        });

        jQuery('#layersBothTable').dataTable({
            "aaSorting": [
                [0, "desc"]
            ],
            "aLengthMenu": [
                [10, 25, 50, 100, -1],
                [10, 25, 50, 100, "All"]
            ],
            "sPaginationType": "full_numbers",
            "sDom": '<"sort-options"fl<"clear">>rt<"pagination"ip<"clear">>',
            "oLanguage": {
                "sSearch": ""
            }
        });

        jQuery('#layersLocalOnlyTable').dataTable({
            "aaSorting": [
                [0, "desc"]
            ],
            "aLengthMenu": [
                [10, 25, 50, 100, -1],
                [10, 25, 50, 100, "All"]
            ],
            "sPaginationType": "full_numbers",
            "sDom": '<"sort-options"fl<"clear">>rt<"pagination"ip<"clear">>',
            "oLanguage": {
                "sSearch": ""
            }
        });

        jQuery("div.dataTables_filter input").attr("placeholder", "Filter within results");

        $("#listSelector").change(function () {
            $(".listBlock").hide()
            $("#" + listSelector.value).show();
        });

    });

    function downloadLayers(type) {
        var downloadurl = "/layers-service/layers";
        var query = jQuery("div.dataTables_filter input").val();
        if (type == "json") {
            downloadurl += ".json";
        } else {
            downloadurl += ".csv";
        }
        if (query != "") {
            downloadurl += "?q=" + query;
        }
        location.href = downloadurl;
    }
</script>
</div>
</body>
</html>