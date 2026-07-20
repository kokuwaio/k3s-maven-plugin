# https://just.systems/man/en/

[private]
@default:
    just --list --unsorted

# Run linter.
@lint:
    docker run --rm --read-only --volume=$PWD:$PWD:ro --workdir=$PWD kokuwaio/just:1.57.0
    docker run --rm --read-only --volume=$PWD:$PWD:ro --workdir=$PWD kokuwaio/yamllint:v1.38.0
    docker run --rm --read-only --volume=$PWD:$PWD:rw --workdir=$PWD kokuwaio/markdownlint:0.49.1 --fix
    docker run --rm --read-only --volume=$PWD:$PWD:ro --workdir=$PWD kokuwaio/renovate-config-validator:43
    docker run --rm --read-only --volume=$PWD:$PWD:ro --workdir=$PWD woodpeckerci/woodpecker-cli:v3 lint

# Runs given invoker test.
[group("maven")]
it TEST:
    just mvn -P-dev verify -DskipTests -Dinvoker.test={{ TEST }} -Dinvoker.streamLogs=true

# Run mvn commands. Checks if mvnd is present.
[group("maven")]
[private]
mvn +ARGS:
    echo "$(command -v mvnd >/dev/null 2>&1 && echo "mvnd" || echo "mvn") --no-transfer-progress {{ ARGS }}"
    eval "$(command -v mvnd >/dev/null 2>&1 && echo "mvnd" || echo "mvn") --no-transfer-progress {{ ARGS }}"
