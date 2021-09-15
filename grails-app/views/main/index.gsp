<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="ala-main"/>
    <title>Spatial Service</title>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'fluid.css')}" type="text/css">
</head>
<body class="fluid">
<div class="container">
<h1>Spatial service</h1>
<p>
    View spatial layers available in the system.
</p>
<ul>
    <li><g:link controller="tabulation" action="index">Tabulations</g:link> - View the available tabulations (2 dimension matrices of contextual variables)</li>
    <li><g:link controller="layer" action="list">Layers</g:link> - View available spatial layers (Grid or Polygon)</li>
</ul>

<h3>Admin tools</h3>
<p>
    This is an administration interface for managing the layers available in the system.
</p>
<ul>
    <li><g:link controller="manageLayers" action="uploads">Manage layer uploads</g:link> - Upload, edit or delete layers from the system</li>
    <li><g:link controller="manageLayers" action="layers">View available layers</g:link> - View lists of existing layers</li>
    <li><g:link controller="tasks" action="index">Background tasks</g:link> - View the status of background task</li>
    <li><g:link controller="manageLayers" action="remote">Copy layers from remote servers</g:link> - transfer layers from test environment to production</li>
</ul>

    <h4>
        Tools
    </h4>
    <ul>
        <li><g:link controller="tasks" action="create"
                    params="${[name: 'Thumbnails']}">Regenerate thumbnails</g:link> - Regenerate thumbnails for the layers</li>
        <li><g:link controller="intersect"
                    action="reloadConfig">Reload intersect configuration</g:link> - Reload intersect configuration (run after adding new layers)</li>
        <li><g:link controller="tasks" action="create"
                    params="${[name: 'DistributionRematchLsid', input: '{"updateAll":true}']}">Rematch checklist and expert distribution LSIDs</g:link> - Rematch expert distribution and checklist LSIDs (using sandbox if configured, otherwise biocache-service)</li>
        <li><g:link controller="log" action="search"
                    params="${[admin: true, accept: 'application/csv', groupBy: 'category1,category2', countBy: 'record,user,session', excludeRoles: 'ROLE_ADMIN', startDate: (java.time.LocalDate.now().minusMonths(6).toString()), endDate: (java.time.LocalDate.now().toString())]}">spatial-hub usage report - last 6 months (csv)</g:link> - Excludes ROLE_ADMIN users</li>
        <li><g:link controller="layer" action="csvlist"
                    params="${[usage: true, months: 6]}">layer usage report - last 6 months (csv)</g:link></li>
        <li><g:link controller="admin" action="defaultGeoserverStyles">Fix layer styles</g:link> - Recreate linear/none linear styles for each Raster layers, Recreate polygon/outlines for Vector layers</li>
        <li><g:link controller="tasks" action="create"
                    params="${[name: 'TabulationCreate']}">Add missing 2-D tabulation</g:link> -Add missing 2-D tabulation</li>
        <li><g:link controller="tasks" action="create"
                    params="${[name: "DownloadRecords"]}">Get data for 2-D tabulation</g:link> -Get data for 2-D tabulation</li>
    </ul>
</div>
</body>
</html>
