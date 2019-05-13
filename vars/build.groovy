def call(Map config) {
   node {
   def imageName 
   def jobName
   def tag
   //docker registry 
   def registry = dockerRegistry
   def namespace = config.namespace
   //拉取代码
   checkout scm
   def gradle = tool 'gradle'
   
   def mvn = tool 'maven'   


   
   try{
   //编译代码 并执行单元测试
   stage('Build') {
       jobName = JOB_NAME.substring(0,JOB_NAME.indexOf("/"))
       if (BRANCH_NAME.startsWith("release/")) {
        //release  jobname:release-xx
        imageName =  JOB_NAME.substring(0,JOB_NAME.indexOf("/")) + ":" + BRANCH_NAME.replaceAll("/","-")
       	sh "${gradle}/bin/gradle clean build -i -x test"
       } else {
        imageName =  JOB_NAME.substring(0,JOB_NAME.indexOf("/")) + ":" + BRANCH_NAME
        //skip test for non release version
        sh "${gradle}/bin/gradle clean buildx -i -x test"
       }

   }
   
   stage('SonarQube find bugs') {
        def sonarScanner = tool 'sonarScanner'
        withSonarQubeEnv() {
            sh "${sonarScanner}/bin/sonar-scanner"
        }
    }
   // docker 编译镜像
   stage('docker build') {
   		sh "docker build -t ${imageName} ."
   }
   
   //docker 打tag
   stage('docker tag ') {
   		tag = registry + "/"+namespace+ "/" +imageName
   		sh "docker tag  ${imageName} ${tag}"
   }
   
   //将镜像 推送到私有仓库
   stage("docker push") {
   		sh "docker push ${tag}"
   }
   
   //清除本地镜像
   stage("docker clear") {
   		sh "docker rmi ${imageName}"
   		sh "docker rmi ${tag}"
   }
   
   //部署服务
   stage("deploy") {
   		//首次部署需要自行创建服务
   		if(BUILD_NUMBER == 1){
   	  		echo "请在rancher 中创建,命名为 : ${jobName}-deploy" 
   	 	}else{
   	 	//在 k8s 中更新
   		  sh "/var/jenkins_home/rancher_cli/rancher kubectl patch deployment ${jobName} -p  '{\"spec\":{\"template\":{\"metadata\":{\"labels\":{\"build-num\":\"$BUILD_NUMBER\"}}}}}' -n ${namespace}"
   	 	  
        }
   }

   
   

   
   } catch (e) {
           currentBuild.result = "FAILED"
           notifyBuild(config.groupId,"Build failed",jobName)
           throw e
       } finally {
          def result =   currentBuild.result == null? "SUCCESS" : currentBuild.result
          
          if (result == 'SUCCESS'){
             notifyBuild(config.groupId,"Build Success",jobName)
          }

           


  }
 
}


def notifyBuild(String groupId, String result,String jobName) {
    def template = "勿回复此消息\n ${jobName}-${result}\n ${env.BUILD_URL}"
    // Send notifications
    sh "curl  -d 'group_id=${groupId}&message=${template}' 'coolq:5700/send_group_msg'"
}
}
