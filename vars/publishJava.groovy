def call(Map config){
    
    node {
 
    checkout scm
   
    def gradle = tool 'gradle'
    def maven  = tool 'maven'
    stage('Build') {
       if(config.buildTool == 'gradle') {
           sh "${gradle}/bin/gradle build"
           sh "${gradle}/bin/gradle publishMavenJavaPublicationToMavenRepository"
       }else{
           //maven
           sh "${maven}/bin/mvn clean deploy -Dmaven.test.skip=true"
       }
    }
   
    stage('SonarQube find bugs') {
        def sonarScanner = tool 'sonarScanner'
        withSonarQubeEnv() {
            sh "${sonarScanner}/bin/sonar-scanner"
        }
    }
    build job: config.triggerJob, wait: false
}
}
