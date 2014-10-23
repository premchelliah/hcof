Assumptions

	1. All static file references should point to the source domain
	2. JS/CSS files should not refer exact file names in text strings
	3. Non-removable JS comments (starting with /*!) will also be removed
	4. Use the entire source code as an input to the tool
	5. Comment "debugger;" statements or eval them "eval("debugger");", otherwise yui compressor ignores the file
	6. If your project is an EAR, run the tool separately against each WAR for best results

Running the plugin

	1. The plugin works on Java 5 and above, use the supplied library files
	2. config.properties should be added to the classpath
			yuicompressorpath 	-	The path where yuicompressor jar is located. Suggest to use the supplied version.
			clienttextfiles 	-	static text file extensions which can be delivered to the browser and can refer other static files
			clientbinaryfiles	-	static binary file extensions which can be delivered to the browser
			servertextfiles		-	text files which reside only on the server side and can refer other static files
			root				-	Root folder of the project which contains all the files mentioned above
	3. Add the filter to your web/app server. If your static resources are served by HTTP servers add this logic in that layer instead of this filter