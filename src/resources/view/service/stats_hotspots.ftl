<!DOCTYPE html>
<#escape x as x?html><html>
<head>
<title>${action.getText('service')}${action.getText('stats')}</title>
</head>
<body>
<#assign baseurl=actionBaseUrl>
<#if request.queryString?has_content>
<#list request.queryString?split('&') as pair>
	<#assign name=pair?keep_before('=')>
	<#if name!='_'&&name!='service'>
		<#assign baseurl+=baseurl?contains('?')?then('&','?')+pair>
	</#if>
</#list>
</#if>
<#if hotspots?size gt 0>
	<table class="table table-hover table-striped table-bordered sortable resizable" style="white-space: nowrap;">
		<caption><h4>${action.getText('hotspots')}</h4></caption>
		<thead>
		<tr>
			<th>${action.getText('service')}</th>
			<th style="width:120px;">${action.getText('times')}</th>
		</tr>
		</thead>
		<tbody>
		<#list hotspots.entrySet() as entry>
		<tr class="warning">
			<td><a href="<@url value="${baseurl+baseurl?contains('?')?then('&','?')+'service='+(entry.key)?url}"/>" class="ajax view" data-replacement="count">${entry.key}</a></td>
			<td>${entry.value?string}</td>
		</tr>
		</#list>
		</tbody>
	</table>
</#if>
</body>
</html></#escape>
