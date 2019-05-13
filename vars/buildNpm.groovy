def call(Map config){
  node {
   def imageName
   def jobName
   def tag
   //docker registry
   def registry = 
   def namespace = config.namespace

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
       if (BRANCH_NAME.startsWith("release/")) {
        //release  jobname:release-xx
        imageName =  JOB_NAME.substring(0,JOB_NAME.indexOf("/")) + ":" + BRANCH_NAME.replaceAll("/","-")

        sh "rm -rf ./node_modules/cyberway-msf-frontend-* && rm -f ./package-lock.json"

        sh "${nodejs}/bin/npm install --registry=http://192.168.0.5:8980/repository/npmGroup/"
        sh "${nodejs}/bin/npm run build"

       } else {
        imageName =  JOB_NAME.substring(0,JOB_NAME.indexOf("/")) + ":" + BRANCH_NAME

        sh "rm -rf ./node_modules/cyberway-msf-frontend-* && rm -f ./package-lock.json"
        sh "${nodejs}/bin/npm install --registry=http://192.168.0.5:8980/repository/npmGroup/"
        sh "${nodejs}/bin/npm run build"
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

          mail to: 'czeqin@cyberway.net.cn',
                       subject: "Failed Pipeline: ${currentBuild.fullDisplayName}",
                       body: "Something is wrong with ${env.BUILD_URL}"
           throw e
       } finally {
           def currentResult = currentBuild.result ?: 'SUCCESS'
           if (currentResult == 'UNSTABLE') {
           }

           def previousResult = currentBuild.previousBuild?.result
           if (previousResult != null && previousResult != currentResult) {
           }


       }

}

}
