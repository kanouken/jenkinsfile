//vars/dockerBuild  config.imageName,  config.namespace
def call(Map config){
     	def tag = dockerRegistry + "/"+config.namespace+ "/" +config.imageName
   		sh "docker build -t ${config.imageName} ."
   		sh "docker tag  ${config.imageName} ${tag}"
   		sh "docker push ${tag}"
   		sh "docker rmi ${config.imageName}"
   		sh "docker rmi ${tag}"
}
