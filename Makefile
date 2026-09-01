build:
	mvn clean package
bump:
	mvn versions:set -DgenerateBackupPoms=false	

publish-central: gpg-unlock
	mvn clean deploy -Pcentral

gpg-unlock:
ifndef MAVEN_GPG_PASSPHRASE
	@# signing key: the gpg.keyname each developer sets in the central profile of ~/.m2/settings.xml (falls back to the gpg default key)
	$(eval GPG_KEYNAME = $(filter-out null object or invalid expression,$(shell mvn -q -N -Pcentral help:evaluate -Dexpression=gpg.keyname -DforceStdout 2>/dev/null)))
	@# without a display (e.g. over ssh) pinentry must use the terminal: tell gpg-agent which one before asking
	$(eval GPG_TTY_SETUP = $(if $(DISPLAY)$(WAYLAND_DISPLAY),,export GPG_TTY=$$(tty) && gpg-connect-agent updatestartuptty /bye >/dev/null &&))
	@$(GPG_TTY_SETUP) gpg --clearsign $(if $(GPG_KEYNAME),--local-user "$(GPG_KEYNAME)") --output /dev/null </dev/null
endif

update-code-snippets:
	$(eval REV := $(shell git rev-parse HEAD))
	@ls */readme.md | xargs -I{} sed -i -E "s/blob\/[a-f0-9]{40}\//blob\/$(REV)\//g" {}

check-updates:
	mvn -U -ntp net.optionfactory:anarchitect-maven-plugin:LATEST:check-updates
