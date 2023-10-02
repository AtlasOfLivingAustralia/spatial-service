<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
 "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <style>
    @page {
        size: ${width}mm ${height}mm;

        margin-top: 0cm;
        margin-left: 0cm;
        margin-right: 0cm;
        margin-bottom: 0cm;
    }
    </style>
</head>

<body>
<div style="width:100%">
    <rendering:inlinePng bytes="${imageBytes}" style="display: block;width: 100%;height: auto;"/>
</div>
</body>
</html>