name = 'martus-client-linux-zip'

define name, :layout=>create_layout_with_source_as_source(name) do
	project.group = 'org.martus'
	project.version = '1'
	
	zip_name = _("target/MartusClient-#{$build_number}-MacLinux.zip")
	zip(zip_name)
	zip(zip_name).include(_("BuildFiles/Documents/installing_martus.txt"))
	zip(zip_name).include(_("BuildFiles/Documents/license.txt"))
	zip(zip_name).include(_("BuildFiles/Documents/README*.txt"))
	zip(zip_name).include(_("martus-jar-verifier/*.bat"), :path=>'Verifier')
	zip(zip_name).include(_("martus-jar-verifier/*.txt"), :path=>'Verifier')
	zip(zip_name).include(_("BuildFiles/Windows/Winsock95"), :path=>"Win95")
	zip(zip_name).include(artifact(BCPROV_SPEC), :path=>'LibExt')
	zip(zip_name).include(artifact(INFINITEMONKEY_JAR_SPEC), :path=>'LibExt')
	zip(zip_name).include(artifact(JUNIT_SPEC), :path=>'LibExt')
	zip(zip_name).include(artifact(XMLRPC_SPEC), :path=>'LibExt')
	zip(zip_name).include(project('martus-client').package(:sources))
#TODO: Add docs to Mac/Linux zip
#	zip(zip_name).include(docs)
#TODO: Add third-party licenses to Mac/Linux zip
#	zip(zip_name).include(licenses)
	zip(zip_name).include(artifact(LOGI_LICENSE_SPEC), :path=>'licenses/logi')
	
	build(zip_name)
end
