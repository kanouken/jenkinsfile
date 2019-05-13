def call(){
    def sonarScanner = tool 'sonarScanner'
        withSonarQubeEnv() {
            sh "${sonarScanner}/bin/sonar-scanner"
        }
}
