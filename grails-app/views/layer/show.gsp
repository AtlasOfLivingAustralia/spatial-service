<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title>${layer.displayname}</title>
    <meta name="breadcrumbs" content="${g.createLink( controller: 'main', action: 'index')}, Spatial Service \\ ${g.createLink( controller: 'manageLayers', action: 'layers')}, Layers"/>
    <meta name="layout" content="main"/>
</head>

<body>
<div class="container-fluid">
    <table class="table table-bordered">
        <tr>
            <td>Description</td>
            <td>${layer.description}</td>
        </tr>
        <tr>
            <td>Short name</td>
            <td>${layer.name}</td>
        </tr>
        <tr>
            <td>Date added</td>
            <td>${layer.dt_added}</td>
        </tr>
        <tr>
            <td>Metadata contact</td>
            <td>${layer.source}</td>
        </tr>
        <tr>
            <td>Organisation role</td>
            <td>${layer.respparty_role}</td>
        </tr>
        <tr>
            <td>Metadata date</td>
            <td>${layer.mddatest}</td>
        </tr>
        <tr>
            <td>Reference date</td>
            <td>${layer.citation_date}</td>
        </tr>
        <tr>
            <td>Licence level</td>
            <td>${layer.licence_level}</td>
        </tr>
        <tr>
            <td>Licence notes</td>
            <td><a href="${layer.licence_link}">${layer.licence_link}</a></td>
        </tr>
        <tr>
            <td>Licence notes</td>
            <td>${layer.licence_notes}</td>
        </tr>
        <tr>
            <td>Type</td>
            <td>
                <g:if test="${layer.type == 'Environmental'}">Environmental (gridded) ${layer.scale}</g:if>
                <g:if test="${layer.type == 'Contextual'}">Contextual (polygon) ${layer.scale}</g:if>
            </td>
        </tr>
        <tr>
            <td>Classification</td>
            <td>${layer.classification1 + ' => ' + layer.classification2}</td>
        </tr>
        <tr>
            <td>Units</td>
            <td>${layer.environmentalvalueunits}</td>
        </tr>
        <tr>
            <td>Data language</td>
            <td>${layer.datalang}</td>
        </tr>
        <tr>
            <td>Notes</td>
            <td>${layer.notes}</td>
        </tr>
        <tr>
            <td>Keywords</td>
            <td>${layer.keywords}</td>
        </tr>
        <tr>
            <td>More information</td>
            <td>
                <g:each var="u" in="${layer.metadatapath.split('\\|')}">
                    <a href="${u}">${u}</a><br/>
                </g:each>
            </td>
        </tr>
        <tr>
            <td>View in spatial portal</td>
            <td><a href="${grailsApplication.config.spatialHubUrl}?layers=${layer.name}">Click to view this layer</a></td>
        </tr>
    </table>
</div>
</body>
</html>