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
   build job: config.triggerJob, wait: false
}
}
