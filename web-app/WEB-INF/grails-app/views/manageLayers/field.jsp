<%@ page contentType="text/html" pageEncoding="UTF-8" %>
<%@
        taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@
        taglib uri="/tld/ala.tld" prefix="ala" %>
<%@
        taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%-- <%@include file="../common/top.jsp" %> --%>
<jsp:include page="../common/top.jsp?fluid=true"/>
<header id="page-header">
    <div class="inner">
        <nav id="breadcrumb">
            <ol>
                <li><a href="http://www.ala.org.au">Home</a></li>
                <li><a href="/layers-service/ingest">Layer Ingestion</a></li>
                <li class="last">Add/Edit Field</li>
            </ol>
        </nav>

    </div><!--inner-->
    <title>uploaded file: ${filename}</title>
</header>
<style>
    input[readonly] {
        background-color: lightgrey;
    }

    input {
        width: 100%;
    }

    .error {
        color: red;
        font-size: 20px;
    }
</style>
<div class="inner">
    <div class="col-wide last" style="width:100%">

        <c:if test="${error != null}">
            <b class="error">${error}</b>
            <br/>
            <br/>
        </c:if>

        <div><h1>${is_field ? "Updating a Field" : "Adding a Field" }</h1></div>
        <c:if test="${layer_creation != null && !has_layer}"><h3 style="color:red">Layer created: <b>${has_layer}</b>
        </h3><br/><b>********* LAYER CREATION IN PROGRESS, WAIT AND REFRESH PAGE *******</b><br/></c:if>

        <c:if test="${has_layer}">
            <h3 style="display:inline-block">Existing fields ( ${fn:length(fields)} )</h3>
            <button onclick="existingFields.style.display='none'">hide</button>
            <button onclick="existingFields.style.display=''">show</button>
            </td>
            <table id="existingFields" class="table-borders" style="display:none">
                <tr>
                    <td>Id</td>
                    <td>name</td>
                    <td>description</td>
                    <td>sid</td>
                    <td>sname</td>
                </tr>
                <c:forEach items="${fields}" var="item">
                    <tr>
                        <td>${item.id}</td>
                        <td>${item.name}</td>
                        <td>${item.desc}</td>
                        <td>${item.sid}</td>
                        <td>${item.sname}</td>
                        <td><a href="../../field/${raw_id}/${item.id}">edit</a></td>
                        <td><a href="../../delete_field/${raw_id}/${item.id}">delete</a></td>
                    </tr>
                </c:forEach>
                <tr>
                    <td colspan="5"><a href="../../field/${raw_id}/">Add new Field</a></td>
                </tr>
            </table>

        </c:if>
        <div>
            <h3 style="display:inline-block">Geoserver preview</h3>
            <button onclick="geoserverPreview.style.display='none'">hide</button>
            <button onclick="geoserverPreview.style.display=''">show</button>
            </td>
            <br>
            <iframe id="geoserverPreview" style="display:none" src="${test_url}" width="800" height="600"></iframe>
        </div>


        <h3>New field</h3>

        <form method="POST" action=".">
            <table class="table-borders">
                <tr>
                    <td>
                        <label for="name" style="color:red">Name (default is layer display name)
                            [${displayname}]:</label></td>
                    <td>
                        <input type="text" id="name" name="name" value="${name}" maxlength="256"/>
                    </td>
                </tr>
                <tr>
                    <td>

                        <label for="desc">Description [${description}]:</label></td>
                    <td>
                        <input type="text" id="desc" name="desc" maxlength="256" value="${desc}"/>
                    </td>
                </tr>
                <tr>
                    <td>

                        <label for="type" style="color:red">[TODO make this work) Type (only for using an Environmental
                            to a Contextual from gridfile when the appropriate textfile is available):</label></td>
                    <td>
                        <select id="type" name="type">
                            <option value="c"
                                    <c:if test="${type == 'c'}">selected</c:if> >Contextual from shapefile
                            </option>
                            <option value="a"
                                    <c:if test="${type == 'a'}">selected</c:if> >Contextual from gridfile (creates types
                                'a' - classes and
                                'b' - individual shapes)
                            </option>
                            <option value="e"
                                    <c:if test="${type == 'e'}">selected</c:if> >Environmental
                            </option>
                        </select>
                    </td>
                </tr>

                <c:if test="${type == 'c'}">
                    <tr>
                        <td>
                            <label for="sid" style="color:red">Source id (contextual only; comma delimited list of shape
                                file column names for aggregation to
                                create unique objects, e.g. "id") ${sid}</label></td>
                        <td>
                            <!--input type="text" id="sid" name="sid" value="${sid}" maxlength="256"/-->
                            <select id="sid" name="sid" style="color:red">
                                <option value=""
                                        <c:if test="${sid == '' || sid == null}">selected</c:if> >(none)
                                </option>
                                <c:forEach items="${columns}" var="column">
                                    <c:if test="${column != 'the_geom'}">
                                        <option value="${column}"
                                                <c:if test="${sid == column}">selected</c:if> >${column}</option>
                                    </c:if>
                                </c:forEach>
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td>

                            <label for="sname" style="color:red">Source name (contextual only; column names with
                                optional formatting for the name for each unique
                                Objects, e.g. "name (state)"</label></td>
                        <td>
                            <!--input type="text" id="sname" name="sname" value="${sname}" maxlength="256"/-->
                            <select id="sname" name="sname">
                                <option value=""
                                        <c:if test="${sname == '' || sname == null}">selected</c:if> >(none)
                                </option>
                                <c:forEach items="${columns}" var="column">
                                    <c:if test="${column != 'the_geom'}">
                                        <option value="${column}"
                                                <c:if test="${sname == column}">selected</c:if> >${column}</option>
                                    </c:if>
                                </c:forEach>
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td>

                            <label for="sdesc">Source description (contextual only; column names with optional
                                formatting for the description
                                for each unique Objects, e.g. "state (area_km)"</label></td>
                        <td>
                            <!--input type="text" id="sdesc" name="sdesc" value="${sdesc}" maxlength="256"/-->
                            <select id="sdesc" name="sdesc">
                                <option value=""
                                        <c:if test="${sdesc == '' || sdesc == null}">selected</c:if> >(none)
                                </option>
                                <c:forEach items="${columns}" var="column">
                                    <c:if test="${column != 'the_geom'}">
                                        <option value="${column}"
                                                <c:if test="${sdesc == column}">selected</c:if> >${column}</option>
                                    </c:if>
                                </c:forEach>
                            </select>
                        </td>
                    </tr>
                </c:if>

                <tr>
                    <td>

                        <label for="indb">This field is intended for inclusion in biocache (SOLR index)</label></td>
                    <td>
                        <input type="checkbox" id="indb" name="indb"
                               <c:if test="${indb}">checked</c:if>  />
                    </td>
                </tr>
                <tr>
                    <td>

                        <c:if test="${type != 'e'}">
                        <label for="namesearch">This field's objects are included in the objects search (gaz
                            autocomplete). (Contextual
                            only)</label></td>
                    <td>
                        <input type="checkbox" id="namesearch" name="namesearch"
                               <c:if test="${namesearch}">checked</c:if> />
                    </td>
                </tr>
                <tr>
                    <td>
                        </c:if>

                        <label for="defaultlayer">When more than ONE field is created from a source layer, use the
                            'defaultlayer' for
                            intersection requests</label></td>
                    <td>
                        <input type="checkbox" id="defaultlayer" name="defaultlayer"
                               <c:if test="${defaultlayer}">checked</c:if> />
                    </td>
                </tr>
                <tr>
                    <td>

                        <c:if test="${type != 'e'}">
                        <label for="intersect">Include this Field in calculated Tabulations (Contextual only)</label>
                    </td>
                    <td>
                        <input type="checkbox" id="intersect" name="intersect"
                               <c:if test="${intersect}">checked</c:if> />
                    </td>
                </tr>
                <tr>
                    <td>
                        </c:if>

                        <c:if test="${type != 'e'}">
                        <label for="layerbranch">Used by Spatial Portal. When Contextual Layers are listed by their
                            Classifications in a
                            tree structure, list objects in the layer as individual leaves in the tree. (Contextual only
                            and
                            defaultlayer=true) [classification=${classification1} > ${classification2}]</label></td>
                    <td>
                        <input type="checkbox" id="layerbranch" name="layerbranch"
                               <c:if test="${layerbranch}">checked</c:if> />
                    </td>
                </tr>
                <tr>
                    <td>
                        </c:if>

                        <label for="analysis">This field is available in the Spatial Portal Tool lists</label></td>
                    <td>
                        <input type="checkbox" id="analysis" name="analysis"
                               <c:if test="${analysis}">checked</c:if> />
                    </td>
                </tr>
                <tr>
                    <td>

                        <label for="addtomap">This field is available in the Spatial Portal Add To Map list</label></td>
                    <td>
                        <input type="checkbox" id="addtomap" name="addtomap"
                               <c:if test="${addtomap}">checked</c:if> />
                    </td>
                </tr>
                <tr>
                    <td>

                        <label for="enabled">Enabled (makes the field available for use, disable to remove field from
                            use)</label></td>
                    <td>
                        <input type="checkbox" id="enabled" name="enabled"
                               <c:if test="${enabled}">checked</c:if> />
                    </td>
                </tr>
                <tr>
                    <td>

                        <c:if test="${layer_creation == null}">
                            <input type="submit" class="button" value=${is_field ? "Update Field" : "Add Field" }/>
                        </c:if>

                        <input type="hidden" name="raw_id" value="${raw_id}"/>

                        <input type="hidden" name="id" value="${id}"/>
                    </td>
                </tr>
            </table>
        </form>
    </div>
</div>
</body>
</html>
