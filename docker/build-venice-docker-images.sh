cd ../
./gradlew shadowJar
cd docker

repository="${1:-venicedb}"
oss_release="${2:-0.4.17}"

set -x -e
echo "Building docker images for repository $repository, version $oss_release"

head_hash=$(git rev-parse --short HEAD)
version=$oss_release

cp *py *yaml venice-client/ 
cp ../clients/venice-push-job/build/libs/venice-push-job-all.jar venice-client/
cp ../clients/venice-thin-client/build/libs/venice-thin-client-all.jar venice-client/
cp ../clients/venice-admin-tool/build/libs/venice-admin-tool-all.jar venice-client/
cp *py *yaml venice-server/ 
cp ../services/venice-server/build/libs/venice-server-all.jar venice-server/
cp *py *yaml venice-controller/ 
cp ../services/venice-controller/build/libs/venice-controller-all.jar venice-controller/
cp *py *yaml venice-router/ 
cp ../services/venice-router/build/libs/venice-router-all.jar venice-router/

targets=(venice-controller venice-server venice-router venice-client)

for target in ${targets[@]}; do
    docker buildx build --load --platform linux/amd64 -t "$repository/$target:$version" $target
done

rm -f venice-client/venice-push-job-all.jar
rm -f venice-client/venice-thin-client-all.jar
rm -f venice-client/venice-admin-tool-all.jar
rm -f venice-server/venice-server-all.jar
rm -f venice-controller/venice-controller-all.jar
rm -f venice-router/venice-router-all.jar
rm */*.py */*yaml

if [[ -z $SKIP_DOCKER_PUSH ]]
then
  for target in ${targets[@]}; do
     docker push $repository/$target:$version
  done
else
  echo "Skipping docker push"
fi