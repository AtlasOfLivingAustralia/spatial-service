<%@ page import="au.org.ala.spatial.service.Task" %>
<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main">
    <g:set var="entityName" value="${message(code: 'task.label', default: 'Task')}"/>
    <title><g:message code="default.show.label" args="[entityName]"/></title>
</head>

<body>
<a href="#show-task" class="skip" tabindex="-1"><g:message code="default.link.skip.label"
                                                           default="Skip to content&hellip;"/></a>

<div class="nav" role="navigation">
    <ul>
        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
        <li><g:link class="list" action="index"><g:message code="default.list.label" args="[entityName]"/></g:link></li>
        <li><g:link class="create" action="create"><g:message code="default.new.label"
                                                              args="[entityName]"/></g:link></li>
    </ul>
</div>

<div id="show-task" class="content scaffold-show" role="main">
    <h1><g:message code="default.show.label" args="[entityName]"/></h1>
    <g:if test="${flash.message}">
        <div class="message" role="status">${flash.message}</div>
    </g:if>
    <ol class="property-list task">

        <g:if test="${taskInstance?.message}">
            <li class="fieldcontain">
                <span id="message-label" class="property-label"><g:message code="task.message.label"
                                                                           default="Message"/></span>

                <span class="property-value" aria-labelledby="message-label"><g:fieldValue bean="${taskInstance}"
                                                                                           field="message"/></span>

            </li>
        </g:if>

        <g:if test="${taskInstance?.url}">
            <li class="fieldcontain">
                <span id="url-label" class="property-label"><g:message code="task.url.label" default="Url"/></span>

                <span class="property-value" aria-labelledby="url-label"><g:fieldValue bean="${taskInstance}"
                                                                                       field="url"/></span>

            </li>
        </g:if>

        <g:if test="${taskInstance?.slave}">
            <li class="fieldcontain">
                <span id="slave-label" class="property-label"><g:message code="task.slave.label"
                                                                         default="Slave"/></span>

                <span class="property-value" aria-labelledby="slave-label"><g:fieldValue bean="${taskInstance}"
                                                                                         field="slave"/></span>

            </li>
        </g:if>

        <g:if test="${taskInstance?.email}">
            <li class="fieldcontain">
                <span id="email-label" class="property-label"><g:message code="task.email.label"
                                                                         default="Email"/></span>

                <span class="property-value" aria-labelledby="email-label"><g:fieldValue bean="${taskInstance}"
                                                                                         field="email"/></span>

            </li>
        </g:if>

        <g:if test="${taskInstance?.tag}">
            <li class="fieldcontain">
                <span id="tag-label" class="property-label"><g:message code="task.tag.label" default="Tag"/></span>

                <span class="property-value" aria-labelledby="tag-label"><g:fieldValue bean="${taskInstance}"
                                                                                       field="tag"/></span>

            </li>
        </g:if>

        <g:if test="${taskInstance?.children}">
            <li class="fieldcontain">
                <span id="children-label" class="property-label"><g:message code="task.children.label"
                                                                            default="Children"/></span>

                <g:each in="${taskInstance.children}" var="c">
                    <span class="property-value" aria-labelledby="children-label"><g:link controller="tasks"
                                                                                          action="show"
                                                                                          id="${c.id}">${c?.encodeAsHTML()}</g:link></span>
                </g:each>

            </li>
        </g:if>

        <g:if test="${taskInstance?.created}">
            <li class="fieldcontain">
                <span id="created-label" class="property-label"><g:message code="task.created.label"
                                                                           default="Created"/></span>

                <span class="property-value" aria-labelledby="created-label"><g:formatDate
                        date="${taskInstance?.created}"/></span>

            </li>
        </g:if>

        <g:if test="${taskInstance?.err}">
            <li class="fieldcontain">
                <span id="err-label" class="property-label"><g:message code="task.err.label" default="Err"/></span>

                <span class="property-value" aria-labelledby="err-label"><g:fieldValue bean="${taskInstance}"
                                                                                       field="err"/></span>

            </li>
        </g:if>

        <g:if test="${taskInstance?.input}">
            <li class="fieldcontain">
                <span id="input-label" class="property-label"><g:message code="task.input.label"
                                                                         default="Input"/></span>

                <g:each in="${taskInstance.input}" var="i">
                    <span class="property-value" aria-labelledby="input-label"><g:link controller="inputParameter"
                                                                                       action="show"
                                                                                       id="${i.id}">${i?.encodeAsHTML()}</g:link></span>
                </g:each>

            </li>
        </g:if>

        <g:if test="${taskInstance?.log}">
            <li class="fieldcontain">
                <span id="log-label" class="property-label"><g:message code="task.log.label" default="Log"/></span>

                <span class="property-value" aria-labelledby="log-label"><g:fieldValue bean="${taskInstance}"
                                                                                       field="log"/></span>

            </li>
        </g:if>

        <g:if test="${taskInstance?.name}">
            <li class="fieldcontain">
                <span id="name-label" class="property-label"><g:message code="task.name.label" default="Name"/></span>

                <span class="property-value" aria-labelledby="name-label"><g:fieldValue bean="${taskInstance}"
                                                                                        field="name"/></span>

            </li>
        </g:if>

        <g:if test="${taskInstance?.output}">
            <li class="fieldcontain">
                <span id="output-label" class="property-label"><g:message code="task.output.label"
                                                                          default="Output"/></span>

                <g:each in="${taskInstance.output}" var="o">
                    <span class="property-value" aria-labelledby="output-label"><g:link controller="outputParameter"
                                                                                        action="show"
                                                                                        id="${o.id}">${o?.encodeAsHTML()}</g:link></span>
                </g:each>

            </li>
        </g:if>

        <g:if test="${taskInstance?.parent}">
            <li class="fieldcontain">
                <span id="parent-label" class="property-label"><g:message code="task.parent.label"
                                                                          default="Parent"/></span>

                <span class="property-value" aria-labelledby="parent-label"><g:link controller="tasks" action="show"
                                                                                    id="${taskInstance?.parent?.id}">${taskInstance?.parent?.encodeAsHTML()}</g:link></span>

            </li>
        </g:if>

        <g:if test="${taskInstance?.status}">
            <li class="fieldcontain">
                <span id="status-label" class="property-label"><g:message code="task.status.label"
                                                                          default="Status"/></span>

                <span class="property-value" aria-labelledby="status-label"><g:fieldValue bean="${taskInstance}"
                                                                                          field="status"/></span>

            </li>
        </g:if>

    </ol>
    <g:form url="[resource: taskInstance, action: 'delete']" method="DELETE">
        <fieldset class="buttons">
            <g:link class="edit" action="edit" resource="${taskInstance}"><g:message code="default.button.edit.label"
                                                                                     default="Edit"/></g:link>
            <g:actionSubmit class="delete" action="delete"
                            value="${message(code: 'default.button.delete.label', default: 'Delete')}"
                            onclick="return confirm('${message(code: 'default.button.delete.confirm.message', default: 'Are you sure?')}');"/>
        </fieldset>
    </g:form>
</div>
</body>
</html>
