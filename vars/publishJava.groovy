def call(Map config){
    
    node {
 
    checkout scm
    def path  = config.path == null ? "" : "cd " + config.path + " &&"
    def gradle = tool 'gradle'
    def maven  = tool 'maven'
    
    stage('Build') {
       if(config.buildTool == 'gradle') {
           sh "${path} ${gradle}/bin/gradle build --no-daemon"
           sh "${path} ${gradle}/bin/gradle publishMavenJavaPublicationToMavenRepository --no-daemon"
       }else{
           //maven
           sh "${path} ${maven}/bin/mvn clean deploy -Dmaven.test.skip=true"
       }
    }
   
    stage('SonarQube find bugs') {
        def sonarScanner = tool 'sonarScanner'
        withSonarQubeEnv() {
            sh "${path} ${sonarScanner}/bin/sonar-scanner"
        }
    }
    if(config.triggerJob && config.triggerJob != null){
       build job: config.triggerJob + "/" + BRANCH_NAME, wait: false
    }
   
}
}
