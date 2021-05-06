
build:
	mvn clean package
bump:
	mvn versions:set -DgenerateBackupPoms=false
deploy-ossrh:
	JAVA_HOME=/usr/java/jdk-11.0.11+9/ mvn clean deploy -Possrh
