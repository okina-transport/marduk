cd /home/gfora/dev/workspace/marduk/
export MAVEN_OPTS="-Dfile.encoding=UTF-8 -Dslf4j.detectLoggerNameMismatch=true -Denv=dev"
mvn -Prutebanken -DskipTests
cd /home/gfora/dev/workspace/ratp-mosaic-docker-stack/
docker service rm mosaic_marduk
./deploy_swarm-local.sh docker-compose-local.yml
