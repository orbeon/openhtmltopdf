#!/bin/bash

# Usage: ./publish-to-orbeon.sh [version]

set -euo pipefail
cd "$(dirname "$0")"

REVISION="${1:-1.1.37-orbeon.4}"
GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-orbeon/openhtmltopdf}"

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [ "$BRANCH" != "orbeon" ]; then
    echo "Refusing to publish: on branch '$BRANCH', expected 'orbeon'." >&2
    exit 1
fi

echo "Publishing $REVISION from branch '$BRANCH' to maven.pkg.github.com/$GITHUB_REPOSITORY ..."

# Only deploy the modules Orbeon Forms needs (build.sbt): core + pdfbox + java2d

GITHUB_REPOSITORY="$GITHUB_REPOSITORY" \
    mvn -B -DskipTests \
    -pl openhtmltopdf-core,openhtmltopdf-pdfbox,openhtmltopdf-java2d \
    deploy -Dgithub-release -Drevision="$REVISION"
