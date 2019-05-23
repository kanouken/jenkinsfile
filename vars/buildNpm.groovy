def notifyBuild(String groupId, String result,String jobName) {
    def template = "勿回复此消息\n ${jobName}-${result}\n ${env.BUILD_URL}"
    // Send notifications
    sh "curl  -d 'group_id=${groupId}&message=${template}' 'coolq:5700/send_group_msg'"
}

def call(Map config){
  node {
   def imageName
   def jobName
   def tag
   //docker registry
   def registry = dockerRegistry
   def namespace = config.namespace
   def deploy = true
   //拉取代码
   checkout scm

   //设置环境变量
   def nodejs = tool 'nodejs'
   env.PATH = "${nodejs}/bin:${env.PATH}"
   //运行代码检查


   //编译代码 并执行单元测试
   try{
   stage('Build') {
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
        //no deploy
       }else if(BRANCH_NAME == "master"){
        //FIXME 为了之前项目的测试 加的这一条件 全部转化后 请去掉
        imageName = jobName + ":master"
          
       }else{
          deploy = false
       }
       
        if(config.preScript && config.preScript != null){
          sh "${config.preScript}"
        }
        //sh "rm -rf ./node_modules/cyberway-msf-frontend-* && rm -f ./package-lock.json"
        sh "npm install --registry=http://192.168.0.5:8980/repository/npmGroup/"
        sh "npm run build"

    

   }
   if(deploy){
   // docker 编译镜像
   stage('docker build') {
   		sh "docker build -t ${imageName} ."
   }

   //docker 打tag
   stage('docker tag ') {
   		tag = registry + "/"+config.namespace+ "/" +imageName
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
