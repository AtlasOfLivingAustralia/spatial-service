<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main"/>
    <title>Spatial Service</title>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'fluid.css')}" type="text/css">
    <meta http-equiv="refresh" content="0;URL='${url}'" />
</head>
<body class="fluid">
<div class="container">

    <div>
        <g:if test="${status == 401}">
            <div class="alert alert-warning" role="alert">Sign in to access this link.</div>
        </g:if>
        <g:elseif test="${status == 403}">
            <div class="alert alert-warning" role="alert">You do not have permission to access this link. Please use sign in with another account.</div>
        </g:elseif>
    </div>
    <div>
       <div><h2>Redirecting to the login page ...........</h2></div>
       <p/>
       <div>
       If automatic redirection is not occurring in few seconds, <a href="${url}" class="btn btn-info" role="button">Click to login</a>
       </div>
    </div>

</div>
</body>
</html>
