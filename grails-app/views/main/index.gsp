<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main"/>
    <title>Spatial Service</title>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'fluid.css')}" type="text/css">
</head>
<body class="fluid">
<h1>Spatial service</h1>
<p>
    This is an administration interface for managing the layers available in the system.
</p>
<ul>
    <li><g:link controller="tabulation" action="index">Tabulations</g:link> - View the available tabulations (2 dimension matrices of contextual variables)</li>
    <li><g:link controller="layer" action="list">Layers</g:link> - View available spatial layers (Grid or Polygon)</li>
</ul>

<h3>Admin tools</h3>
<ul>
    <li><g:link controller="manageLayers" action="uploads">Manage layer uploads</g:link> - Upload, edit or delete layers from the system</li>
    <li><g:link controller="manageLayers" action="layers">View available layers</g:link> - View lists of existing layers</li>
    <li><g:link controller="tasks" action="index">Background tasks</g:link> - View the status of background task</li>
    <li><g:link controller="manageLayers" action="remote">Copy layers from remote servers</g:link> - transfer layers from test environment to production</li>
</ul>
</body>
</html>
