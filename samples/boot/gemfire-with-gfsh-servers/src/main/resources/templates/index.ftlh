<html xmlns="http://www.w3.org/1999/xhtml">
<head>
	<title>Session Attributes</title>
	<style type="text/css">
		body {
			padding: 1em;
		}
	</style>
</head>
<body>
	<div class="container">
		<h1>Description</h1>
		<p>
			This application demonstrates how to use a GemFire instance to back your session. Notice that there
			is no JSESSIONID cookie. We are also able to customize the way of identifying what the requested
			session id is.
		</p>

		<h1>Try it</h1>

		<form role="form" action="./session" method="post">
			<label for="attributeName">Attribute Name</label>
			<input id="attributeName" type="text" name="attributeName"/>
			<label for="attributeValue">Attribute Value</label>
			<input id="attributeValue" type="text" name="attributeValue"/>
			<input type="submit" value="Set Attribute"/>
		</form>

		<hr/>

		<table>
			<thead>
			<tr>
				<th>Attribute Name</th>
				<th>Attribute Value</th>
			</tr>
			</thead>
			<tbody>
				<#attempt>
				<#list sessionAttributes as attributeName, attributeValue>
				<tr>
					<td>${attributeName}</td>
					<td>${attributeValue}</td>
				</tr>
				</#list>
				<#recover>
				</#attempt>
			</tbody>
		</table>
	</div>
</body>
</html>
