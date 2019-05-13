def call(String groupId, String result,String jobName) {
    def template = "勿回复此消息\n ${jobName}-${result}\n ${env.BUILD_URL}"
    // Send notifications
    sh "curl  -d 'group_id=${groupId}&message=${template}' 'coolq:5700/send_group_msg'"
}
