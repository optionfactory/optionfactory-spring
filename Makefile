
build:
	mvn clean package
bump:
	mvn versions:set -DgenerateBackupPoms=false
deploy-ossrh:
	JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64/ mvn clean deploy -Possrh
