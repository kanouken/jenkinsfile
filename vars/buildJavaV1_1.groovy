  
def notifyBuild(String groupId, String result,String jobName) {
    def template = "勿回复此消息\n ${jobName}-${result}\n ${env.BUILD_URL}"
    // Send notifications
    sh "curl  -d 'group_id=${groupId}&message=${template}' 'coolq:5700/send_group_msg'"
}

//vars/build.groovy main 方法
def call(Map config) {
   node {
   def path  = config.path == null ? "" : "cd " + config.path + " &&"    
   def imageName 
   def jobName
   def tag
   def deploy = true
   //docker registry  此变量在jenkins已在全局变量配置
   def registry = dockerRegistry
   //k8s 部署到k8s空间    
   def namespace = config.namespace 
   //拉取代码
   checkout scm
   def gradle = tool 'gradle'
   def maven = tool 'maven' 
   try{
   //编译代码 目前支持 maven 、gradle
   stage('Build') {
        jobName = JOB_NAME.substring(0,JOB_NAME.indexOf("/"))
        if (BRANCH_NAME.startsWith("release-")) {
        //release
        imageName = jobName + ":" + BRANCH_NAME
        namespace = namespace + "-prd"  
       }else if(BRANCH_NAME == "master"){
        imageName = jobName + ":" + BRANCH_NAME
       }else
       {
        deploy = false
       }
     
           if(config.buildTool == 'gradle'){
              	sh "${path} ${gradle}/bin/gradle clean build -i -x test"
            }else{
                sh "${path} ${maven}/bin/mvn clean package -e -U -Dmaven.test.skip=true"
            }

   }
   
   stage('SonarQube find bugs') {
        def sonarScanner = tool 'sonarScanner'
        withSonarQubeEnv() {
            sh "${path} ${sonarScanner}/bin/sonar-scanner"
        }
    }
   if(deploy) { 
   // docker 编译镜像
   stage('docker build') {
   		sh "${path} docker build -t ${imageName} ."
   }
   
   //docker 打tag
   stage('docker tag ') {
   		tag = registry + "/"+config.namespace + "/" +imageName
   		sh "${path} docker tag  ${imageName} ${tag}"
   }
   
   //将镜像 推送到私有仓库
   stage("docker push") {
   		sh "${path} docker push ${tag}"
   }
   
   //清除本地镜像
   stage("docker clear") {
   		sh "${path} docker rmi ${imageName}"
   		sh "${path} docker rmi ${tag}"
   }
   
   //部署服务
   stage("deploy") {
   		//首次部署需要自行创建服务
   		if(BUILD_NUMBER == 1){
   	  		print "请在rancher 中创建,命名为 : ${jobName}-deploy" 
   	 	}else{
   	 	//在 k8s 中更新
   		  sh "/var/jenkins_home/rancher_cli/rancher kubectl patch deployment ${jobName} -p  '{\"spec\":{\"template\":{\"metadata\":{\"labels\":{\"build-num\":\"$BUILD_NUMBER\"}}}}}' -n ${namespace}"
   	 	  
        }
   }

   }
   

   
   } catch (e) {
           currentBuild.result = "FAILED"
           notifyBuild(config.messageGroupId,"Build failed",jobName+"("+ BRANCH_NAME + ")")
           throw e
       } finally {
          def result =   currentBuild.result == null? "SUCCESS" : currentBuild.result
          
          if (result == 'SUCCESS'){
             notifyBuild(config.messageGroupId,"Build Success",jobName +"("+ BRANCH_NAME + ")")
          }

        
  }
 
}



}
