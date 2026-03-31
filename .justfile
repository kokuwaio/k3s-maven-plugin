# https://just.systems/man/en/

[private]
@default:
    just --list --unsorted

# Run linter.
@lint:
    docker run --rm --read-only --volume=$PWD:$PWD:ro --workdir=$PWD kokuwaio/just:1.48.1
    docker run --rm --read-only --volume=$PWD:$PWD:ro --workdir=$PWD kokuwaio/yamllint:v1.38.0
    docker run --rm --read-only --volume=$PWD:$PWD:rw --workdir=$PWD kokuwaio/markdownlint:0.48.0 --fix
    docker run --rm --read-only --volume=$PWD:$PWD:ro --workdir=$PWD kokuwaio/renovate-config-validator:43
    docker run --rm --read-only --volume=$PWD:$PWD:ro --workdir=$PWD woodpeckerci/woodpecker-cli lint
