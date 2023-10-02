<%@ page import="au.org.ala.spatial.Task" %>



<div class="fieldcontain ${hasErrors(bean: taskInstance, field: 'message', 'error')} ">
    <label for="message">
        <g:message code="task.message.label" default="Message"/>

    </label>
    <g:textField name="message" value="${taskInstance?.message}"/>

</div>

<div class="fieldcontain ${hasErrors(bean: taskInstance, field: 'url', 'error')} ">
    <label for="url">
        <g:message code="task.url.label" default="Url"/>

    </label>
    <g:textField name="url" value="${taskInstance?.url}"/>

</div>

<div class="fieldcontain ${hasErrors(bean: taskInstance, field: 'slave', 'error')} ">
    <label for="slave">
        <g:message code="task.slave.label" default="Slave"/>

    </label>
    <g:textField name="slave" value="${taskInstance?.slave}"/>

</div>

<div class="fieldcontain ${hasErrors(bean: taskInstance, field: 'email', 'error')} ">
    <label for="email">
        <g:message code="task.email.label" default="Email"/>

    </label>
    <g:textField name="email" value="${taskInstance?.email}"/>

</div>

<div class="fieldcontain ${hasErrors(bean: taskInstance, field: 'tag', 'error')} ">
    <label for="tag">
        <g:message code="task.tag.label" default="Tag"/>

    </label>
    <g:textField name="tag" value="${taskInstance?.tag}"/>

</div>

<div class="fieldcontain ${hasErrors(bean: taskInstance, field: 'children', 'error')} ">
    <label for="children">
        <g:message code="task.children.label" default="Children"/>

    </label>

    <ul class="one-to-many">
        <g:each in="${taskInstance?.children ?}" var="c">
            <li><g:link controller="tasks" action="show" id="${c.id}">${c?.encodeAsHTML()}</g:link></li>
        </g:each>
        <li class="add">
            <g:link controller="tasks" action="create"
                    params="['task.id': taskInstance?.id]">${message(code: 'default.add.label', args: [message(code: 'task.label', default: 'Task')])}</g:link>
        </li>
    </ul>

</div>

<div class="fieldcontain ${hasErrors(bean: taskInstance, field: 'created', 'error')} required">
    <label for="created">
        <g:message code="task.created.label" default="Created"/>
        <span class="required-indicator">*</span>
    </label>
    <g:datePicker name="created" precision="day" value="${taskInstance?.created}"/>

</div>

<div class="fieldcontain ${hasErrors(bean: taskInstance, field: 'err', 'error')} ">
    <label for="err">
        <g:message code="task.err.label" default="Err"/>

    </label>

</div>

<div class="fieldcontain ${hasErrors(bean: taskInstance, field: 'input', 'error')} ">
    <label for="input">
        <g:message code="task.input.label" default="Input"/>

    </label>

    <ul class="one-to-many">
        <g:each in="${taskInstance?.input ?}" var="i">
            <li><g:link controller="inputParameter" action="show" id="${i.id}">${i?.encodeAsHTML()}</g:link></li>
        </g:each>
        <li class="add">
            <g:link controller="inputParameter" action="create"
                    params="['task.id': taskInstance?.id]">${message(code: 'default.add.label', args: [message(code: 'inputParameter.label', default: 'InputParameter')])}</g:link>
        </li>
    </ul>

</div>

<div class="fieldcontain ${hasErrors(bean: taskInstance, field: 'log', 'error')} ">
    <label for="log">
        <g:message code="task.log.label" default="Log"/>

    </label>

</div>

<div class="fieldcontain ${hasErrors(bean: taskInstance, field: 'name', 'error')} required">
    <label for="name">
        <g:message code="task.name.label" default="Name"/>
        <span class="required-indicator">*</span>
    </label>
    <g:textField name="name" required="" value="${taskInstance?.name}"/>

</div>

<div class="fieldcontain ${hasErrors(bean: taskInstance, field: 'output', 'error')} ">
    <label for="output">
        <g:message code="task.output.label" default="Output"/>

    </label>
    <g:select name="output" from="${au.org.ala.spatial.service.OutputParameter.list()}" multiple="multiple"
              optionKey="id" size="5" value="${taskInstance?.output*.id}" class="many-to-many"/>

</div>

<div class="fieldcontain ${hasErrors(bean: taskInstance, field: 'parent', 'error')} required">
    <label for="parent">
        <g:message code="task.parent.label" default="Parent"/>
        <span class="required-indicator">*</span>
    </label>
    <g:select id="parent" name="parent.id" from="${au.org.ala.spatial.Task.list()}" optionKey="id" required=""
              value="${taskInstance?.parent?.id}" class="many-to-one"/>

</div>

<div class="fieldcontain ${hasErrors(bean: taskInstance, field: 'status', 'error')} required">
    <label for="status">
        <g:message code="task.status.label" default="Status"/>
        <span class="required-indicator">*</span>
    </label>
    <g:field name="status" type="number" value="${taskInstance.status}" required=""/>

</div>

