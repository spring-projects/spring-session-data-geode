<html xmlns="http://www.w3.org/1999/xhtml">
<head>
	<title>Request and Session Counts</title>
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
			This application demonstrates how both Spring 'request' and 'session' scoped (proxy) beans are utilized
			in the context of Spring Session when GemFire is used to back the HTTP Session.
		</p>

		<table>
			<thead>
			<tr>
				<th> * </th>
				<th>Session ID</th>
				<th>Session Count</th>
				<th>Request Count</th>
			</tr>
			</thead>
			<tbody>
				<#attempt>
				<tr>
					<td><b>count</b></td>
					<td>${sessionId}</td>
					<td>${sessionCount}</td>
					<td>${requestCount}/></td>
				</tr>
				<#recover>
				</#attempt>
			</tbody>
		</table>
	</div>
</body>
</html>
