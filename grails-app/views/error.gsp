<!DOCTYPE html>
<html>
<head>
    <title><g:if env="development">Grails Runtime Exception</g:if><g:else>Error</g:else></title>
    <meta name="layout" content="main">
    <g:if env="development"><link rel="stylesheet" href="${resource(dir: 'css', file: 'errors.css')}"
                                  type="text/css"></g:if>
</head>

<body>
    <ul class="errors">
        <li>An error has occurred</li>
    </ul>
    <div 
<g:if env="development">
  style="display:block;"
</g:if>
<g:else>
  style="display:none;"
</g:else>
>
    <g:renderException exception="${exception}"/>
</div>

</body>
</html>
