echo Building docker image

mvn clean package -DskipTests

MVN_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout | sed -r "s/\x1B\[([0-9]{1,3}(;[0-9]{1,2};?)?)?[mGK]//g")
IMAGE_NAME=registry.okina.fr/mobiiti/marduk:"${MVN_VERSION}"

docker build -t "${IMAGE_NAME}" --build-arg JAR_FILE=target/marduk-"${MVN_VERSION}".jar .
docker push "${IMAGE_NAME}"
