cd /home/gfora/dev/workspace/marduk/
export MAVEN_OPTS="-Dfile.encoding=UTF-8 -Dslf4j.detectLoggerNameMismatch=true -Denv=dev"
mvn -Prutebanken -DskipTests
cd /home/gfora/dev/workspace/mobi-iti/
docker service rm mobi_iti_ara_marduk-ara
./deploy_swarm.sh ara loc
