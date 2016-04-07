<%@ page import="grails.converters.JSON" contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title>Demo - run tasks</title>
    <meta name="layout" content="main"/>
    <g:javascript library="jquery"/>
    <g:javascript library="jquery-ui"/>
</head>

<body>
<ul class="breadcrumb">
    <li><g:link controller="main" action="index">Home</g:link></li>
    <li class="active">Demo - tasks</li>
</ul>

<g:set var="capabilities" value="${JSON.parse(g.include(controller: "admin", action: "capabilities").toString())}"/>
<div class="container-fluid">
    <form class="navbar-form navbar-left" role="search" action="http://spatial-dev.ala.org.au/ws/layers/search"
          method="get">
        <div class="form-group">
            <input type="text" class="form-control" id="layer-search" placeholder="Search layers" name="q"></div>
        <button type="submit" class="btn btn-primary">Search</button>
    </form>

    <table class="table table-bordered">
        <g:each var='ckey' in="${capabilities.keySet()}">
            <tr>
                <td rowspan="2">${ckey}</td>
                <td>${capabilities.getAt(ckey).description}</td>
            </tr>
            <tr>
                <td>
                    <form class="form-group">
                        <table class="table table-view">
                            <g:each var='ikey' in="${capabilities.getAt(ckey).input.keySet()}">
                                <tr>
                                    <td>${ikey}</td>
                                    <td>${capabilities.getAt(ckey).input.get(ikey).description}</td>
                                    <td>${capabilities.getAt(ckey).input.get(ikey).type}</td>
                                    <td><input class="form-control" id="${ikey}"/></td>
                                </tr>
                            </g:each>
                            <tr><td><input class="form-control" type="submit"/></td></tr>
                        </table>
                    </form>
                </td>
            </tr>
        </g:each>
    </table>
</div>
<g:javascript>
    $(function () {
        // autocomplete on navbar search input
        $("#layer-search").autocomplete('http://local.ala.org.au:8081/spatial-service/layer/search', {
            extraParams: {limit: 100},
            dataType: 'json',
            parse: function (data) {
                var rows = new Array();
                for (var i = 0; i < data.length; i++) {
                    rows[i] = {
                        data: data[i],
                        value: data[i],
                        result: data[i]
                    };
                }
                return rows;
            },
            matchSubset: false,
            formatItem: function (row, i, n) {
                return row;
            },
            cacheLength: 10,
            minChars: 3,
            scroll: false,
            max: 10,
            selectFirst: false
        });
    });
</g:javascript>
</body>
</html>
