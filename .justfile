# https://just.systems/man/en/

_default:
	@just --list --unsorted

set fallback

MVN	:= "mvn --batch-mode --color=always --no-transfer-progress -Dmaven.plugin.validation=NONE"

# Lints java, yaml and markdown files
@lint:
	{{MVN}} --quiet tidy:check impsort:check formatter:validate
	docker run --rm --read-only --volume $(pwd):/tmp pipelinecomponents/yamllint --config-file /tmp/.yamllint --strict /tmp
	docker run --rm --read-only --volume $(pwd):/tmp tmknom/markdownlint --config /tmp/.markdownlint.yaml /tmp

# Fix java and markdown files
@fix:
	{{MVN}} tidy:pom impsort:sort formatter:format
	docker run --rm --read-only --user $(id -u):$(id -g) --volume $(pwd):/tmp tmknom/markdownlint --config /tmp/.markdownlint.yaml --fix /tmp

# Update maven dependencies
@update:
	{{MVN}} versions:update-properties

# Build project without surefire/invoker tests
@build:
	{{MVN}} verify -Dmaven.test.skip.exec -Dinvoker.skip

# Build project
@verify:
	{{MVN}} verify

# Deploy jar to sonatype snapshot repository
@deploy:
	{{MVN}} deploy -Pdeploy

# Run release as dry-run without tests
@release-dryrun:
	{{MVN}} release:clean
	{{MVN}} release:prepare -DpushChanges=false -DpreparationGoals="clean verify -Dmaven.test.skip -Dinvoker.skip" 
	{{MVN}} release:perform -DlocalCheckout -Dgoals="clean deploy -Dmaven.test.skip -Dinvoker.skip -DskipNexusStagingDeployMojo" 
	git fetch --prune --prune-tags # remove local tag

# Run release and publish to maven central
@release-run:
	{{MVN}} release:clean
	{{MVN}} release:prepare
	{{MVN}} release:perform
