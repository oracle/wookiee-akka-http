#!/bin/bash

projectName="wookiee-akka-http"
echo "Changing $projectName Scala Version to $1, and Scala Artifact Version to $2, and adding repo $3"

sed -i "s/<scala\.version>.*</<scala\.version>$1</" pom.xml
sed -i "s/<scala\.artifact\.version>.*</<scala\.artifact\.version>$2</" pom.xml
sed -i "s/<artifactId>$projectName.*</<artifactId>${projectName}_$2</" pom.xml

if [ "$3" != "" ]; then
  if grep -q $3 pom.xml; then
    echo "URL was already added, skipping"
  else
    escaped=`echo "$3" | sed 's;/;\\/;g'`
    echo "URL Added: $escaped"
    sed -i "s;<repositories>;<repositories>\n<repository>\n<id>local-artifactory<\/id>\n<name>local-artifactory-releases<\/name>\n<url>${escaped}<\/url>\n<\/repository>\n;" pom.xml
  fi
fi