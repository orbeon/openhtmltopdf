#!/bin/bash

set -euo pipefail
cd "$(dirname "$0")"

mvn install -DskipTests -Drevision=1.1.37-orbeon.optimization-tests -pl openhtmltopdf-core,openhtmltopdf-pdfbox,openhtmltopdf-java2d -am
