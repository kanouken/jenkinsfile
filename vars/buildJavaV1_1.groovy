  
def notifyBuild(String groupId, String result,String jobName) {
    //
    def changeString =  getChangeString()
    def template = "勿回复此消息\n ${jobName}-${result}\n ${env.BUILD_URL} \n ${changeString}"
    // Send notifications
    sh "curl  -d 'group_id=${groupId}&message=${template}' 'coolq:5700/send_group_msg'"
}

@NonCPS
def getChangeString() {
 MAX_MSG_LEN = 100
 def changeString = ""
 echo "Gathering SCM changes"
 def changeLogSets = currentBuild.changeSets
 for (int i = 0; i < changeLogSets.size(); i++) {
 def entries = changeLogSets[i].items
 for (int j = 0; j < entries.length; j++) {
 def entry = entries[j]
 truncated_msg = entry.msg.take(MAX_MSG_LEN)
 changeString += "--${truncated_msg}  [${entry.author}]\n"
 }
 }

 if (!changeString) {
 changeString = " - 无"
 }
 return changeString
}

//vars/build.groovy main 方法
def call(Map config) {
   node {
   def path  = config.path == null ? "" : "cd " + config.path + " &&"    
   def imageName 
   def jobName
   def tag
   def deploy = true
   def build  = true
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
         print BRANCH_NAME
         print BUILD_TAG
        jobName = JOB_NAME.substring(0,JOB_NAME.indexOf("/"))
        //测试环境
        if (BRANCH_NAME.startsWith("release/")) {
        imageName = jobName + ":" + BRANCH_NAME.replaceAll("release","rc").replaceAll("/","-")
        namespace = namespace + "-test"
        //开发环境  
       }else if(BRANCH_NAME == "develop"){
         imageName = jobName + ":unstable"
         namespace = namespace + "-dev"
        //uat && prd   
       }else if(BRANCH_NAME.startsWith("v")){
            imageName = jobName + ":" +  BRANCH_NAME
            namespace = namespace + "-uat" 
            //prd 手动发布
            deploy = false 
        //no deploy
       }else{
          build = false 
          deploy = false
       }
     
           if(config.buildTool == 'gradle'){
              	sh "${path} ${gradle}/bin/gradle clean build -i -x test --no-daemon"
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
   if(build) {
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
   }

   if(deploy) {
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
