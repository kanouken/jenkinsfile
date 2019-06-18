def call(Map config){

  node  {
   //拉取代码
   checkout scm
   //设置环境变量
   def nodejs = tool 'nodejs'
   env.PATH = "${nodejs}/bin:${env.PATH}"
   stage('publish') {
         sh "${nodejs}/bin/npm publish"
   }
     if(config.triggerJob && config.triggerJob != null){
       def branch = BRANCH_NAME
       if(BRANCH_NAME.contains("/")){
           branch = BRANCH_NAME.split("/")[0] + '%2F' + BRANCH_NAME.split("/")[1]
       } 
       build job: config.triggerJob + "/" + branch, wait: false
    }
}
}
