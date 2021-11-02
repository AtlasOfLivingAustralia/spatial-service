<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title></title>
    <meta name="layout" content="ala-main"/>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'fluid.css')}" type="text/css">
</head>

<body class="fluid">
<ul class="breadcrumb">
    <li><g:link controller="main" action="index">Home</g:link></li>
</ul>

%{--!sessionView--}%
%{--groupBy=category1&countBy=record--}%
%{--groupBy=category2&countBy=record&category1=category1--}%
%{--category2=category2--}%

%{--sessionView--}%
%{--groupBy=sessionId&countBy=category1--}%
%{--sessionId=sessionId--}%

<a class="btn btn-primary" href="${request.url}?groupBy=category1&countBy=record">Actions</a>
<a class="btn btn-primary" href="${request.url}?groupBy=sessionId&countBy=category1">Sessions</a>

<g:set var="sessionView" value="${params.groupBy == 'sessionId' || params.sessionId}"/>
<g:set var="viewLevel"
       value="${params.groupBy == 'category1' || params.groupBy == 'sessionId' ? 1 : (params.category2 ? 3 : 2)}"/>

<g:if test="${searchResult.size() > 0}">
    <table class="table table-striped">
        <thead>
        <g:each in="${searchResult.getAt(0)}" var="item">
            <th>${item.key}</th>
        </g:each>
        </thead>
        <tbody>
        <g:each in="${searchResult}" var="row">
            <tr>
                <g:each in="${row}" var="item" status="idx">
                    <td>
                        <g:if test="${sessionView}">
                            <g:if test="${idx == 0}">
                                <g:if test="${viewLevel == 1}">
                                    <a href="${request.url}?sessionId=${row.values().getAt(0)}">${item.value}</a>
                                </g:if>
                                <g:if test="${viewLevel == 2}">
                                    ${item.value}
                                </g:if>
                            </g:if>
                            <g:if test="${idx > 0}">
                                ${item.value}
                            </g:if>
                        </g:if>

                        <g:if test="${!sessionView}">
                            <g:if test="${idx > 0}">
                                <g:if test="${viewLevel == 1}">
                                    <a href="${request.url}?groupBy=category2&countBy=record&category1=${row.values().getAt(0)}">${item.value}</a>
                                </g:if>
                                <g:if test="${viewLevel == 2}">
                                    <a href="${request.url}?category2=${row.values().getAt(0)}">${item.value}</a>
                                </g:if>
                                <g:if test="${viewLevel == 3}">
                                    ${item.value}
                                </g:if>
                            </g:if>
                            <g:if test="${idx == 0}">
                                ${item.value}
                            </g:if>
                        </g:if>
                    </td>
                </g:each>
            </tr>
        </g:each>
        </tbody>
    </table>
</g:if>

</div>
</body>
</html>