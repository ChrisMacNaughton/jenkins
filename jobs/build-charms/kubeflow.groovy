@Library('juju-pipeline@master') _

def exec(cmd) {
    sh "sudo lxc exec ${CONTAINER} -- bash -c '${cmd}'"
}

boolean ゴゴゴ = false

pipeline {
    agent {
        label 'runner-amd64'
    }
    /* XXX: Global $PATH setting doesn't translate properly in pipelines
     https://stackoverflow.com/questions/43987005/jenkins-does-not-recognize-command-sh
     */
    environment {
        PATH = "${utils.cipaths}"
        CONTAINER = "kubeflow-release-${uuid()}"
    }
    options {
        ansiColor('xterm')
        timestamps()
    }
    stages {
        // stage('Set Start Time') {
        //     steps {
        //         setStartTime()
        //     }
        // }
        stage('Create virtualenv') {
            steps {
                sh 'virtualenv venv -p python3.6'
                sh 'venv/bin/python -m pip install boto3'
            }
        }
        stage('Check for new commits') {
            steps {
                sh 'git clone https://github.com/juju-solutions/bundle-kubeflow.git'
                script {
                    ゴゴゴ = sh(script: 'venv/bin/python jobs/build-charms/ddbkf.py check', returnStdout: true).trim() == 'GO'
                }
            }
        }
        stage('Setup LXC') {
            steps {
                sh 'sudo lxc profile show kfpush || sudo lxc profile copy default kfpush'
                sh 'sudo lxc profile edit kfpush < jobs/build-charms/lxc.profile'
                sh "sudo lxc launch -p default -p kfpush ubuntu:18.04 ${CONTAINER}"
                sh "sudo lxc file push -p ~/.go-cookies ${CONTAINER}/root/.go-cookies"
                sh "sudo lxc file push -p ~/.local/share/juju/store-usso-token ${CONTAINER}/root/.local/share/juju/store-usso-token"
                exec "sudo snap set system proxy.http='http://squid.internal:3128'"
                exec "sudo snap set system proxy.https='http://squid.internal:3128'"
            }
            when { expression { ゴゴゴ } }
        }
        stage('Wait for snap') {
            options {
                retry(20)
            }
            steps {
                exec 'sudo snap install core'
            }
            when { expression { ゴゴゴ } }
        }
        stage('Install dependencies') {
            steps {
                exec 'sudo snap install charm --classic'
                exec 'sudo snap install juju --classic'
                exec 'sudo snap install juju-helpers --classic --edge'
                exec 'sudo apt update && sudo apt install -y docker.io'
            }
            when { expression { ゴゴゴ } }
        }
        stage('Release Kubeflow Bundle') {
            steps {
                // Copy forked mysql interface from charmed-osm into bundle dir so that
                // it gets picked up by charm build.
                exec 'git clone git://git.launchpad.net/canonical-osm'
                exec 'git clone https://github.com/juju-solutions/bundle-kubeflow.git'
                exec 'cp -r canonical-osm/charms/interfaces/juju-relation-mysql bundle-kubeflow/mysql'

                // Publish bundle to charm store
                exec 'cd bundle-kubeflow && CHARM_BUILD_DIR=/tmp/charms juju bundle publish --url cs:~kubeflow-charmers/kubeflow'
            }
            when { expression { ゴゴゴ } }
        }
        stage('Update DDB') {
            steps {
                sh 'venv/bin/python jobs/build-charms/ddbkf.py update'
            }
            when { expression { ゴゴゴ } }
        }
    }
    post {
        // success {
        //     setPass()
        // }
        // failure {
        //     setFail()
        // }
        // always {
        //     setEndTime()
        // }
        cleanup {
            // saveMeta()
            sh 'rm -rf venv'
            sh "sudo lxc delete --force ${CONTAINER} || true"
        }
    }
}
