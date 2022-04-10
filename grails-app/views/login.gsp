<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="ala-main"/>
    <title>Spatial Service</title>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'fluid.css')}" type="text/css">
    <g:if test="${status == 401}">
        <meta http-equiv="refresh" content="3;URL='${url}'" />
    </g:if>
</head>
<body class="fluid">
<div class="container">

    <div>
        <g:if test="${status == 401}">
            <div class="alert alert-warning" role="alert">Sign in to access this link.</div>
            <div>
                <div><h2>Redirecting to the login page ...........</h2></div>
                <p/>
                <div>
                    If automatic redirection is not occurring in few seconds, <a href="${url}" class="btn btn-info" role="button">Click to login</a>
                </div>
            </div>
        </g:if>
        <g:elseif test="${status == 403}">
            <div class="alert alert-warning" role="alert">You do not have permission to access this link. Please sign in with another account.</div>
        </g:elseif>
    </div>
</div>
</body>
</html>
