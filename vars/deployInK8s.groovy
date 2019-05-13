//config.namespace config.jobName
def call(map config){
   		  sh "/var/jenkins_home/rancher_cli/rancher kubectl patch deployment ${config.jobName} -p  '{\"spec\":{\"template\":{\"metadata\":{\"labels\":{\"build-num\":\"$BUILD_NUMBER\"}}}}}' -n ${config.namespace}"
}
