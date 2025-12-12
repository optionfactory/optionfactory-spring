


build:
	mvn clean package
bump:
	mvn versions:set -DgenerateBackupPoms=false
deploy-central:
	mvn clean deploy -Pcentral

update-code-snippets:
	$(eval REV := $(shell git rev-parse HEAD))
	@ls */readme.md | xargs -I{} sed -i -E "s/blob\/[a-f0-9]{40}\//blob\/$(REV)\//g" {}

check-updates:
	mvn org.codehaus.mojo:versions-maven-plugin:2.16.2:display-dependency-updates  -Dmaven.version.ignore='.*-.*,.*CR\d,.*Alpha\d,.*Beta\d' -DdependencyManagementExcludes='*'
	mvn org.codehaus.mojo:versions-maven-plugin:2.16.2:display-plugin-updates -Dmaven.version.ignore='.*-.*'


