pipeline {
    agent any

    triggers {
        // Polls SCM periodically but the schedule is ignored if a webhook is set up
        githubPush()
        pollSCM('H * * * *')
    }
    parameters {
        string(name: 'ACTION', defaultValue: 'proceed', description: 'Action to take')
    }
    tools {
        terraform 'tf1.6'
    }

    environment {
        // Define a variable to hold the output from the previous stage
        PREVIOUS_STAGE_OUTPUT = ''
    }

    stages {
        stage('Clone Git repo') {
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: 'op/setting_up_instance_and_node_js_app']],
                    userRemoteConfigs: [[
                        url: 'https://github.com/LocalCoding/DevOps_jenkins_tf_ans_node_js_setup.git',
                        credentialsId: 'git_access_PAT'
                    ]]
                ])
            }
        }
        stage('Terraform Plan') {
            steps {
                dir('terraform_ansible_generic_instace_setup_template') {
                    sh '''
                    echo "yes" | terraform init
                    terraform plan -out=terraform.tfplan
                    '''
                    script {
                        env.PREVIOUS_STAGE_OUTPUT = sh(script: 'echo "Output from previous stage"', returnStdout: true).trim()
                    }
                }
            }
        }
        stage('Approval') {
            steps {
                // Echo the output from the previous stage
                echo "Output from the Previous Stage: ${env.PREVIOUS_STAGE_OUTPUT}"
                // Ask for the input to proceed or abort the build
                script {
                    def userInput = input(
                        id: 'userInput', 
                        message: 'Choose to proceed or abort the build:', 
                        parameters: [choice(name: 'Proceed?', choices: ['proceed', 'abort'], description: 'Proceed or Abort')]
                    )
                    if (userInput == 'abort') {
                        error('Aborting the build.')
                    }
                }
            }
        }
        stage('Terraform Apply') {
            steps {
                dir('terraform_ansible_generic_instace_setup_template') {
                    sh 'terraform apply terraform.tfplan'
                }
            }
        }
        stage('Get Terraform Outputs') {
            steps {
                dir('terraform_ansible_generic_instace_setup_template') {
                    sh 'terraform output web-address-nodejs > ./ansible/instance_ip.txt'
                }
            }
        }
        stage('Install Ansible') {
            steps {
                sh '''
                sudo apt-add-repository ppa:ansible/ansible -y
                sudo apt-get update
                sudo apt-get install ansible -y
                '''
            }
        }
        stage('Run Ansible for the battlkships app') {
            steps {
                withCredentials([sshUserPrivateKey(credentialsId: 'access_for_new_node_js_app', keyFileVariable: 'SSH_KEY')]) {
                    dir('terraform_ansible_generic_instace_setup_template/ansible'){
                        sh '''
                        sleep 120
                        ansible-playbook -i instance_ip.txt playbook_nodejs_battleships.yaml -u ubuntu --private-key=$SSH_KEY -e 'ansible_ssh_common_args="-o StrictHostKeyChecking=no"'
                        '''
                    }
                }
            }
        }

        stage('Run Ansible for the dadjokes app') {
            steps {
                withCredentials([sshUserPrivateKey(credentialsId: 'access_for_new_node_js_app', keyFileVariable: 'SSH_KEY')]) {
                    dir('terraform_ansible_generic_instace_setup_template/ansible'){
                        sh '''
                        ansible-playbook -i instance_ip.txt playbook_nodejs_playbook_nodejs_dadjokes.yaml -u ubuntu --private-key=$SSH_KEY -e 'ansible_ssh_common_args="-o StrictHostKeyChecking=no"'
                        '''
                    }
                }
            }
        }
    }
}