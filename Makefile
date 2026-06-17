


build:
	mvn clean package
bump:
	mvn versions:set -DgenerateBackupPoms=false	
publish-central:
	mvn clean deploy -Pcentral

update-code-snippets:
	$(eval REV := $(shell git rev-parse HEAD))
	@ls */readme.md | xargs -I{} sed -i -E "s/blob\/[a-f0-9]{40}\//blob\/$(REV)\//g" {}

check-updates:
	mvn -U -ntp net.optionfactory:anarchitect-maven-plugin:LATEST:check-updates
