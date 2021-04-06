
build:
	mvn clean package
deploy-orrsh:
	JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64/ mvn clean deploy -Possrh
